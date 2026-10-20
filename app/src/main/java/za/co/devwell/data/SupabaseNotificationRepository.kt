package za.co.devwell.data

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import za.co.devwell.model.DevWellUser
import za.co.devwell.model.NotificationPreferences

/** Keeps a per-account local cache and syncs preferences to the user's RLS-protected row. */
class SupabaseNotificationRepository(
    private val client: SupabaseClient,
    currentUser: StateFlow<DevWellUser?>,
    context: Context,
    scope: CoroutineScope,
) : NotificationRepository {
    private val storage = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(NotificationPreferences())
    private val status = MutableStateFlow<String?>(null)
    @Volatile private var activeUserId: String? = null

    override val preferences: Flow<NotificationPreferences> = state
    override val syncStatus: Flow<String?> = status

    init {
        scope.launch {
            currentUser.collectLatest { user ->
                activeUserId = user?.id
                if (user == null) {
                    state.value = NotificationPreferences()
                    status.value = null
                } else {
                    syncFromAccount(user.id)
                }
            }
        }
    }

    override suspend fun updatePreferences(prefs: NotificationPreferences) {
        val userId = activeUserId
        if (userId == null) {
            status.value = "Sign in to sync notification preferences to your account."
            state.value = prefs
            return
        }

        writeLocal(userId, prefs, dirty = true)
        state.value = prefs
        status.value = "Saved on this device. Syncing to your account…"
        try {
            upload(userId, prefs)
            setDirty(userId, false)
            status.value = "Notification preferences synced to your account."
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            status.value = "Saved on this device. Sync will retry the next time you open the app online."
        }
    }

    private suspend fun syncFromAccount(userId: String) {
        val local = readLocal(userId) ?: importLegacyPreferences(userId)
        state.value = local ?: NotificationPreferences()
        status.value = "Loading notification preferences…"

        try {
            val remote = withContext(Dispatchers.IO) {
                client.from(TABLE).select {
                    filter { eq("user_id", userId) }
                }.decodeList<NotificationPreferenceRow>().firstOrNull()
            }
            if (isDirty(userId) && local != null) {
                upload(userId, local)
                setDirty(userId, false)
                state.value = local
                status.value = "Notification preferences synced to your account."
            } else if (remote != null) {
                val preferences = remote.toPreferences()
                writeLocal(userId, preferences, dirty = false)
                state.value = preferences
                status.value = "Notification preferences loaded from your account."
            } else if (local != null) {
                upload(userId, local)
                setDirty(userId, false)
                status.value = "Notification preferences synced to your account."
            } else {
                status.value = null
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            status.value = "Offline. Showing saved notification preferences from this device."
        }
    }

    private suspend fun upload(userId: String, preferences: NotificationPreferences) = withContext(Dispatchers.IO) {
        client.from(TABLE).upsert(preferences.toRow(userId)) {
            onConflict = "user_id"
        }
    }

    private fun importLegacyPreferences(userId: String): NotificationPreferences? {
        if (storage.getBoolean(LEGACY_IMPORTED, false)) return null
        val legacyKeys = listOf(
            KEY_DAILY_ENABLED, KEY_REMINDER_TIME, KEY_QUIET_START, KEY_QUIET_END,
            KEY_CHECK_IN_REMINDERS, KEY_TEAM_ALERTS, KEY_TIP_NUDGES,
        )
        if (legacyKeys.none(storage::contains)) {
            storage.edit().putBoolean(LEGACY_IMPORTED, true).apply()
            return null
        }
        val preferences = NotificationPreferences(
            dailyEnabled = storage.getBoolean(KEY_DAILY_ENABLED, true),
            reminderTime = storage.getString(KEY_REMINDER_TIME, "09:00") ?: "09:00",
            quietStart = storage.getString(KEY_QUIET_START, "21:00"),
            quietEnd = storage.getString(KEY_QUIET_END, "07:00"),
            checkInReminders = storage.getBoolean(KEY_CHECK_IN_REMINDERS, true),
            teamAlerts = storage.getBoolean(KEY_TEAM_ALERTS, true),
            tipNudges = storage.getBoolean(KEY_TIP_NUDGES, false),
        )
        storage.edit().putBoolean(LEGACY_IMPORTED, true).apply()
        writeLocal(userId, preferences, dirty = false)
        return preferences
    }

    private fun readLocal(userId: String): NotificationPreferences? {
        val prefix = accountPrefix(userId)
        if (!storage.getBoolean("${prefix}saved", false)) return null
        return NotificationPreferences(
            dailyEnabled = storage.getBoolean("${prefix}daily_enabled", true),
            reminderTime = storage.getString("${prefix}reminder_time", "09:00") ?: "09:00",
            quietStart = storage.getString("${prefix}quiet_start", "21:00"),
            quietEnd = storage.getString("${prefix}quiet_end", "07:00"),
            checkInReminders = storage.getBoolean("${prefix}check_in_reminders", true),
            teamAlerts = storage.getBoolean("${prefix}team_alerts", true),
            tipNudges = storage.getBoolean("${prefix}tip_nudges", false),
        )
    }

    private fun writeLocal(userId: String, prefs: NotificationPreferences, dirty: Boolean) {
        val prefix = accountPrefix(userId)
        storage.edit()
            .putBoolean("${prefix}saved", true)
            .putBoolean("${prefix}daily_enabled", prefs.dailyEnabled)
            .putString("${prefix}reminder_time", prefs.reminderTime)
            .putString("${prefix}quiet_start", prefs.quietStart)
            .putString("${prefix}quiet_end", prefs.quietEnd)
            .putBoolean("${prefix}check_in_reminders", prefs.checkInReminders)
            .putBoolean("${prefix}team_alerts", prefs.teamAlerts)
            .putBoolean("${prefix}tip_nudges", prefs.tipNudges)
            .putBoolean("${prefix}dirty", dirty)
            .apply()
    }

    private fun isDirty(userId: String) = storage.getBoolean("${accountPrefix(userId)}dirty", false)

    private fun setDirty(userId: String, dirty: Boolean) {
        storage.edit().putBoolean("${accountPrefix(userId)}dirty", dirty).apply()
    }

    private fun accountPrefix(userId: String) = "account.$userId."

    @Serializable
    private data class NotificationPreferenceRow(
        @SerialName("user_id") val userId: String,
        @SerialName("daily_enabled") val dailyEnabled: Boolean,
        @SerialName("reminder_time") val reminderTime: String,
        @SerialName("quiet_start") val quietStart: String? = null,
        @SerialName("quiet_end") val quietEnd: String? = null,
        @SerialName("check_in_reminders") val checkInReminders: Boolean,
        @SerialName("team_alerts") val teamAlerts: Boolean,
        @SerialName("tip_nudges") val tipNudges: Boolean,
    ) {
        fun toPreferences() = NotificationPreferences(
            dailyEnabled = dailyEnabled,
            reminderTime = reminderTime,
            quietStart = quietStart,
            quietEnd = quietEnd,
            checkInReminders = checkInReminders,
            teamAlerts = teamAlerts,
            tipNudges = tipNudges,
        )
    }

    private fun NotificationPreferences.toRow(userId: String) = NotificationPreferenceRow(
        userId = userId,
        dailyEnabled = dailyEnabled,
        reminderTime = reminderTime,
        quietStart = quietStart,
        quietEnd = quietEnd,
        checkInReminders = checkInReminders,
        teamAlerts = teamAlerts,
        tipNudges = tipNudges,
    )

    private companion object {
        const val TABLE = "notification_preference"
        const val PREFERENCES_FILE = "notification_preferences"
        const val LEGACY_IMPORTED = "legacy_imported_to_account"
        const val KEY_DAILY_ENABLED = "daily_enabled"
        const val KEY_REMINDER_TIME = "reminder_time"
        const val KEY_QUIET_START = "quiet_start"
        const val KEY_QUIET_END = "quiet_end"
        const val KEY_CHECK_IN_REMINDERS = "check_in_reminders"
        const val KEY_TEAM_ALERTS = "team_alerts"
        const val KEY_TIP_NUDGES = "tip_nudges"
    }
}
