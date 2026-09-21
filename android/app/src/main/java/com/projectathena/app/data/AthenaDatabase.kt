package com.projectathena.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AthenaDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE books (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uri TEXT NOT NULL UNIQUE,
                display_name TEXT NOT NULL,
                title TEXT NOT NULL,
                author TEXT,
                mime_type TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                modified_at INTEGER NOT NULL,
                sha256 TEXT NOT NULL,
                cover_path TEXT,
                source_folder TEXT,
                folder_name TEXT,
                scan_token TEXT,
                added_at INTEGER NOT NULL,
                last_opened_at INTEGER
            )
            """.trimIndent(),
        )
        database.execSQL("CREATE INDEX books_title_idx ON books(title COLLATE NOCASE)")
        database.execSQL("CREATE INDEX books_author_idx ON books(author COLLATE NOCASE)")
        database.execSQL("CREATE INDEX books_sha_idx ON books(sha256)")
        database.execSQL(
            """
            CREATE TABLE notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text TEXT NOT NULL,
                source_package TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            database.execSQL("ALTER TABLE books ADD COLUMN cover_path TEXT")
        }
        if (oldVersion < 3) {
            database.execSQL("ALTER TABLE books ADD COLUMN folder_name TEXT")
        }
    }

    fun upsertBook(book: Book, scanToken: String? = null): Long {
        val values = ContentValues().apply {
            put("uri", book.uri)
            put("display_name", book.displayName)
            put("title", book.title)
            put("author", book.author)
            put("mime_type", book.mimeType)
            put("size_bytes", book.sizeBytes)
            put("modified_at", book.modifiedAt)
            put("sha256", book.sha256)
            put("cover_path", book.coverPath)
            put("source_folder", book.sourceFolder)
            put("folder_name", book.folderName)
            put("scan_token", scanToken)
            put("added_at", book.addedAt)
            put("last_opened_at", book.lastOpenedAt)
        }
        val database = writableDatabase
        val inserted = database.insertWithOnConflict(
            "books",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE,
        )
        if (inserted != -1L) return inserted

        values.remove("added_at")
        database.update("books", values, "uri = ?", arrayOf(book.uri))
        return database.query(
            "books",
            arrayOf("id"),
            "uri = ?",
            arrayOf(book.uri),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0
        }
    }

    fun removeMissingFromFolder(folderUri: String, scanToken: String) {
        writableDatabase.delete(
            "books",
            "source_folder = ? AND (scan_token IS NULL OR scan_token != ?)",
            arrayOf(folderUri, scanToken),
        )
    }

    fun books(): List<Book> = readableDatabase.query(
        "books",
        null,
        null,
        null,
        null,
        null,
        "title COLLATE NOCASE, author COLLATE NOCASE",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.toBook())
        }
    }

    fun bookByUri(uri: String): Book? = readableDatabase.query(
        "books",
        null,
        "uri = ?",
        arrayOf(uri),
        null,
        null,
        null,
        "1",
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.toBook() else null
    }

    fun updateMetadata(bookId: Long, title: String, author: String?) {
        val values = ContentValues().apply {
            put("title", title.trim())
            put("author", author?.trim()?.ifBlank { null })
        }
        writableDatabase.update("books", values, "id = ?", arrayOf(bookId.toString()))
    }

    fun markOpened(bookId: Long) {
        val values = ContentValues().apply {
            put("last_opened_at", System.currentTimeMillis())
        }
        writableDatabase.update("books", values, "id = ?", arrayOf(bookId.toString()))
    }

    fun addNote(text: String, sourcePackage: String?) {
        val values = ContentValues().apply {
            put("text", text.trim())
            put("source_package", sourcePackage)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insert("notes", null, values)
    }

    fun notes(): List<CapturedNote> = readableDatabase.query(
        "notes",
        null,
        null,
        null,
        null,
        null,
        "created_at DESC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    CapturedNote(
                        id = cursor.long("id"),
                        text = cursor.string("text"),
                        sourcePackage = cursor.nullableString("source_package"),
                        createdAt = cursor.long("created_at"),
                    ),
                )
            }
        }
    }

    fun catalogStats(): CatalogStats {
        val totals = readableDatabase.rawQuery(
            """
            SELECT
                COUNT(*) AS total,
                SUM(CASE WHEN mime_type = 'application/pdf' THEN 1 ELSE 0 END) AS pdf,
                SUM(CASE WHEN mime_type IN ('application/epub+zip', 'application/x-epub+zip')
                    THEN 1 ELSE 0 END) AS epub
            FROM books
            """.trimIndent(),
            null,
        ).use { cursor ->
            cursor.moveToFirst()
            CatalogStats(
                total = cursor.getInt(0),
                pdf = cursor.getInt(1),
                epub = cursor.getInt(2),
            )
        }
        return totals
    }

    private fun Cursor.toBook() = Book(
        id = long("id"),
        uri = string("uri"),
        displayName = string("display_name"),
        title = string("title"),
        author = nullableString("author"),
        mimeType = string("mime_type"),
        sizeBytes = long("size_bytes"),
        modifiedAt = long("modified_at"),
        sha256 = string("sha256"),
        coverPath = nullableString("cover_path"),
        sourceFolder = nullableString("source_folder"),
        folderName = nullableString("folder_name"),
        addedAt = long("added_at"),
        lastOpenedAt = nullableLong("last_opened_at"),
    )

    private fun Cursor.column(name: String) = getColumnIndexOrThrow(name)
    private fun Cursor.string(name: String) = getString(column(name))
    private fun Cursor.long(name: String) = getLong(column(name))
    private fun Cursor.nullableString(name: String): String? {
        val index = column(name)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.nullableLong(name: String): Long? {
        val index = column(name)
        return if (isNull(index)) null else getLong(index)
    }

    companion object {
        private const val DATABASE_NAME = "athena.db"
        private const val DATABASE_VERSION = 3
    }
}
