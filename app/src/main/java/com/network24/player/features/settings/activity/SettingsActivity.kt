package com.network24.player.features.settings.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.network24.player.BuildConfig
import com.network24.player.R
import com.network24.player.core.base.BaseActivity
import com.network24.player.core.cache.memory.MemoryCache
import com.network24.player.core.preferences.PreferenceManager
import com.network24.player.features.login.activity.LoginActivity

class SettingsActivity : BaseActivity() {

    private lateinit var prefs: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager(this)

        setContentView(R.layout.activity_settings)

        findViewById<android.view.View>(R.id.settingsBack).setOnClickListener {
            finish()
        }

        bindAccount()
        bindActions()

        findViewById<android.widget.TextView>(R.id.appVersion).text =
            "Network24  •  Version ${BuildConfig.VERSION_NAME}"
    }

    private fun bindAccount() {
        val username = prefs.getUsername().ifBlank { "Network24 Account" }
        findViewById<android.widget.TextView>(R.id.accountName).text = username

        val expiry = prefs.getExpiry()
        val expiryText = if (expiry > 0L) {
            java.text.SimpleDateFormat(
                "dd MMM yyyy",
                java.util.Locale.getDefault()
            ).format(java.util.Date(expiry * 1000L))
        } else {
            "Not available"
        }

        val status = prefs.getStatus().ifBlank { "Unknown" }
        val connections =
            "${prefs.getActiveConnections()} / ${prefs.getMaxConnections()}"

        findViewById<android.widget.TextView>(R.id.accountDetails).text =
            "Status: $status\nExpiry: $expiryText\nConnections: $connections"
    }

    private fun bindActions() {
        findViewById<android.view.View>(R.id.clearMemory).setOnClickListener {
            MemoryCache.clearAll()
            Toast.makeText(
                this,
                "Temporary memory cleared",
                Toast.LENGTH_SHORT
            ).show()
        }

        findViewById<android.view.View>(R.id.forceRefresh).setOnClickListener {
            MemoryCache.clearAll()
            prefs.setLastSyncTime(0L)
            Toast.makeText(
                this,
                "Cache cleared. Fresh data will load on the next refresh.",
                Toast.LENGTH_SHORT
            ).show()
        }

        findViewById<android.view.View>(R.id.logout).setOnClickListener {
            prefs.clear()
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }
    }
}
