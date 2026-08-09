package com.network24.player.features.dashboard.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.internal.NavigationMenuView
import com.network24.player.R
import com.network24.player.core.base.BaseActivity
import com.network24.player.core.preferences.PreferenceManager
import com.network24.player.databinding.ActivityDashboardBinding
import com.network24.player.features.chat.activity.ChatHubActivity
import com.network24.player.features.live.activity.FavoriteChannelsActivity
import com.network24.player.features.live.activity.LiveCategoryActivity
import com.network24.player.features.live.repository.LiveRepository
import com.network24.player.features.live.repository.SyncCallback
import com.network24.player.features.login.activity.LoginActivity
import com.network24.player.features.settings.activity.SettingsActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class DashboardActivity : BaseActivity() {

    private companion object {
        private const val REQ_POST_NOTIFICATIONS = 9001
        private const val PAYMENT_URL = "https://osterisktechnology.com/makepayment.html"
    }

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var prefs: PreferenceManager
    private lateinit var repository: LiveRepository

    private val handler = Handler(Looper.getMainLooper())
    private var isInitialSyncRunning = false

    private val clockRunnable = object : Runnable {
        override fun run() {
            val now = Date()
            binding.txtClock.text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
            binding.txtDate.text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(now)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        registerDrawerBackHandler(binding.drawerLayout)

        askNotificationPermissionIfNeeded()

        prefs = PreferenceManager(this)
        repository = LiveRepository(this)

        if (!hasCredentials()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
            return
        }

        loadDashboard()
        binding.cardLiveTv.post { binding.cardLiveTv.requestFocus() }
        setupDrawerAndMenu()
        setClickListeners()
        handler.post(clockRunnable)
        syncInitialData(forceRefresh = false)
    }

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_POST_NOTIFICATIONS
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun hasCredentials(): Boolean {
        return prefs.getServer().isNotBlank() &&
                prefs.getUsername().isNotBlank() &&
                prefs.getPassword().isNotBlank()
    }

    private fun loadDashboard() {
        binding.txtUserName.text = prefs.getUsername()
        binding.txtStatus.text = prefs.getStatus()
        binding.txtPlan.text = if (prefs.isTrial()) "Trial" else "Premium"
        binding.txtConnections.text = "${prefs.getActiveConnections()} / ${prefs.getMaxConnections()}"

        val expiry = prefs.getExpiry()
        if (expiry > 0) {
            val expiryDate = Date(expiry * 1000)
            binding.txtExpiry.text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(expiryDate)

            val remainingDays = TimeUnit.MILLISECONDS.toDays(expiryDate.time - System.currentTimeMillis())
            binding.txtRemaining.text = if (remainingDays > 0) "$remainingDays Days" else "Expired"
            binding.btnRenew.visibility = if (remainingDays <= 15) View.VISIBLE else View.GONE
        } else {
            binding.txtExpiry.text = "--"
            binding.txtRemaining.text = "--"
            binding.btnRenew.visibility = View.GONE
        }
    }

    private fun setupDrawerAndMenu() {
        binding.btnMore.setOnClickListener { openRightDrawer(binding.drawerLayout) }

        setupOptionalRightDrawerMenu(
            drawerLayout = binding.drawerLayout,
            navView = binding.rightNav
        ) { itemId ->
            when (itemId) {
                R.id.action_home -> {
                    closeRightDrawer(binding.drawerLayout)
                    true
                }
                R.id.action_refresh_all -> {
                    syncInitialData(forceRefresh = true)
                    true
                }
                R.id.action_refresh_guide -> {
                    refreshTvGuide()
                    true
                }
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.action_logout -> {
                    prefs.clear()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finishAffinity()
                    true
                }
                else -> false
            }
        }

        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                if (drawerView.id == binding.rightNav.id) {
                    binding.rightNav.post {
                        val menuView = binding.rightNav.getChildAt(0) as? NavigationMenuView
                        if (menuView != null) {
                            for (i in 0 until menuView.childCount) {
                                val child = menuView.getChildAt(i)
                                if (child.isFocusable) {
                                    child.requestFocus()
                                    break
                                }
                            }
                        }
                    }
                }
            }
        })
    }

    private fun setClickListeners() {
        binding.cardLiveTv.setOnClickListener {
            startActivity(Intent(this, LiveCategoryActivity::class.java))
        }

        binding.cardFavorites.setOnClickListener {
            startActivity(Intent(this, FavoriteChannelsActivity::class.java))
        }

        binding.cardNotification.setOnClickListener {
            Toast.makeText(this, "Notifications", Toast.LENGTH_SHORT).show()
        }

        val supportContent = binding.cardSupport.getChildAt(0) as? LinearLayout
        (supportContent?.getChildAt(0) as? ImageView)?.setImageResource(R.drawable.ic_live_chat)
        (supportContent?.getChildAt(1) as? TextView)?.text = "Live Chat"

        binding.cardSupport.setOnClickListener {
            startActivity(Intent(this, ChatHubActivity::class.java))
        }

        binding.cardSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnRenew.setOnClickListener {
            showRenewPaymentQr()
        }
    }

    private fun showRenewPaymentQr() {
        val qrSize = 720
        val matrix: BitMatrix = MultiFormatWriter().encode(
            PAYMENT_URL,
            BarcodeFormat.QR_CODE,
            qrSize,
            qrSize
        )

        val pixels = IntArray(qrSize * qrSize)
        for (y in 0 until qrSize) {
            val offset = y * qrSize
            for (x in 0 until qrSize) {
                pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }

        val qrBitmap = Bitmap.createBitmap(
            pixels,
            0,
            qrSize,
            qrSize,
            qrSize,
            Bitmap.Config.ARGB_8888
        )

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 8, 28, 12)
        }

        val instruction = TextView(this).apply {
            text = "Renew your subscription in just a few steps"
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(Color.rgb(30, 30, 30))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 4, 0, 10)
        }

        val steps = TextView(this).apply {
            text = "1. Open your phone's camera.\n2. Point the camera at the QR code below.\n3. Tap the link that appears on your phone.\n4. Follow the instructions on the payment page to renew your subscription."
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setLineSpacing(2f, 1.05f)
            setPadding(8, 0, 8, 10)
        }

        val imageView = ImageView(this).apply {
            setImageBitmap(qrBitmap)
            adjustViewBounds = true
            setPadding(8, 8, 8, 12)
            contentDescription = "QR code to open the subscription payment page"
        }

        val scanHint = TextView(this).apply {
            text = "📱 Scan this code with another phone to open the payment page."
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(Color.rgb(55, 55, 55))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(8, 2, 8, 8)
        }

        container.addView(instruction)
        container.addView(steps)
        container.addView(imageView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        container.addView(scanHint)

        AlertDialog.Builder(this)
            .setView(container)
            .setNegativeButton("Close", null)
            .setPositiveButton("Open Payment Page") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PAYMENT_URL)))
            }
            .show()
    }

    private fun syncInitialData(forceRefresh: Boolean = false) {
        if (!hasCredentials()) return
        if (isInitialSyncRunning) return

        val lastSyncTime = prefs.getLastSyncTime()
        val currentTime = System.currentTimeMillis()
        val twentyFourHoursInMillis = 24L * 60L * 60L * 1000L
        val isFirstSync = lastSyncTime <= 0L

        if (!forceRefresh && !isFirstSync && (currentTime - lastSyncTime < twentyFourHoursInMillis)) {
            return
        }

        isInitialSyncRunning = true

        runCallbackSyncWithLoader(
            loadingMessage = "Refreshing categories & channels…",
            successMessage = "Channels Updated Successfully!"
        ) { ok, fail ->
            repository.syncAllData(
                server = prefs.getServer(),
                username = prefs.getUsername(),
                password = prefs.getPassword(),
                callback = object : SyncCallback {
                    override fun onSuccess() {
                        isInitialSyncRunning = false
                        prefs.setLastSyncTime(System.currentTimeMillis())
                        ok()
                    }

                    override fun onError(message: String) {
                        isInitialSyncRunning = false
                        fail("Failed to update: $message")
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(clockRunnable)
    }
}
