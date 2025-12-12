package com.bitmovin.player.integration.nielsen.mediametrie.metadata

import com.bitmovin.player.integration.nielsen.mediametrie.internal.Logger
import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenContentMetadata
import org.json.JSONObject

internal class ContentMetadataBuilder(
    private val logger: Logger
) {
    private var overrides: NielsenContentMetadata = NielsenContentMetadata()
    private var metadata: NielsenContentMetadata = NielsenContentMetadata()

    fun setOverrides(metadataOverrides: NielsenContentMetadata) {
        overrides = metadataOverrides
    }

    fun update(transform: (NielsenContentMetadata) -> NielsenContentMetadata) {
        metadata = transform(metadata)
    }

    fun resetDynamicValues() {
        metadata = NielsenContentMetadata()
    }

    fun build(): JSONObject {
        val merged = NielsenContentMetadata(
            type = overrides.type ?: metadata.type ?: "content",
            assetId = overrides.assetId ?: metadata.assetId,
            program = overrides.program ?: metadata.program,
            title = overrides.title ?: metadata.title,
            length = overrides.length ?: metadata.length,
            isLivestn = overrides.isLivestn ?: metadata.isLivestn,
            subbrand = overrides.subbrand ?: metadata.subbrand,
            cli_md = overrides.cli_md ?: metadata.cli_md,
            cli_ch = overrides.cli_ch ?: metadata.cli_ch,
            cli_cn = overrides.cli_cn ?: metadata.cli_cn,
            nol_p0 = overrides.nol_p0 ?: metadata.nol_p0,
            nol_p1 = overrides.nol_p1 ?: metadata.nol_p1,
            nol_p2 = overrides.nol_p2 ?: metadata.nol_p2,
            nol_p3 = overrides.nol_p3 ?: metadata.nol_p3,
            nol_p4 = overrides.nol_p4 ?: metadata.nol_p4,
            nol_p5 = overrides.nol_p5 ?: metadata.nol_p5,
            nol_p6 = overrides.nol_p6 ?: metadata.nol_p6,
            nol_p7 = overrides.nol_p7 ?: metadata.nol_p7,
            nol_p8 = overrides.nol_p8 ?: metadata.nol_p8,
            nol_p9 = overrides.nol_p9 ?: metadata.nol_p9,
            nol_p10 = overrides.nol_p10 ?: metadata.nol_p10,
            nol_p11 = overrides.nol_p11 ?: metadata.nol_p11,
            nol_p12 = overrides.nol_p12 ?: metadata.nol_p12,
            nol_p13 = overrides.nol_p13 ?: metadata.nol_p13,
            nol_p14 = overrides.nol_p14 ?: metadata.nol_p14,
            nol_p15 = overrides.nol_p15 ?: metadata.nol_p15,
            nol_p16 = overrides.nol_p16 ?: metadata.nol_p16,
            nol_p17 = overrides.nol_p17 ?: metadata.nol_p17,
            nol_p18 = overrides.nol_p18 ?: metadata.nol_p18,
            nol_p19 = overrides.nol_p19 ?: metadata.nol_p19,
        )

        logMissingMetadata(merged)

        return merged.toJson()
    }

    private fun logMissingMetadata(metadata: NielsenContentMetadata) {
        if (metadata.assetId == null) {
            logger.warn(MISSING_METADATA_MESSAGE_PREFIX + "assetId")
        }
        if (metadata.program == null) {
            logger.warn(MISSING_METADATA_MESSAGE_PREFIX + "program")
        }
        if (metadata.title == null) {
            logger.warn(MISSING_METADATA_MESSAGE_PREFIX + "title")
        }
        if (metadata.length == null && metadata.isLivestn != true) {
            logger.warn(MISSING_METADATA_MESSAGE_PREFIX + "length")
        }
        if (metadata.isLivestn == null) {
            logger.warn(MISSING_METADATA_MESSAGE_PREFIX + "isLive")
        }
        if (metadata.subbrand == null) {
            logger.warn(MISSING_METADATA_MESSAGE_PREFIX + "subbrand")
        }
    }
}

private const val MISSING_METADATA_MESSAGE_PREFIX = "[BitmovinNielsenAnalytics] Required content metadata missing: "
