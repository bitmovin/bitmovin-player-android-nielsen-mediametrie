package com.bitmovin.player.integration.nielsen.mediametrie

import com.bitmovin.player.api.Player
import com.bitmovin.player.api.advertising.Ad
import com.bitmovin.player.api.advertising.vast.VastAdData
import com.bitmovin.player.api.event.PlayerEvent
import com.bitmovin.player.api.event.SourceEvent
import com.bitmovin.player.api.source.Source
import com.bitmovin.player.integration.nielsen.mediametrie.internal.Logger
import com.bitmovin.player.integration.nielsen.mediametrie.metadata.ChannelMetadataBuilder
import com.bitmovin.player.integration.nielsen.mediametrie.metadata.ContentMetadataBuilder
import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenChannelMetadata
import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenContentMetadata
import com.bitmovin.player.integration.nielsen.mediametrie.tracking.NielsenPlayerTracker
import com.nielsen.app.sdk.AppSdk
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NielsenPlayerTrackerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var player: Player
    private lateinit var sdk: AppSdk
    private lateinit var tracker: NielsenPlayerTracker
    private lateinit var contentBuilder: ContentMetadataBuilder
    private lateinit var channelBuilder: ChannelMetadataBuilder

    @Before
    fun setup() {
        player = mockk(relaxed = true)
        sdk = mockk(relaxed = true)

        every { player.currentTime } returns 42.0
        every { player.playbackTimeOffsetToAbsoluteTime } returns 5.0
        every { player.duration } returns 60.0

        every {
            player.on(any<kotlin.reflect.KClass<out PlayerEvent>>(), any<Function1<Any, Unit>>())
        } returns Unit
        every { player.off(any<Function1<Any, Unit>>()) } returns Unit

        contentBuilder = ContentMetadataBuilder(Logger(loggingEnabled = false))
        channelBuilder = ChannelMetadataBuilder(Logger(loggingEnabled = false))

        tracker = NielsenPlayerTracker(
            player = player,
            appSdk = sdk,
            contentMetadataBuilder = contentBuilder,
            channelMetadataBuilder = channelBuilder,
            logger = Logger(loggingEnabled = false),
            coroutineScope = testScope
        )

        channelBuilder.setOverrides(NielsenChannelMetadata(channelName = "Demo Channel"))
        contentBuilder.setOverrides(
            NielsenContentMetadata(
                assetId = "Sample Title",
                program = "Sample Title",
                title = "Sample Title",
                subbrand = "Demo Brand"
            )
        )
    }

    @Test
    fun `attach registers player listeners`() {
        tracker.attach()

        // Verify that listeners for the currently supported events are registered
        verify { player.on(PlayerEvent.Paused::class, any()) }
        verify { player.on(PlayerEvent.TimeChanged::class, any()) }
        verify { player.on(PlayerEvent.PlaybackFinished::class, any()) }
        verify { player.on(PlayerEvent.Error::class, any()) }
        verify { player.on(SourceEvent.Loaded::class, any()) }
        verify { player.on(SourceEvent.Unloaded::class, any()) }
        verify { player.on(PlayerEvent.AdStarted::class, any()) }
        verify { player.on(PlayerEvent.AdFinished::class, any()) }
        verify { player.on(PlayerEvent.AdBreakStarted::class, any()) }
        verify { player.on(PlayerEvent.AdBreakFinished::class, any()) }
        verify { player.on(PlayerEvent.StallStarted::class, any()) }
        verify { player.on(PlayerEvent.StallEnded::class, any()) }
        assertTrue(tracker.listenersRegistered)
    }

    @Test
    fun `source load starts tracking with metadata`() = testScope.runTest {
        tracker.sourceLoadedListener.invoke(createSourceLoadedEvent())

        val contentSlot = slot<JSONObject>()
        val channelSlot = slot<JSONObject>()

        verify { sdk.loadMetadata(capture(contentSlot)) }
        verify { sdk.play(capture(channelSlot)) }

        assertEquals("Sample Title", contentSlot.captured.optString("assetid"))
        assertEquals("Demo Channel", channelSlot.captured.optString("channelName"))
    }

    @Test
    fun `ad start switches state to ad and loads metadata`() {
        tracker.adStartedListener.invoke(mockAdStartedEvent())

        verify {
            sdk.loadMetadata(match<JSONObject> { it.optString("type") == "ad" })
        }
    }

    @Test
    fun `ad start with null ad falls back to defaults`() {
        val event = mockk<PlayerEvent.AdStarted>(relaxed = true) {
            every { ad } returns null
        }

        tracker.adStartedListener.invoke(event)

        verify {
            sdk.loadMetadata(match<JSONObject> {
                it.optString("type") == "ad" &&
                    it.optString("assetid") == "ad-unknown" &&
                    it.optString("title") == "Unknown Ad" &&
                    it.optString("program") == "Unknown Ad"
            })
        }
    }

    @Test
    fun `ad start with null vast fields uses fallbacks`() {
        val vast = mockk<VastAdData>(relaxed = true) {
            every { adTitle } returns null
            every { adDescription } returns null
        }
        val ad = mockk<Ad>(relaxed = true) {
            every { id } returns "vast-123"
            every { data } returns vast
        }
        val event = mockk<PlayerEvent.AdStarted>(relaxed = true) {
            every { this@mockk.ad } returns ad
        }

        tracker.adStartedListener.invoke(event)

        verify {
            sdk.loadMetadata(match<JSONObject> {
                it.optString("assetid") == "vast-123" &&
                    it.optString("title") == "Unknown Ad" &&
                    it.optString("program") == "Unknown Ad"
            })
        }
    }

    @Test
    fun `pause stops tracking while content plays`() = testScope.runTest {
        tracker.sourceLoadedListener.invoke(createSourceLoadedEvent())
        tracker.pause()

        verify { sdk.stop() }
    }

    @Test
    fun `pause ignored when idle`() {
        tracker.pause()
        verify(exactly = 0) { sdk.stop() }
    }

    @Test
    fun `stall timeout triggers sdk stop`() = testScope.runTest {
        tracker.stallStartedListener.invoke(mockk(relaxed = true))
        advanceTimeBy(30_001)
        verify { sdk.stop() }
    }

    @Test
    fun `time changed forwards playhead position`() {
        tracker.timeChangedListener.invoke(mockk(relaxed = true))
        verify { sdk.setPlayheadPosition(47) }
    }

    @Test
    fun `ad break finished resumes content metadata`() = testScope.runTest {
        tracker.sourceLoadedListener.invoke(createSourceLoadedEvent())
        tracker.adBreakStartedListener.invoke(mockk(relaxed = true))
        tracker.adBreakFinishedListener.invoke(mockk(relaxed = true))

        verify(atLeast = 2) { sdk.loadMetadata(any<JSONObject>()) }
    }

    @Test
    fun `ad break started ignored when not tracking content`() = testScope.runTest {
        tracker.adBreakStartedListener.invoke(mockk(relaxed = true))
        verify(exactly = 0) { sdk.stop() }
    }

    @Test
    fun `detach ends tracking and unregisters listeners`() {
        tracker.attach()
        tracker.detach()

        verify { sdk.end() }
        verify { player.off(tracker.sourceLoadedListener) }
        verify { player.off(tracker.sourceUnloadedListener) }
        verify { player.off(tracker.pauseListener) }
        verify { player.off(tracker.timeChangedListener) }
        verify { player.off(tracker.finishedListener) }
        verify { player.off(tracker.errorListener) }
        verify { player.off(tracker.adStartedListener) }
        verify { player.off(tracker.adFinishedListener) }
        verify { player.off(tracker.adBreakStartedListener) }
        verify { player.off(tracker.adBreakFinishedListener) }
        verify { player.off(tracker.stallStartedListener) }
        verify { player.off(tracker.stallEndedListener) }
    }

    @Test
    fun `reconnect reloads metadata when tracking content`() = testScope.runTest {
        tracker.sourceLoadedListener.invoke(createSourceLoadedEvent())
        clearMocks(sdk, answers = false)

        tracker.reconnect()

        verify(exactly = 1) { sdk.loadMetadata(any<JSONObject>()) }
    }

    @Test
    fun `reconnect ignored when tracker idle`() {
        tracker.reconnect()

        verify(exactly = 0) { sdk.loadMetadata(any<JSONObject>()) }
    }

    @Test
    fun `source unloaded ends tracking`() = testScope.runTest {
        tracker.sourceLoadedListener.invoke(createSourceLoadedEvent())
        clearMocks(sdk, answers = false)

        tracker.sourceUnloadedListener.invoke(mockk(relaxed = true))

        verify { sdk.end() }
    }

    @Test
    fun `stall ended cancels timeout`() = testScope.runTest {
        tracker.stallStartedListener.invoke(mockk(relaxed = true))
        tracker.stallEndedListener.invoke(mockk(relaxed = true))
        clearMocks(sdk, answers = false)

        advanceTimeBy(30_100)

        verify(exactly = 0) { sdk.stop() }
    }

    private fun createSourceLoadedEvent(duration: Double = 600.0): SourceEvent.Loaded {
        val source = mockk<Source>(relaxed = true) {
            every { this@mockk.duration } returns duration
        }
        return mockk(relaxed = true) {
            every { this@mockk.source } returns source
        }
    }

    private fun mockAdStartedEvent(): PlayerEvent.AdStarted {
        val ad = mockk<Ad>(relaxed = true) {
            every { id } returns "ad-1"
            every { data } returns mockk<VastAdData>(relaxed = true) {
                every { adTitle } returns "Ad Title"
                every { adDescription } returns "Ad Desc"
            }
        }
        return mockk<PlayerEvent.AdStarted>(relaxed = true) {
            every { this@mockk.ad } returns ad
        }
    }
}