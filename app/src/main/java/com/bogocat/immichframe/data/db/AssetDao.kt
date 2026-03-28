package com.bogocat.immichframe.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AssetDao {

    @Query("SELECT * FROM cached_assets WHERE filePath IS NOT NULL ORDER BY displayCount ASC, RANDOM() LIMIT 1")
    suspend fun getNextAsset(): CachedAsset?

    @Query("UPDATE cached_assets SET displayCount = displayCount + 1, lastDisplayed = :now WHERE id = :id")
    suspend fun markDisplayed(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM cached_assets WHERE filePath IS NOT NULL")
    suspend fun getCachedCount(): Int

    @Query("SELECT COUNT(*) FROM cached_assets")
    suspend fun getTotalCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(assets: List<CachedAsset>)

    @Query("DELETE FROM cached_assets WHERE id NOT IN (:keepIds)")
    suspend fun pruneRemoved(keepIds: List<String>)

    @Query("SELECT * FROM cached_assets WHERE filePath IS NULL ORDER BY RANDOM() LIMIT :limit")
    suspend fun getUncachedAssets(limit: Int): List<CachedAsset>

    @Query("UPDATE cached_assets SET filePath = :path WHERE id = :id")
    suspend fun updateFilePath(id: String, path: String)

    @Query("UPDATE cached_assets SET displayCount = 0")
    suspend fun resetDisplayCounts()

    @Query("SELECT * FROM cached_assets WHERE filePath IS NOT NULL ORDER BY lastDisplayed ASC LIMIT :count")
    suspend fun getOldestDisplayed(count: Int): List<CachedAsset>

    @Query("DELETE FROM cached_assets WHERE id = :id")
    suspend fun delete(id: String)
}
