package com.example.fileexplorerpro.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ShareHelper {
    suspend fun shareIntent(ctx: Context, item: FileItem): Intent? = withContext(Dispatchers.IO) {
        val uri = shareableUri(ctx, item) ?: return@withContext null
        Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun shareableUri(ctx: Context, item: FileItem): Uri? {
        if (item.uri.scheme != "file") return item.uri
        val src = File(item.uri.path ?: return null)
        if (!src.isFile) return null
        val dest = File(ctx.cacheDir, src.name)
        src.inputStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", dest)
    }
}
