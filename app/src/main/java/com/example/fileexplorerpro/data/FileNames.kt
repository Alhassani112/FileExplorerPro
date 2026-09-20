package com.example.fileexplorerpro.data

object FileNames {
    fun sanitize(name: String): String? {
        val n = name.trim()
        if (n.isEmpty() || n.length > 255) return null
        if (n == "." || n == "..") return null
        if (n.contains('/') || n.contains('\\') || n.contains('\u0000')) return null
        if (n.any { it.code < 32 }) return null
        return n
    }
}
