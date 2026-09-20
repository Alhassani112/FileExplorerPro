package com.example.fileexplorerpro.data

/**
 * تحقق من أسماء الملفات قبل الإنشاء أو إعادة التسمية لمنع Path Traversal.
 */
object FileNames {
    /**
     * @return الاسم الآمن أو `null` إن كان فارغاً أو يحتوي فواصل مسار.
     */
    fun sanitize(name: String): String? {
        val n = name.trim()
        if (n.isEmpty() || n.length > 255) return null
        if (n == "." || n == "..") return null
        if (n.contains('/') || n.contains('\\') || n.contains('\u0000')) return null
        if (n.any { it.code < 32 }) return null
        return n
    }
}
