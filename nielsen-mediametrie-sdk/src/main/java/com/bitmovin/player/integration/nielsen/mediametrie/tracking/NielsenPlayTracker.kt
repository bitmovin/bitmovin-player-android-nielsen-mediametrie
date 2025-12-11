package com.bitmovin.player.integration.nielsen.mediametrie.tracking

import android.util.Log
import com.bitmovin.player.api.Player
import com.bitmovin.player.api.advertising.Ad
import com.bitmovin.player.api.advertising.vast.VastAdData
import com.bitmovin.player.api.event.PlayerEvent
import com.bitmovin.player.api.event.SourceEvent
import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenContentMetadata
import com.bitmovin.player.integration.nielsen.mediametrie.utils.MediametrieStreamingType
import com.nielsen.app.sdk.AppSdk
import kotlinx.coroutines.*
import org.json.JSONObject


// enum to manage the Nielsen tracker states
private enum class NielsenState {
    IDLE,
    CONTENT,
    AD
}

public class NielsenPlayerTracker(
    private val appSdk: AppSdk,
    private val coroutineScope: CoroutineScope // `PlaybackViewModelScope`
) {
    private var player: Player? = null
    private var isLive: Boolean = false
    private var duration: Double = 0.0
    internal var playheadJob: Job? = null
    internal var listenersRegistered = false
    private var currentState: NielsenState = NielsenState.IDLE

    private var stallTimeoutJob: Job? = null
    private val STALL_TIMEOUT_MS = 30000L // 30 seconds

    // Listeners - internal for testing
    internal val playListener: (PlayerEvent.Play) -> Unit = {
        Log.d("NielsenTracker", "PlayerEvent: Play")
        handlePlay()
    }
    internal val pauseListener: (PlayerEvent.Paused) -> Unit = {
        Log.d("NielsenTracker", "PlayerEvent: Paused")
        handlePause()
    }
    internal val seekedListener: (PlayerEvent.Seeked) -> Unit = {
        Log.d("NielsenTracker", "PlayerEvent: Seeked to ${player?.currentTime}")
        handleRepositioning()
    }

    internal val timeShiftedListener: (PlayerEvent.TimeShifted) -> Unit = {
        Log.d("NielsenTracker", "PlayerEvent: TimeShifted to ${player?.currentTime}")
        handleRepositioning()
    }

    internal val finishedListener: (PlayerEvent.PlaybackFinished) -> Unit = {
        Log.d("NielsenTracker", "PlayerEvent: PlaybackFinished")
        handleFinished()
    }
    internal val errorListener: (PlayerEvent.Error) -> Unit = {
        Log.e("NielsenTracker", "PlayerEvent: Error: ${it.message}")
        handleError()
    }
    internal val adStartedListener: (PlayerEvent.AdStarted) -> Unit = {
        Log.d("NielsenTracker", "PlayerEvent: AdStarted")
        handleAdStart(it.ad)
    }
    internal val adFinishedListener: (PlayerEvent.AdFinished) -> Unit = {
        Log.d("NielsenTracker", "PlayerEvent: AdFinished")
        handleAdFinished()
    }

    internal val adBreakStartedListener: (PlayerEvent.AdBreakStarted) -> Unit = {
        Log.d("NielsenTracker", "PlayerEvent: AdBreakStarted")
        handleAdBreakStarted()
    }
    internal val adBreakFinishedListener: (PlayerEvent.AdBreakFinished) -> Unit = {
        Log.d("NielsenTracker", "PlayerEvent: AdBreakFinished")
        handleAdBreakFinished()
    }

    // listener for start buffering
    internal val stallStartedListener: (PlayerEvent.StallStarted) -> Unit = {
        Log.d("NielsenTracker", "PlayerEvent: StallStarted - Buffering started")
        stopSendingPlayhead()

        stallTimeoutJob = coroutineScope.launch {
            delay(STALL_TIMEOUT_MS)
            // Safety timeout: if StallEnded never arrives (e.g., permanent network loss),
            // stop tracking to comply with Nielsen documentation for external interruptions
            Log.e("NielsenTracker", "Stall timeout reached (30s). Stopping Nielsen tracking.")
            appSdk.stop()
        }
    }

    // listener for end buffering
    internal val stallEndedListener: (PlayerEvent.StallEnded) -> Unit = {
        Log.d("NielsenTracker", "PlayerEvent: StallEnded - Buffering ended")
        // Cancel the timeout job, as the stall has ended
        stallTimeoutJob?.cancel()
        stallTimeoutJob = null
        startSendingPlayhead()
    }

    internal val sourceLoadedListener: (SourceEvent.Loaded) -> Unit = {
        Log.d("NielsenTracker", "PlayerEvent: SourceLoaded")
        handleSourceLoaded(it)
    }

    var contentMetadataProvider: ((isLive: Boolean, duration: Double) -> JSONObject?)? = null

    /**
     * Attaches the tracker to a player with automatic start on source load.
     * @param player The Bitmovin Player instance
     * @param metadataProvider Function that provides Nielsen metadata when a source is loaded
     */
    fun attachTo(player: Player, metadataProvider: (isLive: Boolean, duration: Double) -> NielsenContentMetadata) {
        if (this.player != null) {
            Log.w("NielsenPlayerTracker", "Already attached to a player. Detach first.")
            return
        }

        this.player = player
        this.contentMetadataProvider = { isLive, duration ->
            val metadata = metadataProvider(isLive, duration)
            metadata.toJson()
        }
        
        registerPlayerEvents()
        
        Log.d("NielsenPlayerTracker", "Attaching to player with auto-start on source load")
        Log.i("NielsenPlayerTracker", "Successfully attached to player")
    }

    fun detach() {
        if (player == null) {
            Log.w("NielsenPlayerTracker", "Not attached to any player")
            return
        }

        Log.d("NielsenPlayerTracker", "Detaching from player")
        stopTracking()
        player = null
        Log.i("NielsenPlayerTracker", "Successfully detached from player")
    }


    fun startTracking() {
        Log.d("NielsenTracker", "Starting Nielsen tracking.")
        handlePlay()
        Log.d("NielsenTracker", "Tracking started")
    }
    fun stopTracking() {
        if (!listenersRegistered) return

        Log.d("NielsenTracker", "Stopping Nielsen tracking.")
        stopSendingPlayhead()
        unregisterPlayerEvents()
        appSdk.end()
        currentState = NielsenState.IDLE
    }

    fun isTracking(): Boolean = listenersRegistered

    // Method to resume the SDK session (e.g. when returning from background)
    fun reconnect() {
        if (currentState == NielsenState.CONTENT) {
            Log.d("NielsenTracker", "Reconnecting with Nielsen SDK.")
            stopSendingPlayhead() // Ensure there is no active playhead job

            contentMetadataProvider?.invoke(isLive, duration)?.let {
                appSdk.loadMetadata(it)
                startSendingPlayhead()
                Log.d("NielsenTracker", "Reconnected content tracking with metadata: $it")
            } ?: run {
                Log.e("NielsenTracker", "No content metadata available to reconnect tracking")
            }
        }
    }
    fun resume() {
        if (currentState == NielsenState.CONTENT) {
            Log.d("NielsenTracker", "Resuming playback. Sending 'play' event to Nielsen.")
            startSendingPlayhead()
        } else {
            Log.d("NielsenTracker", "Tracker is not in CONTENT state, cannot resume.")
        }
    }

    fun pause() {
        if (currentState == NielsenState.CONTENT || currentState == NielsenState.AD) {
            Log.d("NielsenTracker", "Pausing playback. Sending 'stop' event to Nielsen.")
            stopSendingPlayhead()
            appSdk.stop()
        } else {
            Log.d("NielsenTracker", "Tracker is not in a trackable state, cannot pause.")
        }
    }


    private fun handlePlay() {
        if (currentState == NielsenState.IDLE) {
            contentMetadataProvider?.invoke(isLive, duration)?.let {
                appSdk.loadMetadata(it)
                appSdk.play(JSONObject())
                startSendingPlayhead()
                currentState = NielsenState.CONTENT
                Log.d("NielsenTracker", "Content tracking started with metadata: $it")
            } ?: Log.e("NielsenTracker", "No content metadata available for start tracking")
        }
    }

    private fun handlePause() {
        pause()
    }

    private fun handleRepositioning() {
        // Restart playhead job to send updated position immediately to Nielsen
        stopSendingPlayhead()
        startSendingPlayhead()
    }

    private fun handleFinished() {
        stopTracking()
    }
    private fun handleError() {
        stopTracking()
    }

    private fun handleAdBreakStarted() {
        if (currentState != NielsenState.CONTENT) return

        Log.d("NielsenTracker", "Ad break started. Switching to AD state.")
        stopSendingPlayhead()
        appSdk.stop()
        currentState = NielsenState.AD
    }

    private fun handleAdBreakFinished() {
        if (currentState != NielsenState.AD) return

        Log.d("NielsenTracker", "Ad break finished. Resuming content tracking.")
        stopSendingPlayhead()
        appSdk.stop()

        contentMetadataProvider?.invoke(isLive, duration)?.let {
            appSdk.loadMetadata(it)
            startSendingPlayhead()
            currentState = NielsenState.CONTENT
            Log.d("NielsenTracker", "Resumed content tracking after ad break with metadata: $it")
        } ?: run {
            Log.e("NielsenTracker", "No content metadata available to resume tracking")
            currentState = NielsenState.IDLE
        }
    }
    private fun handleAdStart(ad: Ad?) {
        stopSendingPlayhead()
        appSdk.stop()
        val adId = ad?.id ?: "ad-unknown"
        var adTitle = "Unknown Ad"
        var adDescription = "Unknown Ad"
        val adDuration = player?.duration ?: 0.0

        when (val data = ad?.data) {
            is VastAdData -> {
                adTitle = data.adTitle ?: adTitle
                adDescription = data.adDescription ?: adDescription
            }
            else -> {
                Log.w("NielsenTracker", "Ad data type not recognized: ${data?.javaClass?.simpleName}")
            }
        }

        val adMetadata = NielsenContentMetadata(
            type = "ad",
            assetId = adId,
            program = adDescription,
            title = adTitle,
            length = adDuration,
            isLivestn = false,
            cli_md = MediametrieStreamingType.AD,
            cli_ch = "Canal Ads",
            subbrand = "Advertisement"
        )

        appSdk.loadMetadata(adMetadata.toJson())
        startSendingPlayhead()

        currentState = NielsenState.AD

        Log.d("NielsenTracker", "Ad tracking started with metadata: $adMetadata")
    }

    private fun handleAdFinished() {
        Log.d("NielsenTracker", "An individual ad has finished, waiting for ad break to end.")
    }

    private fun handleSourceLoaded(loaded: SourceEvent.Loaded) {
        val sourceDuration = loaded.source.duration
        val isLive = sourceDuration.isInfinite() || sourceDuration <= 0.0

        Log.d("NielsenTracker", "Source loaded - isLive: $isLive, duration: $sourceDuration")
        
        // Store the stream properties
        this.isLive = isLive
        this.duration = sourceDuration
        
        // Create metadata using the provider
        val metadata = contentMetadataProvider?.invoke(isLive, sourceDuration)
        if (metadata != null) {
            val finalMetadata = metadata
            contentMetadataProvider = { _, _ -> finalMetadata }
            startTracking()
            Log.d("NielsenTracker", "Nielsen tracking started automatically with metadata: $metadata")
        } else {
            Log.e("NielsenTracker", "No metadata provider available for source loaded event")
        }
    }

    private fun registerPlayerEvents() {
        player?.let { p ->
            p.on(SourceEvent.Loaded::class, sourceLoadedListener)
            p.on(PlayerEvent.Play::class, playListener)
            p.on(PlayerEvent.Paused::class, pauseListener)
            p.on(PlayerEvent.Seeked::class, seekedListener)
            p.on(PlayerEvent.TimeShifted::class, timeShiftedListener)
            p.on(PlayerEvent.PlaybackFinished::class, finishedListener)
            p.on(PlayerEvent.Error::class, errorListener)
            p.on(PlayerEvent.AdStarted::class, adStartedListener)
            p.on(PlayerEvent.AdFinished::class, adFinishedListener)
            p.on(PlayerEvent.AdStarted::class, adStartedListener)
            p.on(PlayerEvent.AdFinished::class, adFinishedListener)
            p.on(PlayerEvent.AdBreakStarted::class, adBreakStartedListener)
            p.on(PlayerEvent.AdBreakFinished::class, adBreakFinishedListener)
            p.on(PlayerEvent.StallStarted::class, stallStartedListener)
            p.on(PlayerEvent.StallEnded::class, stallEndedListener)
            listenersRegistered = true
        }
    }

    private fun unregisterPlayerEvents() {
        player?.let { p ->
            p.off(sourceLoadedListener)
            p.off(playListener)
            p.off(pauseListener)
            p.off(seekedListener)
            p.off(timeShiftedListener)
            p.off(finishedListener)
            p.off(errorListener)
            p.off(adStartedListener)
            p.off(adFinishedListener)
            p.off(adBreakStartedListener)
            p.off(adBreakFinishedListener)
            p.off(stallStartedListener)
            p.off(stallEndedListener)
            listenersRegistered = false
        }
    }

    private fun startSendingPlayhead() {
        if (playheadJob?.isActive == true) return

        playheadJob = coroutineScope.launch {
            while (isActive) {
                val playhead = if (isLive) {
                    System.currentTimeMillis() / 1000
                } else {
                    player?.currentTime?.toLong() ?: 0L
                }
                try {
                    appSdk.setPlayheadPosition(playhead)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.d("NielsenTracker", "Playhead tracking failed: ${e.message}")
                }
                delay(1000)
            }
        }
    }

    private fun stopSendingPlayhead() {
        playheadJob?.cancel()
        playheadJob = null
    }

}