package com.example.fileexplorerpro.ui

import com.example.fileexplorerpro.data.FileItem

/** ترتيب المجلدات أولاً ثم الملفات حسب [SortMode]. */
object FileSorter {
    fun sort(items: List<FileItem>, mode: SortMode): List<FileItem> {
        val (dirs, files) = items.partition { it.isDirectory }
        val cmp = when (mode) {
            SortMode.NAME -> compareBy<FileItem> { it.name.lowercase() }
            SortMode.DATE -> compareByDescending { it.lastModified }
            SortMode.SIZE -> compareByDescending { it.size }
            SortMode.TYPE -> compareBy { it.extension }
        }
        return dirs.sortedWith(cmp) + files.sortedWith(cmp)
    }
}
