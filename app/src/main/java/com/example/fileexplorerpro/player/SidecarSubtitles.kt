package com.example.fileexplorerpro.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import java.io.File

object SidecarSubtitles {
    private val exts = listOf("srt" to MimeTypes.APPLICATION_SUBRIP, "vtt" to MimeTypes.TEXT_VTT)

    fun mediaItem(uri: Uri): MediaItem {
        val configs = find(uri)
        val b = MediaItem.Builder().setUri(uri)
        if (configs.isNotEmpty()) b.setSubtitleConfigurations(configs)
        return b.build()
    }

    fun find(uri: Uri): List<MediaItem.SubtitleConfiguration> {
        if (uri.scheme != "file") return emptyList()
        val path = uri.path ?: return emptyList()
        val base = path.substringBeforeLast('.')
        return exts.mapNotNull { (ext, mime) ->
            val f = File("$base.$ext")
            if (!f.isFile) return@mapNotNull null
            MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(f))
                .setMimeType(mime)
                .setLanguage("und")
                .setSelectionFlags(0)
                .build()
        }
    }
}
