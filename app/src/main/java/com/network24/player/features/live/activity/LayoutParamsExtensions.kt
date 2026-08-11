package com.network24.player.features.live.activity

import android.view.ViewGroup

/**
 * Safe marginStart bridge for generic ViewGroup.LayoutParams used by the EPG layout.
 * Android's concrete margin params are applied at runtime by the parent container.
 */
var ViewGroup.LayoutParams.marginStart: Int
    get() = (this as? ViewGroup.MarginLayoutParams)?.marginStart ?: 0
    set(value) {
        (this as? ViewGroup.MarginLayoutParams)?.marginStart = value
    }
