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

        verify { player.on(PlayerEvent.Play::class, tracker.playListener) }
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
    fun `detach ends tracking and unregisters listeners`() {
        tracker.attach()
        tracker.detach()

        verify { sdk.end() }
        verify { player.off(tracker.playListener) }
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
