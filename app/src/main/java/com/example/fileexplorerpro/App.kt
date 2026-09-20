package com.example.fileexplorerpro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.StrictMode
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build()
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "مشغل الوسائط",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "تحكم في التشغيل" }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    FILE_OPS_CHANNEL, "عمليات الملفات",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "نسخ ونقل الملفات" }
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "media_playback"
        const val FILE_OPS_CHANNEL = "file_ops"
    }
}
