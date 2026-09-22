package com.projectathena.app.data

import java.text.Normalizer

data class Book(
    val id: Long = 0,
    val uri: String,
    val displayName: String,
    val title: String,
    val author: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val sha256: String,
    val coverPath: String?,
    val sourceFolder: String?,
    val folderName: String?,
    val tags: List<String> = emptyList(),
    val tagsManual: Boolean = false,
    val collections: List<String> = emptyList(),
    val collectionsManual: Boolean = false,
    val readingStatus: ReadingStatus = ReadingStatus.UNREAD,
    val addedAt: Long,
    val lastOpenedAt: Long?,
)

data class CapturedNote(
    val id: Long,
    val bookId: Long? = null,
    val text: String,
    val sourcePackage: String?,
    val collections: List<String> = emptyList(),
    val createdAt: Long,
)

data class BookSummaryImage(
    val id: Long = 0,
    val bookId: Long,
    val imagePath: String,
    val caption: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class CatalogStats(
    val total: Int = 0,
    val pdf: Int = 0,
    val epub: Int = 0,
)

enum class DuplicateKind {
    EXACT,
    LIKELY,
}

data class DuplicateGroup(
    val key: String,
    val kind: DuplicateKind,
    val title: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String?,
    val copies: List<Book>,
) {
    val copyCount: Int get() = copies.size
}

fun normalizeForMatch(value: String): String {
    val decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD)
    return decomposed
        .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}

fun Book.detailKey(): String? {
    val normalizedTitle = normalizeForMatch(title)
    if (normalizedTitle.isBlank()) return null
    return "$normalizedTitle|$mimeType|$sizeBytes"
}

fun buildDuplicateGroups(books: List<Book>): List<DuplicateGroup> {
    val exactGroups = books
        .groupBy { it.sha256 }
        .filterValues { it.size > 1 }
        .map { (hash, copies) ->
            val sample = copies.first()
            DuplicateGroup(
                key = "exact:$hash",
                kind = DuplicateKind.EXACT,
                title = sample.title,
                mimeType = sample.mimeType,
                sizeBytes = sample.sizeBytes,
                sha256 = hash,
                copies = copies.sortedBy { it.folderName.orEmpty() + it.displayName },
            )
        }

    val booksInExact = exactGroups.flatMap { group -> group.copies.map { it.id } }.toSet()
    val likelyGroups = books
        .filter { it.id !in booksInExact }
        .mapNotNull { book -> book.detailKey()?.let { key -> key to book } }
        .groupBy({ it.first }, { it.second })
        .filterValues { copies ->
            copies.size > 1 && copies.map { it.sha256 }.distinct().size > 1
        }
        .map { (detailKey, copies) ->
            val sample = copies.first()
            DuplicateGroup(
                key = "likely:$detailKey",
                kind = DuplicateKind.LIKELY,
                title = sample.title,
                mimeType = sample.mimeType,
                sizeBytes = sample.sizeBytes,
                sha256 = null,
                copies = copies.sortedBy { it.folderName.orEmpty() + it.displayName },
            )
        }

    return (exactGroups + likelyGroups).sortedWith(
        compareByDescending<DuplicateGroup> { it.copyCount }
            .thenBy { it.title.lowercase() },
    )
}

fun catalogStatsFromBooks(books: List<Book>): CatalogStats = CatalogStats(
    total = books.size,
    pdf = books.count { it.mimeType == "application/pdf" },
    epub = books.count {
        it.mimeType == "application/epub+zip" || it.mimeType == "application/x-epub+zip"
    },
)

fun sortBooks(books: List<Book>, sort: LibrarySort): List<Book> = when (sort) {
    LibrarySort.TITLE -> books.sortedWith(
        compareBy<Book>({ it.title.lowercase() }, { it.author.orEmpty().lowercase() }),
    )
    LibrarySort.RECENTLY_OPENED -> books.sortedWith(
        compareByDescending<Book> { it.lastOpenedAt ?: 0L }
            .thenBy { it.title.lowercase() },
    )
    LibrarySort.RECENTLY_ADDED -> books.sortedWith(
        compareByDescending<Book> { it.addedAt }
            .thenBy { it.title.lowercase() },
    )
    LibrarySort.STATUS -> books.sortedWith(
        compareBy<Book> { it.readingStatus.ordinal }
            .thenBy { it.title.lowercase() },
    )
}
