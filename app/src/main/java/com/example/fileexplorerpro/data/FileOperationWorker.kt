package com.example.fileexplorerpro.data
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
class FileOperationWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val op = inputData.getString("op") ?: return@withContext Result.failure()
            val uris = inputData.getStringArray("uris")?.toList() ?: return@withContext Result.failure()
            val dest = Uri.parse(inputData.getString("dest") ?: return@withContext Result.failure())
            val repo = FileRepository(applicationContext)
            uris.forEach { u ->
                val uri = Uri.parse(u)
                val df = DocumentFile.fromSingleUri(applicationContext, uri) ?: return@forEach
                val item = FileItem(df.uri, df.name ?: "file", df.isDirectory,
                    df.length(), df.lastModified(), df.type)
                if (op == "copy") repo.copyRecursive(item, dest) else repo.moveRecursive(item, dest)
            }
            Result.success()
        } catch (e: Exception) { Result.failure() }
    }
    companion object {
        fun enqueue(ctx: Context, op: String, uris: List<Uri>, dest: Uri) {
            WorkManager.getInstance(ctx).enqueue(
                OneTimeWorkRequestBuilder<FileOperationWorker>()
                    .setInputData(workDataOf("op" to op,
                        "uris" to uris.map { it.toString() }.toTypedArray(),
                        "dest" to dest.toString()))
                    .build())
        }
    }
}
