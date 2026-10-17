package za.co.devwell.data.offline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingCheckInDao {
    /** Replaces an existing queued check-in for the same user and day. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PendingCheckIn)

    @Query("SELECT * FROM pending_check_in WHERE userId = :userId ORDER BY createdAt")
    suspend fun forUser(userId: String): List<PendingCheckIn>

    @Query("DELETE FROM pending_check_in WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM pending_check_in WHERE userId = :userId AND localDate = :localDate")
    suspend fun deleteForDay(userId: String, localDate: String)
}