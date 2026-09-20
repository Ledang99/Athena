package com.projectathena.app.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.StringReader
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class EbookRepository(private val context: Context) {
    private val database = AthenaDatabase(context)
    private val resolver: ContentResolver = context.contentResolver

    init {
        PDFBoxResourceLoader.init(context)
    }

    fun books(): List<Book> = database.books()

    fun notes(): List<CapturedNote> = database.notes()

    fun captureNote(text: String, sourcePackage: String?) {
        if (text.isNotBlank()) database.addNote(text, sourcePackage)
    }

    fun updateMetadata(bookId: Long, title: String, author: String?) {
        if (title.isNotBlank()) database.updateMetadata(bookId, title, author)
    }

    fun markOpened(bookId: Long) = database.markOpened(bookId)

    fun scanFolder(treeUri: Uri, onProgress: (Int) -> Unit = {}): ScanResult {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return ScanResult(0, 1, "The selected folder is unavailable")
        val token = UUID.randomUUID().toString()
        var indexed = 0
        var errors = 0
        val pending = ArrayDeque<DocumentFile>()
        pending.add(root)

        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            val children = runCatching { current.listFiles() }.getOrElse {
                errors += 1
                emptyArray()
            }
            children.forEach { document ->
                when {
                    document.isDirectory -> pending.add(document)
                    document.isFile && document.isSupportedEbook() -> {
                        runCatching {
                            val book = readBook(document, treeUri.toString())
                            database.upsertBook(book, token)
                        }.onSuccess {
                            indexed += 1
                            onProgress(indexed)
                        }.onFailure {
                            errors += 1
                        }
                    }
                }
            }
        }

        database.removeMissingFromFolder(treeUri.toString(), token)
        return ScanResult(indexed, errors)
    }

    fun importFiles(uris: List<Uri>): ScanResult {
        var indexed = 0
        var errors = 0
        uris.distinct().forEach { uri ->
            runCatching {
                val document = DocumentFile.fromSingleUri(context, uri)
                    ?: error("File is unavailable")
                require(document.isSupportedEbook()) { "Unsupported file type" }
                database.upsertBook(readBook(document, sourceFolder = null))
            }.onSuccess {
                indexed += 1
            }.onFailure {
                errors += 1
            }
        }
        return ScanResult(indexed, errors)
    }

    private fun readBook(document: DocumentFile, sourceFolder: String?): Book {
        val uri = document.uri
        val displayName = document.name ?: queryDisplayName(uri) ?: "Untitled ebook"
        val size = document.length().coerceAtLeast(0)
        val modifiedAt = document.lastModified().coerceAtLeast(0)
        val existing = database.bookByUri(uri.toString())

        if (
            existing != null &&
            existing.sizeBytes == size &&
            existing.modifiedAt == modifiedAt
        ) {
            return existing.copy(sourceFolder = sourceFolder)
        }

        val mimeType = mimeType(uri, displayName)
        val metadata = when (mimeType) {
            PDF_MIME -> readPdfMetadata(uri)
            EPUB_MIME -> readEpubMetadata(uri)
            else -> EbookMetadata()
        }
        val fallback = filenameMetadata(displayName)
        return Book(
            uri = uri.toString(),
            displayName = displayName,
            title = metadata.title?.takeIf { it.isNotBlank() } ?: fallback.title.orEmpty(),
            author = metadata.author?.takeIf { it.isNotBlank() } ?: fallback.author,
            mimeType = mimeType,
            sizeBytes = size,
            modifiedAt = modifiedAt,
            sha256 = sha256(uri),
            sourceFolder = sourceFolder,
            addedAt = existing?.addedAt ?: System.currentTimeMillis(),
            lastOpenedAt = existing?.lastOpenedAt,
        )
    }

    private fun readPdfMetadata(uri: Uri): EbookMetadata = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { document ->
                EbookMetadata(
                    title = document.documentInformation?.title,
                    author = document.documentInformation?.author,
                )
            }
        } ?: EbookMetadata()
    }.getOrDefault(EbookMetadata())

    private fun readEpubMetadata(uri: Uri): EbookMetadata = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use zipUse@ { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name.endsWith(".opf", ignoreCase = true)) {
                        return@zipUse parseOpf(readLimited(zip, 2 * 1024 * 1024))
                    }
                }
                EbookMetadata()
            }
        } ?: EbookMetadata()
    }.getOrDefault(EbookMetadata())

    private fun parseOpf(bytes: ByteArray): EbookMetadata {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(StringReader(bytes.toString(Charsets.UTF_8)))
        }
        var title: String? = null
        var author: String? = null
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name.substringAfter(':').lowercase()) {
                    "title" -> if (title == null) title = runCatching { parser.nextText() }.getOrNull()
                    "creator" -> if (author == null) author = runCatching { parser.nextText() }.getOrNull()
                }
            }
            parser.next()
        }
        return EbookMetadata(title?.trim(), author?.trim())
    }

    private fun readLimited(zip: ZipInputStream, maximumBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = zip.read(buffer)
            if (count <= 0) break
            total += count
            require(total <= maximumBytes) { "EPUB metadata is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun sha256(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        } ?: error("Unable to read ebook")
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun queryDisplayName(uri: Uri): String? = resolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

    private fun DocumentFile.isSupportedEbook(): Boolean {
        val type = mimeType(uri, name.orEmpty())
        return type == PDF_MIME || type == EPUB_MIME
    }

    private fun mimeType(uri: Uri, name: String): String {
        val reported = resolver.getType(uri)?.lowercase()
        return when {
            reported == PDF_MIME || name.endsWith(".pdf", ignoreCase = true) -> PDF_MIME
            reported in EPUB_MIME_ALIASES || name.endsWith(".epub", ignoreCase = true) -> EPUB_MIME
            else -> reported ?: "application/octet-stream"
        }
    }

    private fun filenameMetadata(displayName: String): EbookMetadata {
        val stem = displayName.substringBeforeLast('.')
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        val parts = stem.split(" - ", limit = 2)
        return if (parts.size == 2) {
            EbookMetadata(title = parts[0].trim(), author = parts[1].trim())
        } else {
            EbookMetadata(title = stem)
        }
    }

    data class ScanResult(
        val indexed: Int,
        val errors: Int,
        val message: String? = null,
    )

    private data class EbookMetadata(
        val title: String? = null,
        val author: String? = null,
    )

    companion object {
        const val PDF_MIME = "application/pdf"
        const val EPUB_MIME = "application/epub+zip"
        private val EPUB_MIME_ALIASES = setOf(
            EPUB_MIME,
            "application/x-epub+zip",
        )
    }
}
