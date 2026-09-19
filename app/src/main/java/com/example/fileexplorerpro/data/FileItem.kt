package com.example.fileexplorerpro.data
import android.net.Uri
data class FileItem(
    val uri: Uri, val name: String, val isDirectory: Boolean,
    val size: Long, val lastModified: Long, val mimeType: String?,
    val isHidden: Boolean = name.startsWith("."),
    val posterUri: Uri? = null
) {
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
    val isVideo get() = extension in videoExts
    val isAudio get() = extension in audioExts
    val isImage get() = extension in imageExts
    val isSubtitle get() = extension in subtitleExts
    val isDocument get() = extension in documentExts
    val isArchive get() = extension in archiveExts
    val isPlayable get() = isVideo || isAudio
    companion object {
        val videoExts = setOf("mp4","mkv","avi","mov","webm","flv","3gp","ts","m4v","wmv","rmvb","rm")
        val audioExts = setOf("mp3","m4a","aac","flac","ogg","wav","wma","opus","amr","mid","midi")
        val imageExts = setOf("jpg","jpeg","png","gif","webp","bmp","svg","tiff","tif","ico")
        val subtitleExts = setOf("srt","ass","ssa","sub","idx","vtt","smi")
        val documentExts = setOf("pdf","doc","docx","xls","xlsx","ppt","pptx","txt","csv","xml","json")
        val archiveExts = setOf("zip","rar","7z","tar","gz","bz2","xz","lz")
    }
}
