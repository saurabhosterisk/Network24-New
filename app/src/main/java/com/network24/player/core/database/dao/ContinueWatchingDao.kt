package com.network24.player.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.network24.player.core.database.entity.ContinueWatchingEntity

@Dao
interface ContinueWatchingDao {

    @Query("SELECT * FROM continue_watching ORDER BY updatedAtMs DESC")
    suspend fun getAll(): List<ContinueWatchingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ContinueWatchingEntity)

    @Query("DELETE FROM continue_watching WHERE key = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM continue_watching")
    suspend fun clearAll()
}
