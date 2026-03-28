package com.bogocat.immichframe.data.cache

import android.content.Context
import com.bogocat.immichframe.api.ImmichApi
import com.bogocat.immichframe.data.db.AssetDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ImmichApi,
    private val assetDao: AssetDao
) {
    private val cacheDir: File
        get() = File(context.filesDir, "immich_images").also { it.mkdirs() }

    fun getImageFile(assetId: String): File = File(cacheDir, "$assetId.jpg")

    fun isAssetCached(assetId: String): Boolean = getImageFile(assetId).exists()

    suspend fun downloadAndCache(assetId: String): String? {
        return try {
            val response = api.getThumbnail(assetId, "preview")
            val file = getImageFile(assetId)
            response.byteStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val path = file.absolutePath
            assetDao.updateFilePath(assetId, path)
            path
        } catch (e: Exception) {
            null
        }
    }

    suspend fun evictIfNeeded(maxCount: Int = 300) {
        val count = assetDao.getCachedCount()
        if (count > maxCount) {
            val toEvict = assetDao.getOldestDisplayed(count - maxCount)
            for (asset in toEvict) {
                asset.filePath?.let { File(it).delete() }
                assetDao.delete(asset.id)
            }
        }
    }

    fun getCacheSizeBytes(): Long {
        return cacheDir.listFiles()?.sumOf { it.length() } ?: 0
    }

    fun getCacheFileCount(): Int {
        return cacheDir.listFiles()?.size ?: 0
    }
}
