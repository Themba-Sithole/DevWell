package za.co.devwell.data.offline

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PendingCheckIn::class], version = 1, exportSchema = false)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun pendingCheckIns(): PendingCheckInDao

    companion object {
        @Volatile private var instance: OfflineDatabase? = null

        fun get(context: Context): OfflineDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OfflineDatabase::class.java,
                    "devwell_offline.db",
                ).build().also { instance = it }
            }
    }
}