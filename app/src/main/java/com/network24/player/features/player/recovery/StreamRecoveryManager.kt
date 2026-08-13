package com.network24.player.features.player.recovery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StreamRecoveryManager(
    private val scope: CoroutineScope,
    private val onAttempt: (Int) -> Unit,
    private val onRetry: () -> Unit,
    private val onFailed: () -> Unit
) {

    companion object {
        private const val RECOVERY_WINDOW_MS = 30000L
    }

    private var recoveryJob: Job? = null
    private var recoveryStartedAtMs = 0L
    private var recoveryAttempt = 0

    fun start() {
        if (recoveryJob?.isActive == true) return

        if (recoveryStartedAtMs == 0L) {
            recoveryStartedAtMs = System.currentTimeMillis()
        }

        if (System.currentTimeMillis() - recoveryStartedAtMs >= RECOVERY_WINDOW_MS) {
            onFailed()
            cancel()
            return
        }

        recoveryAttempt++
        onAttempt(recoveryAttempt)

        val delayTime = when (recoveryAttempt) {
            1 -> 3000L
            2 -> 5000L
            else -> 7000L
        }

        recoveryJob = scope.launch {
            delay(delayTime)
            onRetry()
            start()
        }
    }

    fun reset() {
        recoveryAttempt = 0
        recoveryStartedAtMs = 0L
    }

    fun cancel() {
        recoveryJob?.cancel()
        recoveryJob = null
        recoveryStartedAtMs = 0L
    }

    fun currentAttempt(): Int = recoveryAttempt
}
