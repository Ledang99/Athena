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
    val addedAt: Long,
    val lastOpenedAt: Long?,
)

data class CapturedNote(
    val id: Long,
    val text: String,
    val sourcePackage: String?,
    val createdAt: Long,
)

enum class DuplicateKind {
    NONE,
    EXACT,
    POSSIBLE,
}

fun normalizeForMatch(value: String): String {
    val decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD)
    return decomposed
        .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}

fun Book.identityKey(): String? {
    val normalizedTitle = normalizeForMatch(title)
    val normalizedAuthor = normalizeForMatch(author.orEmpty())
    if (normalizedTitle.isBlank() || normalizedAuthor.isBlank()) return null
    return "$normalizedTitle|$normalizedAuthor"
}

fun duplicateKinds(books: List<Book>): Map<Long, DuplicateKind> {
    val hashCounts = books.groupingBy { it.sha256 }.eachCount()
    val possibleKeys = books
        .mapNotNull { book -> book.identityKey()?.let { key -> key to book.sha256 } }
        .groupBy({ it.first }, { it.second })
        .filterValues { hashes -> hashes.distinct().size > 1 }
        .keys

    return books.associate { book ->
        val kind = when {
            (hashCounts[book.sha256] ?: 0) > 1 -> DuplicateKind.EXACT
            book.identityKey() in possibleKeys -> DuplicateKind.POSSIBLE
            else -> DuplicateKind.NONE
        }
        book.id to kind
    }
}
