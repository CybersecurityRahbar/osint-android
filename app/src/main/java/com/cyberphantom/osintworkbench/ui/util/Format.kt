package com.cyberphantom.osintworkbench.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatTime(timestamp: Long): String = runCatching {
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}.getOrDefault("—")

fun formatClock(timestamp: Long): String = runCatching {
    SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestamp))
}.getOrDefault("--:--:--")

fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> "${seconds}s"
        else -> "${seconds / 60}m ${seconds % 60}s"
    }
}

fun safeFileName(raw: String): String =
    raw.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-')
        .take(50)
        .ifBlank { "osint" }
