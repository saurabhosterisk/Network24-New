package com.network24.player.features.player.activity

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import com.network24.player.R
import com.network24.player.core.base.BaseActivity
import com.network24.player.core.preferences.PreferenceManager
import com.network24.player.databinding.ActivityPlayerBinding
import com.network24.player.features.live.models.LiveChannel
import com.network24.player.features.live.repository.LiveRepository
import com.network24.player.features.player.manager.PlayerManager
import com.network24.player.features.player.state.PlayerState
import com.network24.player.features.player.ui.dialogs.StreamInfoDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class PlayerActivity : BaseActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private lateinit var prefs: PreferenceManager
    private lateinit var repository: LiveRepository

    private var retryCount = 0
    private val MAX_RETRIES = 3
    private var retryJob: Job? = null

    private var isSubtitleEnabled = false
    private var currentAspectRatioIndex = 0

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable {
        val animationDuration = 300L
        binding.topTint.animate().alpha(0f).setDuration(animationDuration).withEndAction {
            binding.topTint.visibility = View.GONE
        }.start()
        binding.txtChannelTitle.animate().alpha(0f).setDuration(animationDuration).withEndAction {
            binding.txtChannelTitle.visibility = View.GONE
        }.start()
        binding.bottomOverlay.animate().alpha(0f).translationY(50f).setDuration(animationDuration).withEndAction {
            binding.bottomOverlay.visibility = View.GONE
        }.start()
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_BUFFERING) {
                binding.progressBar.visibility = View.VISIBLE
            } else {
                binding.progressBar.visibility = View.GONE
                if (playbackState == Player.STATE_READY) {
                    retryCount = 0
                    // ✅ Hide error and report button when playback works
                    binding.txtPlayerError.visibility = View.GONE
                    binding.btnReportChannel.visibility = View.GONE
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
            } else {
                binding.btnPlayPause.setImageResource(R.drawable.ic_play)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)

            if (retryCount < MAX_RETRIES) {
                retryCount++
                val toastMsg = "Reconnecting... ($retryCount/$MAX_RETRIES)"
                Toast.makeText(this@PlayerActivity, toastMsg, Toast.LENGTH_SHORT).show()

                retryJob?.cancel()
                retryJob = lifecycleScope.launch(Dispatchers.Main) {
                    delay(3000)
                    val currentChannel = PlayerState.currentChannel()
                    if (currentChannel != null) {
                        val streamUrl = buildStreamUrl(currentChannel)
                        binding.progressBar.visibility = View.VISIBLE
                        binding.txtPlayerError.visibility = View.GONE
                        binding.btnReportChannel.visibility = View.GONE
                        PlayerManager.play(this@PlayerActivity, binding.playerView, streamUrl)
                    }
                }
            } else {
                binding.progressBar.visibility = View.GONE
                binding.txtPlayerError.visibility = View.VISIBLE

                // ✅ Show the report button when max retries fail
                binding.btnReportChannel.visibility = View.VISIBLE
                binding.btnReportChannel.post {
                    binding.btnReportChannel.requestFocus() // Request focus for TV users
                }

                showUiWithTimeout()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceManager(this)
        repository = LiveRepository(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        binding.progressBar.visibility = View.GONE
        binding.btnReportChannel.visibility = View.GONE

        binding.playerView.setShowSubtitleButton(false)
        binding.playerView.subtitleView?.setApplyEmbeddedStyles(false)

        updateChannelUI(PlayerState.currentChannel())
        showUiWithTimeout()
        setupClickListeners()

        PlayerManager.moveTo(this, binding.playerView)

        onBackPressedDispatcher.addCallback(this) {
            finish()
        }
    }

    private fun buildStreamUrl(channel: LiveChannel): String {
        val server = prefs.getServer().trim().trimEnd('/')
        val username = prefs.getUsername().trim()
        val password = prefs.getPassword().trim()
        return "$server/live/$username/$password/${channel.stream_id}.m3u8"
    }

    private fun setupClickListeners() {
        binding.root.setOnClickListener { toggleUi() }
        binding.playerView.setOnClickListener { toggleUi() }

        binding.btnPlayPause.setOnClickListener {
            if (PlayerManager.isPlaying()) {
                PlayerManager.pause()
            } else {
                PlayerManager.resume()
            }
            showUiWithTimeout()
        }

        binding.btnNext.setOnClickListener {
            playNextChannel()
            showUiWithTimeout()
        }

        binding.btnPrev.setOnClickListener {
            playPreviousChannel()
            showUiWithTimeout()
        }

        binding.btnInfo.setOnClickListener {
            val currentChannel = PlayerState.currentChannel()
            val streamId = currentChannel?.stream_id
            if (streamId == null) {
                Toast.makeText(this, "Channel not available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            StreamInfoDialog.newInstance(streamId.toString())
                .show(supportFragmentManager, "StreamInfoDialog")

            showUiWithTimeout()
        }

        binding.btnAspect.setOnClickListener {
            currentAspectRatioIndex = (currentAspectRatioIndex + 1) % 4
            val toastMessage = when (currentAspectRatioIndex) {
                0 -> {
                    binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    "Aspect Ratio: Fit"
                }
                1 -> {
                    binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    "Aspect Ratio: Fill"
                }
                2 -> {
                    binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    "Aspect Ratio: Zoom"
                }
                3 -> {
                    binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    "Aspect Ratio: Fixed Width"
                }
                else -> "Aspect Ratio: Fit"
            }
            Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
            showUiWithTimeout()
        }

        binding.btnSubtitle.setOnClickListener {
            isSubtitleEnabled = !isSubtitleEnabled
            toggleSubtitles(isSubtitleEnabled)
            val msg = if (isSubtitleEnabled) "Subtitles Enabled" else "Subtitles Disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            showUiWithTimeout()
        }

        // ✅ NEW: Report Channel Logic (Send to "General Chat")
        binding.btnReportChannel.setOnClickListener {
            val currentChannel = PlayerState.currentChannel()
            val channelName = currentChannel?.name ?: "Unknown Channel"
            val username = prefs.getUsername()

            val alertMessage = "🚨 System Alert : $username reported that the channel '$channelName' is currently down."

            // 1. EXACT FIELD NAMES TO MATCH ChatRepository & ChatHubActivity
            val chatData = hashMapOf(
                "senderId" to "system_bot",
                "senderName" to "System",
                "text" to alertMessage,
                "ts" to com.google.firebase.firestore.FieldValue.serverTimestamp() // 🔥 IMPORTANT: "ts" instead of "timestamp"
            )

            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

            // 2. Hide button immediately
            binding.btnReportChannel.visibility = View.GONE
            binding.txtPlayerError.text = "Sending report..."

            // 3. Collection path exact match with ChatHubActivity ("general" room)
            db.collection("rooms")
                .document("channel_down")
                .collection("messages")
                .add(chatData)
                .addOnSuccessListener {
                    binding.txtPlayerError.text = "Channel reported. Our team will look into it."
                }
                .addOnFailureListener { exception ->
                    binding.btnReportChannel.visibility = View.VISIBLE
                    binding.txtPlayerError.text = "Failed to send report."
                    Toast.makeText(
                        this@PlayerActivity,
                        "Error: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }




    }

    private fun toggleSubtitles(enable: Boolean) {
        val player = binding.playerView.player ?: return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enable)
            .build()

        if (enable) {
            binding.btnSubtitle.setColorFilter(Color.parseColor("#FFC107"))
        } else {
            binding.btnSubtitle.setColorFilter(Color.WHITE)
        }
    }

    override fun onResume() {
        super.onResume()
        PlayerManager.attach(this, binding.playerView)
        PlayerManager.resume()
        binding.playerView.player?.addListener(playerListener)

        val currentState = binding.playerView.player?.playbackState

        if (currentState == Player.STATE_READY) {
            binding.progressBar.visibility = View.GONE
            binding.txtPlayerError.visibility = View.GONE
            binding.btnReportChannel.visibility = View.GONE
        } else if (currentState == Player.STATE_BUFFERING) {
            binding.progressBar.visibility = View.VISIBLE
        } else if (binding.playerView.player?.playerError != null) {
            binding.progressBar.visibility = View.GONE
            binding.txtPlayerError.visibility = View.VISIBLE
            binding.btnReportChannel.visibility = View.VISIBLE // Keep it visible if returning to error state
        }

        toggleSubtitles(isSubtitleEnabled)
        showUiWithTimeout()

        binding.root.postDelayed({
            binding.btnPlayPause.requestFocus()
        }, 350)
    }

    override fun onPause() {
        super.onPause()
        binding.playerView.player?.removeListener(playerListener)
        hideHandler.removeCallbacks(hideRunnable)
        PlayerManager.pause()
        PlayerManager.detach(binding.playerView)
    }

    override fun onDestroy() {
        retryJob?.cancel()
        PlayerManager.detach(binding.playerView)
        super.onDestroy()
    }

    private fun showUiWithTimeout() {
        val animationDuration = 300L
        if (binding.bottomOverlay.visibility != View.VISIBLE) {
            binding.topTint.alpha = 0f
            binding.topTint.visibility = View.VISIBLE
            binding.topTint.animate().alpha(1f).setDuration(animationDuration).start()

            binding.txtChannelTitle.alpha = 0f
            binding.txtChannelTitle.visibility = View.VISIBLE
            binding.txtChannelTitle.animate().alpha(1f).setDuration(animationDuration).start()

            binding.bottomOverlay.alpha = 0f
            binding.bottomOverlay.translationY = 50f
            binding.bottomOverlay.visibility = View.VISIBLE
            binding.bottomOverlay.animate().alpha(1f).translationY(0f).setDuration(animationDuration)
                .withEndAction {
                    // Only request focus on play if report button isn't visible (handling TV remotes)
                    if (binding.btnReportChannel.visibility != View.VISIBLE) {
                        binding.btnPlayPause.post {
                            binding.btnPlayPause.requestFocus()
                        }
                    }
                }.start()
        }
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 5000)
    }

    private fun toggleUi() {
        if (binding.bottomOverlay.visibility == View.VISIBLE) {
            hideHandler.removeCallbacks(hideRunnable)
            hideRunnable.run()
        } else {
            showUiWithTimeout()
        }
    }

    private fun playNextChannel() {
        val nextChannel = PlayerState.next()
        if (nextChannel != null) {
            val streamUrl = buildStreamUrl(nextChannel)
            PlayerManager.play(this, binding.playerView, streamUrl)
            updateChannelUI(nextChannel)
            toggleSubtitles(isSubtitleEnabled)
        }
    }

    private fun playPreviousChannel() {
        val prevChannel = PlayerState.previous()
        if (prevChannel != null) {
            val streamUrl = buildStreamUrl(prevChannel)
            PlayerManager.play(this, binding.playerView, streamUrl)
            updateChannelUI(prevChannel)
            toggleSubtitles(isSubtitleEnabled)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (binding.bottomOverlay.visibility != View.VISIBLE) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    showUiWithTimeout()
                    return true
                }
            }
        } else {
            hideHandler.removeCallbacks(hideRunnable)
            hideHandler.postDelayed(hideRunnable, 5000)
            when (keyCode) {
                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_UP -> {
                    playNextChannel()
                    return true
                }
                KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    playPreviousChannel()
                    return true
                }
            }
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            showUiWithTimeout()
            if (PlayerManager.isPlaying()) PlayerManager.pause() else PlayerManager.resume()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun updateChannelUI(channel: LiveChannel?) {
        if (channel == null) return

        retryJob?.cancel()
        retryCount = 0
        binding.txtPlayerError.visibility = View.GONE
        binding.btnReportChannel.visibility = View.GONE // Ensure it is hidden on new channel

        val num = channel.num?.let { "$it - " } ?: ""
        val name = channel.name ?: "Unknown Channel"
        binding.txtChannelTitle.text = "$num$name"

        val epgId = channel.epg_channel_id ?: channel.stream_id?.toString() ?: ""
        if (epgId.isNotEmpty()) {
            loadEpg(epgId)
        }
    }

    private fun loadEpg(epgId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val (nowEpg, nextEpg) = repository.getNowNextEpg(epgId)
                withContext(Dispatchers.Main) {
                    updateEpg(nowEpg, nextEpg)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.txtNowTitle.text = "No EPG Data"
                    binding.txtNextTitle.text = ""
                    binding.txtNowTime.text = ""
                    binding.txtNextTime.text = ""

                    val layoutParams = binding.epgProgress.layoutParams
                    layoutParams.width = 0
                    binding.epgProgress.layoutParams = layoutParams
                }
            }
        }
    }

    private fun updateEpg(now: com.network24.player.core.database.entity.EpgEntity?, next: com.network24.player.core.database.entity.EpgEntity?) {
        if (now != null) {
            binding.txtNowTitle.text = now.title ?: "No Program Info"
            binding.txtNowTime.text = "${formatTime(now.startTimestamp)} - ${formatTime(now.stopTimestamp)}"

            val progressPercent = calculateEpgProgress(now.startTimestamp, now.stopTimestamp)
            binding.epgTrack.post {
                val trackWidth = binding.epgTrack.width
                val layoutParams = binding.epgProgress.layoutParams
                layoutParams.width = (trackWidth * progressPercent).toInt()
                binding.epgProgress.layoutParams = layoutParams
            }
        } else {
            binding.txtNowTitle.text = "No Program Info"
            binding.txtNowTime.text = ""
            val layoutParams = binding.epgProgress.layoutParams
            layoutParams.width = 0
            binding.epgProgress.layoutParams = layoutParams
        }

        if (next != null) {
            binding.txtNextTitle.text = next.title ?: ""
            binding.txtNextTime.text = "${formatTime(next.startTimestamp)} - ${formatTime(next.stopTimestamp)}"
        } else {
            binding.txtNextTitle.text = ""
            binding.txtNextTime.text = ""
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

    private fun calculateEpgProgress(startTimeMs: Long?, endTimeMs: Long?): Float {
        if (startTimeMs == null || endTimeMs == null || startTimeMs == 0L || endTimeMs == 0L) return 0f
        return try {
            val currentTime = System.currentTimeMillis()
            if (currentTime <= startTimeMs) return 0f
            if (currentTime >= endTimeMs) return 1f

            val totalDuration = endTimeMs - startTimeMs
            val elapsed = currentTime - startTimeMs
            elapsed.toFloat() / totalDuration.toFloat()
        } catch (e: Exception) {
            0f
        }
    }
}
