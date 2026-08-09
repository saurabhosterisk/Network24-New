package com.network24.player.core.cache.memory

import java.util.concurrent.ConcurrentHashMap

object MemoryCache {

    private val map = ConcurrentHashMap<String, CacheEntry<Any>>()

    fun clearAll() {
        map.clear()
    }

    fun invalidate(key: String) {
        map.remove(key)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        val entry = map[key] ?: return null
        val now = System.currentTimeMillis()
        return if (entry.isExpired(now)) {
            map.remove(key)
            null
        } else {
            entry.value as T
        }
    }

    fun <T : Any> put(key: String, value: T, ttlMs: Long) {
        map[key] = CacheEntry(value = value, savedAtMs = System.currentTimeMillis(), ttlMs = ttlMs)
    }
}
