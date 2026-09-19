package com.example.fileexplorerpro.data
import android.net.Uri
enum class ClipOperation { COPY, CUT }
data class ClipEntry(val items: List<FileItem>, val operation: ClipOperation, val sourceDirUri: Uri)
object ClipboardManager {
    private var entry: ClipEntry? = null
    fun set(items: List<FileItem>, op: ClipOperation, src: Uri) { entry = ClipEntry(items, op, src) }
    fun get(): ClipEntry? = entry
    fun clear() { entry = null }
}
