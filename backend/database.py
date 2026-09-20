from __future__ import annotations

import os
import sqlite3
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator


def default_database_path() -> Path:
    data_dir = Path(
        os.environ.get("ATHENA_DATA_DIR", Path.home() / ".project-athena")
    )
    return data_dir / "athena.db"


class Database:
    def __init__(self, path: Path | str | None = None) -> None:
        self.path = Path(path) if path else default_database_path()
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.initialize()

    @contextmanager
    def connect(self) -> Iterator[sqlite3.Connection]:
        connection = sqlite3.connect(self.path, timeout=30)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys = ON")
        connection.execute("PRAGMA journal_mode = WAL")
        try:
            yield connection
            connection.commit()
        except Exception:
            connection.rollback()
            raise
        finally:
            connection.close()

    def initialize(self) -> None:
        with self.connect() as connection:
            connection.executescript(
                """
                CREATE TABLE IF NOT EXISTS folders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    path TEXT NOT NULL,
                    path_key TEXT NOT NULL UNIQUE,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    last_scan_at TEXT,
                    file_count INTEGER NOT NULL DEFAULT 0
                );

                CREATE TABLE IF NOT EXISTS scan_runs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    folder_id INTEGER NOT NULL REFERENCES folders(id)
                        ON DELETE CASCADE,
                    status TEXT NOT NULL,
                    started_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    completed_at TEXT,
                    discovered INTEGER NOT NULL DEFAULT 0,
                    indexed INTEGER NOT NULL DEFAULT 0,
                    errors INTEGER NOT NULL DEFAULT 0,
                    message TEXT
                );

                CREATE TABLE IF NOT EXISTS books (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    folder_id INTEGER NOT NULL REFERENCES folders(id)
                        ON DELETE CASCADE,
                    path TEXT NOT NULL,
                    path_key TEXT NOT NULL UNIQUE,
                    filename TEXT NOT NULL,
                    file_type TEXT NOT NULL,
                    title TEXT NOT NULL,
                    author TEXT,
                    isbn TEXT,
                    size_bytes INTEGER NOT NULL,
                    modified_ns INTEGER NOT NULL,
                    sha256 TEXT NOT NULL,
                    identity_key TEXT,
                    scan_error TEXT,
                    last_seen_scan_id INTEGER REFERENCES scan_runs(id),
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                );

                CREATE INDEX IF NOT EXISTS idx_books_folder
                    ON books(folder_id);
                CREATE INDEX IF NOT EXISTS idx_books_title
                    ON books(title COLLATE NOCASE);
                CREATE INDEX IF NOT EXISTS idx_books_author
                    ON books(author COLLATE NOCASE);
                CREATE INDEX IF NOT EXISTS idx_books_hash
                    ON books(sha256);
                CREATE INDEX IF NOT EXISTS idx_books_identity
                    ON books(identity_key);
                """
            )
            connection.execute(
                """
                UPDATE scan_runs
                SET status = 'failed',
                    completed_at = CURRENT_TIMESTAMP,
                    message = 'Scan was interrupted'
                WHERE status = 'running'
                """
            )
