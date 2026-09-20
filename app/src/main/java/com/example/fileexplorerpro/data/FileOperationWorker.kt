package com.example.fileexplorerpro.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class FileOperationWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val op = inputData.getString("op") ?: return@withContext Result.failure()
            val uris = inputData.getStringArray("uris")?.toList() ?: return@withContext Result.failure()
            val dest = Uri.parse(inputData.getString("dest") ?: return@withContext Result.failure())
            val repo = FileRepository(applicationContext)
            uris.forEach { u ->
                val uri = Uri.parse(u)
                val item = itemFrom(uri) ?: return@forEach
                val ok = if (op == "copy") repo.copyRecursive(item, dest)
                else repo.moveRecursive(item, dest)
                if (!ok) return@withContext Result.failure()
            }
            Result.success()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    private fun itemFrom(uri: Uri): FileItem? {
        if (uri.scheme == "file") {
            val f = File(uri.path ?: return null)
            if (!f.exists()) return null
            return FileItem(
                Uri.fromFile(f), f.name, f.isDirectory,
                if (f.isDirectory) 0 else f.length(), f.lastModified(), null
            )
        }
        val df = DocumentFile.fromSingleUri(applicationContext, uri)
            ?: DocumentFile.fromTreeUri(applicationContext, uri)
            ?: return null
        return FileItem(
            df.uri, df.name ?: "file", df.isDirectory,
            df.length(), df.lastModified(), df.type
        )
    }

    companion object {
        const val UNIQUE_NAME = "file_ops"

        fun enqueue(ctx: Context, op: String, uris: List<Uri>, dest: Uri): UUID {
            val request = OneTimeWorkRequestBuilder<FileOperationWorker>()
                .setInputData(
                    workDataOf(
                        "op" to op,
                        "uris" to uris.map { it.toString() }.toTypedArray(),
                        "dest" to dest.toString()
                    )
                )
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
            return request.id
        }
    }
}
