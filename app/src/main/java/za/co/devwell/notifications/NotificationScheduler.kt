package za.co.devwell.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import za.co.devwell.model.NotificationPreferences
import java.time.Duration
import java.time.LocalTime
import java.util.concurrent.TimeUnit

private const val DAILY_REMINDER_WORK_NAME = "daily_reminder"
private const val CUSTOM_REMINDER_WORK_NAME = "custom_reminder"

const val KEY_QUIET_START = "quiet_start"
const val KEY_QUIET_END = "quiet_end"
const val KEY_CUSTOM_REMINDER = "custom_reminder"

object NotificationScheduler {

    fun scheduleInMinutes(context: Context, minutes: Int) {
        require(minutes in 1..10_080) { "Reminder delay must be between 1 minute and 7 days." }
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
            .setInputData(Data.Builder().putBoolean(KEY_CUSTOM_REMINDER, true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CUSTOM_REMINDER_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    // Call every time preferences are saved
    fun applyPreferences(context: Context, prefs: NotificationPreferences) {
        if (!prefs.dailyEnabled) {
            cancel(context)
            return
        }
        schedule(context, prefs)
    }

    private fun schedule(context: Context, prefs: NotificationPreferences) {
        val initialDelay = delayUntil(prefs.reminderTime)

        val data = Data.Builder()
            .putString(KEY_QUIET_START, prefs.quietStart)
            .putString(KEY_QUIET_END, prefs.quietEnd)
            .build()

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(Duration.ofDays(1))
            .setInitialDelay(initialDelay.toMinutes(), TimeUnit.MINUTES)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_REMINDER_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_REMINDER_WORK_NAME)
    }


    private fun delayUntil(reminderTime: String): Duration {
        val target = runCatching { LocalTime.parse(reminderTime) }.getOrDefault(LocalTime.of(9, 0))
        val now = LocalTime.now()
        val minutesUntil = if (target.isAfter(now)) {
            Duration.between(now, target)
        } else {
            Duration.between(now, target).plusDays(1)
        }
        return minutesUntil
    }
}
