package com.example.fileexplorerpro.player

import android.content.Context
import android.net.Uri

/** يحفظ موضع التشغيل لكل URI لاستئنافه لاحقاً. */
class PlayerResumeStore(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("player_resume", Context.MODE_PRIVATE)

    fun save(uri: Uri, positionMs: Long, durationMs: Long = 0L) {
        if (positionMs < 5_000L) {
            prefs.edit().remove(uri.toString()).apply()
            return
        }
        if (durationMs > 0 && positionMs > durationMs - 8_000L) {
            prefs.edit().remove(uri.toString()).apply()
            return
        }
        prefs.edit().putLong(uri.toString(), positionMs).apply()
    }

    fun get(uri: Uri): Long = prefs.getLong(uri.toString(), 0L)
}
