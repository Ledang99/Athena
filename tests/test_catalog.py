from __future__ import annotations

import shutil
import zipfile
from io import BytesIO
from pathlib import Path

import pymupdf
from ebooklib import epub
from fastapi.testclient import TestClient

from backend.main import create_app


def make_pdf(path: Path, title: str, author: str, text: str) -> None:
    document = pymupdf.open()
    document.set_metadata({"title": title, "author": author})
    page = document.new_page()
    page.insert_text((72, 72), text)
    document.save(path)
    document.close()


def make_epub(path: Path, title: str, author: str) -> None:
    book = epub.EpubBook()
    book.set_identifier("9781234567890")
    book.set_title(title)
    book.add_author(author)
    chapter = epub.EpubHtml(title="Opening", file_name="opening.xhtml", lang="en")
    chapter.content = "<h1>Opening</h1><p>A short test chapter.</p>"
    book.add_item(chapter)
    book.toc = (chapter,)
    book.spine = ["nav", chapter]
    book.add_item(epub.EpubNcx())
    book.add_item(epub.EpubNav())
    epub.write_epub(path, book)


def setup_catalog(tmp_path: Path) -> tuple[TestClient, Path]:
    library = tmp_path / "library"
    library.mkdir()
    original = library / "deep-work.pdf"
    make_pdf(original, "Deep Work", "Cal Newport", "First edition")
    shutil.copyfile(original, library / "deep-work-copy.pdf")
    make_pdf(
        library / "deep-work-revised.pdf",
        "Deep Work",
        "Cal Newport",
        "A revised file with different content",
    )
    make_epub(library / "the-pragmatic-programmer.epub", "The Pragmatic Programmer", "David Thomas")

    app = create_app(tmp_path / "athena.db")
    database = app.state.database
    with database.connect() as connection:
        cursor = connection.execute(
            "INSERT INTO folders (path, path_key) VALUES (?, ?)",
            (str(library.resolve()), str(library.resolve()).casefold()),
        )
        folder_id = int(cursor.lastrowid)
    app.state.scanner.scan_folder(folder_id)
    return TestClient(app), library


def test_scan_search_and_stats(tmp_path: Path) -> None:
    client, _ = setup_catalog(tmp_path)

    stats = client.get("/api/stats").json()
    assert stats == {
        "total": 4,
        "pdf": 3,
        "epub": 1,
        "folders": 1,
        "exact_duplicate_groups": 1,
        "possible_duplicate_groups": 1,
    }

    by_title = client.get("/api/books", params={"q": "pragmatic"}).json()
    assert by_title["total"] == 1
    assert by_title["items"][0]["author"] == "David Thomas"

    by_author = client.get("/api/books", params={"q": "Cal Newport"}).json()
    assert by_author["total"] == 3


def test_duplicate_groups_distinguish_exact_and_possible(tmp_path: Path) -> None:
    client, _ = setup_catalog(tmp_path)

    exact = client.get("/api/duplicates", params={"kind": "exact"}).json()
    assert exact["total"] == 1
    assert len(exact["groups"][0]["books"]) == 2

    possible = client.get("/api/duplicates", params={"kind": "possible"}).json()
    assert possible["total"] == 1
    assert len(possible["groups"][0]["books"]) == 3
    assert len({book["sha256"] for book in possible["groups"][0]["books"]}) == 2


def test_rescan_removes_missing_files_from_catalog(tmp_path: Path) -> None:
    client, library = setup_catalog(tmp_path)
    folder_id = client.get("/api/folders").json()[0]["id"]
    (library / "the-pragmatic-programmer.epub").unlink()

    client.app.state.scanner.scan_folder(folder_id)

    stats = client.get("/api/stats").json()
    assert stats["total"] == 3
    assert stats["epub"] == 0


def test_android_download_is_a_valid_zip_archive(tmp_path: Path) -> None:
    app = create_app(tmp_path / "athena.db")
    response = TestClient(app).get("/api/downloads/android")

    assert response.status_code == 200
    assert response.headers["content-type"] == "application/zip"
    assert "attachment" in response.headers["content-disposition"]
    with zipfile.ZipFile(BytesIO(response.content)) as archive:
        assert archive.testzip() is None
        assert archive.namelist() == ["project-athena-v0.3.0-debug.apk"]
