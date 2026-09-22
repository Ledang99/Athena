package com.projectathena.app.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.documentfile.provider.DocumentFile
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.FileOutputStream
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

    fun notesForBook(bookId: Long): List<CapturedNote> = database.notesForBook(bookId)

    fun catalogStats(): CatalogStats = database.catalogStats()

    fun captureNote(text: String, sourcePackage: String?, bookId: Long? = null) {
        if (text.isNotBlank()) database.addNote(text, sourcePackage, bookId = bookId)
    }

    fun deleteNote(noteId: Long) {
        database.deleteNote(noteId)
    }

    fun bookImages(bookId: Long): List<BookSummaryImage> = database.bookImages(bookId)

    fun addSummaryImage(bookId: Long, sourceUri: Uri, caption: String? = null): BookSummaryImage? {
        val summariesDir = File(context.filesDir, "summaries").apply { mkdirs() }
        val filename = "summary_${bookId}_${UUID.randomUUID()}.jpg"
        val destination = File(summariesDir, filename)
        val copied = runCatching {
            resolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }
        }.isSuccess
        if (!copied || !destination.isFile) return null
        val id = database.addBookImage(bookId, destination.absolutePath, caption)
        return BookSummaryImage(
            id = id,
            bookId = bookId,
            imagePath = destination.absolutePath,
            caption = caption,
        )
    }

    fun deleteSummaryImage(imageId: Long) {
        val path = database.deleteBookImage(imageId)
        if (path != null) {
            runCatching { File(path).delete() }
        }
    }

    fun updateMetadata(bookId: Long, title: String, author: String?) {
        if (title.isNotBlank()) database.updateMetadata(bookId, title, author)
    }

    fun updateOrganization(
        bookId: Long,
        title: String,
        author: String?,
        tags: List<String>,
        collections: List<String>,
        readingStatus: ReadingStatus,
    ) {
        if (title.isBlank()) return
        database.updateOrganization(
            bookId = bookId,
            title = title,
            author = author,
            tags = tags,
            collections = collections,
            readingStatus = readingStatus,
        )
    }

    fun updateReadingStatus(bookId: Long, status: ReadingStatus) {
        database.updateReadingStatus(bookId, status)
    }

    fun updateNoteCollections(noteId: Long, collections: List<String>) {
        database.updateNoteCollections(noteId, collections)
    }

    fun categories(): List<BookCategory> = database.categories()

    fun addCategory(label: String): BookCategory? = database.addCategory(label)

    fun updateCategory(id: String, newLabel: String): Boolean = database.updateCategory(id, newLabel)

    fun deleteCategory(id: String) = database.deleteCategory(id)

    fun markOpened(bookId: Long) = database.markOpened(bookId)

    fun scanFolder(
        treeUri: Uri,
        onProgress: (ScanProgress) -> Unit = {},
    ): ScanResult {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return ScanResult(0, 1, "The selected folder is unavailable")
        val rootName = root.name ?: "Library folder"
        onProgress(
            ScanProgress(
                phase = "Discovering ebooks",
                currentFolder = rootName,
            ),
        )

        val discovered = mutableListOf<DocumentFile>()
        val pending = ArrayDeque<DocumentFile>()
        pending.add(root)
        var discoveryErrors = 0

        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            val children = runCatching { current.listFiles() }.getOrElse {
                discoveryErrors += 1
                emptyArray()
            }
            children.forEach { document ->
                when {
                    document.isDirectory -> pending.add(document)
                    document.isFile && document.isSupportedEbook() -> discovered += document
                }
            }
            onProgress(
                ScanProgress(
                    phase = "Discovering ebooks",
                    current = discovered.size,
                    currentFolder = current.name ?: rootName,
                ),
            )
        }

        val token = UUID.randomUUID().toString()
        var indexed = 0
        var errors = discoveryErrors
        val total = discovered.size

        discovered.forEachIndexed { index, document ->
            val fileName = document.name ?: "ebook"
            val parentName = document.parentFile?.name ?: rootName
            onProgress(
                ScanProgress(
                    phase = "Indexing ebooks",
                    current = index,
                    total = total,
                    currentFile = fileName,
                    currentFolder = parentName,
                ),
            )
            runCatching {
                val book = readBook(document, treeUri.toString())
                database.upsertBook(book, token)
            }.onSuccess {
                indexed += 1
            }.onFailure {
                errors += 1
            }
            onProgress(
                ScanProgress(
                    phase = "Indexing ebooks",
                    current = index + 1,
                    total = total,
                    currentFile = fileName,
                    currentFolder = parentName,
                ),
            )
        }

        database.removeMissingFromFolder(treeUri.toString(), token)
        return ScanResult(indexed, errors)
    }

    fun importFiles(
        uris: List<Uri>,
        onProgress: (ScanProgress) -> Unit = {},
    ): ScanResult {
        val unique = uris.distinct()
        var indexed = 0
        var errors = 0
        unique.forEachIndexed { index, uri ->
            val document = DocumentFile.fromSingleUri(context, uri)
            val fileName = document?.name ?: queryDisplayName(uri) ?: "ebook"
            onProgress(
                ScanProgress(
                    phase = "Importing ebooks",
                    current = index,
                    total = unique.size,
                    currentFile = fileName,
                    currentFolder = "Selected files",
                ),
            )
            runCatching {
                val file = document ?: error("File is unavailable")
                require(file.isSupportedEbook()) { "Unsupported file type" }
                database.upsertBook(readBook(file, sourceFolder = null))
            }.onSuccess {
                indexed += 1
            }.onFailure {
                errors += 1
            }
            onProgress(
                ScanProgress(
                    phase = "Importing ebooks",
                    current = index + 1,
                    total = unique.size,
                    currentFile = fileName,
                    currentFolder = "Selected files",
                ),
            )
        }
        return ScanResult(indexed, errors)
    }

    private fun readBook(document: DocumentFile, sourceFolder: String?): Book {
        val uri = document.uri
        val displayName = document.name ?: queryDisplayName(uri) ?: "Untitled ebook"
        val size = document.length().coerceAtLeast(0)
        val modifiedAt = document.lastModified().coerceAtLeast(0)
        val folderName = document.parentFile?.name
        val existing = database.bookByUri(uri.toString())
        val needsOrganizationBackfill = existing != null &&
            !existing.tagsManual &&
            existing.tags.isEmpty() &&
            !existing.collectionsManual &&
            existing.collections.isEmpty()

        if (
            existing != null &&
            !needsOrganizationBackfill &&
            existing.sizeBytes == size &&
            existing.modifiedAt == modifiedAt &&
            existing.coverPath?.let { File(it).isFile } == true
        ) {
            return existing.copy(
                sourceFolder = sourceFolder,
                folderName = folderName ?: existing.folderName,
            )
        }

        val mimeType = mimeType(uri, displayName)
        val digest = sha256(uri)
        val metadata = when (mimeType) {
            PDF_MIME -> readPdfMetadata(uri)
            EPUB_MIME -> readEpubMetadata(uri)
            else -> EbookMetadata()
        }
        val coverPath = when (mimeType) {
            PDF_MIME -> extractPdfCover(uri, digest)
            EPUB_MIME -> extractEpubCover(uri, metadata.coverEntry, digest)
            else -> null
        }
        val fallback = filenameMetadata(displayName)
        val title = metadata.title?.takeIf { it.isNotBlank() } ?: fallback.title.orEmpty()
        val author = metadata.author?.takeIf { it.isNotBlank() } ?: fallback.author
        val metadataTags = normalizeTags(metadata.subjects)
        val mappedCollections = LibraryCollection.matchAll(
            metadataTags + listOfNotNull(title, author, folderName, displayName),
        ).map { it.id }

        return Book(
            uri = uri.toString(),
            displayName = displayName,
            title = title,
            author = author,
            mimeType = mimeType,
            sizeBytes = size,
            modifiedAt = modifiedAt,
            sha256 = digest,
            coverPath = coverPath,
            sourceFolder = sourceFolder,
            folderName = folderName,
            tags = if (existing?.tagsManual == true) existing.tags else metadataTags,
            tagsManual = existing?.tagsManual == true,
            collections = if (existing?.collectionsManual == true) {
                existing.collections
            } else {
                mappedCollections
            },
            collectionsManual = existing?.collectionsManual == true,
            readingStatus = existing?.readingStatus ?: ReadingStatus.UNREAD,
            addedAt = existing?.addedAt ?: System.currentTimeMillis(),
            lastOpenedAt = existing?.lastOpenedAt,
        )
    }

    private fun readPdfMetadata(uri: Uri): EbookMetadata = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { document ->
                val info = document.documentInformation
                val subjects = buildList {
                    info?.subject?.let { add(it) }
                    info?.keywords?.let { add(it) }
                }
                EbookMetadata(
                    title = info?.title,
                    author = info?.author,
                    subjects = subjects,
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
                        return@zipUse parseOpf(
                            bytes = readLimited(zip, 2 * 1024 * 1024),
                            opfEntry = entry.name,
                        )
                    }
                }
                EbookMetadata()
            }
        } ?: EbookMetadata()
    }.getOrDefault(EbookMetadata())

    private fun parseOpf(bytes: ByteArray, opfEntry: String): EbookMetadata {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(StringReader(bytes.toString(Charsets.UTF_8)))
        }
        var title: String? = null
        var author: String? = null
        var coverId: String? = null
        val subjects = mutableListOf<String>()
        val images = mutableListOf<ManifestImage>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name.substringAfter(':').lowercase()) {
                    "title" -> if (title == null) title = runCatching { parser.nextText() }.getOrNull()
                    "creator" -> if (author == null) author = runCatching { parser.nextText() }.getOrNull()
                    "subject" -> {
                        runCatching { parser.nextText() }.getOrNull()
                            ?.takeIf { it.isNotBlank() }
                            ?.let { subjects += it }
                    }
                    "meta" -> {
                        if (parser.attribute("name").equals("cover", ignoreCase = true)) {
                            coverId = parser.attribute("content")
                        }
                    }
                    "item" -> {
                        val mediaType = parser.attribute("media-type").orEmpty()
                        if (mediaType.startsWith("image/")) {
                            images += ManifestImage(
                                id = parser.attribute("id").orEmpty(),
                                href = parser.attribute("href").orEmpty(),
                                properties = parser.attribute("properties").orEmpty(),
                            )
                        }
                    }
                }
            }
            parser.next()
        }
        val coverImage = images.firstOrNull {
            "cover-image" in it.properties.split(Regex("\\s+"))
        } ?: images.firstOrNull {
            coverId != null && it.id == coverId
        } ?: images.firstOrNull {
            "cover" in it.id.lowercase() || "cover" in it.href.lowercase()
        } ?: images.firstOrNull()
        return EbookMetadata(
            title = title?.trim(),
            author = author?.trim(),
            subjects = subjects,
            coverEntry = coverImage?.href
                ?.takeIf { it.isNotBlank() }
                ?.let { resolveZipEntry(opfEntry, it) },
        )
    }

    private fun XmlPullParser.attribute(name: String): String? {
        for (index in 0 until attributeCount) {
            if (getAttributeName(index).substringAfter(':').equals(name, ignoreCase = true)) {
                return getAttributeValue(index)
            }
        }
        return null
    }

    private fun resolveZipEntry(opfEntry: String, href: String): String {
        val base = opfEntry.substringBeforeLast('/', "")
        val combined = listOf(base, href.substringBefore('#'))
            .filter { it.isNotBlank() }
            .joinToString("/")
        val parts = ArrayDeque<String>()
        combined.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.add(Uri.decode(part))
            }
        }
        return parts.joinToString("/")
    }

    private fun extractPdfCover(uri: Uri, digest: String): String? = runCatching {
        val destination = coverFile(digest)
        if (destination.isFile) return@runCatching destination.absolutePath
        resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount > 0) {
                    renderer.openPage(0).use { page ->
                        val width = COVER_WIDTH
                        val height = (width * page.height.toFloat() / page.width)
                            .toInt()
                            .coerceIn(COVER_WIDTH, COVER_MAX_HEIGHT)
                        val bitmap = createBitmap(width, height)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        saveCover(bitmap, destination)
                        bitmap.recycle()
                    }
                }
            }
        }
        destination.takeIf { it.isFile }?.absolutePath
    }.getOrNull()

    private fun extractEpubCover(
        uri: Uri,
        coverEntry: String?,
        digest: String,
    ): String? = runCatching {
        if (coverEntry == null) return@runCatching null
        val destination = coverFile(digest)
        if (destination.isFile) return@runCatching destination.absolutePath
        resolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && Uri.decode(entry.name) == coverEntry) {
                        val bytes = readLimited(zip, 10 * 1024 * 1024)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { decoded ->
                            val thumbnail = scaleCover(decoded)
                            saveCover(thumbnail, destination)
                            if (thumbnail !== decoded) thumbnail.recycle()
                            decoded.recycle()
                        }
                        break
                    }
                }
            }
        }
        destination.takeIf { it.isFile }?.absolutePath
    }.getOrNull()

    private fun coverFile(digest: String): File {
        val directory = File(context.filesDir, "covers").apply { mkdirs() }
        return File(directory, "$digest.jpg")
    }

    private fun scaleCover(source: Bitmap): Bitmap {
        val scale = minOf(
            1f,
            COVER_WIDTH.toFloat() / source.width.coerceAtLeast(1),
            COVER_MAX_HEIGHT.toFloat() / source.height.coerceAtLeast(1),
        )
        if (scale >= 1f) return source
        return source.scale(
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
        )
    }

    private fun saveCover(source: Bitmap, destination: File) {
        val flattened = createBitmap(
            source.width,
            source.height,
        )
        Canvas(flattened).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, 0f, 0f, null)
        }
        FileOutputStream(destination).use { output ->
            check(flattened.compress(Bitmap.CompressFormat.JPEG, 84, output)) {
                "Unable to save cover"
            }
        }
        flattened.recycle()
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
        val subjects: List<String> = emptyList(),
        val coverEntry: String? = null,
    )

    private data class ManifestImage(
        val id: String,
        val href: String,
        val properties: String,
    )

    companion object {
        const val PDF_MIME = "application/pdf"
        const val EPUB_MIME = "application/epub+zip"
        private const val COVER_WIDTH = 360
        private const val COVER_MAX_HEIGHT = 540
        private val EPUB_MIME_ALIASES = setOf(
            EPUB_MIME,
            "application/x-epub+zip",
        )
    }
}
