package com.bitmovin.player.integration.nielsen.mediametrie.internal

import com.bitmovin.player.api.Player
import com.bitmovin.player.integration.nielsen.mediametrie.api.BitmovinNielsenAnalytics
import com.bitmovin.player.integration.nielsen.mediametrie.metadata.ChannelMetadataBuilder
import com.bitmovin.player.integration.nielsen.mediametrie.metadata.ContentMetadataBuilder
import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenChannelMetadata
import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenContentMetadata
import com.bitmovin.player.integration.nielsen.mediametrie.tracking.NielsenPlayerTracker
import com.nielsen.app.sdk.AppSdk

internal class DefaultBitmovinNielsenAnalytics(
    private val appSdk: AppSdk,
    private val logger: Logger
) : BitmovinNielsenAnalytics {

    private val contentMetadataBuilder = ContentMetadataBuilder(logger)
    private val channelMetadataBuilder = ChannelMetadataBuilder(logger)

    private var tracker: NielsenPlayerTracker? = null

    override fun setContentMetadata(metadata: NielsenContentMetadata) {
        contentMetadataBuilder.setOverrides(metadata)
    }

    override fun setChannelMetadata(metadata: NielsenChannelMetadata) {
        channelMetadataBuilder.setOverrides(metadata)
    }

    override fun attach(player: Player) {
        if (tracker != null) {
            logger.warn("Attempted to attach a second Player instance. Detach the current one first.")
            return
        }

        tracker = NielsenPlayerTracker(
            player = player,
            appSdk = appSdk,
            contentMetadataBuilder = contentMetadataBuilder,
            channelMetadataBuilder = channelMetadataBuilder,
            logger = logger
        ).also { it.attach() }
    }

    override fun detach() {
        tracker?.detach()
        tracker = null
    }

    override fun pause() {
        tracker?.pause()
    }

    override fun end() {
        tracker?.stopTracking()
    }
}
