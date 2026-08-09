package com.network24.player.features.player.activity

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private var retryJob: Job? = null
    private var recoveryStartedAtMs = 0L
    private var errorActive = false

    private companion object {
        private const val RECOVERY_WINDOW_MS = 30_000L
    }

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
                return
            }

            binding.progressBar.visibility = View.GONE

            if (playbackState == Player.STATE_READY) {
                retryJob?.cancel()
                retryJob = null
                retryCount = 0
                recoveryStartedAtMs = 0L
                errorActive = false
                binding.txtPlayerError.visibility = View.GONE
                binding.btnReportChannel.visibility = View.GONE
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            binding.btnPlayPause.setImageResource(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)

            // A single player error is not enough to declare a live stream dead.
            // Give a live stream a bounded recovery window, while keeping the
            // total wait short enough that a genuinely dead channel does not trap the user.
            if (recoveryStartedAtMs == 0L) {
                recoveryStartedAtMs = System.currentTimeMillis()
            }

            val elapsed = System.currentTimeMillis() - recoveryStartedAtMs
            if (elapsed >= RECOVERY_WINDOW_MS) {
                showPermanentPlaybackError()
                return
            }

            retryCount++
            val remainingMs = RECOVERY_WINDOW_MS - elapsed
            val retryDelay = when (retryCount) {
                1 -> 2_000L
                2 -> 3_000L
                3 -> 5_000L
                4 -> 7_000L
                else -> 8_000L
            }.coerceAtMost(remainingMs)

            Toast.makeText(
                this@PlayerActivity,
                "Reconnecting... (${elapsed / 1000}s)",
                Toast.LENGTH_SHORT
            ).show()

            retryJob?.cancel()
            retryJob = lifecycleScope.launch(Dispatchers.Main) {
                delay(retryDelay)

                if (isFinishing || isDestroyed) return@launch

                val recoveryElapsed = System.currentTimeMillis() - recoveryStartedAtMs
                if (recoveryElapsed >= RECOVERY_WINDOW_MS) {
                    showPermanentPlaybackError()
                    return@launch
                }

                binding.progressBar.visibility = View.VISIBLE
                binding.txtPlayerError.visibility = View.GONE
                binding.btnReportChannel.visibility = View.GONE
                PlayerManager.retryCurrent()
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

        onBackPressedDispatcher.addCallback(this) { finish() }
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
            if (PlayerManager.isPlaying()) PlayerManager.pause() else PlayerManager.resume()
            showUiWithTimeout()
        }
        binding.btnNext.setOnClickListener { playNextChannel(); showUiWithTimeout() }
        binding.btnPrev.setOnClickListener { playPreviousChannel(); showUiWithTimeout() }

        binding.btnInfo.setOnClickListener {
            val streamId = PlayerState.currentChannel()?.stream_id
            if (streamId == null) {
                Toast.makeText(this, "Channel not available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            StreamInfoDialog.newInstance(streamId.toString()).show(supportFragmentManager, "StreamInfoDialog")
            showUiWithTimeout()
        }

        binding.btnAspect.setOnClickListener {
            currentAspectRatioIndex = (currentAspectRatioIndex + 1) % 4
            val toastMessage = when (currentAspectRatioIndex) {
                0 -> { binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; "Aspect Ratio: Fit" }
                1 -> { binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL; "Aspect Ratio: Fill" }
                2 -> { binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; "Aspect Ratio: Zoom" }
                3 -> { binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH; "Aspect Ratio: Fixed Width" }
                else -> "Aspect Ratio: Fit"
            }
            Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
            showUiWithTimeout()
        }

        binding.btnSubtitle.setOnClickListener {
            isSubtitleEnabled = !isSubtitleEnabled
            toggleSubtitles(isSubtitleEnabled)
            Toast.makeText(this, if (isSubtitleEnabled) "Subtitles Enabled" else "Subtitles Disabled", Toast.LENGTH_SHORT).show()
            showUiWithTimeout()
        }

        binding.btnReportChannel.setOnClickListener {
            val currentChannel = PlayerState.currentChannel()
            val channelName = currentChannel?.name ?: "Unknown Channel"
            val username = prefs.getUsername()
            val alertMessage = "🚨 System Alert : $username reported that the channel '$channelName' is currently down."
            val chatData = hashMapOf(
                "senderId" to "system_bot",
                "senderName" to "System",
                "text" to alertMessage,
                "ts" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            binding.btnReportChannel.visibility = View.GONE
            binding.txtPlayerError.text = "Sending report..."
            db.collection("rooms").document("channel_down").collection("messages").add(chatData)
                .addOnSuccessListener { binding.txtPlayerError.text = "Channel reported. Our team will look into it." }
                .addOnFailureListener { exception ->
                    binding.btnReportChannel.visibility = View.VISIBLE
                    binding.txtPlayerError.text = "Failed to send report."
                    Toast.makeText(this@PlayerActivity, "Error: ${exception.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun toggleSubtitles(enable: Boolean) {
        val player = binding.playerView.player ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enable)
            .build()
        binding.btnSubtitle.setColorFilter(if (enable) Color.parseColor("#FFC107") else Color.WHITE)
    }

    override fun onResume() {
        super.onResume()
        PlayerManager.attach(this, binding.playerView)
        PlayerManager.resume()
        binding.playerView.player?.addListener(playerListener)
        when {
            binding.playerView.player?.playbackState == Player.STATE_READY -> {
                binding.progressBar.visibility = View.GONE
                binding.txtPlayerError.visibility = View.GONE
                binding.btnReportChannel.visibility = View.GONE
            }
            binding.playerView.player?.playbackState == Player.STATE_BUFFERING -> binding.progressBar.visibility = View.VISIBLE
            binding.playerView.player?.playerError != null -> {
                binding.progressBar.visibility = View.GONE
                binding.txtPlayerError.visibility = View.VISIBLE
                binding.btnReportChannel.visibility = View.VISIBLE
            }
        }
        toggleSubtitles(isSubtitleEnabled)
        showUiWithTimeout()
        binding.root.postDelayed({ binding.btnPlayPause.requestFocus() }, 350)
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

    private fun showPermanentPlaybackError() {
        retryJob?.cancel()
        retryJob = null
        errorActive = true
        binding.progressBar.visibility = View.GONE
        binding.txtPlayerError.text = "Unable to play this stream right now. It may be temporarily unavailable or your connection may be unstable."
        binding.txtPlayerError.visibility = View.VISIBLE
        binding.btnReportChannel.visibility = View.VISIBLE
        binding.btnReportChannel.post { binding.btnReportChannel.requestFocus() }
        showUiWithTimeout()
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
            binding.bottomOverlay.animate().alpha(1f).translationY(0f).setDuration(animationDuration).withEndAction {
                if (binding.btnReportChannel.visibility != View.VISIBLE) binding.btnPlayPause.post { binding.btnPlayPause.requestFocus() }
            }.start()
        }
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 5000)
    }

    private fun toggleUi() {
        if (binding.bottomOverlay.visibility == View.VISIBLE) {
            hideHandler.removeCallbacks(hideRunnable)
            hideRunnable.run()
        } else showUiWithTimeout()
    }

    private fun playNextChannel() {
        PlayerState.next()?.let { nextChannel ->
            retryJob?.cancel()
            PlayerManager.play(this, binding.playerView, buildStreamUrl(nextChannel))
            updateChannelUI(nextChannel)
            toggleSubtitles(isSubtitleEnabled)
        }
    }

    private fun playPreviousChannel() {
        PlayerState.previous()?.let { prevChannel ->
            retryJob?.cancel()
            PlayerManager.play(this, binding.playerView, buildStreamUrl(prevChannel))
            updateChannelUI(prevChannel)
            toggleSubtitles(isSubtitleEnabled)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (binding.bottomOverlay.visibility != View.VISIBLE) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> { showUiWithTimeout(); return true }
            }
        } else {
            hideHandler.removeCallbacks(hideRunnable)
            hideHandler.postDelayed(hideRunnable, 5000)
            when (keyCode) {
                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_UP -> { playNextChannel(); return true }
                KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> { playPreviousChannel(); return true }
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
        recoveryStartedAtMs = 0L
        errorActive = false
        binding.txtPlayerError.visibility = View.GONE
        binding.btnReportChannel.visibility = View.GONE
        val num = channel.num?.let { "$it - " } ?: ""
        binding.txtChannelTitle.text = "$num${channel.name ?: "Unknown Channel"}"
        val epgId = channel.epg_channel_id ?: channel.stream_id?.toString() ?: ""
        if (epgId.isNotEmpty()) loadEpg(epgId)
    }

    private fun loadEpg(epgId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val (nowEpg, nextEpg) = repository.getNowNextEpg(epgId)
                withContext(Dispatchers.Main) { updateEpg(nowEpg, nextEpg) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.txtNowTitle.text = "No EPG Data"
                    binding.txtNextTitle.text = ""
                    binding.txtNowTime.text = ""
                    binding.txtNextTime.text = ""
                    binding.epgProgress.layoutParams = binding.epgProgress.layoutParams.apply { width = 0 }
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
                binding.epgProgress.layoutParams = binding.epgProgress.layoutParams.apply { width = (binding.epgTrack.width * progressPercent).toInt() }
            }
        } else {
            binding.txtNowTitle.text = "No Program Info"
            binding.txtNowTime.text = ""
            binding.epgProgress.layoutParams = binding.epgProgress.layoutParams.apply { width = 0 }
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
        return try { SimpleDateFormat("hh:mm a", Locale.getDefault()).format(java.util.Date(timeMs)) } catch (e: Exception) { "" }
    }

    private fun calculateEpgProgress(startMs: Long?, stopMs: Long?): Float {
        if (startMs == null || stopMs == null || stopMs <= startMs) return 0f
        return ((System.currentTimeMillis() - startMs).toFloat() / (stopMs - startMs).toFloat()).coerceIn(0f, 1f)
    }

    private fun showPlayerError(message: String) {
        binding.txtPlayerError.text = message
        binding.txtPlayerError.visibility = View.VISIBLE
        showUiWithTimeout()
    }
}
