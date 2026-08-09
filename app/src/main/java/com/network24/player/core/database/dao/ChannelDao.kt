package com.network24.player.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.network24.player.core.database.entity.ChannelEntity

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE categoryId = :categoryId ORDER BY name ASC")
    suspend fun getByCategory(categoryId: String): List<ChannelEntity>

    @Query("SELECT * FROM channels ORDER BY name ASC")
    suspend fun getAll(): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ChannelEntity>)

    @Query("DELETE FROM channels")
    suspend fun clearAll()

    @Query("DELETE FROM channels WHERE categoryId = :categoryId")
    suspend fun clearByCategory(categoryId: String)
}
