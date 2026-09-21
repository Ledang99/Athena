package com.projectathena.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCatalogueTest {
    @Test
    fun matchesInvestingFromSubjectsAndTitle() {
        val matched = LibraryCollection.matchAll(
            listOf("Personal Finance", "Atomic Habits", "investing basics"),
        )

        assertTrue(LibraryCollection.INVESTING in matched || LibraryCollection.PERSONAL_FINANCE in matched)
        assertTrue(LibraryCollection.HABITS in matched)
    }

    @Test
    fun normalizeTagsSplitsAndDedupes() {
        val tags = normalizeTags(listOf("habits, focus", "Focus", "deep-work; productivity"))

        assertEquals(
            listOf("Deep-work", "Focus", "Habits", "Productivity"),
            tags,
        )
    }

    @Test
    fun sortBooksByRecentlyOpened() {
        val books = listOf(
            sample(id = 1, title = "Older", opened = 10),
            sample(id = 2, title = "Never opened", opened = null),
            sample(id = 3, title = "Newest", opened = 30),
        )

        val sorted = sortBooks(books, LibrarySort.RECENTLY_OPENED)

        assertEquals(listOf(3L, 1L, 2L), sorted.map { it.id })
    }

    @Test
    fun encodeDecodeStringListRoundTrip() {
        val values = listOf("Habits", "Investing")
        assertEquals(values, decodeStringList(encodeStringList(values)))
    }

    private fun sample(
        id: Long,
        title: String,
        opened: Long?,
    ) = Book(
        id = id,
        uri = "content://book/$id",
        displayName = "$id.pdf",
        title = title,
        author = "Author",
        mimeType = "application/pdf",
        sizeBytes = 100,
        modifiedAt = 1,
        sha256 = "hash-$id",
        coverPath = null,
        sourceFolder = null,
        folderName = "Library",
        addedAt = id,
        lastOpenedAt = opened,
    )
}
