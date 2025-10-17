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
import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenMetadata
import com.bitmovin.player.integration.nielsen.mediametrie.utils.MediametrieStreamingType
import android.util.Log
import com.bitmovin.player.api.advertising.Ad
import com.bitmovin.player.api.advertising.vast.VastAdData
import kotlinx.coroutines.Job
import org.junit.Assert.*
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

    private val sampleNielsenMetadata = NielsenMetadata(
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
        
        tracker.attachTo(player) { _, _ -> sampleNielsenMetadata }
    }


    private fun simulateSourceLoaded() {
        // Simulate SourceEvent.Loaded by triggering the listener directly
        val mockSourceLoaded = mockk<SourceEvent.Loaded> {
            every { source.duration } returns 600.0
        }
        tracker.sourceLoadedListener.invoke(mockSourceLoaded)
    }

    private fun simulateSourceLoaded(tracker: NielsenPlayerTracker, isLive: Boolean, duration: Double, metadata: NielsenMetadata) {
        // Simulate SourceEvent.Loaded by triggering the listener directly
        val mockSourceLoaded = mockk<SourceEvent.Loaded> {
            every { source.duration } returns duration
        }
        tracker.sourceLoadedListener.invoke(mockSourceLoaded)
    }

    @Test
    fun `startTracking should load metadata, send play and schedule playhead`() = testScope.runTest {
        simulateSourceLoaded()
        verify { sdk.loadMetadata(any<JSONObject>()) }
        verify { sdk.play(any<JSONObject>()) }

        advanceTimeBy(1_000)
        verify { sdk.setPlayheadPosition(42L) }
        tracker.stopTracking()
    }

    @Test
    fun `startTracking called twice returns early`() = testScope.runTest {
        // Tracker is already configured in setUp()
        clearMocks(sdk, answers = false)
        verify(exactly = 0) { sdk.loadMetadata(any<JSONObject>()) }
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
    fun `resume should call sdk play when in CONTENT state`() = testScope.runTest {
        simulateSourceLoaded()
        clearMocks(sdk)
        tracker.resume()

        advanceTimeBy(1_000)

        verify { sdk.setPlayheadPosition(42L) }
        tracker.stopTracking()
    }

    @Test
    fun `resume does nothing when not in CONTENT state`() = testScope.runTest {
        clearMocks(sdk)
        tracker.resume()
        verify(exactly = 0) { sdk.setPlayheadPosition(any()) }
    }

    @Test
    fun `resume while playhead already active returns early`() = testScope.runTest {
        tracker.resume()
        tracker.stopTracking()
    }


    @Test
    fun `reconnect should reload metadata and resume play when in CONTENT state`() = testScope.runTest {
        simulateSourceLoaded()
        clearMocks(sdk)

        tracker.reconnect()

        verify { sdk.loadMetadata(any<JSONObject>()) }
        advanceTimeBy(1_000)
        verify { sdk.setPlayheadPosition(42L) }
        tracker.stopTracking()
    }

    @Test
    fun `reconnect should log error if metadata is null`() = testScope.runTest {
        tracker = NielsenPlayerTracker(sdk, testScope)
        tracker.contentMetadataProvider = null
        clearMocks(sdk)

        tracker.reconnect()

        verify(exactly = 0) { sdk.loadMetadata(any<JSONObject>()) }
        verify(exactly = 0) { sdk.play(any<JSONObject>()) }
    }

    @Test
    fun `reconnect in CONTENT with null metadata logs and does nothing`() = testScope.runTest {
        tracker.contentMetadataProvider = null
        clearMocks(sdk)

        tracker.reconnect()

        verify(exactly = 0) { sdk.loadMetadata(any<JSONObject>()) }
        verify(exactly = 0) { sdk.setPlayheadPosition(any()) }
        tracker.stopTracking()
    }

    @Test
    fun `reconnect does nothing when not in CONTENT`() = testScope.runTest {
        clearMocks(sdk)
        tracker.reconnect()
        verify(exactly = 0) { sdk.loadMetadata(any<JSONObject>()) }
        verify(exactly = 0) { sdk.setPlayheadPosition(any()) }
    }


    @Test
    fun `seeked should restart playhead job`() {
        simulateSourceLoaded()

        val initialJob = tracker.playheadJob
        assertNotNull("Expected playheadJob to be non‐null after startTracking()", initialJob)
        assertTrue("Initial playheadJob should be active", initialJob!!.isActive)

        val seekListener = tracker.seekedListener
        seekListener.invoke(mockk(relaxed = true))

        val newJob = tracker.playheadJob
        assertNotNull("Expected a new playheadJob after seek", newJob)

        assertFalse("Old playheadJob should be cancelled on seek", initialJob.isActive)
        assertTrue("New playheadJob should be active", newJob!!.isActive)
        assertNotSame("Should have a different Job instance after seek", initialJob, newJob)
    }

    @Test
    fun `timeShifted should restart playhead job for live streams`() {
        // Create a live tracker
        val liveTracker = NielsenPlayerTracker(sdk, testScope)
        liveTracker.attachTo(player) { _, _ ->
            NielsenMetadata(
                type = "content",
                assetId = "live-stream",
                program = "Live Program",
                title = "Live Title",
                length = 0.0,
                isLivestn = true,
                cli_md = MediametrieStreamingType.LIVE,
                cli_ch = "Live Channel",
                subbrand = "Live"
            )
        }
        
        val metadata = NielsenMetadata(
            type = "content",
            assetId = "live-stream",
            program = "Live Program",
            title = "Live Title",
            length = 0.0,
            isLivestn = true,
            cli_md = MediametrieStreamingType.LIVE,
            cli_ch = "Live Channel",
            subbrand = "Live"
        )
        simulateSourceLoaded(liveTracker, true, 0.0, metadata)

        val initialJob = liveTracker.playheadJob
        assertNotNull("Expected playheadJob to be non‐null after startTracking()", initialJob)
        assertTrue("Initial playheadJob should be active", initialJob!!.isActive)

        val timeShiftedListener = liveTracker.timeShiftedListener
        timeShiftedListener.invoke(mockk(relaxed = true))

        val newJob = liveTracker.playheadJob
        assertNotNull("Expected a new playheadJob after timeshift", newJob)

        assertFalse("Old playheadJob should be cancelled on timeshift", initialJob.isActive)
        assertTrue("New playheadJob should be active", newJob!!.isActive)
        assertNotSame("Should have a different Job instance after timeshift", initialJob, newJob)
    }

    @Test
    fun `stallStarted should cancel playhead`() = testScope.runTest {
        simulateSourceLoaded()
        advanceTimeBy(1_000)
        verify { sdk.setPlayheadPosition(42L) }
        val stallListener = tracker.stallStartedListener
        clearMocks(sdk)
        stallListener.invoke(mockk(relaxed = true))

        advanceTimeBy(1_500)
        verify(exactly = 0) { sdk.setPlayheadPosition(any()) }
    }

    @Test
    fun `stallEnded should resume sdk play and start playhead`() = testScope.runTest {
        val stallEndedListener = tracker.stallEndedListener
        clearMocks(sdk)
        stallEndedListener.invoke(mockk(relaxed = true))
        advanceTimeBy(1_000)
        verify { sdk.setPlayheadPosition(42L) }

        tracker.stopTracking()
    }

    @Test
    fun `stallEnded cancels timeout when a stall was active`() = testScope.runTest {
        val stallStarted = tracker.stallStartedListener
        val stallEnded = tracker.stallEndedListener

        stallStarted.invoke(mockk(relaxed = true))
        clearMocks(sdk)
        stallEnded.invoke(mockk(relaxed = true))

        advanceTimeBy(1_000)
        verify { sdk.setPlayheadPosition(42L) }
        tracker.stopTracking()
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
        advanceTimeBy(1_000)
        verify { sdk.setPlayheadPosition(42L) }
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
        verify { sdk.stop() }
        verify { sdk.loadMetadata(any<JSONObject>()) }
        advanceTimeBy(1_000)
        verify { sdk.setPlayheadPosition(42L) }
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
        try {
            adBreakFinishedListener.invoke(mockk(relaxed = true))

            verify(exactly = 0) { sdk.loadMetadata(any<JSONObject>()) }

            advanceTimeBy(1_100)
            verify(exactly = 0) { sdk.setPlayheadPosition(any()) }
        } finally {
            tracker.pause()
        }
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
        tracker.contentMetadataProvider = { _, _ -> sampleNielsenMetadata.toJson() }

        clearMocks(sdk)
        playListener.invoke(mockk(relaxed = true))
        verify { sdk.loadMetadata(any<JSONObject>()) }
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

    @Test
    fun `isLive branch in playhead uses live timestamp`() = testScope.runTest {
        val liveTracker = NielsenPlayerTracker(sdk, testScope)
        liveTracker.attachTo(player) { _, _ ->
            NielsenMetadata(
                type = "content",
                assetId = "video123",
                program = "program",
                title = "title",
                length = 600.0,
                isLivestn = true,
                cli_md = MediametrieStreamingType.LIVE,
                cli_ch = "860",
                subbrand = null
            )
        }
        clearMocks(sdk)
        val metadata = NielsenMetadata(
            type = "content",
            assetId = "video123",
            program = "program",
            title = "title",
            length = 600.0,
            isLivestn = true,
            cli_md = MediametrieStreamingType.LIVE,
            cli_ch = "860",
            subbrand = null
        )
        simulateSourceLoaded(liveTracker, true, 600.0, metadata)
        advanceTimeBy(1_000)
        verify(atLeast = 1) { sdk.setPlayheadPosition(any<Long>()) }
        liveTracker.stopTracking()
    }

    @Test
    fun `sendPlayhead logs error on failure but continues`() = testScope.runTest {
        simulateSourceLoaded()

        clearMocks(sdk)
        every { sdk.setPlayheadPosition(any()) } throws Exception("cancelled")

        advanceTimeBy(1_050)

        verify(atLeast = 1) { sdk.setPlayheadPosition(any<Long>()) }
        tracker.stopTracking()
    }

    @Test
    fun `sendPlayhead continues on repeated failures`() = testScope.runTest {
        simulateSourceLoaded()

        clearMocks(sdk)
        every { sdk.setPlayheadPosition(any()) } throws Exception("cancelled")

        advanceTimeBy(1_050)
        advanceTimeBy(1_050)

        verify(atLeast = 2) { sdk.setPlayheadPosition(any<Long>()) }

        tracker.stopTracking()
    }



    @After
    fun tearDown() {
        tracker.detach()
        testScope.cancel()
        unmockkAll()
    }
}