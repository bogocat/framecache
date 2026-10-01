package com.bogocat.framecache.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bogocat.framecache.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private const val PERIODIC_WORK_NAME = "immich_periodic_sync"
    private const val INITIAL_WORK_NAME = "immich_initial_sync"
    private const val MUSIC_PERIODIC_WORK_NAME = "music_periodic_sync"
    private const val MUSIC_IMMEDIATE_WORK_NAME = "music_immediate_sync"

    private val wifiConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .setRequiresStorageNotLow(true)
        .build()

    private val anyNetworkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    // WorkManager enforces a 15-minute minimum for periodic work.
    const val MIN_PERIODIC_MINUTES = 15L

    /**
     * Reads the desired interval and reconciles it with what is actually scheduled.
     * Uses KEEP when nothing changed (safe, won't cancel an in-flight sync, still
     * creates the work if it's missing) and UPDATE only when the interval moved.
     */
    suspend fun applySyncInterval(
        context: Context,
        settings: SettingsRepository,
        desiredMinutes: Int? = null
    ) {
        val desired = desiredMinutes ?: settings.syncIntervalMinutes.first()
        val applied = settings.appliedSyncIntervalMinutes.first()
        schedulePeriodicSync(context, desired.toLong(), updateExisting = desired != applied)
        if (desired != applied) {
            settings.save(SettingsRepository.APPLIED_SYNC_INTERVAL_MINUTES, desired)
        }
    }

    fun schedulePeriodicSync(context: Context, intervalMinutes: Long, updateExisting: Boolean = false) {
        // Clamp so a stale/out-of-range setting can never crash WorkManager.
        val minutes = intervalMinutes.coerceIn(MIN_PERIODIC_MINUTES, 24L * 60L)

        val imageWork = PeriodicWorkRequestBuilder<SyncWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(wifiConstraints)
            .build()

        val musicWork = PeriodicWorkRequestBuilder<MusicSyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(wifiConstraints)
            .build()

        val wm = WorkManager.getInstance(context)
        // applySyncInterval() passes updateExisting=true only when the interval moved,
        // so a routine startup never cancels an in-flight sync.
        val imagePolicy = if (updateExisting) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP
        wm.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, imagePolicy, imageWork)
        wm.enqueueUniquePeriodicWork(MUSIC_PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, musicWork)
    }

    fun triggerImmediateSync(context: Context) {
        val imageWork = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(anyNetworkConstraints)
            .build()

        val musicWork = OneTimeWorkRequestBuilder<MusicSyncWorker>()
            .setConstraints(anyNetworkConstraints)
            .build()

        val wm = WorkManager.getInstance(context)
        wm.enqueueUniqueWork(INITIAL_WORK_NAME, ExistingWorkPolicy.REPLACE, imageWork)
        wm.enqueueUniqueWork(MUSIC_IMMEDIATE_WORK_NAME, ExistingWorkPolicy.REPLACE, musicWork)
    }

    fun triggerMusicSync(context: Context) {
        val work = OneTimeWorkRequestBuilder<MusicSyncWorker>()
            .setConstraints(anyNetworkConstraints)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(MUSIC_IMMEDIATE_WORK_NAME, ExistingWorkPolicy.REPLACE, work)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWork()
    }
}
