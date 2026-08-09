package com.network24.player.features.player.state

import com.network24.player.features.live.models.LiveChannel

object PlayerState {

    /**
     * Current channel playlist.
     */
    val channels = mutableListOf<LiveChannel>()

    /**
     * Currently selected channel position.
     */
    var currentPosition: Int = 0

    /**
     * Returns current channel or null.
     */
    fun currentChannel(): LiveChannel? {

        return if (
            currentPosition in channels.indices
        ) {
            channels[currentPosition]
        } else {
            null
        }

    }

    /**
     * Next channel.
     */
    /**
     * Next channel (with Looping).
     */
    fun next(): LiveChannel? {
        if (channels.isEmpty()) return null

        if (currentPosition < channels.lastIndex) {
            currentPosition++
        } else {
            // Agar aakhri channel par hain, toh wapas pehle (0) par aa jayein
            currentPosition = 0
        }
        return currentChannel()
    }

    /**
     * Previous channel (with Looping).
     */
    fun previous(): LiveChannel? {
        if (channels.isEmpty()) return null

        if (currentPosition > 0) {
            currentPosition--
        } else {
            // Agar pehle channel par hain, toh wapas aakhri par chale jayein
            currentPosition = channels.lastIndex
        }
        return currentChannel()
    }


    /**
     * Clear playlist.
     */
    fun clear() {

        channels.clear()

        currentPosition = 0

    }

}