package com.bitmovin.player.integration.nielsen.mediametrie.internal

import android.util.Log

private const val TAG = "BitmovinNielsen"

internal class Logger(
    private val loggingEnabled: Boolean
) {
    fun debug(message: String) {
        if (loggingEnabled) {
            Log.d(TAG, message)
        }
    }

    fun info(message: String) {
        if (loggingEnabled) {
            Log.i(TAG, message)
        }
    }

    fun warn(message: String) {
        if (loggingEnabled) {
            Log.w(TAG, message)
        }
    }

    fun error(message: String, throwable: Throwable? = null) {
        if (loggingEnabled) {
            Log.e(TAG, message, throwable)
        }
    }
}
