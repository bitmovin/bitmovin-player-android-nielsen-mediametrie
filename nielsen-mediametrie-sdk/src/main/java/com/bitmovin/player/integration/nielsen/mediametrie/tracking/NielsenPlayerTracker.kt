package com.bitmovin.player.integration.nielsen.mediametrie.tracking

import com.bitmovin.player.api.Player
import com.bitmovin.player.api.advertising.Ad
import com.bitmovin.player.api.advertising.vast.VastAdData
import com.bitmovin.player.api.event.PlayerEvent
import com.bitmovin.player.api.event.SourceEvent
import com.bitmovin.player.integration.nielsen.mediametrie.internal.Logger
import com.bitmovin.player.integration.nielsen.mediametrie.metadata.ChannelMetadataBuilder
import com.bitmovin.player.integration.nielsen.mediametrie.metadata.ContentMetadataBuilder
import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenContentMetadata
import com.bitmovin.player.integration.nielsen.mediametrie.utils.MediametrieStreamingType
import com.nielsen.app.sdk.AppSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val STALL_TIMEOUT_MS = 30_000L
private const val DEFAULT_AD_CHANNEL = "Canal Ads"
private const val DEFAULT_AD_SUBBRAND = "Advertisement"

private enum class NielsenState {
    IDLE,
    CONTENT,
    AD
}

internal class NielsenPlayerTracker private constructor(
    private val player: Player,
    private val appSdk: AppSdk,
    private val contentMetadataBuilder: ContentMetadataBuilder,
    private val channelMetadataBuilder: ChannelMetadataBuilder,
    private val logger: Logger,
    private val coroutineScope: CoroutineScope,
    private val ownsScope: Boolean
) {
    constructor(
        player: Player,
        appSdk: AppSdk,
        contentMetadataBuilder: ContentMetadataBuilder,
        channelMetadataBuilder: ChannelMetadataBuilder,
        logger: Logger
    ) : this(
        player = player,
        appSdk = appSdk,
        contentMetadataBuilder = contentMetadataBuilder,
        channelMetadataBuilder = channelMetadataBuilder,
        logger = logger,
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        ownsScope = true
    )

    internal constructor(
        player: Player,
        appSdk: AppSdk,
        contentMetadataBuilder: ContentMetadataBuilder,
        channelMetadataBuilder: ChannelMetadataBuilder,
        logger: Logger,
        coroutineScope: CoroutineScope
    ) : this(
        player = player,
        appSdk = appSdk,
        contentMetadataBuilder = contentMetadataBuilder,
        channelMetadataBuilder = channelMetadataBuilder,
        logger = logger,
        coroutineScope = coroutineScope,
        ownsScope = false
    )

    private var currentState: NielsenState = NielsenState.IDLE
    private var stallTimeoutJob: Job? = null

    internal var listenersRegistered: Boolean = false
        private set

    private var isLiveSource: Boolean = false

    internal val pauseListener: (PlayerEvent.Paused) -> Unit = {
        logger.debug("PlayerEvent: Paused")
        pause()
    }

    internal val timeChangedListener: (PlayerEvent.TimeChanged) -> Unit = {
        val playhead = (player.currentTime + player.playbackTimeOffsetToAbsoluteTime).toLong()
        appSdk.setPlayheadPosition(playhead)
    }

    internal val finishedListener: (PlayerEvent.PlaybackFinished) -> Unit = {
        logger.debug("PlayerEvent: PlaybackFinished")
        stopTracking()
    }

    internal val errorListener: (PlayerEvent.Error) -> Unit = {
        logger.warn("PlayerEvent: Error ${it.message}")
        stopTracking()
    }

    internal val sourceLoadedListener: (SourceEvent.Loaded) -> Unit = {
        logger.debug("SourceEvent: Loaded")
        handleSourceLoaded(it)
    }

    internal val sourceUnloadedListener: (SourceEvent.Unloaded) -> Unit = {
        logger.debug("SourceEvent: Unloaded")
        stopTracking()
    }

    internal val adStartedListener: (PlayerEvent.AdStarted) -> Unit = {
        logger.debug("PlayerEvent: AdStarted")
        handleAdStart(it.ad)
    }

    internal val adFinishedListener: (PlayerEvent.AdFinished) -> Unit = {
        logger.debug("PlayerEvent: AdFinished")
    }

    internal val adBreakStartedListener: (PlayerEvent.AdBreakStarted) -> Unit = {
        logger.debug("PlayerEvent: AdBreakStarted")
        handleAdBreakStarted()
    }

    internal val adBreakFinishedListener: (PlayerEvent.AdBreakFinished) -> Unit = {
        logger.debug("PlayerEvent: AdBreakFinished")
        handleAdBreakFinished()
    }

    internal val stallStartedListener: (PlayerEvent.StallStarted) -> Unit = {
        logger.debug("PlayerEvent: StallStarted")
        stallTimeoutJob?.cancel()
        stallTimeoutJob = coroutineScope.launch {
            delay(STALL_TIMEOUT_MS)
            logger.warn("Stall timeout reached (${STALL_TIMEOUT_MS}ms). Ending Nielsen tracking.")
            appSdk.stop()
        }
    }

    internal val stallEndedListener: (PlayerEvent.StallEnded) -> Unit = {
        logger.debug("PlayerEvent: StallEnded")
        cancelStallTimeout()
    }

    fun attach() {
        if (listenersRegistered) {
            logger.warn("NielsenPlayerTracker already attached to a Player.")
            return
        }
        registerPlayerEvents()
        logger.info("NielsenPlayerTracker attached to Player.")
    }

    fun detach() {
        stopTracking()
        unregisterPlayerEvents()
        cancelStallTimeout()
        if (ownsScope) {
            coroutineScope.cancel()
        }
        logger.info("NielsenPlayerTracker detached from Player.")
    }

    fun pause() {
        if (currentState == NielsenState.CONTENT || currentState == NielsenState.AD) {
            logger.debug("Pausing Nielsen tracking.")
            appSdk.stop()
        } else {
            logger.debug("Pause requested while tracker is idle.")
        }
    }

    fun stopTracking() {
        if (currentState != NielsenState.IDLE) {
            logger.debug("Stopping Nielsen tracking.")
        }
        appSdk.end()
        currentState = NielsenState.IDLE
    }

    fun reconnect() {
        if (currentState != NielsenState.CONTENT) {
            logger.debug("Reconnect ignored because tracker is not tracking content.")
            return
        }
        val metadata = contentMetadataBuilder.build()
        appSdk.loadMetadata(metadata)
        logger.debug("Reconnected Nielsen content tracking.")
    }

    private fun handleSourceLoaded(loaded: SourceEvent.Loaded) {
        val duration = loaded.source.duration
        isLiveSource = duration.isInfinite() || duration <= 0.0

        val fallbackId = "bitmovin-content"
        val streamType = if (isLiveSource) {
            MediametrieStreamingType.LIVE
        } else {
            MediametrieStreamingType.VOD
        }

        contentMetadataBuilder.update { current ->
            current.copy(
                type = "content",
                assetId = current.assetId ?: fallbackId,
                program = current.program ?: fallbackId,
                title = current.title ?: fallbackId,
                length = if (isLiveSource) null else duration,
                isLivestn = isLiveSource,
                cli_md = streamType
            )
        }

        startTracking()
    }

    private fun handleAdBreakStarted() {
        if (currentState != NielsenState.CONTENT) return
        appSdk.stop()
        currentState = NielsenState.AD
    }

    private fun handleAdBreakFinished() {
        if (currentState != NielsenState.AD) return
        resumeContentAfterInterruption()
    }

    private fun resumeContentAfterInterruption() {
        val metadata = contentMetadataBuilder.build()
        appSdk.loadMetadata(metadata)
        currentState = NielsenState.CONTENT
    }

    private fun handleAdStart(ad: Ad?) {
        appSdk.stop()

        val adId = ad?.id ?: "ad-unknown"
        val adData = ad?.data as? VastAdData

        val adTitle = adData?.adTitle ?: "Unknown Ad"
        val adProgram = adData?.adDescription ?: "Unknown Ad"
        val adDuration = player.duration

        val adMetadata = NielsenContentMetadata(
            type = "ad",
            assetId = adId,
            program = adProgram,
            title = adTitle,
            length = adDuration,
            isLivestn = false,
            cli_md = MediametrieStreamingType.AD,
            cli_ch = DEFAULT_AD_CHANNEL,
            subbrand = DEFAULT_AD_SUBBRAND
        )

        appSdk.loadMetadata(adMetadata.toJson())
        currentState = NielsenState.AD
    }

    private fun startTracking() {
        val contentMetadata = contentMetadataBuilder.build()
        val channelMetadata = channelMetadataBuilder.build()
        appSdk.loadMetadata(contentMetadata)
        appSdk.play(channelMetadata)
        currentState = NielsenState.CONTENT
    }

    private fun registerPlayerEvents() {
        with(player) {
            on(SourceEvent.Loaded::class, sourceLoadedListener)
            on(SourceEvent.Unloaded::class, sourceUnloadedListener)
            on(PlayerEvent.Paused::class, pauseListener)
            on(PlayerEvent.TimeChanged::class, timeChangedListener)
            on(PlayerEvent.PlaybackFinished::class, finishedListener)
            on(PlayerEvent.Error::class, errorListener)
            on(PlayerEvent.AdStarted::class, adStartedListener)
            on(PlayerEvent.AdFinished::class, adFinishedListener)
            on(PlayerEvent.AdBreakStarted::class, adBreakStartedListener)
            on(PlayerEvent.AdBreakFinished::class, adBreakFinishedListener)
            on(PlayerEvent.StallStarted::class, stallStartedListener)
            on(PlayerEvent.StallEnded::class, stallEndedListener)
        }
        listenersRegistered = true
    }

    private fun unregisterPlayerEvents() {
        if (!listenersRegistered) return
        with(player) {
            off(sourceLoadedListener)
            off(sourceUnloadedListener)
            off(pauseListener)
            off(timeChangedListener)
            off(finishedListener)
            off(errorListener)
            off(adStartedListener)
            off(adFinishedListener)
            off(adBreakStartedListener)
            off(adBreakFinishedListener)
            off(stallStartedListener)
            off(stallEndedListener)
        }
        listenersRegistered = false
    }

    private fun cancelStallTimeout() {
        stallTimeoutJob?.cancel()
        stallTimeoutJob = null
    }
}
