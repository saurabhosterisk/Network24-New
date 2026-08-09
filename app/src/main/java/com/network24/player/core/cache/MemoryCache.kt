package com.network24.player.core.cache

import java.util.concurrent.ConcurrentHashMap

object MemoryCache {

    // Changed to ConcurrentHashMap to prevent crashes if multiple coroutines read/write at the same time
    private val cache = ConcurrentHashMap<String, Any>()

    // Data class to wrap objects with an expiration time
    data class CacheEntryWithTtl(
        val data: Any,
        val expirationTimeMs: Long
    )

    // ------------------------------------------------
    // EXISTING GENERIC METHODS (Kept intact)
    // ------------------------------------------------
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        return cache[key] as? T
    }

    fun put(key: String, value: Any) {
        cache[key] = value
    }

    fun remove(key: String) {
        cache.remove(key)
    }

    fun clear() {
        cache.clear()
    }

    fun contains(key: String): Boolean {
        return cache.containsKey(key)
    }

    // ------------------------------------------------
    // NEW TTL METHODS (For Phase 5: EPG Now/Next)
    // ------------------------------------------------

    /**
     * Store data with a Time-To-Live.
     * @param ttlMs Time to live in milliseconds (default 1 minute)
     */
    fun putWithTtl(key: String, value: Any, ttlMs: Long = 60_000L) {
        val expiration = System.currentTimeMillis() + ttlMs
        val entry = CacheEntryWithTtl(value, expiration)
        cache[key] = entry
    }

    /**
     * Retrieve data. Returns null if it doesn't exist OR if it has expired.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getWithTtl(key: String): T? {
        val entry = cache[key] as? CacheEntryWithTtl ?: return null

        // If the current time is past expiration, remove it and return null
        if (System.currentTimeMillis() > entry.expirationTimeMs) {
            cache.remove(key)
            return null
        }

        return entry.data as? T
    }
}
