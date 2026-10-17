package za.co.devwell.data.offline

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A check-in saved on the phone while offline, waiting to be uploaded.
 * One queued check-in per user per day. userId stops one account's queued
 * check-ins from ever uploading under another account.
 */
@Entity(
    tableName = "pending_check_in",
    indices = [Index(value = ["userId", "localDate"], unique = true)],
)
data class PendingCheckIn(
    @PrimaryKey val id: String,
    val userId: String,
    val localDate: String,
    val wellbeing: Int,
    val workload: Int,
    val energy: Int,
    val focus: Int,
    val afterHoursMinutes: Int,
    val note: String,
    val createdAt: Long,
)