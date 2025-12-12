package com.bitmovin.player.integration.nielsen.mediametrie.model

import org.json.JSONObject

public data class NielsenChannelMetadata(
    val channelName: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        putOpt("channelName", channelName)
    }
}
