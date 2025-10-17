package com.bitmovin.player.integration.nielsen.mediametrie.model

import org.json.JSONObject

public data class NielsenInitSettings(
    val appId: String,
    val optOut: Boolean,
    val enableFpid: Boolean,
    val debugLogging: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("appid", appId)
        put("optout", optOut)
        put("enableFpid", enableFpid)
        if (debugLogging) {
            put("nol_devDebug", "DEBUG")
        }
    }
}