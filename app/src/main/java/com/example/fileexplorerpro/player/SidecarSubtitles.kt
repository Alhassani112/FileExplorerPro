package com.example.fileexplorerpro.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import java.io.File

object SidecarSubtitles {
    private val exts = listOf(
        "srt" to MimeTypes.APPLICATION_SUBRIP,
        "vtt" to MimeTypes.TEXT_VTT
    )

    fun mediaItem(uri: Uri): MediaItem {
        val configs = find(uri)
        val builder = MediaItem.Builder().setUri(uri)
        if (configs.isNotEmpty()) builder.setSubtitleConfigurations(configs)
        return builder.build()
    }

    fun find(uri: Uri): List<MediaItem.SubtitleConfiguration> {
        if (uri.scheme != "file") return emptyList()
        val path = uri.path ?: return emptyList()
        val base = path.substringBeforeLast('.')
        return exts.mapNotNull { (ext, mime) ->
            val file = File("$base.$ext")
            if (!file.isFile) return@mapNotNull null
            MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
                .setMimeType(mime)
                .setLanguage("und")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                .build()
        }
    }
}
