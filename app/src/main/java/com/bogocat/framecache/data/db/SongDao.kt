package com.bogocat.framecache.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SongDao {

    // -- Songs --

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<CachedSong>)

    @Query("SELECT * FROM cached_songs WHERE filePath IS NOT NULL ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomCached(limit: Int): List<CachedSong>

    @Query("SELECT * FROM cached_songs WHERE playlistId = :playlistId ORDER BY track ASC, title ASC")
    suspend fun getSongsForPlaylist(playlistId: String): List<CachedSong>

    @Query("SELECT * FROM cached_songs ORDER BY playCount ASC, RANDOM() LIMIT :limit")
    suspend fun getShuffleQueue(limit: Int = 50): List<CachedSong>

    @Query("SELECT * FROM cached_songs WHERE filePath IS NULL LIMIT :limit")
    suspend fun getUncachedSongs(limit: Int): List<CachedSong>

    @Query("SELECT * FROM cached_songs WHERE (filePath IS NULL OR filePath = '') AND playlistId IN (:playlistIds) LIMIT :limit")
    suspend fun getUncachedSongsForPlaylists(playlistIds: List<String>, limit: Int): List<CachedSong>

    @Query("UPDATE cached_songs SET filePath = :path, fileSize = :size WHERE id = :id")
    suspend fun updateFilePath(id: String, path: String, size: Long)

    @Query("UPDATE cached_songs SET coverPath = :path WHERE id = :id")
    suspend fun updateCoverPath(id: String, path: String)

    @Query("UPDATE cached_songs SET playCount = playCount + 1, lastPlayed = :now WHERE id = :id")
    suspend fun markPlayed(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM cached_songs WHERE filePath IS NOT NULL")
    suspend fun getCachedCount(): Int

    @Query("SELECT COUNT(*) FROM cached_songs")
    suspend fun getTotalCount(): Int

    @Query("SELECT COALESCE(SUM(fileSize), 0) FROM cached_songs WHERE filePath IS NOT NULL")
    suspend fun getCachedSizeBytes(): Long

    @Query("SELECT * FROM cached_songs WHERE filePath IS NOT NULL ORDER BY lastPlayed ASC LIMIT :count")
    suspend fun getOldestPlayed(count: Int): List<CachedSong>

    @Query("DELETE FROM cached_songs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM cached_songs WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: String)

    @Query("SELECT * FROM cached_songs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CachedSong?

    @Query("SELECT DISTINCT artist FROM cached_songs ORDER BY artist ASC")
    suspend fun getAllArtists(): List<String>

    @Query("SELECT * FROM cached_songs WHERE artist = :artist ORDER BY album ASC, track ASC, title ASC")
    suspend fun getSongsByArtist(artist: String): List<CachedSong>

    // -- Playlists --

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<CachedPlaylist>)

    @Query("SELECT * FROM cached_playlists ORDER BY name ASC")
    suspend fun getAllPlaylists(): List<CachedPlaylist>

    @Query("DELETE FROM cached_playlists WHERE id NOT IN (:keepIds)")
    suspend fun prunePlaylists(keepIds: List<String>)

    @Query("DELETE FROM cached_playlists")
    suspend fun deleteAllPlaylists()
}
