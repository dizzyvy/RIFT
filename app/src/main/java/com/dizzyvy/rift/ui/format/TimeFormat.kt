package com.dizzyvy.rift.ui.format

import java.util.Locale

fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return if (seconds >= 3_600L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3_600L, seconds / 60L % 60L, seconds % 60L)
    } else {
        String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L)
    }
}
