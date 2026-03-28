package com.bogocat.immichframe.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bogocat.immichframe.api.ImmichApi
import com.bogocat.immichframe.api.model.AssetDto
import com.bogocat.immichframe.data.cache.ImageCacheManager
import com.bogocat.immichframe.data.db.AssetDao
import com.bogocat.immichframe.data.db.CachedAsset
import com.bogocat.immichframe.data.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Locale

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val api: ImmichApi,
    private val assetDao: AssetDao,
    private val cacheManager: ImageCacheManager,
    private val settings: SettingsRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "SyncWorker"
        const val DOWNLOAD_BATCH_SIZE = 20
    }

    override suspend fun doWork(): Result {
        return try {
            val albumIds = settings.albumIds.first()
            Log.i(TAG, "Album IDs from settings: $albumIds")
            if (albumIds.isEmpty()) {
                Log.w(TAG, "No album IDs configured, skipping sync")
                return Result.success()
            }

            Log.i(TAG, "Starting sync for ${albumIds.size} album(s)")

            val allAssets = mutableListOf<AssetDto>()
            for (albumId in albumIds) {
                try {
                    val album = api.getAlbum(albumId)
                    allAssets.addAll(album.assets.filter { it.type == "IMAGE" })
                    Log.i(TAG, "Album '${album.albumName}': ${album.assets.size} assets")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch album $albumId: ${e.message}")
                }
            }

            if (allAssets.isEmpty()) return Result.success()

            // Upsert metadata
            val cached = allAssets.map { it.toCachedAsset(albumIds.first()) }
            assetDao.insertAll(cached)

            // Prune assets removed from albums
            val keepIds = allAssets.map { it.id }
            assetDao.pruneRemoved(keepIds)

            // Download uncached images
            val uncached = assetDao.getUncachedAssets(DOWNLOAD_BATCH_SIZE)
            Log.i(TAG, "Downloading ${uncached.size} uncached images")
            for (asset in uncached) {
                cacheManager.downloadAndCache(asset.id)
            }

            // Evict old images if over limit
            cacheManager.evictIfNeeded()

            val count = assetDao.getCachedCount()
            Log.i(TAG, "Sync complete. $count images cached")

            // Record last sync time
            val now = java.text.SimpleDateFormat("MMM dd, h:mm a", java.util.Locale.getDefault())
                .format(java.util.Date())
            settings.save(SettingsRepository.LAST_SYNC_TIME, now)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}", e)
            Result.retry()
        }
    }

    private fun AssetDto.toCachedAsset(albumId: String): CachedAsset {
        val dateTaken = exifInfo?.dateTimeOriginal?.let { parseDate(it) }
        val location = listOfNotNull(
            exifInfo?.city,
            exifInfo?.state
        ).joinToString(", ").ifBlank { null }
        val peopleNames = people
            ?.mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }
            ?.joinToString(", ")
            ?.ifBlank { null }
        val camera = exifInfo?.let {
            listOfNotNull(it.make, it.model).joinToString(" ").ifBlank { null }
        }

        return CachedAsset(
            id = id,
            albumId = albumId,
            thumbhash = thumbhash,
            dateTaken = dateTaken,
            location = location,
            description = exifInfo?.description?.takeIf { it.isNotBlank() },
            cameraModel = camera,
            peopleName = peopleNames,
            width = width,
            height = height
        )
    }

    private fun parseDate(dateStr: String): Long? {
        return try {
            val formats = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd"
            )
            for (fmt in formats) {
                try {
                    return SimpleDateFormat(fmt, Locale.US).parse(dateStr)?.time
                } catch (_: Exception) {}
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
