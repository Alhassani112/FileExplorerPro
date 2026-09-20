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

    private fun documentFor(uri: Uri): DocumentFile? {
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            return DocumentFile.fromFile(File(path))
        }
        return DocumentFile.fromTreeUri(ctx, uri) ?: DocumentFile.fromSingleUri(ctx, uri)
    }

    suspend fun listDirectory(dirUri: Uri, showHidden: Boolean): List<FileItem> =
        withContext(Dispatchers.IO) {
            if (dirUri.scheme == "file") {
                val path = dirUri.path ?: return@withContext emptyList()
                return@withContext listByPath(path, showHidden)
            }
            val dir = documentFor(dirUri) ?: return@withContext emptyList()
            if (!dir.isDirectory) return@withContext emptyList()
            dir.listFiles().mapNotNull { f ->
                val n = f.name ?: return@mapNotNull null
                if (!showHidden && n.startsWith(".")) return@mapNotNull null
                FileItem(
                    f.uri, n, f.isDirectory,
                    if (f.isDirectory) 0 else f.length(),
                    f.lastModified(), f.type
                )
            }
        }

    fun listByPath(path: String, showHidden: Boolean): List<FileItem> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val files = dir.listFiles() ?: return emptyList()
        return files.asSequence()
            .filter { showHidden || !it.name.startsWith(".") }
            .map { f ->
                FileItem(
                    uri = Uri.fromFile(f),
                    name = f.name,
                    isDirectory = f.isDirectory,
                    size = if (f.isDirectory) 0 else f.length(),
                    lastModified = f.lastModified(),
                    mimeType = null
                )
            }
            .toList()
    }

    suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val total = stat.totalBytes
            val free = stat.freeBytes
            StorageInfo(total, total - free, free)
        } catch (_: Exception) {
            StorageInfo(0, 0, 0)
        }
    }

    suspend fun delete(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            if (uri.scheme == "file") {
                val f = File(uri.path ?: return@withContext false)
                if (f.isDirectory) f.deleteRecursively() else f.delete()
            } else {
                documentFor(uri)?.delete() ?: false
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun rename(uri: Uri, newName: String): Boolean = withContext(Dispatchers.IO) {
        val safe = FileNames.sanitize(newName) ?: return@withContext false
        try {
            if (uri.scheme == "file") {
                val src = File(uri.path ?: return@withContext false)
                val dest = File(src.parentFile, safe)
                if (dest.exists()) return@withContext false
                src.renameTo(dest)
            } else {
                documentFor(uri)?.renameTo(safe) ?: false
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun createFolder(parent: Uri, name: String): Uri? = withContext(Dispatchers.IO) {
        val safe = FileNames.sanitize(name) ?: return@withContext null
        try {
            if (parent.scheme == "file") {
                val base = File(parent.path ?: return@withContext null)
                val nf = File(base, safe)
                if (nf.mkdirs()) Uri.fromFile(nf) else null
            } else {
                documentFor(parent)?.createDirectory(safe)?.uri
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun copyRecursive(s: FileItem, destUri: Uri): Boolean = withContext(Dispatchers.IO) {
        copyRecursiveInternal(s, destUri)
    }

    private fun copyRecursiveInternal(s: FileItem, destUri: Uri): Boolean {
        return try {
            if (s.uri.scheme == "file" && destUri.scheme == "file") {
                val src = File(s.uri.path ?: return false)
                val destDir = File(destUri.path ?: return false)
                val target = File(destDir, s.name)
                return if (src.isDirectory) src.copyRecursively(target, overwrite = false)
                else {
                    src.copyTo(target, overwrite = false)
                    true
                }
            }
            val dest = documentFor(destUri) ?: return false
            if (!s.isDirectory) {
                val nf = dest.createFile(s.mimeType ?: "application/octet-stream", s.name)
                    ?: return false
                ctx.contentResolver.openInputStream(s.uri)?.use { i ->
                    ctx.contentResolver.openOutputStream(nf.uri)?.use { o ->
                        i.copyTo(o, 65536)
                    }
                } ?: return false
                return true
            }
            val src = documentFor(s.uri) ?: return false
            val nd = dest.createDirectory(s.name) ?: return false
            for (c in src.listFiles()) {
                val child = FileItem(
                    c.uri, c.name ?: "?", c.isDirectory,
                    c.length(), c.lastModified(), c.type
                )
                if (!copyRecursiveInternal(child, nd.uri)) return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun moveRecursive(s: FileItem, destUri: Uri): Boolean =
        copyRecursive(s, destUri).also { if (it) delete(s.uri) }

    fun uriToFile(uri: Uri): File? {
        return try {
            if (uri.scheme == "file") uri.path?.let { File(it) } else null
        } catch (_: Exception) {
            null
        }
    }

    fun currentParentUri(path: String?, uri: Uri?): Uri? {
        return when {
            path != null -> Uri.fromFile(File(path))
            uri != null -> uri
            else -> null
        }
    }
}
