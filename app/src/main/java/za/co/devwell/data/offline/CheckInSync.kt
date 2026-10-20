package za.co.devwell.data.offline

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import za.co.devwell.data.AppContainer

/** Uploads check-ins */
class CheckInSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // The app process may have been started by WorkManager, so make sure the container exists.
        AppContainer.initialize(applicationContext)
        return if (AppContainer.wellbeing.syncPending()) Result.success() else Result.retry()
    }
}

object CheckInSyncScheduler {
    /** Runs once the phone has a network connection; retries with backoff if the upload fails. */
    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<CheckInSyncWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork("check_in_sync", ExistingWorkPolicy.REPLACE, request)
    }
}