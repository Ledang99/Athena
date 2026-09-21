package com.projectathena.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateDetectionTest {
    @Test
    fun exactCopiesGroupByHashAndCountCopies() {
        val books = listOf(
            book(id = 1, hash = "same", folder = "Downloads"),
            book(id = 2, hash = "same", folder = "Backup"),
            book(id = 3, hash = "different", folder = "Downloads"),
        )

        val groups = buildDuplicateGroups(books)

        assertEquals(1, groups.size)
        assertEquals(DuplicateKind.EXACT, groups[0].kind)
        assertEquals(2, groups[0].copyCount)
        assertEquals(listOf("Backup", "Downloads"), groups[0].copies.map { it.folderName })
    }

    @Test
    fun likelyMatchesRequireSameTitleSizeAndTypeWithDifferentHashes() {
        val books = listOf(
            book(id = 1, hash = "one", title = "Deep Work", size = 100),
            book(id = 2, hash = "two", title = "Deep Work", size = 100),
            book(id = 3, hash = "three", title = "Deep Work", size = 200),
        )

        val groups = buildDuplicateGroups(books)

        assertEquals(1, groups.size)
        assertEquals(DuplicateKind.LIKELY, groups[0].kind)
        assertEquals(2, groups[0].copyCount)
    }

    @Test
    fun matchingIgnoresPunctuationAndAccentsForLikelyGroups() {
        val books = listOf(
            book(id = 1, hash = "one", title = "Café: A Guide"),
            book(id = 2, hash = "two", title = "Cafe - A Guide"),
        )

        val groups = buildDuplicateGroups(books)

        assertEquals(1, groups.size)
        assertEquals(DuplicateKind.LIKELY, groups[0].kind)
    }

    @Test
    fun differentTypesAreNotGroupedAsLikely() {
        val books = listOf(
            book(id = 1, hash = "one", title = "Deep Work", mime = "application/pdf"),
            book(
                id = 2,
                hash = "two",
                title = "Deep Work",
                mime = "application/epub+zip",
            ),
        )

        assertTrue(buildDuplicateGroups(books).isEmpty())
    }

    @Test
    fun catalogStatsCountAllIndexedBooks() {
        val books = listOf(
            book(id = 1, hash = "a", mime = "application/pdf"),
            book(id = 2, hash = "b", mime = "application/pdf"),
            book(id = 3, hash = "c", mime = "application/epub+zip"),
        )

        assertEquals(CatalogStats(total = 3, pdf = 2, epub = 1), catalogStatsFromBooks(books))
    }

    private fun book(
        id: Long,
        hash: String,
        title: String = "Deep Work",
        author: String? = "Cal Newport",
        size: Long = 100,
        mime: String = "application/pdf",
        folder: String? = "Library",
    ) = Book(
        id = id,
        uri = "content://book/$id",
        displayName = "$id.pdf",
        title = title,
        author = author,
        mimeType = mime,
        sizeBytes = size,
        modifiedAt = 1,
        sha256 = hash,
        coverPath = null,
        sourceFolder = null,
        folderName = folder,
        addedAt = 1,
        lastOpenedAt = null,
    )
}
