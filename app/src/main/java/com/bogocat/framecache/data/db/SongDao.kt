package com.bogocat.framecache.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface SongDao {

    // -- Songs --

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<CachedSong>)

    @Query("SELECT * FROM cached_songs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CachedSong?

    @Query("SELECT * FROM cached_songs ORDER BY artist ASC, album ASC, track ASC, title ASC")
    suspend fun getAllSongs(): List<CachedSong>

    @Query("SELECT * FROM cached_songs WHERE filePath IS NOT NULL AND filePath != '' ORDER BY artist ASC, album ASC, track ASC, title ASC")
    suspend fun getAllCachedSongs(): List<CachedSong>

    @Query("SELECT * FROM cached_songs WHERE filePath IS NOT NULL AND filePath != '' ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomCached(limit: Int): List<CachedSong>

    @Query("SELECT * FROM cached_songs WHERE filePath IS NOT NULL AND filePath != '' ORDER BY playCount ASC, RANDOM() LIMIT :limit")
    suspend fun getShuffleQueue(limit: Int = 50): List<CachedSong>

    @Query("SELECT * FROM cached_songs WHERE (filePath IS NULL OR filePath = '') LIMIT :limit")
    suspend fun getUncachedSongs(limit: Int): List<CachedSong>

    @Query("""
        SELECT s.* FROM cached_songs s
        INNER JOIN playlist_songs ps ON s.id = ps.songId
        WHERE ps.playlistId IN (:playlistIds) AND (s.filePath IS NULL OR s.filePath = '')
        LIMIT :limit
    """)
    suspend fun getUncachedSongsForPlaylists(playlistIds: List<String>, limit: Int): List<CachedSong>

    @Query("""
        SELECT DISTINCT s.id FROM cached_songs s
        INNER JOIN playlist_songs ps ON s.id = ps.songId
        WHERE ps.playlistId IN (:playlistIds)
    """)
    suspend fun getSongIdsForPlaylists(playlistIds: List<String>): List<String>

    // -- Starred / favorites --

    @Query("UPDATE cached_songs SET isStarred = 0")
    suspend fun clearStarred()

    @Query("UPDATE cached_songs SET isStarred = 1 WHERE id IN (:ids)")
    suspend fun markStarred(ids: List<String>)

    @Query("SELECT COUNT(*) FROM cached_songs WHERE isStarred = 1")
    suspend fun getStarredCount(): Int

    @Query("SELECT * FROM cached_songs WHERE isStarred = 1 ORDER BY artist ASC, album ASC, track ASC, title ASC")
    suspend fun getStarredSongs(): List<CachedSong>

    @Query("SELECT * FROM cached_songs WHERE isStarred = 1 AND (filePath IS NULL OR filePath = '') LIMIT :limit")
    suspend fun getUncachedStarred(limit: Int): List<CachedSong>

    // -- Local cache pins (songs the user asked to keep offline) --

    @Query("UPDATE cached_songs SET pinned = :pinned WHERE id IN (:ids)")
    suspend fun setPinnedBatch(ids: List<String>, pinned: Boolean)

    @Query("UPDATE cached_songs SET pinned = :pinned WHERE albumId = :albumId")
    suspend fun setPinnedForAlbum(albumId: String, pinned: Boolean)

    @Query("UPDATE cached_songs SET pinned = :pinned WHERE artist = :artist")
    suspend fun setPinnedForArtist(artist: String, pinned: Boolean)

    @Query("SELECT * FROM cached_songs WHERE pinned = 1 AND (filePath IS NULL OR filePath = '') LIMIT :limit")
    suspend fun getUncachedPinned(limit: Int): List<CachedSong>

    @Query("SELECT id FROM cached_songs WHERE pinned = 1")
    suspend fun getPinnedIds(): List<String>

    @Query("""
        SELECT albumId, album, artist, MAX(coverArt) AS coverArt, MAX(year) AS year,
               COUNT(*) AS songCount,
               SUM(CASE WHEN pinned = 1 THEN 1 ELSE 0 END) AS pinnedCount,
               SUM(CASE WHEN filePath IS NOT NULL AND filePath != '' THEN 1 ELSE 0 END) AS cachedCount
        FROM cached_songs WHERE artist = :artist
        GROUP BY albumId ORDER BY year ASC, album ASC
    """)
    suspend fun getAlbumsByArtist(artist: String): List<AlbumWithCounts>

    @Query("UPDATE cached_songs SET filePath = :path, fileSize = :size WHERE id = :id")
    suspend fun updateFilePath(id: String, path: String, size: Long)

    @Query("UPDATE cached_songs SET coverPath = :path WHERE id = :id")
    suspend fun updateCoverPath(id: String, path: String)

    // Drop the local files for a song without removing it from the index.
    @Query("UPDATE cached_songs SET filePath = '', coverPath = NULL, fileSize = 0 WHERE id = :id")
    suspend fun clearPaths(id: String)

    @Query("UPDATE cached_songs SET playCount = playCount + 1, lastPlayed = :now WHERE id = :id")
    suspend fun markPlayed(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM cached_songs WHERE filePath IS NOT NULL AND filePath != ''")
    suspend fun getCachedCount(): Int

    @Query("SELECT COUNT(*) FROM cached_songs")
    suspend fun getTotalCount(): Int

    @Query("SELECT COALESCE(SUM(fileSize), 0) FROM cached_songs WHERE filePath IS NOT NULL AND filePath != ''")
    suspend fun getCachedSizeBytes(): Long

    @Query("SELECT * FROM cached_songs WHERE filePath IS NOT NULL AND filePath != '' ORDER BY lastPlayed ASC LIMIT :count")
    suspend fun getOldestPlayed(count: Int): List<CachedSong>

    @Query("SELECT * FROM cached_songs WHERE filePath IS NOT NULL AND filePath != ''")
    suspend fun getAllCachedWithPath(): List<CachedSong>

    @Query("DELETE FROM cached_songs WHERE id = :id")
    suspend fun delete(id: String)

    // -- Artists --

    @Query("SELECT DISTINCT artist FROM cached_songs ORDER BY artist ASC")
    suspend fun getAllArtists(): List<String>

    @Query("SELECT DISTINCT artist FROM cached_songs WHERE filePath IS NOT NULL AND filePath != '' ORDER BY artist ASC")
    suspend fun getCachedArtists(): List<String>

    @Query("SELECT * FROM cached_songs WHERE artist = :artist ORDER BY album ASC, track ASC, title ASC")
    suspend fun getSongsByArtist(artist: String): List<CachedSong>

    // -- Albums --

    @Query("SELECT DISTINCT albumId, album, artist, coverArt, year FROM cached_songs ORDER BY artist ASC, album ASC")
    suspend fun getAllAlbums(): List<AlbumSummary>

    // Per-album cached song counts, used to skip re-fetching unchanged albums.
    @Query("SELECT albumId, COUNT(*) AS count FROM cached_songs WHERE albumId != '' GROUP BY albumId")
    suspend fun getSongCountsByAlbum(): List<AlbumSongCount>

    @Query("SELECT * FROM cached_songs WHERE albumId = :albumId ORDER BY track ASC, title ASC")
    suspend fun getSongsByAlbum(albumId: String): List<CachedSong>

    // -- Playlists --

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<CachedPlaylist>)

    @Query("SELECT * FROM cached_playlists ORDER BY name ASC")
    suspend fun getAllPlaylists(): List<CachedPlaylist>

    @Query("DELETE FROM cached_playlists WHERE id NOT IN (:keepIds)")
    suspend fun prunePlaylists(keepIds: List<String>)

    // -- Playlist Songs (junction) --

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongs(items: List<PlaylistSong>)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: String)

    @Query("""
        SELECT s.* FROM cached_songs s
        INNER JOIN playlist_songs ps ON s.id = ps.songId
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.trackOrder ASC
    """)
    suspend fun getSongsForPlaylist(playlistId: String): List<CachedSong>

    // -- Search --

    @Query("SELECT * FROM cached_songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%' ORDER BY artist ASC, album ASC, track ASC LIMIT 100")
    suspend fun search(query: String): List<CachedSong>

    // -- Sync metadata --

    @Query("SELECT MAX(cachedAt) FROM cached_songs")
    suspend fun getLastSongSyncTime(): Long?
}

data class AlbumSummary(
    val albumId: String,
    val album: String,
    val artist: String,
    val coverArt: String?,
    val year: Int?
)

data class AlbumSongCount(
    val albumId: String,
    val count: Int
)

data class AlbumWithCounts(
    val albumId: String,
    val album: String,
    val artist: String,
    val coverArt: String?,
    val year: Int?,
    val songCount: Int,
    val pinnedCount: Int,
    val cachedCount: Int
)
