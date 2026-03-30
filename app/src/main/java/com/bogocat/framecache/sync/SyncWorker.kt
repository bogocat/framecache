package com.bogocat.framecache.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bogocat.framecache.api.ImmichApi
import com.bogocat.framecache.api.model.AssetDto
import com.bogocat.framecache.data.cache.ImageCacheManager
import com.bogocat.framecache.data.db.AssetDao
import com.bogocat.framecache.data.db.CachedAsset
import com.bogocat.framecache.data.settings.SettingsRepository
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
            // Sync local folder if enabled
            val localEnabled = settings.localFolderEnabled.first()
            val localUri = settings.localFolderUri.first()
            if (localEnabled && localUri.isNotBlank()) {
                syncLocalFolder(localUri)
            }

            val albumIds = settings.albumIds.first()
            Log.i(TAG, "Album IDs from settings: $albumIds")
            if (albumIds.isEmpty() && !localEnabled) {
                Log.w(TAG, "No sources configured, skipping sync")
                return Result.success()
            }

            if (albumIds.isEmpty()) {
                // Local-only mode, skip Immich sync
                val count = assetDao.getCachedCount()
                Log.i(TAG, "Local-only sync complete. $count images cached")
                val now = java.text.SimpleDateFormat("MMM dd, h:mm a", java.util.Locale.getDefault())
                    .format(java.util.Date())
                settings.save(SettingsRepository.LAST_SYNC_TIME, now)
                return Result.success()
            }

            Log.i(TAG, "Starting Immich sync for ${albumIds.size} album(s)")

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

            // Prune assets removed from albums + delete orphaned files
            val keepIds = allAssets.map { it.id }
            val allCachedFiles = cacheManager.getAllCachedIds()
            val orphanedFiles = allCachedFiles - keepIds.toSet()
            for (orphanId in orphanedFiles) {
                cacheManager.getImageFile(orphanId).delete()
            }
            if (orphanedFiles.isNotEmpty()) Log.i(TAG, "Deleted ${orphanedFiles.size} orphaned files")
            assetDao.pruneRemoved(keepIds)

            // Re-link existing files on disk (e.g., after DB migration)
            val uncached = assetDao.getUncachedAssets(1000)
            var relinked = 0
            var toDownload = mutableListOf<CachedAsset>()
            for (asset in uncached) {
                if (cacheManager.isAssetCached(asset.id)) {
                    assetDao.updateFilePath(asset.id, cacheManager.getImageFile(asset.id).absolutePath)
                    relinked++
                } else {
                    toDownload.add(asset)
                }
            }
            if (relinked > 0) Log.i(TAG, "Re-linked $relinked existing files")

            // Download truly uncached images
            val batch = toDownload.take(DOWNLOAD_BATCH_SIZE)
            Log.i(TAG, "Downloading ${batch.size} uncached images")
            for (asset in batch) {
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
        // Dedup camera make/model ("Canon Canon EOS 5D" -> "Canon EOS 5D")
        val camera = exifInfo?.let {
            val make = it.make?.trim()
            val model = it.model?.trim()
            when {
                make == null && model == null -> null
                make == null -> model
                model == null -> make
                model.startsWith(make, ignoreCase = true) -> model
                else -> "$make $model"
            }
        }

        // Person birth dates for age calculation
        val birthDates = people
            ?.mapNotNull { p -> p.name?.let { name -> p.birthDate?.let { bd -> "$name=$bd" } } }
            ?.joinToString(";")
            ?.ifBlank { null }

        return CachedAsset(
            id = id,
            albumId = albumId,
            thumbhash = thumbhash,
            dateTaken = dateTaken,
            location = location,
            description = exifInfo?.description?.takeIf { it.isNotBlank() },
            cameraModel = camera,
            peopleName = peopleNames,
            peopleBirthDates = birthDates,
            rating = exifInfo?.rating,
            isFavorite = isFavorite,
            width = width,
            height = height
        )
    }

    private suspend fun syncLocalFolder(uriString: String) {
        try {
            val uri = android.net.Uri.parse(uriString)
            val resolver = applicationContext.contentResolver
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                uri, android.provider.DocumentsContract.getTreeDocumentId(uri)
            )
            val cursor = resolver.query(
                childrenUri,
                arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                    android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null, null, null
            ) ?: return

            val localAssets = mutableListOf<CachedAsset>()
            cursor.use {
                while (it.moveToNext()) {
                    val docId = it.getString(0)
                    val name = it.getString(1)
                    val mime = it.getString(2) ?: ""
                    val lastModified = it.getLong(3)

                    if (!mime.startsWith("image/")) continue

                    val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, docId)
                    val id = "local_${docUri.hashCode()}"

                    localAssets.add(CachedAsset(
                        id = id,
                        albumId = "local",
                        dateTaken = if (lastModified > 0) lastModified else null,
                        description = name,
                        filePath = docUri.toString(),
                        cachedAt = System.currentTimeMillis()
                    ))
                }
            }

            if (localAssets.isNotEmpty()) {
                assetDao.insertAll(localAssets)
                Log.i(TAG, "Local folder: ${localAssets.size} images found")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Local folder sync failed: ${e.message}")
        }
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
