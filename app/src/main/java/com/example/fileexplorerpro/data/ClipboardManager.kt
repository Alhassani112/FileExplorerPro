package com.example.fileexplorerpro.data

import android.net.Uri

enum class ClipOperation { COPY, CUT }

data class ClipEntry(
    val items: List<FileItem>,
    val operation: ClipOperation,
    val sourceDirUri: Uri
)

object ClipboardManager {
    @Volatile
    private var entry: ClipEntry? = null

    @Synchronized
    fun set(items: List<FileItem>, op: ClipOperation, src: Uri) {
        entry = ClipEntry(items, op, src)
    }

    @Synchronized
    fun get(): ClipEntry? = entry

    @Synchronized
    fun clear() {
        entry = null
    }

    fun hasItems(): Boolean = get() != null
}
