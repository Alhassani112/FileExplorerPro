package com.example.fileexplorerpro

import android.net.Uri
import com.example.fileexplorerpro.data.FileItem
import com.example.fileexplorerpro.ui.FileSorter
import com.example.fileexplorerpro.ui.SortMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FileSorterTest {
    private fun item(name: String, dir: Boolean, size: Long = 0, modified: Long = 0) =
        FileItem(Uri.parse("file:///$name"), name, dir, size, modified, null)

    @Test
    fun directoriesFirstThenName() {
        val sorted = FileSorter.sort(
            listOf(
                item("b.txt", false),
                item("Z", true),
                item("a.txt", false),
                item("A", true)
            ),
            SortMode.NAME
        )
        assertEquals(listOf("A", "Z", "a.txt", "b.txt"), sorted.map { it.name })
    }

    @Test
    fun sortBySizeDescendingAmongFiles() {
        val sorted = FileSorter.sort(
            listOf(
                item("small", false, 10),
                item("big", false, 100)
            ),
            SortMode.SIZE
        )
        assertEquals(listOf("big", "small"), sorted.map { it.name })
    }
}
