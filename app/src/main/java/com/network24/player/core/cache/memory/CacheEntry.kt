package com.network24.player.core.cache.memory

internal data class CacheEntry<T>(
    val value: T,
    val savedAtMs: Long,
    val ttlMs: Long
) {
    fun isExpired(nowMs: Long): Boolean = (ttlMs > 0) && (nowMs - savedAtMs > ttlMs)
}
