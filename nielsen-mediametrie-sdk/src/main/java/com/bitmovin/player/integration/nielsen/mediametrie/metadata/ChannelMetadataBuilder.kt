package com.bitmovin.player.integration.nielsen.mediametrie.metadata

import com.bitmovin.player.integration.nielsen.mediametrie.internal.Logger
import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenChannelMetadata
import org.json.JSONObject

internal class ChannelMetadataBuilder(
    private val logger: Logger
) {
    private var overrides: NielsenChannelMetadata = NielsenChannelMetadata()
    private var metadata: NielsenChannelMetadata = NielsenChannelMetadata()

    fun setOverrides(metadataOverrides: NielsenChannelMetadata) {
        overrides = metadataOverrides
    }

    fun update(transform: (NielsenChannelMetadata) -> NielsenChannelMetadata) {
        metadata = transform(metadata)
    }

    fun resetDynamicValues() {
        metadata = NielsenChannelMetadata()
    }

    fun build(): JSONObject {
        val merged = NielsenChannelMetadata(
            channelName = overrides.channelName ?: metadata.channelName
        )

        if (merged.channelName == null) {
            logger.warn(MISSING_METADATA_MESSAGE_PREFIX + "channelName")
        }

        return merged.toJson()
    }
}

private const val MISSING_METADATA_MESSAGE_PREFIX = "[BitmovinNielsenAnalytics] Required channel metadata missing: "
