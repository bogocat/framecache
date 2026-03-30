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
            val localEnabled = settings.localFolderEnabled.first()
            val localUri = settings.localFolderUri.first()
            val albumIds = settings.albumIds.first()

            if (albumIds.isEmpty() && !localEnabled) {
                Log.i(TAG, "No sources configured, keeping existing cache")
                return Result.success()
            }

            // Track valid IDs per source — only prune what we can confirm
            val confirmedImmichIds = mutableSetOf<String>()
            var immichSyncSucceeded = false

            val confirmedLocalIds = mutableSetOf<String>()
            var localSyncSucceeded = false

            // ── Sync local folder ──
            if (localEnabled && localUri.isNotBlank()) {
                val localIds = syncLocalFolder(localUri)
                confirmedLocalIds.addAll(localIds)
                localSyncSucceeded = true
            }

            // ── Sync Immich albums ──
            if (albumIds.isNotEmpty()) {
                Log.i(TAG, "Starting Immich sync for ${albumIds.size} album(s)")
                val allAssets = mutableListOf<AssetDto>()
                var anyAlbumFetched = false
                for (albumId in albumIds) {
                    try {
                        val album = api.getAlbum(albumId)
                        allAssets.addAll(album.assets.filter { it.type == "IMAGE" })
                        Log.i(TAG, "Album '${album.albumName}': ${album.assets.size} assets")
                        anyAlbumFetched = true
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to fetch album $albumId: ${e.message}")
                    }
                }

                if (anyAlbumFetched) {
                    immichSyncSucceeded = true
                    if (allAssets.isNotEmpty()) {
                        val cached = allAssets.map { it.toCachedAsset(albumIds.first()) }
                        assetDao.insertAll(cached)
                        confirmedImmichIds.addAll(allAssets.map { it.id })
                    }
                } else {
                    Log.w(TAG, "All Immich album fetches failed — keeping existing cache (network may be down)")
                }

                // Re-link existing files on disk
                val uncached = assetDao.getUncachedAssets(1000)
                var relinked = 0
                val toDownload = mutableListOf<CachedAsset>()
                for (asset in uncached) {
                    if (asset.id.startsWith("local_")) continue
                    if (cacheManager.isAssetCached(asset.id)) {
                        assetDao.updateFilePath(asset.id, cacheManager.getImageFile(asset.id).absolutePath)
                        relinked++
                    } else {
                        toDownload.add(asset)
                    }
                }
                if (relinked > 0) Log.i(TAG, "Re-linked $relinked existing files")

                val batch = toDownload.take(DOWNLOAD_BATCH_SIZE)
                if (batch.isNotEmpty()) {
                    Log.i(TAG, "Downloading ${batch.size} uncached images")
                    for (asset in batch) {
                        cacheManager.downloadAndCache(asset.id)
                    }
                }
            }

            // ── Pruning: only prune sources we successfully synced ──
            // If Immich sync succeeded, prune Immich assets not in the confirmed list
            if (immichSyncSucceeded) {
                val allCachedFiles = cacheManager.getAllCachedIds()
                val orphanedFiles = allCachedFiles - confirmedImmichIds
                for (orphanId in orphanedFiles) {
                    cacheManager.getImageFile(orphanId).delete()
                }
                if (orphanedFiles.isNotEmpty()) Log.i(TAG, "Deleted ${orphanedFiles.size} orphaned Immich files")
            }

            // If local sync succeeded, prune local assets not in the confirmed list
            // (safe — local files are still on device, just removes from slideshow)
            if (localSyncSucceeded || !localEnabled) {
                // Prune local assets that are no longer in the folder (or local is disabled)
                val localKeepIds = if (localEnabled) confirmedLocalIds else emptySet()
                // We only prune local_ prefixed assets here
                // handled by pruneRemoved below
            }

            // Build the full keep list: confirmed IDs + unconfirmed source's existing IDs
            val allKeepIds = mutableSetOf<String>()
            if (immichSyncSucceeded) {
                allKeepIds.addAll(confirmedImmichIds)
            } else {
                // Keep all existing Immich assets (don't prune what we can't verify)
                allKeepIds.addAll(assetDao.getUncachedAssets(10000).map { it.id })
                allKeepIds.addAll(cacheManager.getAllCachedIds())
            }
            if (localEnabled && localSyncSucceeded) {
                allKeepIds.addAll(confirmedLocalIds)
            } else if (!localEnabled) {
                // Local disabled — don't keep local assets (prune them)
            } else {
                // Local enabled but sync failed — keep existing local assets
                // They have "local_" prefix
            }

            if (allKeepIds.isNotEmpty()) {
                assetDao.pruneRemoved(allKeepIds.toList())
            }

            cacheManager.evictIfNeeded()

            val count = assetDao.getCachedCount()
            Log.i(TAG, "Sync complete. $count images cached")

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

    private suspend fun syncLocalFolder(uriString: String): Set<String> {
        val ids = mutableSetOf<String>()
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
            ) ?: return ids

            val localAssets = mutableListOf<CachedAsset>()
            cursor.use {
                while (it.moveToNext()) {
                    val docId = it.getString(0)
                    val name = it.getString(1) ?: continue
                    val mime = it.getString(2) ?: ""
                    val lastModified = it.getLong(3)

                    if (!mime.startsWith("image/")) continue
                    // Skip macOS resource fork files
                    if (name.startsWith("._")) continue

                    val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, docId)
                    val id = "local_${docUri.hashCode()}"
                    ids.add(id)

                    localAssets.add(CachedAsset(
                        id = id,
                        albumId = "local",
                        dateTaken = if (lastModified > 0) lastModified else null,
                        description = name.substringBeforeLast("."),
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
        return ids
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
