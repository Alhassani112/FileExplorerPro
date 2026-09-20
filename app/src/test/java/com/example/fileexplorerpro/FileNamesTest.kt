package com.example.fileexplorerpro

import com.example.fileexplorerpro.data.FileNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileNamesTest {
    @Test
    fun acceptsSimpleName() {
        assertEquals("photo.jpg", FileNames.sanitize("photo.jpg"))
    }

    @Test
    fun rejectsPathTraversal() {
        assertNull(FileNames.sanitize("../secret"))
        assertNull(FileNames.sanitize("a/b"))
        assertNull(FileNames.sanitize("a\\b"))
    }

    @Test
    fun rejectsDotsAndEmpty() {
        assertNull(FileNames.sanitize("."))
        assertNull(FileNames.sanitize(".."))
        assertNull(FileNames.sanitize("  "))
        assertNull(FileNames.sanitize(""))
    }

    @Test
    fun trimsWhitespace() {
        assertEquals("notes.txt", FileNames.sanitize("  notes.txt  "))
    }
}
