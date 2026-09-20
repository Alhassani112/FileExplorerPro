package com.example.fileexplorerpro.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileRepository(private val ctx: Context) {

    data class StorageInfo(val total: Long, val used: Long, val free: Long)

    suspend fun listDirectory(dirUri: Uri, showHidden: Boolean): List<FileItem> =
        withContext(Dispatchers.IO) {
            val dir = DocumentFile.fromTreeUri(ctx, dirUri) ?: return@withContext emptyList()
            dir.listFiles().mapNotNull { f ->
                val n = f.name ?: return@mapNotNull null
                if (!showHidden && n.startsWith(".")) return@mapNotNull null
                FileItem(
                    f.uri, n, f.isDirectory,
                    if (f.isDirectory) 0 else f.length(),
                    f.lastModified(), f.type
                )
            }.map {
                if (it.isDirectory)
                    it.copy(posterUri = PosterResolver.findPoster(ctx, it.uri))
                else it
            }
        }

    suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val total = stat.totalBytes
            val free = stat.freeBytes
            StorageInfo(total, total - free, free)
        } catch (e: Exception) {
            StorageInfo(0, 0, 0)
        }
    }

    suspend fun delete(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            if (uri.scheme == "file") {
                File(uri.path ?: return@withContext false).delete()
            } else {
                DocumentFile.fromSingleUri(ctx, uri)?.delete() ?: false
            }
        } catch (_: Exception) { false }
    }

    suspend fun rename(uri: Uri, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (uri.scheme == "file") {
                val src = File(uri.path ?: return@withContext false)
                src.renameTo(File(src.parentFile, newName))
            } else {
                DocumentFile.fromSingleUri(ctx, uri)?.renameTo(newName) ?: false
            }
        } catch (_: Exception) { false }
    }

    suspend fun createFolder(p: Uri, name: String): Uri? = withContext(Dispatchers.IO) {
        try {
            if (p.scheme == "file") {
                val base = File(p.path ?: return@withContext null)
                val nf = File(base, name)
                if (nf.mkdirs()) Uri.fromFile(nf) else null
            } else {
                DocumentFile.fromTreeUri(ctx, p)?.createDirectory(name)?.uri
            }
        } catch (_: Exception) { null }
    }

    suspend fun copyRecursive(s: FileItem, destUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val dest = DocumentFile.fromTreeUri(ctx, destUri) ?: return@withContext false
            if (!s.isDirectory) {
                val src = DocumentFile.fromSingleUri(ctx, s.uri) ?: return@withContext false
                val nf = dest.createFile(src.type ?: "application/octet-stream", s.name)
                    ?: return@withContext false
                ctx.contentResolver.openInputStream(s.uri)?.use { i ->
                    ctx.contentResolver.openOutputStream(nf.uri)?.use { o ->
                        i.copyTo(o, 65536)
                    }
                }
                return@withContext true
            }
            val src = DocumentFile.fromTreeUri(ctx, s.uri) ?: return@withContext false
            val nd = dest.createDirectory(s.name) ?: return@withContext false
            for (c in src.listFiles()) {
                copyRecursive(
                    FileItem(
                        c.uri, c.name ?: "?", c.isDirectory,
                        c.length(), c.lastModified(), c.type
                    ),
                    nd.uri
                )
            }
            true
        } catch (e: Exception) { false }
    }

    suspend fun moveRecursive(s: FileItem, destUri: Uri): Boolean =
        copyRecursive(s, destUri).also { if (it) delete(s.uri) }

    fun uriToFile(uri: Uri): File? {
        return try {
            if (uri.scheme == "file") {
                val p = uri.path
                if (p != null) File(p) else null
            } else null
        } catch (_: Exception) { null }
    }
}
