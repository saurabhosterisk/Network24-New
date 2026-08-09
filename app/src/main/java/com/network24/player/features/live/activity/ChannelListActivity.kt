package com.network24.player.features.live.activity

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.network24.player.databinding.ActivityChannelListBinding
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

class ChannelListActivity : BaseActivity() {

    private lateinit var binding: ActivityChannelListBinding
    private lateinit var repository: LiveRepository
    private lateinit var prefs: PreferenceManager
    private lateinit var favRepo: FavoritesRepository
    private lateinit var adapter: ChannelAdapter
    private var isGoingToFullscreen = false
    private var loadingDialog: AlertDialog? = null
    private var retryCount = 0
    private val MAX_RETRIES = 1
    private var retryJob: Job? = null
    private val isTouchDevice by lazy {
        !packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
    private var previewPosition = -1
    private val allChannels = mutableListOf<LiveChannel>()
    private val channelList = mutableListOf<LiveChannel>()
    private lateinit var categoryId: String

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
                    this@ChannelListActivity,
                    "Playback Error. Trying to reconnect in 3 sec. ($retryCount)",
                    Toast.LENGTH_SHORT
                ).show()
                retryJob?.cancel()
                retryJob = lifecycleScope.launch {
                    delay(3000)
                    if (channelList.isNotEmpty() && previewPosition != -1) {
                        val currentChannel = channelList[previewPosition]
                        val streamUrl = buildStreamUrl(currentChannel)
                        binding.progressLoading.visibility = View.VISIBLE
                        PlayerManager.play(this@ChannelListActivity, binding.playerView, streamUrl)
                    }
                }
            } else {
                binding.progressLoading.visibility = View.GONE
                binding.txtPlayerError.visibility = View.VISIBLE
                binding.txtNowTitle.text = "Playback Failed"

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
        binding = ActivityChannelListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        registerDrawerBackHandler(binding.drawerLayout)
        val db = DatabaseProvider.get(this)
        favRepo = FavoritesRepository(db.favoritesDao(), FirebaseFirestore.getInstance())
        prefs = PreferenceManager(this)
        repository = LiveRepository(this)
        categoryId = intent.getStringExtra("category_id") ?: ""
        binding.txtCategoryName.text = intent.getStringExtra("category_name") ?: "Live TV"
        binding.btnBack.setOnClickListener { finish() }
        binding.playerView.setShowSubtitleButton(false)
        binding.playerView.subtitleView?.visibility = View.GONE

        setupDrawerAndMenu()

        // ✅ Initialize Report Button Click Logic
        setupReportButton()

        binding.playerView.setOnClickListener {
            if (isTouchDevice && previewPosition != -1 && channelList.isNotEmpty()) {
                val currentChannel = channelList[previewPosition]
                openFullscreen(currentChannel, previewPosition)
            }
        }
        setupRecycler()
        setupSearch()
        // Room Flow observation (Safe on Main Thread)
        lifecycleScope.launch {
            db.favoritesDao().observeByType("LIVE_CHANNEL").collect { favs ->
                val favIds = favs.map { it.itemId }.toSet()
                adapter.updateFavorites(favIds)
            }
        }
        ensureInitialSyncThenLoad()
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

    private fun ensureInitialSyncThenLoad() {
        // Runs safely on Main Thread. Suspend functions handle IO internally.
        lifecycleScope.launch {
            try {
                val channels = repository.getChannels(
                    server = prefs.getServer(),
                    username = prefs.getUsername(),
                    password = prefs.getPassword(),
                    categoryId = categoryId,
                    forceRefresh = false
                )
                if (channels.isNotEmpty()) {
                    applyChannelsToUi(channels)
                } else {
                    forceRefreshData(isInitialSync = true)
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChannelListActivity, e.message ?: "Initial load failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadChannels(forceRefresh: Boolean = false) {
        binding.edtSearch.text?.clear()
        binding.edtSearch.clearFocus()
        lifecycleScope.launch {
            try {
                val channels = repository.getChannels(
                    server = prefs.getServer(),
                    username = prefs.getUsername(),
                    password = prefs.getPassword(),
                    categoryId = categoryId,
                    forceRefresh = forceRefresh
                )
                applyChannelsToUi(channels)
            } catch (e: Exception) {
                Toast.makeText(this@ChannelListActivity, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun applyChannelsToUi(channels: List<LiveChannel>) {
        allChannels.clear()
        allChannels.addAll(channels)
        channelList.clear()
        channelList.addAll(channels)
        adapter.updateData(channelList)
        if (channelList.isEmpty()) return
        val targetPos = if (PlayerState.currentPosition in channelList.indices) {
            PlayerState.currentPosition
        } else 0
        previewPosition = targetPos
        adapter.setPlaying(targetPos)
        showPreview(channelList[targetPos])
        loadProgramGuide(channelList[targetPos])
        if (!isTouchDevice) {
            binding.rvChannels.post {
                binding.rvChannels.findViewHolderForAdapterPosition(targetPos)?.itemView?.requestFocus()
            }
        }
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
                        loadChannels(forceRefresh = true)
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
                            DatabaseProvider.get(this@ChannelListActivity).favoritesDao().clearAll()
                        } catch (_: Exception) {}
                        prefs.clear()
                        startActivity(Intent(this@ChannelListActivity, LoginActivity::class.java))
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
                confirmToggleFavorite(channel)
            }
        )
        binding.rvChannels.adapter = adapter
        PlayerManager.attach(this, binding.playerView)
    }

    private fun setupSearch() {
        binding.edtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterChannels(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterChannels(keyword: String) {
        val filtered = allChannels.filter { channel ->
            channel.name?.contains(keyword, ignoreCase = true) ?: false
        }
        channelList.clear()
        channelList.addAll(filtered)
        adapter.updateData(channelList)
        if (previewPosition != -1 && allChannels.isNotEmpty() && channelList.isNotEmpty()) {
            val currentlyPlayingChannel = allChannels[previewPosition]
            val newPosition = channelList.indexOf(currentlyPlayingChannel)
            adapter.setPlaying(newPosition)
        } else {
            adapter.setPlaying(-1)
        }
    }

    private fun showPreview(channel: LiveChannel) {
        retryJob?.cancel()
        retryCount = 0
        binding.txtPlayerError.visibility = View.GONE
        binding.btnReportChannel.visibility = View.GONE // ✅ Hide button immediately when new channel loads

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

    private fun toggleChannelFavorite(channel: LiveChannel) {
        val streamId = channel.stream_id?.toString() ?: return
        val userId = prefs.getUsername()
        lifecycleScope.launch {
            val key = "LIVE_CHANNEL:$streamId"
            val isFav = DatabaseProvider.get(this@ChannelListActivity).favoritesDao().getAll().any { it.key == key }
            if (isFav) {
                favRepo.removeFavorite(userId, "LIVE_CHANNEL", streamId)
                Toast.makeText(this@ChannelListActivity, "${channel.name} removed from Favorites", Toast.LENGTH_SHORT).show()
            } else {
                favRepo.addFavorite(userId, "LIVE_CHANNEL", streamId)
                Toast.makeText(this@ChannelListActivity, "${channel.name} added to Favorites", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmToggleFavorite(channel: LiveChannel) {
        val streamId = channel.stream_id?.toString() ?: return
        val name = channel.name ?: "this channel"
        lifecycleScope.launch {
            val key = "LIVE_CHANNEL:$streamId"
            val isFav = DatabaseProvider.get(this@ChannelListActivity).favoritesDao().getAll().any { it.key == key }
            val title = if (isFav) "Remove Favorite" else "Add Favorite"
            val message = if (isFav) "Do you want to remove \"$name\" from favorites?" else "Do you want to add \"$name\" to favorites?"
            val positiveText = if (isFav) "Remove" else "Add"
            androidx.appcompat.app.AlertDialog.Builder(this@ChannelListActivity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveText) { dialog, _ ->
                    dialog.dismiss()
                    toggleChannelFavorite(channel)
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
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

            val finalError = "Sorry, This video can not be played. Please try again or pick another video."
            binding.txtNowTitle.text = finalError
            binding.txtOverlayProgram.text = finalError
        }
        registerEpgRefresh {
            if (previewPosition != -1 && channelList.isNotEmpty()) {
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
