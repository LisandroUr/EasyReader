package com.example.easyreader

import com.example.easyreader.util.BookFormatUtils
import org.junit.Assert.*
import org.junit.Test

class BookFormatUtilsTest {

    @Test
    fun extensionFromName_extractsCorrectExtension() {
        assertEquals("pdf", BookFormatUtils.extensionFromName("sample.pdf"))
        assertEquals("pdf", BookFormatUtils.extensionFromName("sample.PDF"))
        assertEquals("pdf", BookFormatUtils.extensionFromName("/storage/emulated/0/Books/chapter.pdf"))
        assertEquals("pdf", BookFormatUtils.extensionFromName("https://example.com/doc.pdf?version=2"))
        assertEquals("", BookFormatUtils.extensionFromName("no_extension"))
        assertEquals("", BookFormatUtils.extensionFromName(null))
        assertEquals("", BookFormatUtils.extensionFromName(""))
    }

    @Test
    fun isPdf_detectsPdfsCorrectly() {
        assertTrue(BookFormatUtils.isPdf("application/pdf", "document"))
        assertTrue(BookFormatUtils.isPdf(null, "document.pdf"))
        assertTrue(BookFormatUtils.isPdf("application/octet-stream", "document.PDF"))
        assertTrue(BookFormatUtils.isPdf(null, "/path/to/my_book.pdf"))

        assertFalse(BookFormatUtils.isPdf(null, "image.png"))
        assertFalse(BookFormatUtils.isPdf("image/jpeg", "picture.jpg"))
        assertFalse(BookFormatUtils.isPdf(null, "archive.zip"))
        assertFalse(BookFormatUtils.isPdf(null, "comic.cbr"))
        assertFalse(BookFormatUtils.isPdf(null, null))
    }

    @Test
    fun isSupported_alignsWithPdf() {
        assertTrue(BookFormatUtils.isSupported("application/pdf", null))
        assertTrue(BookFormatUtils.isSupported(null, "test.pdf"))
        assertFalse(BookFormatUtils.isSupported("text/plain", "notes.txt"))
        assertFalse(BookFormatUtils.isSupported("application/zip", "pack.zip"))
    }

    @Test
    fun typeLabelFromUri_returnsCorrectLabels() {
        assertEquals("PDF", BookFormatUtils.typeLabelFromUri("content://com.android.providers/document/123.pdf"))
        assertEquals("DOC", BookFormatUtils.typeLabelFromUri("content://com.android.providers/document/notes.txt"))
    }

    @Test
    fun isPdf_detectsAlternativeMimeTypesAndEncodedNames() {
        assertTrue(BookFormatUtils.isPdf("application/x-pdf", "doc"))
        assertTrue(BookFormatUtils.isPdf("application/acrobat", "doc"))
        assertTrue(BookFormatUtils.isPdf("text/pdf", "doc"))
        assertTrue(BookFormatUtils.isPdf(null, "primary%3ADownload%2Fchapter.pdf"))
        assertTrue(BookFormatUtils.isPdf(null, "content%3A%2F%2Fcom.android%2Ffile.PDF%3Fversion%3D1"))
    }
}
