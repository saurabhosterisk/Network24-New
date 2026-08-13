package com.network24.player.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import com.network24.player.core.database.entity.ChannelEntity
import com.network24.player.core.database.entity.MasterChannelSearchResult
import androidx.sqlite.db.SupportSQLiteQuery

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE categoryId = :categoryId ORDER BY name ASC")
    suspend fun getByCategory(categoryId: String): List<ChannelEntity>

    @Query("SELECT * FROM channels ORDER BY name ASC")
    suspend fun getAll(): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE streamId IN (:streamIds)")
    suspend fun getByStreamIds(streamIds: List<Int>): List<ChannelEntity>

    /**
     * Executes the ranked global channel search built by [LiveRepository].
     * The query joins categories and reads favorite state without loading the
     * complete channel catalogue into memory.
     */
    @RawQuery(observedEntities = [ChannelEntity::class])
    suspend fun searchAllLiveChannels(query: SupportSQLiteQuery): List<MasterChannelSearchResult>

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ChannelEntity>)

    @Query("DELETE FROM channels")
    suspend fun clearAll()

    @Query("DELETE FROM channels WHERE categoryId = :categoryId")
    suspend fun clearByCategory(categoryId: String)
}
