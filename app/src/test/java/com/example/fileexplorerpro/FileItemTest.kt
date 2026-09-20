package com.example.fileexplorerpro

import android.net.Uri
import com.example.fileexplorerpro.data.FileItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FileItemTest {
    private fun f(name: String) =
        FileItem(Uri.parse("file:///$name"), name, false, 1, 0, null)

    @Test
    fun classifiesExtensions() {
        assertTrue(f("a.mp4").isVideo)
        assertTrue(f("a.MP3").isAudio)
        assertTrue(f("a.png").isImage)
        assertTrue(f("a.pdf").isDocument)
        assertTrue(f("a.zip").isArchive)
        assertTrue(f("movie.mkv").isPlayable)
        assertFalse(f("a.bin").isPlayable)
    }
}
