package com.bitmovin.player.integration.nielsen.mediametrie.model

import com.bitmovin.player.integration.nielsen.mediametrie.utils.Constants.LIVE_STREAM_LENGTH_SECONDS
import com.bitmovin.player.integration.nielsen.mediametrie.utils.MediametrieStreamingType
import org.json.JSONObject

public data class NielsenMetadata(
    val type: String,
    val assetId: String,
    val program: String,
    val title: String,
    val length: Double?, // The Source's duration is expected. Said duration will be converted to Nielsen's SDK format
    val isLivestn: Boolean, // A true/false value is expected. Said value will be converted to the "y", "n" values expected by Nielsen's SDK
    val cli_md: MediametrieStreamingType?,
    val cli_ch: String?,
    val subbrand: String?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        put("assetid", assetId)
        put("program", program)
        put("title", title)
        length?.let {
            val safeLength = when {
                it.isNaN() || it.isInfinite() || it <= 0.0 -> LIVE_STREAM_LENGTH_SECONDS
                else -> it.toInt()
            }
            put("length", safeLength)
        }
        put("islivestn", if (isLivestn) "y" else "n")
        putOpt("cli_md", cli_md?.name)
        putOpt("cli_ch", cli_ch)
        putOpt("subbrand", subbrand)
    }
}