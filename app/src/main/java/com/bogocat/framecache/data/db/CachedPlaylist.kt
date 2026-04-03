package com.bogocat.framecache.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_playlists")
data class CachedPlaylist(
    @PrimaryKey val id: String,
    val name: String = "",
    val songCount: Int = 0,
    val duration: Int = 0,
    val coverArt: String? = null,
    val cachedAt: Long = System.currentTimeMillis()
)
