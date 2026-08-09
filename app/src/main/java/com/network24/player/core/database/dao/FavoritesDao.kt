package com.network24.player.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.network24.player.core.database.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {

    // List (one-time)
    @Query("SELECT * FROM favorites ORDER BY createdAtMs DESC")
    suspend fun getAll(): List<FavoriteEntity>

    @Query("SELECT * FROM favorites WHERE itemType = :type ORDER BY createdAtMs DESC")
    suspend fun getByType(type: String): List<FavoriteEntity>

    // Observe (auto UI refresh)
    @Query("SELECT * FROM favorites ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE itemType = :type ORDER BY createdAtMs DESC")
    fun observeByType(type: String): Flow<List<FavoriteEntity>>

    // Heart icon state
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE itemType = :type AND itemId = :itemId)")
    fun isFavoriteFlow(type: String, itemId: String): Flow<Boolean>

    // Insert/Update
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FavoriteEntity)

    // Delete
    @Query("DELETE FROM favorites WHERE key = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM favorites WHERE itemType = :type AND itemId = :itemId")
    suspend fun deleteByItem(type: String, itemId: String)

    @Query("DELETE FROM favorites")
    suspend fun clearAll()
}
