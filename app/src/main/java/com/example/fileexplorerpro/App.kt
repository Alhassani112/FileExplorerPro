package com.example.fileexplorerpro
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "مشغل الوسائط",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "تحكم في التشغيل" }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
    companion object {
        const val CHANNEL_ID = "media_playback"
    }
}
