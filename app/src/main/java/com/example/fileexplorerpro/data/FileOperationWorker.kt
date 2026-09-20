package com.example.fileexplorerpro.data

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.fileexplorerpro.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class FileOperationWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            setForeground(foregroundInfo())
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

    private fun foregroundInfo(): ForegroundInfo {
        val notification: Notification = NotificationCompat.Builder(
            applicationContext,
            App.FILE_OPS_CHANNEL
        )
            .setContentTitle("عمليات الملفات")
            .setContentText("جارٍ النسخ أو النقل…")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
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
        private const val NOTIFICATION_ID = 1001

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
