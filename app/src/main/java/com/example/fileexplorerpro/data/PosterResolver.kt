package com.example.fileexplorerpro.data
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
object PosterResolver {
    private val names = listOf("folder", "cover", "poster", "thumb", "thumbnail")
    private val exts = listOf("jpg", "jpeg", "png", "webp")
    private val cache = mutableMapOf<String, Uri?>()
    fun findPoster(context: Context, dirUri: Uri): Uri? {
        val key = dirUri.toString()
        cache[key]?.let { return it }
        val dir = DocumentFile.fromTreeUri(context, dirUri) ?: return null
        for (n in names) for (e in exts) {
            val c = dir.findFile("$n.$e")
            if (c != null && c.isFile) { cache[key] = c.uri; return c.uri }
        }
        val firstImage = dir.listFiles().firstOrNull { f ->
            val name = f.name?.lowercase() ?: return@firstOrNull false
            f.isFile && exts.any { name.endsWith(".$it") } && name.length <= 20
        }
        if (firstImage != null) { cache[key] = firstImage.uri; return firstImage.uri }
        cache[key] = null
        return null
    }
    fun clearCache() { cache.clear() }
}
