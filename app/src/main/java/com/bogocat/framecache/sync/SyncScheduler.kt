package com.bogocat.framecache.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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

    fun schedulePeriodicSync(context: Context) {
        val imageWork = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(wifiConstraints)
            .build()

        val musicWork = PeriodicWorkRequestBuilder<MusicSyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(wifiConstraints)
            .build()

        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, imageWork)
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
