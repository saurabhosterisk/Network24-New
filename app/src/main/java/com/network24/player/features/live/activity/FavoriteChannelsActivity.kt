package com.network24.player.features.live.activity

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.internal.NavigationMenuView
import com.google.firebase.firestore.FirebaseFirestore
import com.network24.player.R
import com.network24.player.core.base.BaseActivity
import com.network24.player.core.database.DatabaseProvider
import com.network24.player.core.database.repository.FavoritesRepository
import com.network24.player.core.preferences.PreferenceManager
import com.network24.player.databinding.ActivityFavoriteChannelsBinding
import com.network24.player.features.dashboard.activity.DashboardActivity
import com.network24.player.features.live.adapter.ChannelAdapter
import com.network24.player.features.live.models.LiveChannel
import com.network24.player.features.live.repository.LiveRepository
import com.network24.player.features.live.repository.SyncCallback
import com.network24.player.features.login.activity.LoginActivity
import com.network24.player.features.player.activity.PlayerActivity
import com.network24.player.features.player.manager.PlayerManager
import com.network24.player.features.player.state.PlayerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class FavoriteChannelsActivity : BaseActivity() {

    private lateinit var binding: ActivityFavoriteChannelsBinding
    private lateinit var repository: LiveRepository
    private lateinit var prefs: PreferenceManager
    private lateinit var favRepo: FavoritesRepository
    private lateinit var adapter: ChannelAdapter
    private var isGoingToFullscreen = false
    private var loadingDialog: AlertDialog? = null
    private var retryCount = 0
    private val MAX_RETRIES = 3
    private var retryJob: Job? = null
    private val isTouchDevice by lazy {
        !packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
    private var previewPosition = -1
    private val allChannels = mutableListOf<LiveChannel>()
    private val channelList = mutableListOf<LiveChannel>()
    private var currentFavIds: Set<String> = emptySet()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            binding.progressLoading.visibility =
                if (playbackState == Player.STATE_BUFFERING) View.VISIBLE else View.GONE

            // ✅ Hide error and report button when player is ready
            if (playbackState == Player.STATE_READY) {
                retryCount = 0
                binding.txtPlayerError.visibility = View.GONE
                binding.btnReportChannel.visibility = View.GONE
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            if (retryCount < MAX_RETRIES) {
                retryCount++
                Toast.makeText(
                    this@FavoriteChannelsActivity,
                    "Playback Error. Trying to reconnect in 3 sec. ($retryCount)",
                    Toast.LENGTH_SHORT
                ).show()
                retryJob?.cancel()
                retryJob = lifecycleScope.launch {
                    delay(3000)
                    if (channelList.isNotEmpty() && previewPosition in channelList.indices) {
                        val currentChannel = channelList[previewPosition]
                        val streamUrl = buildStreamUrl(currentChannel)
                        binding.progressLoading.visibility = View.VISIBLE
                        PlayerManager.play(this@FavoriteChannelsActivity, binding.playerView, streamUrl)
                    }
                }
            } else {
                binding.progressLoading.visibility = View.GONE
                binding.txtPlayerError.visibility = View.VISIBLE
                val finalError = "Sorry, This video can not be played. Please try again or pick another video."
                binding.txtNowTitle.text = "Playback Failed"
                binding.txtOverlayProgram.text = finalError
                Toast.makeText(this@FavoriteChannelsActivity, finalError, Toast.LENGTH_LONG).show()

                // ✅ Show the report button when max retries fail
                binding.btnReportChannel.visibility = View.VISIBLE
                binding.btnReportChannel.post {
                    binding.btnReportChannel.requestFocus() // Request focus for Android TV
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoriteChannelsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        registerDrawerBackHandler(binding.drawerLayout)
        prefs = PreferenceManager(this)
        repository = LiveRepository(this)
        val db = DatabaseProvider.get(this)
        favRepo = FavoritesRepository(db.favoritesDao(), FirebaseFirestore.getInstance())
        binding.btnBack.setOnClickListener { finish() }
        binding.playerView.setShowSubtitleButton(false)
        binding.playerView.subtitleView?.visibility = View.GONE
        setupDrawerAndMenu()

        // ✅ Initialize Report Button Click Logic
        setupReportButton()

        binding.playerView.setOnClickListener {
            if (isTouchDevice && previewPosition != -1 && channelList.isNotEmpty()) {
                openFullscreen(channelList[previewPosition], previewPosition)
            }
        }
        setupRecycler()
        setupSearch()
        // Room Flow observation (Safe on Main Thread)
        lifecycleScope.launch {
            db.favoritesDao().observeByType("LIVE_CHANNEL").collect { favs ->
                val favIds = favs.map { it.itemId }.toSet()
                currentFavIds = favIds
                refreshFavoriteListFromDb(favIds)
            }
        }
        ensureInitialSyncThenLoadFavorites()
    }

    // ✅ NEW: Report Button Logic matching the working ChatHub format
    private fun setupReportButton() {
        binding.btnReportChannel.visibility = View.GONE
        binding.btnReportChannel.setOnClickListener {
            if (previewPosition == -1 || channelList.isEmpty()) return@setOnClickListener

            val currentChannel = channelList[previewPosition]
            val channelName = currentChannel.name ?: "Unknown Channel"
            val username = prefs.getUsername()

            val alertMessage = "🚨 System Alert: $username reported that the channel '$channelName' is currently down."

            val chatData = hashMapOf(
                "senderId" to "system_bot",
                "senderName" to "System",
                "text" to alertMessage,
                "ts" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            val firestore = FirebaseFirestore.getInstance()

            // Hide immediately to prevent spam clicks
            binding.btnReportChannel.visibility = View.GONE
            binding.txtPlayerError.text = "Sending report..."

            firestore.collection("rooms")
                .document("channel_down")
                .collection("messages")
                .add(chatData)
                .addOnSuccessListener {
                    binding.txtPlayerError.text = "Channel reported. Our team will look into it."
                }
                .addOnFailureListener { exception ->
                    binding.btnReportChannel.visibility = View.VISIBLE
                    binding.txtPlayerError.text = "Failed to send report."
                    Toast.makeText(this, "Error: ${exception.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun ensureInitialSyncThenLoadFavorites() {
        lifecycleScope.launch {
            try {
                val allDbChannels = repository.getChannels(
                    server = prefs.getServer(),
                    username = prefs.getUsername(),
                    password = prefs.getPassword(),
                    categoryId = "",
                    forceRefresh = false
                )
                if (allDbChannels.isNotEmpty()) {
                    allChannels.clear()
                    allChannels.addAll(allDbChannels)
                    refreshFavoriteListFromDb(currentFavIds)
                } else {
                    forceRefreshData(isInitialSync = true)
                }
            } catch (e: Exception) {
                Toast.makeText(this@FavoriteChannelsActivity, e.message ?: "Initial load failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadAllChannelsToMemory(forceRefresh: Boolean) {
        lifecycleScope.launch {
            try {
                val allChannelsFromRepo = repository.getChannels(
                    server = prefs.getServer(),
                    username = prefs.getUsername(),
                    password = prefs.getPassword(),
                    categoryId = "",
                    forceRefresh = forceRefresh
                )
                allChannels.clear()
                allChannels.addAll(allChannelsFromRepo)
                refreshFavoriteListFromDb(currentFavIds)
            } catch (e: Exception) {
                Toast.makeText(this@FavoriteChannelsActivity, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshFavoriteListFromDb(favIds: Set<String>) {
        if (allChannels.isEmpty()) {
            adapter.updateData(emptyList())
            adapter.updateFavorites(favIds)
            return
        }
        val favChannels = allChannels.filter { channel ->
            favIds.contains(channel.stream_id?.toString().orEmpty())
        }
        channelList.clear()
        channelList.addAll(favChannels)
        adapter.updateData(channelList)
        adapter.updateFavorites(favIds)
        if (channelList.isEmpty()) {
            previewPosition = -1
            binding.txtOverlayChannel.text = ""
            binding.txtOverlayProgram.text = ""
            binding.txtNowTitle.text = "No favorite channels"
            binding.txtNowTime.text = ""
            binding.txtNextTitle.text = ""
            binding.txtNextTime.text = ""
            binding.txtPlayerError.visibility = View.GONE
            binding.btnReportChannel.visibility = View.GONE // Ensure button hides when empty
            PlayerManager.pause()
            return
        }
        if (previewPosition !in channelList.indices) previewPosition = 0
        adapter.setPlaying(previewPosition)
        showPreview(channelList[previewPosition])
        loadProgramGuide(channelList[previewPosition])
    }

    private var isRefreshing = false

    private fun forceRefreshData(isInitialSync: Boolean = false) {
        if (isRefreshing) return
        isRefreshing = true
        val msg = if (isInitialSync) "Downloading Channels for the first time…" else "Refreshing channels & categories…"
        runCallbackSyncWithLoader(
            loadingMessage = msg,
            successMessage = "Channels Refreshed Successfully!"
        ) { onSuccess, onError ->
            repository.syncAllData(
                server = prefs.getServer(),
                username = prefs.getUsername(),
                password = prefs.getPassword(),
                callback = object : SyncCallback {
                    override fun onSuccess() {
                        isRefreshing = false
                        prefs.setLastSyncTime(System.currentTimeMillis())
                        onSuccess()
                        loadAllChannelsToMemory(forceRefresh = true)
                    }
                    override fun onError(message: String) {
                        isRefreshing = false
                        onError("Failed to refresh: $message")
                    }
                }
            )
        }
    }

    private fun setupDrawerAndMenu() {
        binding.btnMore.setOnClickListener { openRightDrawer(binding.drawerLayout) }
        setupOptionalRightDrawerMenu(binding.drawerLayout, binding.rightNav) { itemId ->
            when (itemId) {
                R.id.action_home -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                    true
                }
                R.id.action_refresh_all -> {
                    forceRefreshData()
                    true
                }
                R.id.action_refresh_guide -> {
                    refreshTvGuide()
                    true
                }
                R.id.action_logout -> {
                    lifecycleScope.launch {
                        try {
                            DatabaseProvider.get(this@FavoriteChannelsActivity).favoritesDao().clearAll()
                        } catch (_: Exception) {}
                        prefs.clear()
                        startActivity(Intent(this@FavoriteChannelsActivity, LoginActivity::class.java))
                        finishAffinity()
                    }
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

    private fun removeFromFavorites(channel: LiveChannel) {
        val streamId = channel.stream_id?.toString() ?: return
        val userId = prefs.getUsername()
        lifecycleScope.launch {
            favRepo.removeFavorite(userId, "LIVE_CHANNEL", streamId)
            Toast.makeText(this@FavoriteChannelsActivity, "${channel.name} removed from Favorites", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPreview(channel: LiveChannel) {
        retryJob?.cancel()
        retryCount = 0
        binding.txtPlayerError.visibility = View.GONE
        binding.btnReportChannel.visibility = View.GONE // ✅ Hide button when new channel loads

        val streamUrl = buildStreamUrl(channel)
        PlayerManager.play(this, binding.playerView, streamUrl)
        binding.txtOverlayChannel.text = channel.name ?: ""
        binding.txtOverlayProgram.text = "Loading TV Guide..."
        binding.txtNowTitle.text = "Loading TV Guide..."
        binding.txtNowTime.text = ""
        binding.txtNextTitle.text = ""
        binding.txtNextTime.text = ""
    }

    private fun openFullscreen(channel: LiveChannel, position: Int) {
        isGoingToFullscreen = true
        PlayerState.channels.clear()
        PlayerState.channels.addAll(channelList)
        PlayerState.currentPosition = position
        val streamUrl = buildStreamUrl(channel)
        PlayerManager.play(this, binding.playerView, streamUrl)
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    private fun buildStreamUrl(channel: LiveChannel): String {
        val server = prefs.getServer().trim().trimEnd('/')
        val username = prefs.getUsername()
        val password = prefs.getPassword()
        return "$server/live/$username/$password/${channel.stream_id}.m3u8"
    }

    private fun loadProgramGuide(channel: LiveChannel) {
        val epgId = channel.epg_channel_id ?: channel.stream_id?.toString() ?: return
        lifecycleScope.launch {
            try {
                val (nowEpg, nextEpg) = repository.getNowNextEpg(epgId)
                if (nowEpg != null) {
                    binding.txtNowTitle.text = nowEpg.title ?: "No Program Info"
                    binding.txtNowTime.text = "${formatTime(nowEpg.startTimestamp)} - ${formatTime(nowEpg.stopTimestamp)}"
                    binding.txtOverlayProgram.text = nowEpg.title ?: ""
                } else {
                    binding.txtNowTitle.text = "No EPG"
                    binding.txtNowTime.text = ""
                    binding.txtOverlayProgram.text = ""
                }
                if (nextEpg != null) {
                    binding.txtNextTitle.text = nextEpg.title ?: ""
                    binding.txtNextTime.text = "${formatTime(nextEpg.startTimestamp)} - ${formatTime(nextEpg.stopTimestamp)}"
                } else {
                    binding.txtNextTitle.text = ""
                    binding.txtNextTime.text = ""
                }
            } catch (e: Exception) {
                binding.txtNowTitle.text = "EPG unavailable"
                binding.txtNowTime.text = ""
                binding.txtNextTitle.text = ""
                binding.txtNextTime.text = ""
                binding.txtOverlayProgram.text = ""
            }
        }
    }

    private fun formatTime(timeMs: Long?): String {
        if (timeMs == null || timeMs == 0L) return ""
        return try {
            val output = SimpleDateFormat("hh:mm a", Locale.getDefault())
            output.format(timeMs)
        } catch (e: Exception) {
            ""
        }
    }

    private fun setupRecycler() {
        binding.rvChannels.layoutManager = LinearLayoutManager(this)
        adapter = ChannelAdapter(
            channels = mutableListOf(),
            favoriteIds = emptySet(),
            onFocused = { _, _ -> },
            onClicked = { channel, position ->
                if (previewPosition == position) {
                    openFullscreen(channel, position)
                } else {
                    previewPosition = position
                    adapter.setPlaying(position)
                    showPreview(channel)
                    loadProgramGuide(channel)
                }
            },
            onLongClicked = { channel, _ ->
                confirmRemoveFavorite(channel)
            }
        )
        binding.rvChannels.adapter = adapter
        PlayerManager.attach(this, binding.playerView)
    }

    private fun setupSearch() {
        binding.edtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val keyword = s.toString()
                val filtered = allChannels.filter {
                    it.name?.contains(keyword, ignoreCase = true) ?: false
                }
                val favIds = currentFavIds
                val favFiltered = filtered.filter {
                    favIds.contains(it.stream_id?.toString().orEmpty())
                }
                channelList.clear()
                channelList.addAll(favFiltered)
                adapter.updateData(channelList)
                adapter.updateFavorites(favIds)
                if (previewPosition !in channelList.indices) {
                    adapter.setPlaying(-1)
                } else {
                    adapter.setPlaying(previewPosition)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun confirmRemoveFavorite(channel: LiveChannel) {
        val channelName = channel.name ?: "this channel"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Remove Favorite")
            .setMessage("Do you want to remove \"$channelName\" from favorites?")
            .setPositiveButton("Remove") { dialog, _ ->
                dialog.dismiss()
                removeFromFavorites(channel)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }


    override fun onResume() {
        super.onResume()
        isGoingToFullscreen = false
        PlayerManager.attach(this, binding.playerView)
        PlayerManager.resume()
        binding.playerView.player?.addListener(playerListener)

        val player = binding.playerView.player
        if (player?.playbackState == Player.STATE_READY) {
            binding.progressLoading.visibility = View.GONE
            binding.txtPlayerError.visibility = View.GONE
            binding.btnReportChannel.visibility = View.GONE
        } else if (player?.playbackState == Player.STATE_BUFFERING) {
            binding.progressLoading.visibility = View.VISIBLE
            binding.txtPlayerError.visibility = View.GONE
        } else if (player?.playerError != null) {
            binding.progressLoading.visibility = View.GONE
            binding.txtPlayerError.visibility = View.VISIBLE

            // Show report button if error state is persisted
            binding.btnReportChannel.visibility = View.VISIBLE
        }

        registerEpgRefresh {
            if (previewPosition in channelList.indices) {
                loadProgramGuide(channelList[previewPosition])
            }
        }
    }

    override fun onPause() {
        super.onPause()
        binding.playerView.player?.removeListener(playerListener)
        if (!isGoingToFullscreen) PlayerManager.pause()
        PlayerManager.detach(binding.playerView)
        unregisterEpgRefresh()
    }

    override fun onDestroy() {
        retryJob?.cancel()
        PlayerManager.detach(binding.playerView)
        if (isFinishing) PlayerManager.stop()
        hideLoader()
        super.onDestroy()
    }
}
