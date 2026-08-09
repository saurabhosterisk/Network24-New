package com.network24.player.features.player.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.media3.common.Player
import com.network24.player.core.net.SpeedMonitor
import com.network24.player.databinding.DialogStreamInfoBinding
import com.network24.player.features.player.manager.PlayerManager
import java.util.Locale

class StreamInfoDialog : DialogFragment() {

    private var _binding: DialogStreamInfoBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refreshDynamicInfo()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogStreamInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        binding.btnClose.setOnClickListener { dismiss() }

        // First time open
        refreshStaticInfo()
        refreshDynamicInfo()
    }

    /**
     * Static info: update only when dialog opens or user taps Refresh.
     * (URL, resolution, codecs, channels, network type etc.)
     */
    private fun refreshStaticInfo() {

        val player = PlayerManager.getExoPlayerOrNull() ?: run {
            setUnknownAll("Player not ready")
            return
        }

        val video = player.videoFormat
        binding.tvResolution.text =
            if (video != null && video.width > 0 && video.height > 0) "${video.width} × ${video.height}" else "-"
        binding.tvVideoCodec.text = video?.sampleMimeType ?: "-"

        val audio = player.audioFormat
        binding.tvAudioCodec.text = audio?.sampleMimeType ?: "-"
        binding.tvNetworkType.text = getNetworkTypeOnce()
    }

    /**
     * Dynamic info: update every second.
     * (buffer %, download speed, player state, health score, diagnosis)
     */
    private fun refreshDynamicInfo() {
        val player = PlayerManager.getExoPlayerOrNull() ?: run {
            setUnknownAll("Player not ready")
            return
        }

        // Player state
        val state = when (player.playbackState) {
            Player.STATE_IDLE -> "Idle"
            Player.STATE_BUFFERING -> "Buffering"
            Player.STATE_READY -> if (player.isPlaying) "Playing" else "Paused"
            Player.STATE_ENDED -> "Ended"
            else -> "Unknown"
        }
        binding.tvPlayerState.text = state

        // Buffer %
        binding.tvBufferedPercent.text = "${player.bufferedPercentage}%"

        // Download speed (from SpeedMonitor / CountingDataSource)
        val downloadMbps = SpeedMonitor.getMbps().toFloat()
        binding.tvDownloadSpeed.text =
            if (downloadMbps > 0f) String.format(Locale.US, "%.2f Mbps", downloadMbps) else "--"

        // Required speed heuristic (based on resolution)
        val h = player.videoFormat?.height ?: 0
        val requiredMbps = when {
            h >= 2160 -> 30f
            h >= 1080 -> 12f
            h >= 720 -> 6f
            h > 0 -> 3f
            else -> 0f
        }
        binding.tvRequiredSpeed.text =
            if (requiredMbps > 0f) String.format(Locale.US, "%.1f Mbps", requiredMbps) else "-"


        // Health score
        var score = 100
        if (player.playbackState == Player.STATE_BUFFERING) score -= 40
        if (player.bufferedPercentage < 20) score -= 20
        if (requiredMbps > 0f && downloadMbps > 0f && downloadMbps < requiredMbps) score -= 30
        score = score.coerceIn(0, 100)

        binding.tvHealthScore.text = "$score / 100"
        binding.tvHealthLabel.text = when {
            score >= 80 -> "GOOD"
            score >= 50 -> "FAIR"
            else -> "POOR"
        }


        // Buffering detected label (if present in your XML)
        if (hasViewId("tvBufferingDetected")) {
            val isBuffering = player.playbackState == Player.STATE_BUFFERING
            val id = resources.getIdentifier("tvBufferingDetected", "id", requireContext().packageName)
            val v = binding.root.findViewById<android.widget.TextView>(id)
            v?.text = if (isBuffering) "BUFFERING DETECTED" else "No buffering detected"
        }
    }

    private fun getNetworkTypeOnce(): String {
        return try {
            val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                caps == null -> "Offline"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
                else -> "Unknown"
            }
        } catch (_: Exception) {
            "-"
        }
    }

    private fun hasViewId(idName: String): Boolean {
        return resources.getIdentifier(idName, "id", requireContext().packageName) != 0
    }

    private fun setUnknownAll(playerStateText: String) {
        binding.tvPlayerState.text = playerStateText
        binding.tvBufferedPercent.text = "-"
        binding.tvResolution.text = "-"
        binding.tvVideoCodec.text = "-"
        binding.tvAudioCodec.text = "-"

        if (hasViewId("tvDownloadSpeed")) binding.tvDownloadSpeed.text = "--"
        if (hasViewId("tvRequiredSpeed")) binding.tvRequiredSpeed.text = "-"
    }

    private fun buildStreamInfoText(): String {
        val url = PlayerManager.getCurrentUrlOrEmpty()
        val player = PlayerManager.getExoPlayerOrNull()

        val playbackState = if (player == null) {
            "Not ready"
        } else {
            when (player.playbackState) {
                Player.STATE_IDLE -> "Idle"
                Player.STATE_BUFFERING -> "Buffering"
                Player.STATE_READY -> if (player.isPlaying) "Playing" else "Paused"
                Player.STATE_ENDED -> "Ended"
                else -> "Unknown"
            }
        }

        val buffered = player?.bufferedPercentage?.let { "${it}%" } ?: "-"
        val vf = player?.videoFormat
        val resolution =
            if (vf != null && vf.width > 0 && vf.height > 0) "${vf.width} x ${vf.height}" else "-"
        val vCodec = vf?.sampleMimeType ?: "-"

        val af = player?.audioFormat
        val aCodec = af?.sampleMimeType ?: "-"
        val channels = af?.channelCount?.toString() ?: "-"

        val downloadMbps = SpeedMonitor.getMbps()

        return """
STREAM INFORMATION
URL:
$url

PLAYBACK:
State: $playbackState
Buffered: $buffered

VIDEO:
Resolution: $resolution
Codec: $vCodec

AUDIO:
Codec: $aCodec
Channels: $channels

NETWORK:
Download Speed: ${String.format(Locale.US, "%.2f Mbps", downloadMbps)}
""".trim()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.95).toInt(),
            (resources.displayMetrics.heightPixels * 0.92).toInt()
        )
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(ticker)
        _binding = null
    }

    companion object {
        fun newInstance(anyId: String): StreamInfoDialog = StreamInfoDialog()
    }
}
