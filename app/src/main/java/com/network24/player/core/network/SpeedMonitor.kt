package com.network24.player.core.net

import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

object SpeedMonitor {

    // Exponential moving average in bits per second
    private val emaBps = AtomicLong(0L)

    // EMA alpha = 1/4 (0.25) using integer math:
    // ema = (ema * 3 + sample * 1) / 4
    private const val ALPHA_NUM = 1L
    private const val ALPHA_DEN = 4L

    fun reportSample(bytes: Long, durationMs: Long) {
        if (bytes <= 0L || durationMs <= 0L) return

        // bits per second (bps) from bytes and milliseconds:
        // bps = bytes * 8 * 1000 / ms = bytes * 8000 / ms
        val bps = (bytes * 8_000L) / max(1L, durationMs)
        if (bps <= 0L) return

        while (true) {
            val prev = emaBps.get()
            val next = if (prev == 0L) {
                bps
            } else {
                // integer EMA: (prev*(den-num) + bps*num)/den
                ((prev * (ALPHA_DEN - ALPHA_NUM)) + (bps * ALPHA_NUM)) / ALPHA_DEN
            }
            if (emaBps.compareAndSet(prev, next)) break
        }
    }

    fun getBps(): Long = emaBps.get()

    fun getMbps(): Double = emaBps.get() / 1_000_000.0

    fun reset() {
        emaBps.set(0L)
    }
}
