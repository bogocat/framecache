package com.bogocat.framecache.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bogocat.framecache.api.navidrome.NavidromeClient
import com.bogocat.framecache.data.cache.MusicCacheManager
import com.bogocat.framecache.data.db.CachedPlaylist
import com.bogocat.framecache.data.db.CachedSong
import com.bogocat.framecache.data.db.SongDao
import com.bogocat.framecache.data.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.File

@HiltWorker
class MusicSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val navidromeClient: NavidromeClient,
    private val songDao: SongDao,
    private val cacheManager: MusicCacheManager,
    private val settings: SettingsRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "MusicSync"
        const val DOWNLOAD_BATCH_SIZE = 10
    }

    override suspend fun doWork(): Result {
        val enabled = settings.navidromeEnabled.first()
        if (!enabled) {
            Log.i(TAG, "Music disabled, skipping sync")
            return Result.success()
        }

        return try {
            val syncIds = settings.navidromeSyncPlaylistIds.first()
            syncPlaylists(syncIds)
            cleanUnsyncedPlaylists(syncIds)
            downloadUncachedSongs(syncIds)
            cacheManager.evictIfNeeded()

            val cached = songDao.getCachedCount()
            val total = songDao.getTotalCount()
            Log.i(TAG, "Music sync complete: $cached/$total songs cached, ${syncIds.size} playlists selected for caching")

            val now = java.text.SimpleDateFormat("MMM dd, h:mm a", java.util.Locale.getDefault())
                .format(java.util.Date())
            settings.save(SettingsRepository.LAST_MUSIC_SYNC_TIME, now)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Music sync failed: ${e.message}", e)
            Result.retry()
        }
    }

    private suspend fun syncPlaylists(syncIds: Set<String>) {
        val remotePlaylists = navidromeClient.getPlaylists()
        if (remotePlaylists.isEmpty()) {
            Log.i(TAG, "No playlists found")
            return
        }

        // Update all playlist metadata (so they're all visible)
        val cached = remotePlaylists.map {
            CachedPlaylist(
                id = it.id,
                name = it.name,
                songCount = it.songCount,
                duration = it.duration,
                coverArt = it.coverArt
            )
        }
        songDao.insertPlaylists(cached)
        songDao.prunePlaylists(remotePlaylists.map { it.id })
        Log.i(TAG, "Synced ${remotePlaylists.size} playlists metadata")

        // Sync song metadata for ALL playlists (so they can be streamed)
        for (playlist in remotePlaylists) {
            try {
                val (_, songs) = navidromeClient.getPlaylist(playlist.id)
                val cachedSongs = songs.map { song ->
                    val existing = songDao.getById(song.id)
                    CachedSong(
                        id = song.id,
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        albumId = song.albumId,
                        coverArt = song.coverArt,
                        duration = song.duration,
                        track = song.track,
                        year = song.year,
                        genre = song.genre,
                        playlistId = playlist.id,
                        filePath = existing?.filePath,
                        coverPath = existing?.coverPath,
                        fileSize = existing?.fileSize ?: 0,
                        playCount = existing?.playCount ?: 0,
                        lastPlayed = existing?.lastPlayed
                    )
                }
                songDao.insertSongs(cachedSongs)
                Log.d(TAG, "Playlist '${playlist.name}': ${songs.size} songs")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync playlist ${playlist.name}: ${e.message}")
            }
        }
    }

    /**
     * Delete cached audio/cover files for playlists not selected for sync.
     */
    private suspend fun cleanUnsyncedPlaylists(syncIds: Set<String>) {
        if (syncIds.isEmpty()) return

        val allPlaylists = songDao.getAllPlaylists()
        for (playlist in allPlaylists) {
            if (playlist.id in syncIds) continue

            // Delete cached files for songs in unsynced playlists
            val songs = songDao.getSongsForPlaylist(playlist.id)
            var cleaned = 0
            for (song in songs) {
                if (song.filePath != null) {
                    File(song.filePath).delete()
                    song.coverPath?.let { File(it).delete() }
                    songDao.updateFilePath(song.id, "", 0)
                    cleaned++
                }
            }
            if (cleaned > 0) {
                Log.i(TAG, "Cleaned $cleaned cached files from unsynced playlist '${playlist.name}'")
            }
        }
    }

    /**
     * Only download songs for playlists selected for caching.
     */
    private suspend fun downloadUncachedSongs(syncIds: Set<String>) {
        if (syncIds.isEmpty()) {
            Log.d(TAG, "No playlists selected for caching")
            return
        }

        val uncached = songDao.getUncachedSongsForPlaylists(syncIds.toList(), DOWNLOAD_BATCH_SIZE)
        if (uncached.isEmpty()) {
            Log.d(TAG, "All selected playlist songs cached")
            return
        }

        Log.i(TAG, "Downloading ${uncached.size} songs")
        for (song in uncached) {
            cacheManager.downloadSong(song.id)
            if (song.coverArt != null && song.coverPath == null) {
                cacheManager.downloadCover(song.id, song.coverArt)
            }
        }
    }
}
