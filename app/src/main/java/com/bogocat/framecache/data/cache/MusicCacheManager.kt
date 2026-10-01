package com.bogocat.framecache.data.cache

import android.content.Context
import android.util.Log
import com.bogocat.framecache.api.navidrome.NavidromeClient
import com.bogocat.framecache.data.db.SongDao
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val navidromeClient: NavidromeClient
) {
    companion object {
        const val TAG = "MusicCache"
        const val DEFAULT_MAX_SONGS = 200
    }

    private val songDir: File
        get() = File(context.filesDir, "music_cache").also { it.mkdirs() }

    private val coverDir: File
        get() = File(context.filesDir, "music_covers").also { it.mkdirs() }

    private val httpClient = OkHttpClient()

    fun getSongFile(songId: String): File = File(songDir, "$songId.mp3")
    fun getCoverFile(songId: String): File = File(coverDir, "$songId.jpg")

    fun isSongCached(songId: String): Boolean = getSongFile(songId).exists()

    suspend fun downloadSong(songId: String): String? {
        return try {
            val url = navidromeClient.getStreamUrl(songId)
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Download failed for $songId: ${response.code}")
                return null
            }
            val file = getSongFile(songId)
            response.body?.byteStream()?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val size = file.length()
            songDao.updateFilePath(songId, file.absolutePath, size)
            Log.d(TAG, "Cached song $songId (${size / 1024}KB)")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache song $songId: ${e.message}")
            null
        }
    }

    suspend fun downloadCover(songId: String, coverArt: String): String? {
        return try {
            val url = navidromeClient.getCoverArtUrl(coverArt, 600)
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val file = getCoverFile(songId)
            response.body?.byteStream()?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            songDao.updateCoverPath(songId, file.absolutePath)
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache cover for $songId: ${e.message}")
            null
        }
    }

    suspend fun evictIfNeeded(maxCount: Int = DEFAULT_MAX_SONGS) {
        val count = songDao.getCachedCount()
        if (count > maxCount) {
            val toEvict = songDao.getOldestPlayed(count - maxCount)
            for (song in toEvict) {
                // Delete the audio/cover files but keep the row so the song stays
                // in the indexed library (it just becomes streaming-only).
                song.filePath?.let { File(it).delete() }
                song.coverPath?.let { File(it).delete() }
                songDao.clearPaths(song.id)
            }
            Log.i(TAG, "Evicted ${toEvict.size} song files (kept index rows)")
        }
    }

    fun getCacheSizeBytes(): Long {
        val songs = songDir.listFiles()?.sumOf { it.length() } ?: 0
        val covers = coverDir.listFiles()?.sumOf { it.length() } ?: 0
        return songs + covers
    }

    fun getCachedFileCount(): Int {
        return songDir.listFiles()?.size ?: 0
    }
}
