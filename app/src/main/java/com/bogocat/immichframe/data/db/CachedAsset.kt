package com.bogocat.immichframe.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_assets")
data class CachedAsset(
    @PrimaryKey val id: String,
    val albumId: String,
    val thumbhash: String? = null,
    val dateTaken: Long? = null,
    val location: String? = null,
    val description: String? = null,
    val cameraModel: String? = null,
    val peopleName: String? = null,
    val cachedAt: Long = System.currentTimeMillis(),
    val displayCount: Int = 0,
    val lastDisplayed: Long? = null,
    val filePath: String? = null,
    val width: Int? = null,
    val height: Int? = null
)
