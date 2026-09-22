from __future__ import annotations

from pathlib import Path
from typing import Any, Literal

from fastapi import FastAPI, HTTPException, Query, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

from backend.database import Database
from backend.scanner import LibraryScanner, path_key


class FolderCreate(BaseModel):
    path: str = Field(min_length=1, max_length=4096)


def row_to_dict(row: Any) -> dict[str, Any]:
    return dict(row)


def create_app(database_path: Path | str | None = None) -> FastAPI:
    app = FastAPI(title="Project Athena", version="0.1.0")
    database = Database(database_path)
    scanner = LibraryScanner(database)
    app.state.database = database
    app.state.scanner = scanner

    app.add_middleware(
        CORSMiddleware,
        allow_origins=["http://localhost:43181", "http://127.0.0.1:43181"],
        allow_credentials=False,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    @app.get("/api/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.get("/api/downloads/android")
    def download_android_apk() -> FileResponse:
        releases_dir = Path(__file__).resolve().parent.parent / "releases"
        # Serve the latest version available in releases directory
        apk_files = sorted(releases_dir.glob("project-athena-v*-debug.apk"))
        zip_files = sorted(releases_dir.glob("project-athena-v*-debug.zip"))
        
        target_path = zip_files[-1] if zip_files else (apk_files[-1] if apk_files else None)
        if not target_path or not target_path.exists():
            raise HTTPException(status_code=404, detail="Android build is unavailable")

        media_type = "application/zip" if target_path.suffix == ".zip" else "application/vnd.android.package-archive"
        return FileResponse(
            target_path,
            media_type=media_type,
            filename=target_path.name,
        )

    @app.get("/api/downloads/android/apk")
    def download_android_apk_direct() -> FileResponse:
        releases_dir = Path(__file__).resolve().parent.parent / "releases"
        apk_files = sorted(releases_dir.glob("project-athena-v*-debug.apk"))
        if not apk_files or not apk_files[-1].exists():
            raise HTTPException(status_code=404, detail="Android APK is unavailable")

        target_path = apk_files[-1]
        return FileResponse(
            target_path,
            media_type="application/vnd.android.package-archive",
            filename=target_path.name,
        )

    @app.get("/api/folders")
    def list_folders() -> list[dict[str, Any]]:
        with database.connect() as connection:
            rows = connection.execute(
                """
                SELECT folders.*,
                       (
                           SELECT status
                           FROM scan_runs
                           WHERE folder_id = folders.id
                           ORDER BY id DESC
                           LIMIT 1
                       ) AS scan_status
                FROM folders
                ORDER BY created_at ASC
                """
            ).fetchall()
        result = []
        for row in rows:
            folder = row_to_dict(row)
            folder["is_scanning"] = scanner.is_scanning(row["id"])
            result.append(folder)
        return result

    @app.post("/api/folders", status_code=status.HTTP_201_CREATED)
    def add_folder(payload: FolderCreate) -> dict[str, Any]:
        candidate = Path(payload.path).expanduser()
        try:
            resolved = candidate.resolve(strict=True)
        except (OSError, RuntimeError) as error:
            raise HTTPException(
                status_code=400, detail=f"Folder cannot be opened: {error}"
            ) from error
        if not resolved.is_dir():
            raise HTTPException(status_code=400, detail="Path must be a folder")

        key = path_key(resolved)
        with database.connect() as connection:
            existing = connection.execute(
                "SELECT id, path FROM folders"
            ).fetchall()
            for folder in existing:
                existing_path = Path(folder["path"])
                if resolved == existing_path:
                    raise HTTPException(
                        status_code=409, detail="This folder is already in Athena"
                    )
                if resolved.is_relative_to(existing_path) or existing_path.is_relative_to(
                    resolved
                ):
                    raise HTTPException(
                        status_code=409,
                        detail=(
                            "Library folders cannot overlap. Add only the highest "
                            "shared folder."
                        ),
                    )
            cursor = connection.execute(
                "INSERT INTO folders (path, path_key) VALUES (?, ?)",
                (str(resolved), key),
            )
            folder_id = int(cursor.lastrowid)

        scanner.start(folder_id)
        return {
            "id": folder_id,
            "path": str(resolved),
            "file_count": 0,
            "is_scanning": True,
            "scan_status": "running",
        }

    @app.delete("/api/folders/{folder_id}", status_code=status.HTTP_204_NO_CONTENT)
    def remove_folder(folder_id: int) -> None:
        if scanner.is_scanning(folder_id):
            raise HTTPException(
                status_code=409, detail="Wait for the current scan to finish"
            )
        with database.connect() as connection:
            cursor = connection.execute(
                "DELETE FROM folders WHERE id = ?", (folder_id,)
            )
            if cursor.rowcount == 0:
                raise HTTPException(status_code=404, detail="Library folder not found")

    @app.post("/api/folders/{folder_id}/scan", status_code=status.HTTP_202_ACCEPTED)
    def scan_folder(folder_id: int) -> dict[str, str]:
        with database.connect() as connection:
            exists = connection.execute(
                "SELECT 1 FROM folders WHERE id = ?", (folder_id,)
            ).fetchone()
        if not exists:
            raise HTTPException(status_code=404, detail="Library folder not found")
        if not scanner.start(folder_id):
            raise HTTPException(status_code=409, detail="This folder is already scanning")
        return {"status": "started"}

    @app.get("/api/scans")
    def list_scans(limit: int = Query(10, ge=1, le=100)) -> list[dict[str, Any]]:
        with database.connect() as connection:
            rows = connection.execute(
                """
                SELECT scan_runs.*, folders.path AS folder_path
                FROM scan_runs
                JOIN folders ON folders.id = scan_runs.folder_id
                ORDER BY scan_runs.id DESC
                LIMIT ?
                """,
                (limit,),
            ).fetchall()
        return [row_to_dict(row) for row in rows]

    @app.get("/api/stats")
    def stats() -> dict[str, int]:
        with database.connect() as connection:
            totals = connection.execute(
                """
                SELECT
                    COUNT(*) AS total,
                    SUM(CASE WHEN file_type = 'pdf' THEN 1 ELSE 0 END) AS pdf,
                    SUM(CASE WHEN file_type = 'epub' THEN 1 ELSE 0 END) AS epub
                FROM books
                """
            ).fetchone()
            folder_count = connection.execute(
                "SELECT COUNT(*) FROM folders"
            ).fetchone()[0]
            exact_groups = connection.execute(
                """
                SELECT COUNT(*)
                FROM (
                    SELECT sha256
                    FROM books
                    GROUP BY sha256
                    HAVING COUNT(*) > 1
                )
                """
            ).fetchone()[0]
            possible_groups = connection.execute(
                """
                SELECT COUNT(*)
                FROM (
                    SELECT identity_key
                    FROM books
                    WHERE identity_key IS NOT NULL
                    GROUP BY identity_key
                    HAVING COUNT(*) > 1 AND COUNT(DISTINCT sha256) > 1
                )
                """
            ).fetchone()[0]
        return {
            "total": totals["total"] or 0,
            "pdf": totals["pdf"] or 0,
            "epub": totals["epub"] or 0,
            "folders": folder_count,
            "exact_duplicate_groups": exact_groups,
            "possible_duplicate_groups": possible_groups,
        }

    @app.get("/api/books")
    def list_books(
        q: str = Query("", max_length=300),
        file_type: Literal["all", "pdf", "epub"] = "all",
        limit: int = Query(100, ge=1, le=500),
        offset: int = Query(0, ge=0),
    ) -> dict[str, Any]:
        clauses: list[str] = []
        parameters: list[Any] = []
        if q.strip():
            query = f"%{q.strip()}%"
            clauses.append(
                "(title LIKE ? COLLATE NOCASE OR author LIKE ? COLLATE NOCASE)"
            )
            parameters.extend([query, query])
        if file_type != "all":
            clauses.append("file_type = ?")
            parameters.append(file_type)

        where = f"WHERE {' AND '.join(clauses)}" if clauses else ""
        with database.connect() as connection:
            total = connection.execute(
                f"SELECT COUNT(*) FROM books {where}", parameters
            ).fetchone()[0]
            rows = connection.execute(
                f"""
                SELECT id, folder_id, path, filename, file_type, title, author,
                       isbn, size_bytes, sha256, scan_error, updated_at
                FROM books
                {where}
                ORDER BY title COLLATE NOCASE ASC, author COLLATE NOCASE ASC
                LIMIT ? OFFSET ?
                """,
                [*parameters, limit, offset],
            ).fetchall()
        return {"items": [row_to_dict(row) for row in rows], "total": total}

    @app.get("/api/duplicates")
    def duplicates(
        kind: Literal["exact", "possible"] = "exact",
    ) -> dict[str, Any]:
        if kind == "exact":
            group_query = """
                SELECT sha256 AS group_key, COUNT(*) AS file_count
                FROM books
                GROUP BY sha256
                HAVING COUNT(*) > 1
                ORDER BY file_count DESC, MIN(title) COLLATE NOCASE
            """
            book_query = """
                SELECT id, path, filename, file_type, title, author, size_bytes,
                       sha256, scan_error
                FROM books
                WHERE sha256 = ?
                ORDER BY path COLLATE NOCASE
            """
        else:
            group_query = """
                SELECT identity_key AS group_key, COUNT(*) AS file_count
                FROM books
                WHERE identity_key IS NOT NULL
                GROUP BY identity_key
                HAVING COUNT(*) > 1 AND COUNT(DISTINCT sha256) > 1
                ORDER BY file_count DESC, MIN(title) COLLATE NOCASE
            """
            book_query = """
                SELECT id, path, filename, file_type, title, author, size_bytes,
                       sha256, scan_error
                FROM books
                WHERE identity_key = ?
                ORDER BY path COLLATE NOCASE
            """

        with database.connect() as connection:
            group_rows = connection.execute(group_query).fetchall()
            groups = []
            for group in group_rows:
                books = connection.execute(
                    book_query, (group["group_key"],)
                ).fetchall()
                groups.append(
                    {
                        "key": group["group_key"],
                        "file_count": group["file_count"],
                        "books": [row_to_dict(book) for book in books],
                    }
                )
        return {"kind": kind, "groups": groups, "total": len(groups)}

    frontend_dist = Path(__file__).resolve().parent.parent / "dist"
    if frontend_dist.exists():
        app.mount("/", StaticFiles(directory=frontend_dist, html=True), name="frontend")

    return app


app = create_app()
