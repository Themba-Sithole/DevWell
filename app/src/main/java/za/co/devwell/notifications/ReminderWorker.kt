package za.co.devwell.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        // Android 13+ requires this permission to be granted at runtime, not just declared.
        // If it's missing, don't crash — just skip silently and try again next scheduled run.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return Result.success()
        }

        val notification = NotificationCompat.Builder(applicationContext, DAILY_REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // change to our logo later
            .setContentTitle("Daily check-in")
            .setContentText("Take a minute for your DevWell check-in.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(REMINDER_NOTIFICATION_ID, notification)
        return Result.success()
    }

    companion object {
        const val REMINDER_NOTIFICATION_ID = 1001
    }
}
