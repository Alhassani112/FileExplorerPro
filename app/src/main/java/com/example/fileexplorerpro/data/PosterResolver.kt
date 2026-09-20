package com.example.fileexplorerpro.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

object PosterResolver {
    private val posterNames = listOf("folder", "cover", "poster", "folder_cover", "movie")
    private val imageExts = listOf("jpg", "jpeg", "png", "webp")

    fun findPoster(context: Context, dirUri: Uri): Uri? {
        val dir = DocumentFile.fromTreeUri(context, dirUri) ?: return null
        if (!dir.isDirectory) return null
        for (name in posterNames) for (ext in imageExts) {
            val c = dir.findFile("$name.$ext")
            if (c != null && c.isFile) return c.uri
        }
        return null
    }

    /** للعمل مع File API مباشرة */
    fun findPosterByPath(dir: File): Uri? {
        if (!dir.isDirectory) return null
        for (name in posterNames) {
            for (ext in imageExts) {
                val f = File(dir, "$name.$ext")
                if (f.exists() && f.isFile) return Uri.fromFile(f)
            }
        }
        return null
    }
}
