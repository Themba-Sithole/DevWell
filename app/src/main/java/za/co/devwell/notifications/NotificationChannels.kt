package za.co.devwell.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

const val DAILY_REMINDER_CHANNEL_ID = "daily_reminder_channel"

/**
 * Call this once, early, before any notification is posted.
 * Safe to call multiple times — recreating an existing channel is a no-op.
 */
fun createNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return // channels don't exist pre-Android 8

    val channel = NotificationChannel(
        DAILY_REMINDER_CHANNEL_ID,
        "Daily check-in reminder",
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = "Reminds you to complete your daily wellbeing check-in"
    }

    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(channel)
}
