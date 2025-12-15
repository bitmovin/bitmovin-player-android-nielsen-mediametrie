package com.bitmovin.player.integration.nielsen.mediametrie.model

import com.bitmovin.player.integration.nielsen.mediametrie.utils.Constants.LIVE_STREAM_LENGTH_SECONDS
import com.bitmovin.player.integration.nielsen.mediametrie.utils.MediametrieStreamingType
import org.json.JSONObject

public data class NielsenContentMetadata(
    val type: String? = null,
    val assetId: String? = null,
    val program: String? = null,
    val title: String? = null,
    val length: Double? = null,
    val isLivestn: Boolean? = null,
    val subbrand: String? = null,
    val cli_md: MediametrieStreamingType? = null,
    val cli_ch: String? = null,
    val cli_cn: String? = null,
    val nol_p0: String? = null,
    val nol_p1: String? = null,
    val nol_p2: String? = null,
    val nol_p3: String? = null,
    val nol_p4: String? = null,
    val nol_p5: String? = null,
    val nol_p6: String? = null,
    val nol_p7: String? = null,
    val nol_p8: String? = null,
    val nol_p9: String? = null,
    val nol_p10: String? = null,
    val nol_p11: String? = null,
    val nol_p12: String? = null,
    val nol_p13: String? = null,
    val nol_p14: String? = null,
    val nol_p15: String? = null,
    val nol_p16: String? = null,
    val nol_p17: String? = null,
    val nol_p18: String? = null,
    val nol_p19: String? = null,
) {
    public fun toJson(): JSONObject = JSONObject().apply {
        putOpt("type", type)
        putOpt("assetid", assetId)
        putOpt("program", program)
        putOpt("title", title)
        length
            ?.takeIf { !it.isNaN() && !it.isInfinite() && it > 0.0 }
            ?.let { put("length", it.toInt()) }
            ?: run {
                if (isLivestn == true) {
                    put("length", LIVE_STREAM_LENGTH_SECONDS)
                }
            }
        isLivestn?.let { put("islivestn", if (it) "y" else "n") }
        putOpt("subbrand", subbrand)
        putOpt("cli_md", cli_md?.name)
        putOpt("cli_ch", cli_ch)
        putOpt("cli_cn", cli_cn)
        putOpt("nol_p0", nol_p0)
        putOpt("nol_p1", nol_p1)
        putOpt("nol_p2", nol_p2)
        putOpt("nol_p3", nol_p3)
        putOpt("nol_p4", nol_p4)
        putOpt("nol_p5", nol_p5)
        putOpt("nol_p6", nol_p6)
        putOpt("nol_p7", nol_p7)
        putOpt("nol_p8", nol_p8)
        putOpt("nol_p9", nol_p9)
        putOpt("nol_p10", nol_p10)
        putOpt("nol_p11", nol_p11)
        putOpt("nol_p12", nol_p12)
        putOpt("nol_p13", nol_p13)
        putOpt("nol_p14", nol_p14)
        putOpt("nol_p15", nol_p15)
        putOpt("nol_p16", nol_p16)
        putOpt("nol_p17", nol_p17)
        putOpt("nol_p18", nol_p18)
        putOpt("nol_p19", nol_p19)
    }
}
