package com.network24.player.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.network24.player.core.database.entity.EpgEntity

@Dao
interface EpgDao {
    // -------------------------
    // OLD (kept as-is)
    // -------------------------
    @Query("SELECT * FROM epg WHERE streamId = :streamId ORDER BY startTimestamp ASC")
    suspend fun getByStream(streamId: Int): List<EpgEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<EpgEntity>)

    @Query("DELETE FROM epg WHERE streamId = :streamId")
    suspend fun deleteForStream(streamId: Int)

    @Transaction
    suspend fun replaceForStream(streamId: Int, items: List<EpgEntity>) {
        deleteForStream(streamId)
        insertAll(items)
    }

    // -------------------------
    // NEW (for full EPG via epgChannelId)
    // Works even if streamId is unknown, as long as epgChannelId matches.
    // -------------------------
    @Query("""
        SELECT * FROM epg
        WHERE epgChannelId = :epgChannelId
          AND startTimestamp IS NOT NULL
        ORDER BY startTimestamp ASC
    """)
    suspend fun getByEpgChannelId(epgChannelId: String): List<EpgEntity>

    @Query("""
        SELECT * FROM epg
        WHERE epgChannelId = :epgChannelId
          AND startTimestamp IS NOT NULL
          AND stopTimestamp IS NOT NULL
          AND startTimestamp <= :nowTs
          AND stopTimestamp > :nowTs
        ORDER BY startTimestamp DESC
        LIMIT 1
    """)
    suspend fun getNowByEpgChannelId(epgChannelId: String, nowTs: Long): EpgEntity?

    @Query("""
        SELECT * FROM epg
        WHERE epgChannelId = :epgChannelId
          AND startTimestamp IS NOT NULL
          AND startTimestamp > :nowTs
        ORDER BY startTimestamp ASC
        LIMIT 1
    """)
    suspend fun getNextByEpgChannelId(epgChannelId: String, nowTs: Long): EpgEntity?

    // REMOVED the extra @Insert annotation and fixed table name to "epg"
    @Query("DELETE FROM epg")
    suspend fun deleteAll()

    // Transaction ensures the DB isn't left empty if the insert fails midway
    @Transaction
    suspend fun replaceAllEpgs(epgs: List<EpgEntity>) {
        deleteAll()
        insertAll(epgs) // Reuses the insertAll method from the OLD section
    }
}
