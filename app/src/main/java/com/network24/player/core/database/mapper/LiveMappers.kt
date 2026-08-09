package com.network24.player.core.database.mapper

import com.network24.player.core.database.entity.CategoryEntity
import com.network24.player.core.database.entity.CategoryType
import com.network24.player.core.database.entity.ChannelEntity
import com.network24.player.core.database.entity.EpgEntity
import com.network24.player.features.live.models.EPGListing
import com.network24.player.features.live.models.LiveCategory
import com.network24.player.features.live.models.LiveChannel

// ----------------------------------------------------
// Feature Models -> Room Entities
// ----------------------------------------------------

fun LiveCategory.toCategoryEntity(position: Int): CategoryEntity =
    CategoryEntity(
        categoryId = category_id,
        position = position,
        name = category_name,
        parentId = parent_id,
        type = CategoryType.LIVE
    )

fun LiveChannel.toChannelEntity(): ChannelEntity =
    ChannelEntity(
        streamId = stream_id ?: 0,
        name = name,
        categoryId = category_id,
        icon = stream_icon,

        streamType = stream_type,
        epgChannelId = epg_channel_id,

        tvArchive = tv_archive,
        tvArchiveDuration = tv_archive_duration,
        directSource = direct_source,

        num = num,
        added = added,
        customSid = custom_sid
    )

fun EPGListing.toEpgEntity(streamId: Int): EpgEntity =
    EpgEntity(
        // If server doesn't provide a stable id, create one
        id = id ?: "${streamId}_${start_timestamp ?: 0L}",
        streamId = streamId,

        title = title,
        description = description,

        start = start,
        end = end,

        startTimestamp = start_timestamp,
        stopTimestamp = stop_timestamp
    )

// ----------------------------------------------------
// Room Entities -> Feature Models
// ----------------------------------------------------

fun CategoryEntity.toLiveCategory(): LiveCategory =
    LiveCategory(
        category_id = categoryId,
        category_name = name,
        parent_id = parentId
    )

fun ChannelEntity.toLiveChannel(): LiveChannel =
    LiveChannel(
        num = num,
        name = name,
        stream_type = streamType,
        stream_id = streamId,
        stream_icon = icon,
        epg_channel_id = epgChannelId,
        added = added,
        category_id = categoryId,
        custom_sid = customSid,
        tv_archive = tvArchive,
        tv_archive_duration = tvArchiveDuration,
        direct_source = directSource
    )

fun EpgEntity.toEpgListing(): EPGListing =
    EPGListing(
        id = id,
        title = title,
        description = description,
        start = start,
        end = end,
        start_timestamp = startTimestamp,
        stop_timestamp = stopTimestamp
    )
