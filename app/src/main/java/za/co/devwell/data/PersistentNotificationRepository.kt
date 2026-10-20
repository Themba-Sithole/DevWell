package za.co.devwell.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import za.co.devwell.model.NotificationPreferences

/** Stores notification preferences on this device across app process restarts. */
class PersistentNotificationRepository(context: Context) : NotificationRepository {
    private val storage = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(readPreferences())

    override val preferences: Flow<NotificationPreferences> = state

    override suspend fun updatePreferences(prefs: NotificationPreferences) {
        storage.edit()
            .putBoolean(KEY_DAILY_ENABLED, prefs.dailyEnabled)
            .putString(KEY_REMINDER_TIME, prefs.reminderTime)
            .putString(KEY_QUIET_START, prefs.quietStart)
            .putString(KEY_QUIET_END, prefs.quietEnd)
            .putBoolean(KEY_CHECK_IN_REMINDERS, prefs.checkInReminders)
            .putBoolean(KEY_TEAM_ALERTS, prefs.teamAlerts)
            .putBoolean(KEY_TIP_NUDGES, prefs.tipNudges)
            .apply()
        state.value = prefs
    }

    private fun readPreferences() = NotificationPreferences(
        dailyEnabled = storage.getBoolean(KEY_DAILY_ENABLED, true),
        reminderTime = storage.getString(KEY_REMINDER_TIME, "09:00") ?: "09:00",
        quietStart = storage.getString(KEY_QUIET_START, "21:00"),
        quietEnd = storage.getString(KEY_QUIET_END, "07:00"),
        checkInReminders = storage.getBoolean(KEY_CHECK_IN_REMINDERS, true),
        teamAlerts = storage.getBoolean(KEY_TEAM_ALERTS, true),
        tipNudges = storage.getBoolean(KEY_TIP_NUDGES, false),
    )

    private companion object {
        const val PREFERENCES_FILE = "notification_preferences"
        const val KEY_DAILY_ENABLED = "daily_enabled"
        const val KEY_REMINDER_TIME = "reminder_time"
        const val KEY_QUIET_START = "quiet_start"
        const val KEY_QUIET_END = "quiet_end"
        const val KEY_CHECK_IN_REMINDERS = "check_in_reminders"
        const val KEY_TEAM_ALERTS = "team_alerts"
        const val KEY_TIP_NUDGES = "tip_nudges"
    }
}
