package com.network24.player.core.preferences

import android.content.Context
import android.content.SharedPreferences
import com.network24.player.common.models.LoginCredentials

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("network24", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SERVER = "server"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_REMEMBER = "remember"

        private const val KEY_STATUS = "status"
        private const val KEY_EXPIRY = "expiry"
        private const val KEY_ACTIVE_CONNECTIONS = "active_connections"
        private const val KEY_MAX_CONNECTIONS = "max_connections"
        private const val KEY_IS_TRIAL = "is_trial"

        private const val KEY_LAST_SYNC_TIME = "last_sync_time"

        // Chat
        private const val KEY_CHAT_LAST_ROOM_ID = "chat_last_room_id"
    }

    // -------------------------
    // Login / Credentials
    // -------------------------

    fun saveLogin(
        server: String,
        username: String,
        password: String,
        remember: Boolean
    ) {
        prefs.edit()
            .putString(KEY_SERVER, server)
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .putBoolean(KEY_REMEMBER, remember)
            .apply()
    }

    fun getServer(): String = prefs.getString(KEY_SERVER, "") ?: ""
    fun getUsername(): String = prefs.getString(KEY_USERNAME, "") ?: ""
    fun getPassword(): String = prefs.getString(KEY_PASSWORD, "") ?: ""
    fun isRememberMe(): Boolean = prefs.getBoolean(KEY_REMEMBER, false)

    /**
     * Always returns a LoginCredentials object (may contain empty strings).
     * Useful when you want a non-null object.
     */
    fun getCredentials(): LoginCredentials {
        return LoginCredentials(
            server = getServer(),
            username = getUsername(),
            password = getPassword()
        )
    }

    /**
     * Returns null when credentials are missing.
     * This matches SyncManager usage.
     */
    fun getLoginCredentials(): LoginCredentials? {
        val server = getServer().trim()
        val username = getUsername().trim()
        val password = getPassword()

        if (server.isBlank() || username.isBlank() || password.isBlank()) return null

        return LoginCredentials(
            server = server,
            username = username,
            password = password
        )
    }

    // -------------------------
    // User Info
    // -------------------------

    fun saveUserInfo(
        username: String,
        status: String,
        expiry: Long,
        activeConnections: Int,
        maxConnections: Int,
        isTrial: Boolean
    ) {
        // username param currently unused (keeping it for API parity/future)
        prefs.edit()
            .putString(KEY_STATUS, status)
            .putLong(KEY_EXPIRY, expiry)
            .putInt(KEY_ACTIVE_CONNECTIONS, activeConnections)
            .putInt(KEY_MAX_CONNECTIONS, maxConnections)
            .putBoolean(KEY_IS_TRIAL, isTrial)
            .apply()
    }

    fun getStatus(): String = prefs.getString(KEY_STATUS, "Unknown") ?: "Unknown"
    fun getExpiry(): Long = prefs.getLong(KEY_EXPIRY, 0L)
    fun getActiveConnections(): Int = prefs.getInt(KEY_ACTIVE_CONNECTIONS, 0)
    fun getMaxConnections(): Int = prefs.getInt(KEY_MAX_CONNECTIONS, 0)
    fun isTrial(): Boolean = prefs.getBoolean(KEY_IS_TRIAL, false)

    // -------------------------
    // Sync time
    // -------------------------

    fun setLastSyncTime(time: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIME, time).apply()
    }

    fun getLastSyncTime(): Long {
        return prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
    }

    // -------------------------
    // Chat preferences
    // -------------------------

    fun setLastChatRoomId(roomId: String) {
        prefs.edit().putString(KEY_CHAT_LAST_ROOM_ID, roomId).apply()
    }

    fun getLastChatRoomId(): String? {
        return prefs.getString(KEY_CHAT_LAST_ROOM_ID, null)
    }

    private fun chatLastSeenKey(roomId: String) = "chat_last_seen_$roomId"

    fun setChatLastSeen(roomId: String, tsMs: Long) {
        prefs.edit().putLong(chatLastSeenKey(roomId), tsMs).apply()
    }

    fun getChatLastSeen(roomId: String): Long {
        return prefs.getLong(chatLastSeenKey(roomId), 0L)
    }

    // -------------------------
    // Maintenance
    // -------------------------

    fun clear() {
        prefs.edit().clear().apply()
    }
}
