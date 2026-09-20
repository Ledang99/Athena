from __future__ import annotations

import hashlib
import os
import re
import threading
import unicodedata
from dataclasses import dataclass
from pathlib import Path

import pymupdf
from ebooklib import epub

from backend.database import Database


SUPPORTED_EXTENSIONS = {".pdf", ".epub"}
ISBN_PATTERN = re.compile(r"(?:97[89][-\s]?)?\d(?:[-\s]?\d){8,12}[\dXx]")


def path_key(path: Path) -> str:
    return str(path.resolve()).casefold()


def clean_filename(stem: str) -> str:
    return re.sub(r"\s+", " ", stem.replace("_", " ").replace(".", " ")).strip()


def clean_text(value: str | None) -> str | None:
    if not value:
        return None
    cleaned = re.sub(r"\s+", " ", value).strip()
    return cleaned or None


def normalize_text(value: str) -> str:
    decomposed = unicodedata.normalize("NFKD", value)
    ascii_value = "".join(
        character for character in decomposed if not unicodedata.combining(character)
    )
    return re.sub(r"[^a-z0-9]+", " ", ascii_value.casefold()).strip()


def make_identity_key(title: str, author: str | None) -> str | None:
    normalized_title = normalize_text(title)
    normalized_author = normalize_text(author or "")
    if not normalized_title or not normalized_author:
        return None
    return f"{normalized_title}|{normalized_author}"


def file_hash(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def first_metadata_value(
    values: list[tuple[str, dict[str, str]]] | None,
) -> str | None:
    if not values:
        return None
    return clean_text(values[0][0])


@dataclass(slots=True)
class BookMetadata:
    title: str
    author: str | None
    isbn: str | None
    error: str | None = None


def read_pdf_metadata(path: Path) -> BookMetadata:
    fallback_title = clean_filename(path.stem)
    try:
        with pymupdf.open(path) as document:
            metadata = document.metadata or {}
            title = clean_text(metadata.get("title")) or fallback_title
            author = clean_text(metadata.get("author"))
            subject = clean_text(metadata.get("subject")) or ""
            keywords = clean_text(metadata.get("keywords")) or ""
            identifier_text = f"{subject} {keywords}"
            isbn_match = ISBN_PATTERN.search(identifier_text)
            isbn = (
                re.sub(r"[-\s]", "", isbn_match.group(0)) if isbn_match else None
            )
            return BookMetadata(title=title, author=author, isbn=isbn)
    except Exception as error:
        return BookMetadata(
            title=fallback_title,
            author=None,
            isbn=None,
            error=f"Metadata unavailable: {error}",
        )


def read_epub_metadata(path: Path) -> BookMetadata:
    fallback_title = clean_filename(path.stem)
    try:
        book = epub.read_epub(str(path), options={"ignore_ncx": True})
        title = first_metadata_value(book.get_metadata("DC", "title")) or fallback_title
        author = first_metadata_value(book.get_metadata("DC", "creator"))
        isbn = None
        for value, attributes in book.get_metadata("DC", "identifier"):
            candidate = clean_text(value) or ""
            scheme = str(attributes.get("scheme", "")).casefold()
            match = ISBN_PATTERN.search(candidate)
            if match and ("isbn" in scheme or len(re.sub(r"\D", "", candidate)) >= 10):
                isbn = re.sub(r"[-\s]", "", match.group(0))
                break
        return BookMetadata(title=title, author=author, isbn=isbn)
    except Exception as error:
        return BookMetadata(
            title=fallback_title,
            author=None,
            isbn=None,
            error=f"Metadata unavailable: {error}",
        )


def read_metadata(path: Path) -> BookMetadata:
    if path.suffix.casefold() == ".pdf":
        return read_pdf_metadata(path)
    return read_epub_metadata(path)


class LibraryScanner:
    def __init__(self, database: Database) -> None:
        self.database = database
        self._active_folders: set[int] = set()
        self._lock = threading.Lock()

    def is_scanning(self, folder_id: int) -> bool:
        with self._lock:
            return folder_id in self._active_folders

    def start(self, folder_id: int) -> bool:
        with self._lock:
            if folder_id in self._active_folders:
                return False
            self._active_folders.add(folder_id)
        thread = threading.Thread(
            target=self._run_and_release,
            args=(folder_id,),
            daemon=True,
            name=f"athena-scan-{folder_id}",
        )
        thread.start()
        return True

    def _run_and_release(self, folder_id: int) -> None:
        try:
            self.scan_folder(folder_id)
        finally:
            with self._lock:
                self._active_folders.discard(folder_id)

    def scan_folder(self, folder_id: int) -> int:
        with self.database.connect() as connection:
            folder = connection.execute(
                "SELECT id, path FROM folders WHERE id = ?", (folder_id,)
            ).fetchone()
            if folder is None:
                raise ValueError("Library folder not found")
            cursor = connection.execute(
                "INSERT INTO scan_runs (folder_id, status) VALUES (?, 'running')",
                (folder_id,),
            )
            scan_id = int(cursor.lastrowid)

        root = Path(folder["path"])
        discovered = 0
        indexed = 0
        errors = 0

        try:
            for current_root, directories, files in os.walk(root, followlinks=False):
                directories[:] = [
                    directory
                    for directory in directories
                    if not directory.startswith(".")
                ]
                for filename in files:
                    path = Path(current_root) / filename
                    if path.suffix.casefold() not in SUPPORTED_EXTENSIONS:
                        continue
                    discovered += 1
                    try:
                        self._index_file(folder_id, scan_id, path)
                        indexed += 1
                    except (OSError, ValueError):
                        errors += 1

                    if discovered % 25 == 0:
                        self._update_progress(scan_id, discovered, indexed, errors)

            with self.database.connect() as connection:
                connection.execute(
                    """
                    DELETE FROM books
                    WHERE folder_id = ?
                      AND (last_seen_scan_id IS NULL OR last_seen_scan_id != ?)
                    """,
                    (folder_id, scan_id),
                )
                file_count = connection.execute(
                    "SELECT COUNT(*) FROM books WHERE folder_id = ?", (folder_id,)
                ).fetchone()[0]
                connection.execute(
                    """
                    UPDATE folders
                    SET last_scan_at = CURRENT_TIMESTAMP, file_count = ?
                    WHERE id = ?
                    """,
                    (file_count, folder_id),
                )
                connection.execute(
                    """
                    UPDATE scan_runs
                    SET status = 'completed',
                        completed_at = CURRENT_TIMESTAMP,
                        discovered = ?,
                        indexed = ?,
                        errors = ?
                    WHERE id = ?
                    """,
                    (discovered, indexed, errors, scan_id),
                )
        except Exception as error:
            with self.database.connect() as connection:
                connection.execute(
                    """
                    UPDATE scan_runs
                    SET status = 'failed',
                        completed_at = CURRENT_TIMESTAMP,
                        discovered = ?,
                        indexed = ?,
                        errors = ?,
                        message = ?
                    WHERE id = ?
                    """,
                    (discovered, indexed, errors + 1, str(error), scan_id),
                )
            raise

        return scan_id

    def _update_progress(
        self, scan_id: int, discovered: int, indexed: int, errors: int
    ) -> None:
        with self.database.connect() as connection:
            connection.execute(
                """
                UPDATE scan_runs
                SET discovered = ?, indexed = ?, errors = ?
                WHERE id = ?
                """,
                (discovered, indexed, errors, scan_id),
            )

    def _index_file(self, folder_id: int, scan_id: int, path: Path) -> None:
        resolved = path.resolve()
        stat = resolved.stat()
        key = path_key(resolved)

        with self.database.connect() as connection:
            existing = connection.execute(
                """
                SELECT id, size_bytes, modified_ns
                FROM books
                WHERE path_key = ?
                """,
                (key,),
            ).fetchone()
            if (
                existing
                and existing["size_bytes"] == stat.st_size
                and existing["modified_ns"] == stat.st_mtime_ns
            ):
                connection.execute(
                    """
                    UPDATE books
                    SET last_seen_scan_id = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """,
                    (scan_id, existing["id"]),
                )
                return

        digest = file_hash(resolved)
        metadata = read_metadata(resolved)
        identity = make_identity_key(metadata.title, metadata.author)
        values = (
            folder_id,
            str(resolved),
            key,
            resolved.name,
            resolved.suffix.casefold().lstrip("."),
            metadata.title,
            metadata.author,
            metadata.isbn,
            stat.st_size,
            stat.st_mtime_ns,
            digest,
            identity,
            metadata.error,
            scan_id,
        )

        with self.database.connect() as connection:
            connection.execute(
                """
                INSERT INTO books (
                    folder_id, path, path_key, filename, file_type, title, author,
                    isbn, size_bytes, modified_ns, sha256, identity_key,
                    scan_error, last_seen_scan_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(path_key) DO UPDATE SET
                    folder_id = excluded.folder_id,
                    path = excluded.path,
                    filename = excluded.filename,
                    file_type = excluded.file_type,
                    title = excluded.title,
                    author = excluded.author,
                    isbn = excluded.isbn,
                    size_bytes = excluded.size_bytes,
                    modified_ns = excluded.modified_ns,
                    sha256 = excluded.sha256,
                    identity_key = excluded.identity_key,
                    scan_error = excluded.scan_error,
                    last_seen_scan_id = excluded.last_seen_scan_id,
                    updated_at = CURRENT_TIMESTAMP
                """,
                values,
            )
