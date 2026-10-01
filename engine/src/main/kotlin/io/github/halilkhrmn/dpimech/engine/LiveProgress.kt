package io.github.halilkhrmn.dpimech.engine

import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Progress in a notification: on Android 16 a Live Update (progress style, promoted to the
 * status bar chip and lock screen when the system allows it), a plain progress bar before.
 */
object LiveProgress {
    fun apply(builder: NotificationCompat.Builder, done: Int, total: Int, chip: String? = null): NotificationCompat.Builder {
        val max = total.coerceAtLeast(1)
        val value = done.coerceIn(0, max)
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setStyle(
                NotificationCompat.ProgressStyle()
                    .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(max)))
                    .setProgress(value)
                    .setProgressIndeterminate(total <= 0),
            )
            builder.setRequestPromotedOngoing(true)
            chip?.let { builder.setShortCriticalText(it) }
        } else {
            builder.setProgress(max, value, total <= 0)
        }
        return builder
    }
}
