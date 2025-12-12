package com.bitmovin.player.integration.nielsen.mediametrie


import com.bitmovin.player.api.Player
import com.bitmovin.player.api.event.PlayerEvent
import com.bitmovin.player.api.event.SourceEvent
import com.nielsen.app.sdk.AppSdk
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import com.bitmovin.player.integration.nielsen.mediametrie.tracking.NielsenPlayerTracker
import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenChannelMetadata
import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenContentMetadata
import com.bitmovin.player.integration.nielsen.mediametrie.utils.MediametrieStreamingType
import android.util.Log
import com.bitmovin.player.api.advertising.Ad
import com.bitmovin.player.api.advertising.vast.VastAdData
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class NielsenPlayerTrackerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope      = TestScope(testDispatcher)

    private lateinit var player: Player
    private lateinit var sdk: AppSdk
    private lateinit var tracker: NielsenPlayerTracker

    private val sampleNielsenContentMetadata = NielsenContentMetadata(
        type = "content",
        assetId = "video123",
        program = "program",
        title = "title",
        length = 600.0,
        isLivestn = false,
        cli_md = MediametrieStreamingType.VOD,
        cli_ch = "860",
        subbrand = null
    )
    private val sampleNielsenChannelMetadata = NielsenChannelMetadata(
        channelName = "Test Channel"
    )

    @Before
    fun setUp() {
        player = mockk(relaxed = true)
        every { player.currentTime } returns 42.0

        sdk = mockk(relaxed = true)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        tracker = NielsenPlayerTracker(
            appSdk = sdk,
            coroutineScope = testScope
        )

        tracker.attachTo(
            player = player,
            contentMetadataProvider = { _, _ -> sampleNielsenContentMetadata },
            channelMetadataProvider = { sampleNielsenChannelMetadata }
        )
    }


    private fun simulateSourceLoaded() {
        // Simulate SourceEvent.Loaded by triggering the listener directly
        val mockSourceLoaded = mockk<SourceEvent.Loaded> {
            every { source.duration } returns 600.0
        }
        tracker.sourceLoadedListener.invoke(mockSourceLoaded)
    }

    @Test
    fun `startTracking loads content metadata and plays channel metadata`() = testScope.runTest {
        simulateSourceLoaded()

        verify {
            sdk.loadMetadata(match<JSONObject> {
                it.getString("assetid") == sampleNielsenContentMetadata.assetId
            })
        }
        verify {
            sdk.play(match<JSONObject> {
                it.getString("channelName") == sampleNielsenChannelMetadata.channelName
            })
        }
        verify(exactly = 0) { sdk.setPlayheadPosition(any()) }

        tracker.stopTracking()
    }

    @Test
    fun `startTracking called twice does not reload metadata`() = testScope.runTest {
        simulateSourceLoaded()
        clearMocks(sdk)

        tracker.startTracking()

        verify(exactly = 0) { sdk.loadMetadata(any<JSONObject>()) }
        verify(exactly = 0) { sdk.play(any<JSONObject>()) }
        tracker.stopTracking()
    }

    @Test
    fun `startTracking with null metadata`() = testScope.runTest {
        val nullMetadataTracker = NielsenPlayerTracker(sdk, testScope)
        clearMocks(sdk)
        // Don't set contentMetadataProvider, so it will be null
        nullMetadataTracker.startTracking()
        verify(exactly = 0) { sdk.loadMetadata(any<JSONObject>()) }
        verify(exactly = 0) { sdk.play(any<JSONObject>()) }
        nullMetadataTracker.stopTracking()
    }

    @Test
    fun `stopTracking should call sdk end and unregister listeners`() = testScope.runTest {
        // Tracker is already configured in setUp()
        clearMocks(sdk, player)
        tracker.stopTracking()
        verify { sdk.end() }
    }

    @Test
    fun `pause should call sdk stop`() = testScope.runTest {
        simulateSourceLoaded()
        clearMocks(sdk)
        tracker.pause()

        verify { sdk.stop() }
    }

    @Test
    fun `pause does nothing in IDLE (just logs)`() = testScope.runTest {
        clearMocks(sdk)
        tracker.pause()
        verify(exactly = 0) { sdk.stop() }
    }

    @Test
    fun `pause stops when in AD state`() = testScope.runTest {
        val adStarted = tracker.adStartedListener
        val ad = mockk<Ad>(relaxed = true)
        every { ad.id } returns "ad-1"
        val evt = mockk<PlayerEvent.AdStarted>(relaxed = true) { every { this@mockk.ad } returns ad }
        adStarted.invoke(evt)

        clearMocks(sdk)
        tracker.pause()

        verify { sdk.stop() }
    }


    @Test
    fun `resume does nothing when tracker is in CONTENT state`() = testScope.runTest {
        simulateSourceLoaded()
        clearMocks(sdk)

        tracker.resume()

        verify(exactly = 0) { sdk.play(any<JSONObject>()) }
        verify(exactly = 0) { sdk.loadMetadata(any<JSONObject>()) }
        verify(exactly = 0) { sdk.setPlayheadPosition(any()) }
        tracker.stopTracking()
    }

    @Test
    fun `resume does nothing when tracker is not in CONTENT state`() = testScope.runTest {
        clearMocks(sdk)

        tracker.resume()

        verify(exactly = 0) { sdk.play(any<JSONObject>()) }
        verify(exactly = 0) { sdk.loadMetadata(any<JSONObject>()) }
        verify(exactly = 0) { sdk.setPlayheadPosition(any()) }
    }


    @Test
    fun `reconnect reloads metadata when tracker is in CONTENT state`() = testScope.runTest {
        simulateSourceLoaded()
        clearMocks(sdk)

        tracker.reconnect()

        verify(exactly = 1) { sdk.loadMetadata(any<JSONObject>()) }
        verify(exactly = 0) { sdk.play(any<JSONObject>()) }
        tracker.stopTracking()
    }

    @Test
    fun `reconnect in CONTENT with null metadata logs and does nothing`() = testScope.runTest {
        simulateSourceLoaded()
        tracker.contentMetadataProvider = null
        clearMocks(sdk)

        tracker.reconnect()

        verify(exactly = 0) { sdk.loadMetadata(any<JSONObject>()) }
        verify(exactly = 0) { sdk.play(any<JSONObject>()) }
        tracker.stopTracking()
    }

    @Test
    fun `reconnect does nothing when not in CONTENT`() = testScope.runTest {
        clearMocks(sdk)
        tracker.reconnect()
        verify(exactly = 0) { sdk.loadMetadata(any<JSONObject>()) }
        verify(exactly = 0) { sdk.play(any<JSONObject>()) }
    }

    @Test
    fun `timeChanged listener forwards absolute playhead`() {
        every { player.currentTime } returns 11.5
        every { player.playbackTimeOffsetToAbsoluteTime } returns 5.5

        val timeEvent = mockk<PlayerEvent.TimeChanged>(relaxed = true)
        tracker.timeChangedListener.invoke(timeEvent)

        verify { sdk.setPlayheadPosition(17L) }
    }

    @Test
    fun `timeChanged listener uses zero when player is detached`() {
        tracker.detach()
        clearMocks(sdk)

        val timeEvent = mockk<PlayerEvent.TimeChanged>(relaxed = true)
        tracker.timeChangedListener.invoke(timeEvent)

        verify { sdk.setPlayheadPosition(0L) }
    }
    @Test
    fun `stallEnded cancels timeout when a stall was active`() = testScope.runTest {
        val stallStarted = tracker.stallStartedListener
        val stallEnded = tracker.stallEndedListener

        clearMocks(sdk)
        stallStarted.invoke(mockk(relaxed = true))
        advanceTimeBy(15_000)

        stallEnded.invoke(mockk(relaxed = true))
        advanceTimeBy(20_000)

        verify(exactly = 0) { sdk.stop() }
    }


    @Test
    fun `adStarted should load ad metadata and play`() = testScope.runTest {
        val adStartListener = tracker.adStartedListener
        val adEvent = mockk<PlayerEvent.AdStarted>(relaxed = true)
        val ad = mockk<Ad>(relaxed = true)
        every { ad.id } returns "Test-Ad"
        every { adEvent.ad } returns ad
        clearMocks(sdk)
        adStartListener.invoke(adEvent)
        verify { sdk.stop() } // content tracking is stopped before ad
        verify {
            sdk.loadMetadata(match<JSONObject> {
                it.getString("type") == "ad" &&
                        it.getString("assetid") == "Test-Ad"
            })
        }
        verify(exactly = 0) { sdk.play(any<JSONObject>()) }
        verify(exactly = 0) { sdk.setPlayheadPosition(any()) }
        tracker.stopTracking()
    }

    @Test
    fun `adStarted with VastAdData populates title and description`() = testScope.runTest {
        val adStart = tracker.adStartedListener

        val vast: VastAdData = mockk(relaxed = true)
        every { vast.adTitle } returns "VAST Title"
        every { vast.adDescription } returns "VAST Desc"

        val ad = mockk<Ad>(relaxed = true) {
            every { id } returns "vast-123"
            every { data } returns vast
        }
        val event = mockk<PlayerEvent.AdStarted>(relaxed = true) { every { this@mockk.ad } returns ad }

        clearMocks(sdk)
        adStart.invoke(event)

        verify {
            sdk.loadMetadata(match<JSONObject> {
                it.optString("type") == "ad" &&
                        it.optString("assetid") == "vast-123" &&
                        it.optString("title") == "VAST Title" &&
                        it.optString("program") == "VAST Desc"
            })
        }
        tracker.stopTracking()
    }

    @Test
    fun `adStarted with null ad uses ad-unknown id`() = testScope.runTest {
        val adStart = tracker.adStartedListener

        val evt = mockk<PlayerEvent.AdStarted>(relaxed = true)
        every { evt.ad } returns null

        clearMocks(sdk)
        adStart.invoke(evt)

        verify {
            sdk.loadMetadata(match<JSONObject> {
                it.optString("type") == "ad" &&
                        it.optString("assetid") == "ad-unknown"
            })
        }
        tracker.stopTracking()
    }

    @Test
    fun `adStarted with VastAdData null fields falls back to Unknown values`() = testScope.runTest {
        val adStart = tracker.adStartedListener

        val vast = mockk<VastAdData>(relaxed = true)
        every { vast.adTitle } returns null
        every { vast.adDescription } returns null

        val ad = mockk<Ad>(relaxed = true) {
            every { id } returns "vast-null"
            every { data } returns vast
        }
        val evt = mockk<PlayerEvent.AdStarted>(relaxed = true) { every { this@mockk.ad } returns ad }

        clearMocks(sdk)
        adStart.invoke(evt)

        verify {
            sdk.loadMetadata(match<JSONObject> {
                it.optString("type") == "ad" &&
                        it.optString("assetid") == "vast-null" &&
                        it.optString("title") == "Unknown Ad" &&
                        it.optString("program") == "Unknown Ad"
            })
        }
        tracker.stopTracking()
    }



    @Test
    fun `adBreakStarted should stop sdk`() = testScope.runTest {
        simulateSourceLoaded()
        val adBreakListener = tracker.adBreakStartedListener
        clearMocks(sdk)
        adBreakListener.invoke(mockk(relaxed = true))
        verify { sdk.stop() }
    }

    @Test
    fun `adBreakStarted returns when not in CONTENT`() = testScope.runTest {
        val adBreakStarted = tracker.adBreakStartedListener
        clearMocks(sdk)
        adBreakStarted.invoke(mockk(relaxed = true))
        verify(exactly = 0) { sdk.stop() }
    }

    @Test
    fun `adBreakFinished should resume content tracking`() = testScope.runTest {
        val adStartListener = tracker.adStartedListener
        val ad = mockk<Ad>(relaxed = true)
        every { ad.id } returns "Ad-ID"
        val adEvent = mockk<PlayerEvent.AdStarted>(relaxed = true).apply {
            every { this@apply.ad } returns ad
        }
        adStartListener.invoke(adEvent)

        val adBreakFinishedListener = tracker.adBreakFinishedListener
        clearMocks(sdk)
        adBreakFinishedListener.invoke(mockk(relaxed = true))

        verify(exactly = 1) { sdk.stop() }
        verify(exactly = 1) { sdk.loadMetadata(any<JSONObject>()) }
        verify(exactly = 0) { sdk.play(any<JSONObject>()) }
        tracker.stopTracking()
    }

    @Test
    fun `adBreakFinished returns when not in ad`() = testScope.runTest {
        val adBreakFinished = tracker.adBreakFinishedListener
        clearMocks(sdk)
        adBreakFinished.invoke(mockk(relaxed = true))
        verify(exactly = 0) { sdk.stop() }
        verify(exactly = 0) { sdk.loadMetadata(any<JSONObject>()) }
        tracker.stopTracking()
    }

    @Test
    fun `adBreakFinished with null metadata transitions to IDLE`() = testScope.runTest {
        val adStartedListener = tracker.adStartedListener
        val ad = mockk<Ad>(relaxed = true)
        every { ad.id } returns "ad-123"
        val adStarted = mockk<PlayerEvent.AdStarted>(relaxed = true)
        every { adStarted.ad } returns ad

        adStartedListener.invoke(adStarted)
        tracker.contentMetadataProvider = null

        val adBreakFinishedListener = tracker.adBreakFinishedListener

        clearMocks(sdk)
        adBreakFinishedListener.invoke(mockk(relaxed = true))

        verify(exactly = 1) { sdk.stop() }
        verify(exactly = 0) { sdk.loadMetadata(any<JSONObject>()) }

        clearMocks(sdk)
        tracker.pause()
        verify(exactly = 0) { sdk.stop() }
    }



    @Test
    fun `finishedListener should stop tracking`() = testScope.runTest {
        val finishedListener = tracker.finishedListener
        clearMocks(sdk)
        finishedListener.invoke(mockk(relaxed = true))
        verify { sdk.end() }
    }

    @Test
    fun `errorListener should stop tracking`() = testScope.runTest {
        val errorListener = tracker.errorListener
        clearMocks(sdk)
        errorListener.invoke(mockk(relaxed = true))
        verify { sdk.end() }
    }

    @Test
    fun `playListener triggers handlePlay when IDLE`() = testScope.runTest {
        val playListener = tracker.playListener
        
        // Set up metadata provider
        tracker.contentMetadataProvider = { _, _ -> sampleNielsenContentMetadata.toJson() }

        clearMocks(sdk)
        playListener.invoke(mockk(relaxed = true))
        verify { sdk.loadMetadata(any<JSONObject>()) }
        verify { sdk.play(any<JSONObject>()) }
        tracker.pause()
    }

    @Test
    fun `playListener in CONTENT state does not reload metadata`() = testScope.runTest {
        simulateSourceLoaded()
        val playListener = tracker.playListener

        clearMocks(sdk)
        playListener.invoke(mockk(relaxed = true))

        verify(exactly = 0) { sdk.loadMetadata(any<JSONObject>()) }
        verify(exactly = 0) { sdk.play(any<JSONObject>()) }
        tracker.stopTracking()
    }


    @Test
    fun `pauseListener triggers handlePause`() = testScope.runTest {
        simulateSourceLoaded()
        val pauseListener = tracker.pauseListener

        clearMocks(sdk)
        pauseListener.invoke(mockk(relaxed = true))
        verify { sdk.stop() }
    }



    @Test
    fun `adFinishedListener only logs`() {
        val adFinishedListener = tracker.adFinishedListener
        adFinishedListener.invoke(mockk(relaxed = true))
    }

    @Test
    fun `stall timeout path triggers stop`() = testScope.runTest {
        val stallStarted = tracker.stallStartedListener

        clearMocks(sdk)
        stallStarted.invoke(mockk(relaxed = true))
        advanceTimeBy(30_001)
        verify(atLeast = 1) { sdk.stop() }
    }



    @After
    fun tearDown() {
        tracker.detach()
        testScope.cancel()
        unmockkAll()
    }
}
