package com.example.fileexplorerpro.player

import android.content.Context
import android.net.Uri

class PlayerResumeStore(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("player_resume", Context.MODE_PRIVATE)

    fun key(uri: Uri): String = uri.toString()

    fun save(uri: Uri, positionMs: Long) {
        if (positionMs < 5_000L) {
            prefs.edit().remove(key(uri)).apply()
            return
        }
        prefs.edit().putLong(key(uri), positionMs).apply()
    }

    fun get(uri: Uri): Long = prefs.getLong(key(uri), 0L)
}
