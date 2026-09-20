package com.projectathena.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateDetectionTest {
    @Test
    fun exactCopiesTakePriorityOverPossibleMatches() {
        val books = listOf(
            book(id = 1, hash = "same"),
            book(id = 2, hash = "same"),
            book(id = 3, hash = "different"),
        )

        val result = duplicateKinds(books)

        assertEquals(DuplicateKind.EXACT, result[1])
        assertEquals(DuplicateKind.EXACT, result[2])
        assertEquals(DuplicateKind.POSSIBLE, result[3])
    }

    @Test
    fun matchingTitleWithoutAuthorIsNotFlaggedAsPossible() {
        val books = listOf(
            book(id = 1, hash = "one", author = null),
            book(id = 2, hash = "two", author = null),
        )

        val result = duplicateKinds(books)

        assertEquals(DuplicateKind.NONE, result[1])
        assertEquals(DuplicateKind.NONE, result[2])
    }

    @Test
    fun matchingIgnoresPunctuationAndAccents() {
        val books = listOf(
            book(id = 1, hash = "one", title = "Café: A Guide"),
            book(id = 2, hash = "two", title = "Cafe - A Guide"),
        )

        val result = duplicateKinds(books)

        assertEquals(DuplicateKind.POSSIBLE, result[1])
        assertEquals(DuplicateKind.POSSIBLE, result[2])
    }

    private fun book(
        id: Long,
        hash: String,
        title: String = "Deep Work",
        author: String? = "Cal Newport",
    ) = Book(
        id = id,
        uri = "content://book/$id",
        displayName = "$id.pdf",
        title = title,
        author = author,
        mimeType = "application/pdf",
        sizeBytes = 100,
        modifiedAt = 1,
        sha256 = hash,
        sourceFolder = null,
        addedAt = 1,
        lastOpenedAt = null,
    )
}
