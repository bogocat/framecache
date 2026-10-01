package com.bogocat.framecache.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bogocat.framecache.api.navidrome.NavidromeClient
import com.bogocat.framecache.api.navidrome.Song
import com.bogocat.framecache.data.cache.MusicCacheManager
import com.bogocat.framecache.data.db.CachedPlaylist
import com.bogocat.framecache.data.db.CachedSong
import com.bogocat.framecache.data.db.PlaylistSong
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
        const val ALBUM_PAGE_SIZE = 500
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
            val syncFavorites = settings.navidromeSyncFavorites.first()
            val maxSongs = settings.navidromeMaxCachedSongs.first()

            syncLibrary()
            syncStarred()
            syncPlaylists()
            pruneUnwantedFiles(syncIds, syncFavorites)
            downloadUncachedSongs(syncIds, syncFavorites)
            cacheManager.evictIfNeeded(maxSongs)

            val cached = songDao.getCachedCount()
            val total = songDao.getTotalCount()
            val starred = songDao.getStarredCount()
            Log.i(TAG, "Music sync complete: $cached/$total songs cached, $starred starred")

            val now = java.text.SimpleDateFormat("MMM dd, h:mm a", java.util.Locale.getDefault())
                .format(java.util.Date())
            settings.save(SettingsRepository.LAST_MUSIC_SYNC_TIME, now)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Music sync failed: ${e.message}", e)
            Result.retry()
        }
    }

    /**
     * Sync full library metadata via paginated getAlbumList2 + getAlbum per album.
     * Preserves existing file paths and play counts.
     */
    private suspend fun syncLibrary() {
        Log.i(TAG, "Starting full library sync...")
        var offset = 0
        var totalAlbums = 0
        var totalSongs = 0

        while (true) {
            val albums = navidromeClient.getAlbumList(
                type = "alphabeticalByName",
                size = ALBUM_PAGE_SIZE,
                offset = offset
            )
            if (albums.isEmpty()) break

            totalAlbums += albums.size
            Log.d(TAG, "Fetched ${albums.size} albums (offset=$offset, total=$totalAlbums)")

            for (album in albums) {
                try {
                    val (albumDetail, songs) = navidromeClient.getAlbum(album.id)
                    val cachedSongs = songs.map { song ->
                        song.toCachedSong(songDao.getById(song.id))
                    }
                    if (cachedSongs.isNotEmpty()) {
                        songDao.insertSongs(cachedSongs)
                        totalSongs += cachedSongs.size
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch album ${album.name}: ${e.message}")
                }
            }

            if (albums.size < ALBUM_PAGE_SIZE) break
            offset += ALBUM_PAGE_SIZE
        }

        Log.i(TAG, "Library sync: $totalAlbums albums, $totalSongs songs")
    }

    /**
     * Sync playlists — metadata + song membership via junction table.
     */
    private suspend fun syncPlaylists() {
        val remotePlaylists = navidromeClient.getPlaylists()
        if (remotePlaylists.isEmpty()) {
            Log.i(TAG, "No playlists found")
            return
        }

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

        for (playlist in remotePlaylists) {
            try {
                val (_, songs) = navidromeClient.getPlaylist(playlist.id)

                // Clear and rebuild junction table for this playlist
                songDao.clearPlaylistSongs(playlist.id)
                val junctions = songs.mapIndexed { index, song ->
                    PlaylistSong(
                        playlistId = playlist.id,
                        songId = song.id,
                        trackOrder = index
                    )
                }
                songDao.insertPlaylistSongs(junctions)

                Log.d(TAG, "Playlist '${playlist.name}': ${songs.size} songs")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync playlist ${playlist.name}: ${e.message}")
            }
        }

        Log.i(TAG, "Synced ${remotePlaylists.size} playlists")
    }

    /**
     * Pull starred (favorited) song ids and flag them in the index. Also inserts
     * any starred song that isn't already present in the library index.
     */
    private suspend fun syncStarred() {
        val starred = try {
            navidromeClient.getStarredSongs()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch starred songs: ${e.message}")
            return
        }

        songDao.clearStarred()
        if (starred.isEmpty()) {
            Log.i(TAG, "No starred songs")
            return
        }

        val missing = starred.filter { songDao.getById(it.id) == null }
        if (missing.isNotEmpty()) {
            songDao.insertSongs(missing.map { it.toCachedSong(null).copy(isStarred = true) })
        }

        starred.map { it.id }.chunked(500).forEach { ids -> songDao.markStarred(ids) }
        Log.i(TAG, "Starred: ${starred.size} songs")
    }

    /**
     * Deletes cached audio that is no longer wanted (not in a selected playlist and
     * not starred when favorites caching is on). Index rows are kept — only the
     * local files are removed, so the song stays available for streaming.
     */
    private suspend fun pruneUnwantedFiles(syncIds: Set<String>, syncFavorites: Boolean) {
        if (syncIds.isEmpty() && !syncFavorites) {
            Log.d(TAG, "No cache sources selected — keeping existing audio")
            return
        }

        val keep = mutableSetOf<String>()
        if (syncIds.isNotEmpty()) keep += songDao.getSongIdsForPlaylists(syncIds.toList())
        if (syncFavorites) keep += songDao.getStarredSongs().map { it.id }

        var removed = 0
        for (song in songDao.getAllCachedWithPath()) {
            if (song.id in keep) continue
            song.filePath?.let { File(it).delete() }
            song.coverPath?.let { File(it).delete() }
            songDao.clearPaths(song.id)
            removed++
        }
        if (removed > 0) Log.i(TAG, "Pruned $removed song(s) no longer selected for caching")
    }

    private suspend fun downloadUncachedSongs(syncIds: Set<String>, syncFavorites: Boolean) {
        val uncached = mutableListOf<CachedSong>()
        if (syncIds.isNotEmpty()) {
            uncached += songDao.getUncachedSongsForPlaylists(syncIds.toList(), DOWNLOAD_BATCH_SIZE)
        }
        if (syncFavorites && uncached.size < DOWNLOAD_BATCH_SIZE) {
            uncached += songDao.getUncachedStarred(DOWNLOAD_BATCH_SIZE - uncached.size)
        }

        val batch = uncached.distinctBy { it.id }
        if (batch.isEmpty()) {
            Log.d(TAG, "All selected songs cached")
            return
        }

        Log.i(TAG, "Downloading ${batch.size} songs")
        for (song in batch) {
            cacheManager.downloadSong(song.id)
            if (song.coverArt != null && song.coverPath == null) {
                cacheManager.downloadCover(song.id, song.coverArt)
            }
        }
    }

    private fun Song.toCachedSong(existing: CachedSong?): CachedSong = CachedSong(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        artistId = artistId,
        coverArt = coverArt,
        duration = duration,
        track = track,
        year = year,
        genre = genre,
        filePath = existing?.filePath,
        coverPath = existing?.coverPath,
        fileSize = existing?.fileSize ?: 0,
        playCount = existing?.playCount ?: 0,
        lastPlayed = existing?.lastPlayed,
        isStarred = existing?.isStarred ?: false
    )
}
