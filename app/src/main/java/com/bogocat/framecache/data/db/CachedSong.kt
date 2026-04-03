package com.bogocat.framecache.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_songs")
data class CachedSong(
    @PrimaryKey val id: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumId: String = "",
    val coverArt: String? = null,
    val duration: Int = 0,
    val track: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val playlistId: String? = null,
    val filePath: String? = null,
    val coverPath: String? = null,
    val cachedAt: Long = System.currentTimeMillis(),
    val playCount: Int = 0,
    val lastPlayed: Long? = null,
    val fileSize: Long = 0
)
