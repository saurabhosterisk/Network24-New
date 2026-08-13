package com.network24.player.features.live.models

import com.network24.player.core.database.entity.EpgEntity

data class ProgramSearchResult(
    val channel: LiveChannel,
    val program: EpgEntity
)
