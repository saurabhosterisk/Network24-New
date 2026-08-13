package com.network24.player.features.player.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.media3.common.C
import androidx.media3.common.Player
import com.network24.player.core.net.SpeedMonitor
import com.network24.player.databinding.DialogStreamInfoBinding
import com.network24.player.features.player.manager.PlayerManager
import java.util.Locale
import java.util.concurrent.TimeUnit

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogStreamInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnCopy.setOnClickListener {
            copyToClipboard("Network24 Diagnostics", buildStreamInfoText())
            Toast.makeText(requireContext(), "Diagnostics copied", Toast.LENGTH_SHORT).show()
        }
        refreshStaticInfo()
        refreshDynamicInfo()
    }

    private fun refreshStaticInfo() {
        val player = PlayerManager.getExoPlayerOrNull() ?: run {
            setUnknownAll("Player not ready")
            return
        }

        val video = player.videoFormat
        binding.tvResolution.text = if (video != null && video.width > 0 && video.height > 0) "${video.width} × ${video.height}" else "-"
        binding.tvVideoCodec.text = video?.sampleMimeType ?: "-"
        binding.tvVideoBitrate.text = formatBitrate(video?.bitrate)
        binding.tvFrameRate.text = video?.frameRate?.takeIf { it > 0f }?.let { String.format(Locale.US, "%.2f fps", it) } ?: "-"

        val audio = player.audioFormat
        binding.tvAudioCodec.text = audio?.sampleMimeType ?: "-"
        binding.tvAudioDetails.text = if (audio != null) {
            val channels = if (audio.channelCount > 0) "${audio.channelCount} ch" else ""
            val sampleRate = if (audio.sampleRate > 0) "${audio.sampleRate} Hz" else ""
            listOf(channels, sampleRate).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "-" }
        } else "-"

        binding.tvNetworkType.text = getNetworkTypeOnce()
        binding.tvServerHost.text = getSafeServerHost()
    }

    private fun refreshDynamicInfo() {
        val player = PlayerManager.getExoPlayerOrNull() ?: run {
            setUnknownAll("Player not ready")
            return
        }

        binding.tvPlayerState.text = when (player.playbackState) {

            Player.STATE_IDLE ->
                "Idle"

            Player.STATE_BUFFERING -> {
                if (player.isPlaying) {
                    "Playing (Buffer Refresh)"
                } else {
                    "Buffering"
                }
            }

            Player.STATE_READY ->
                if (player.isPlaying) "Playing" else "Paused"

            Player.STATE_ENDED ->
                "Ended"

            else ->
                "Unknown"
        }
        binding.tvBufferedPercent.text = "${player.bufferedPercentage}%"
        binding.tvBufferDuration.text = formatDuration(player.totalBufferedDuration)
        binding.tvPosition.text = formatPosition(player.currentPosition)
        binding.tvLoading.text = if (player.isLoading) "Loading data" else "Not loading"
        binding.tvPlaybackSpeed.text = String.format(Locale.US, "%.2fx", player.playbackParameters.speed)

        val downloadMbps = SpeedMonitor.getMbps()
        binding.tvDownloadSpeed.text = if (downloadMbps > 0.0) String.format(Locale.US, "%.2f Mbps", downloadMbps) else "--"

        val requiredMbps = getRequiredSpeedMbps(player.videoFormat?.height ?: 0)
        binding.tvRequiredSpeed.text = if (requiredMbps > 0f) String.format(Locale.US, "%.1f Mbps", requiredMbps) else "-"

        val liveOffset = player.currentLiveOffset
        binding.tvLiveLatency.text = if (liveOffset != C.TIME_UNSET && liveOffset >= 0L) formatDuration(liveOffset) else "Not available"

        val rebufferCount = PlayerManager.getRebufferCount()
        val bufferingMs = PlayerManager.getTotalBufferingMs()
        binding.tvRebufferCount.text = rebufferCount.toString()
        binding.tvBufferingTime.text = formatDuration(bufferingMs)

        val error = PlayerManager.getLastError()
        binding.tvLastError.text = error?.errorCodeName ?: "None"

        val score = calculateHealthScore(player, downloadMbps, requiredMbps, rebufferCount, error != null)
        binding.tvHealthScore.text = "$score / 100"
        binding.tvHealthLabel.text = when {
            score >= 85 -> "GOOD"
            score >= 65 -> "FAIR"
            else -> "POOR"
        }
        binding.tvDiagnosis.text = buildDiagnosis(player, downloadMbps, requiredMbps, rebufferCount, bufferingMs, error != null)
    }

    private fun calculateHealthScore(
        player: Player,
        downloadMbps: Double,
        requiredMbps: Float,
        rebufferCount: Int,
        hasError: Boolean
    ): Int {

        var score = 100


        val bufferingMs =
            PlayerManager.getTotalBufferingMs()


        // Real buffering events
        if (rebufferCount > 0) {
            score -= (rebufferCount * 10)
        }


        // Real buffering duration
        when {
            bufferingMs > 30000L -> {
                score -= 25
            }

            bufferingMs > 10000L -> {
                score -= 10
            }
        }


        // Network capacity
        if (
            requiredMbps > 0f &&
            downloadMbps > 0.0 &&
            downloadMbps < requiredMbps
        ) {
            score -= 20
        }


        // Playback error
        if (hasError) {
            score -= 30
        }


        return score.coerceIn(0,100)
    }

    private fun buildDiagnosis(
        player: Player,
        downloadMbps: Double,
        requiredMbps: Float,
        rebufferCount: Int,
        bufferingMs: Long,
        hasError: Boolean
    ): String {


        if (hasError) {

            return when (
                PlayerManager.getStreamErrorType()
            ) {

                PlayerManager.StreamErrorType.NETWORK ->
                    "Network connection lost. Trying to reconnect..."

                PlayerManager.StreamErrorType.SOURCE ->
                    "Unable to play this stream. Source may be unavailable."

                PlayerManager.StreamErrorType.UNKNOWN ->
                    "Playback error detected. Please wait while player recovers."

                else ->
                    "Stream is recovering..."
            }
        }


        if (
            rebufferCount > 0 &&
            bufferingMs > 0 &&
            PlayerManager.hasEverStartedPlayback()
        ) {
            return "Stream had $rebufferCount buffering event(s) with ${formatDuration(bufferingMs)} total buffering time."
        }


        if (
            player.playbackState == Player.STATE_BUFFERING &&
            !PlayerManager.hasEverStartedPlayback()
        ) {
            return "Stream is loading for first time."
        }


        if (
            requiredMbps > 0f &&
            downloadMbps > 0.0 &&
            downloadMbps < requiredMbps
        ) {
            return "Network speed may be insufficient for this quality."
        }


        if (bufferingMs > 10000L) {
            return "Playback experienced noticeable buffering."
        }


        return "Stream is playing smoothly. No buffering issue detected."
    }

    private fun getRequiredSpeedMbps(height: Int): Float = when {
        height >= 2160 -> 30f
        height >= 1440 -> 18f
        height >= 1080 -> 12f
        height >= 720 -> 6f
        height > 0 -> 3f
        else -> 0f
    }

    private fun formatBitrate(bitrate: Int?): String {
        val value = bitrate ?: -1
        if (value <= 0) return "-"
        return if (value >= 1_000_000) String.format(Locale.US, "%.2f Mbps", value / 1_000_000.0) else String.format(Locale.US, "%.0f kbps", value / 1000.0)
    }

    private fun formatDuration(ms: Long): String {

        if (ms <= 0) {
            return "0s"
        }


        val totalSeconds = ms / 1000

        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60


        return when {

            hours > 0 -> {
                "${hours}h ${minutes}m ${seconds}s"
            }

            minutes > 0 -> {
                "${minutes}m ${seconds}s"
            }

            else -> {
                "${seconds}s"
            }
        }
    }

    private fun formatPosition(ms: Long): String = if (ms >= 0L) formatDuration(ms) else "-"

    private fun getSafeServerHost(): String = try { Uri.parse(PlayerManager.getCurrentUrlOrEmpty()).host ?: "-" } catch (_: Exception) { "-" }

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
        } catch (_: Exception) { "-" }
    }

    private fun setUnknownAll(playerStateText: String) {
        binding.tvPlayerState.text = playerStateText
        binding.tvBufferedPercent.text = "-"
        binding.tvBufferDuration.text = "-"
        binding.tvPosition.text = "-"
        binding.tvLoading.text = "-"
        binding.tvPlaybackSpeed.text = "-"
        binding.tvResolution.text = "-"
        binding.tvVideoCodec.text = "-"
        binding.tvVideoBitrate.text = "-"
        binding.tvFrameRate.text = "-"
        binding.tvAudioCodec.text = "-"
        binding.tvAudioDetails.text = "-"
        binding.tvDownloadSpeed.text = "--"
        binding.tvRequiredSpeed.text = "-"
        binding.tvNetworkType.text = "-"
        binding.tvServerHost.text = "-"
        binding.tvLiveLatency.text = "-"
        binding.tvRebufferCount.text = "-"
        binding.tvBufferingTime.text = "-"
        binding.tvLastError.text = "-"
        binding.tvHealthScore.text = "-"
        binding.tvHealthLabel.text = "-"
        binding.tvDiagnosis.text = "Player is not ready."
    }

    private fun buildStreamInfoText(): String {
        val player = PlayerManager.getExoPlayerOrNull()
        val vf = player?.videoFormat
        val downloadMbps = SpeedMonitor.getMbps()
        val required = getRequiredSpeedMbps(vf?.height ?: 0)
        val error = PlayerManager.getLastError()
        return """
NETWORK24 STREAM DIAGNOSTICS

PLAYBACK
State: ${binding.tvPlayerState.text}
Buffer: ${binding.tvBufferedPercent.text}
Buffer duration: ${binding.tvBufferDuration.text}
Position: ${binding.tvPosition.text}
Load state: ${binding.tvLoading.text}
Playback speed: ${binding.tvPlaybackSpeed.text}
Rebuffers: ${PlayerManager.getRebufferCount()}
Total buffering: ${formatDuration(PlayerManager.getTotalBufferingMs())}

VIDEO
Resolution: ${binding.tvResolution.text}
Codec: ${binding.tvVideoCodec.text}
Bitrate: ${binding.tvVideoBitrate.text}
Frame rate: ${binding.tvFrameRate.text}

AUDIO
Codec: ${binding.tvAudioCodec.text}
Details: ${binding.tvAudioDetails.text}

NETWORK
Type: ${binding.tvNetworkType.text}
Server: ${binding.tvServerHost.text}
Download speed: ${String.format(Locale.US, "%.2f Mbps", downloadMbps)}
Estimated required: ${if (required > 0f) String.format(Locale.US, "%.1f Mbps", required) else "-"}
Live latency: ${binding.tvLiveLatency.text}

HEALTH
Score: ${binding.tvHealthScore.text}
Quality: ${binding.tvHealthLabel.text}
Diagnosis: ${binding.tvDiagnosis.text}
Last error: ${error?.errorCodeName ?: "None"}
""".trim()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout((resources.displayMetrics.widthPixels * 0.95).toInt(), (resources.displayMetrics.heightPixels * 0.92).toInt())
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
