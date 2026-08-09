package com.network24.player.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.network24.player.core.database.entity.DownloadEntity

@Dao
interface DownloadsDao {

    @Query("SELECT * FROM downloads ORDER BY updatedAtMs DESC")
    suspend fun getAll(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY updatedAtMs DESC")
    suspend fun getByStatus(status: String): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DownloadEntity)

    @Query("DELETE FROM downloads WHERE key = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM downloads")
    suspend fun clearAll()
}
