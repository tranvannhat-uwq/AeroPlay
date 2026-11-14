package com.aeroplay.app.core.common

import java.util.Locale

object FormatUtils {
    fun formatDuration(seconds: Long): String {
        if (seconds <= 0L) return "LIVE"
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    fun formatMillis(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0L)) / 1000L
        val hrs = totalSeconds / 3600
        val mins = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return if (hrs > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    fun formatViewCount(views: Long): String {
        if (views < 0) return ""
        return when {
            views >= 1_000_000_000L -> String.format(Locale.US, "%.1fB lượt xem", views / 1_000_000_000.0)
            views >= 1_000_000L -> String.format(Locale.US, "%.1fM lượt xem", views / 1_000_000.0)
            views >= 1_000L -> String.format(Locale.US, "%.1fK lượt xem", views / 1_000.0)
            else -> "$views lượt xem"
        }
    }

    fun formatSubscribers(subscribers: Long): String {
        if (subscribers < 0) return ""
        return when {
            subscribers >= 1_000_000L -> String.format(Locale.US, "%.2fM người đăng ký", subscribers / 1_000_000.0)
            subscribers >= 1_000L -> String.format(Locale.US, "%.1fK người đăng ký", subscribers / 1_000.0)
            else -> "$subscribers người đăng ký"
        }
    }
}
