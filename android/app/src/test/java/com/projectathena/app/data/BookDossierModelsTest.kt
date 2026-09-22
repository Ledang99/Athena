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

    @Test
    fun bookCategoryPropertiesAndLabels() {
        val cat = BookCategory("startups", "Startups & Solopreneurship")
        assertEquals("startups", cat.id)
        assertEquals("Startups & Solopreneurship", cat.label)

        val book = Book(
            id = 1L,
            uri = "content://books/1",
            displayName = "the 100 startup.pdf",
            title = "The $100 Startup",
            author = "Chris Guillebeau",
            mimeType = "application/pdf",
            sizeBytes = 1000L,
            modifiedAt = 1000L,
            sha256 = "abc",
            coverPath = null,
            sourceFolder = null,
            folderName = null,
            collections = listOf("startups", "habits"),
            addedAt = 1000L,
            lastOpenedAt = null,
        )

        val labels = book.collectionLabels(listOf(cat))
        assertEquals(listOf("Startups & Solopreneurship", "Habits"), labels)
    }

    @Test
    fun bookTocItemAndChapterTextModels() {
        val tocItem = BookTocItem(
            title = "Chapter 1: The Renaissance of Self-Employment",
            level = 1,
            pageNumber = 15,
            resourceHref = "text/chapter1.xhtml",
        )
        assertEquals("Chapter 1: The Renaissance of Self-Employment", tocItem.title)
        assertEquals(1, tocItem.level)
        assertEquals(15, tocItem.pageNumber)
        assertEquals("text/chapter1.xhtml", tocItem.resourceHref)

        val chapterText = ChapterText(
            title = tocItem.title,
            text = "To succeed in a business project, you must find where your skills intersect with what other people value.",
            sourceRef = "Pages 15–24 of 280",
        )
        assertEquals(tocItem.title, chapterText.title)
        assertEquals("Pages 15–24 of 280", chapterText.sourceRef)
        assert(chapterText.text.contains("intersect"))
    }
}
