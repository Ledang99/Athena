package com.projectathena.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BookDossierModelsTest {
    @Test
    fun capturedNoteWithBookId() {
        val note = CapturedNote(
            id = 10L,
            bookId = 42L,
            text = "Crucial insight about habit stacking",
            sourcePackage = "com.projectathena.app",
            collections = listOf("habits"),
            createdAt = 123456L,
        )

        assertEquals(42L, note.bookId)
        assertEquals("Crucial insight about habit stacking", note.text)
        assertEquals(listOf("habits"), note.collections)
    }

    @Test
    fun capturedNoteDefaultBookIdIsNull() {
        val note = CapturedNote(
            id = 11L,
            text = "General note without attached book",
            sourcePackage = null,
            createdAt = 123456L,
        )

        assertNull(note.bookId)
    }

    @Test
    fun bookSummaryImageProperties() {
        val summaryImage = BookSummaryImage(
            id = 1L,
            bookId = 42L,
            imagePath = "/data/user/0/com.projectathena.app/files/summaries/mindmap.jpg",
            caption = "Chapter 1-3 Mind Map",
            createdAt = 1000L,
        )

        assertEquals(1L, summaryImage.id)
        assertEquals(42L, summaryImage.bookId)
        assertEquals("Chapter 1-3 Mind Map", summaryImage.caption)
        assertNotNull(summaryImage.imagePath)
    }
}
