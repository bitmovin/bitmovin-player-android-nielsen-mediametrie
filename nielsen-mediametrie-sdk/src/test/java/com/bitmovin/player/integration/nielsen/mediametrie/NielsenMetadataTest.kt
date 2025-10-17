package com.bitmovin.player.integration.nielsen.mediametrie

import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenMetadata
import com.bitmovin.player.integration.nielsen.mediametrie.utils.Constants.LIVE_STREAM_LENGTH_SECONDS
import com.bitmovin.player.integration.nielsen.mediametrie.utils.MediametrieStreamingType
import org.junit.Assert.*
import org.junit.Test

class NielsenMetadataTest {

    private fun base(
        type: String = "content",
        assetId: String = "video123",
        program: String = "Program",
        title: String = "Title",
        length: Double? = 600.0,
        isLive: Boolean = false,
        cliMd: MediametrieStreamingType? = MediametrieStreamingType.VOD,
        cliCh: String? = "860",
        subbrand: String? = "MySub"
    ) = NielsenMetadata(
        type = type,
        assetId = assetId,
        program = program,
        title = title,
        length = length,
        isLivestn = isLive,
        cli_md = cliMd,
        cli_ch = cliCh,
        subbrand = subbrand
    )

    @Test
    fun `maps core fields correctly`() {
        val json = base().toJson()
        assertEquals("content", json.getString("type"))
        assertEquals("video123", json.getString("assetid"))
        assertEquals("Program", json.getString("program"))
        assertEquals("Title", json.getString("title"))
    }

    @Test
    fun `isLivestn true becomes y and false becomes n`() {
        val liveJson = base(isLive = true).toJson()
        val vodJson  = base(isLive = false).toJson()
        assertEquals("y", liveJson.getString("islivestn"))
        assertEquals("n", vodJson.getString("islivestn"))
    }

    @Test
    fun `length null is omitted`() {
        val json = base(length = null).toJson()
        assertFalse("length should not be present when null", json.has("length"))
    }

    @Test
    fun `length positive double is truncated to int`() {
        val json = base(length = 120.99).toJson()
        assertTrue(json.has("length"))
        assertEquals(120, json.getInt("length"))
    }

    @Test
    fun `length zero negative NaN and Infinity fallback to LIVE_STREAM_LENGTH_SECONDS`() {
        listOf(0.0, -5.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { bad ->
            val json = base(length = bad).toJson()
            assertTrue(json.has("length"))
            assertEquals(LIVE_STREAM_LENGTH_SECONDS, json.getInt("length"))
        }
    }

    @Test
    fun `cli_md included when non-null and omitted when null`() {
        val withMd = base(cliMd = MediametrieStreamingType.VOD).toJson()
        val noMd   = base(cliMd = null).toJson()
        assertTrue(withMd.has("cli_md"))
        assertEquals(MediametrieStreamingType.VOD.toString(), withMd.get("cli_md"))

        assertFalse("cli_md should be omitted when null", noMd.has("cli_md"))
    }

    @Test
    fun `cli_ch included when non-null and omitted when null`() {
        val withCh = base(cliCh = "860").toJson()
        val noCh   = base(cliCh = null).toJson()

        assertTrue(withCh.has("cli_ch"))
        assertEquals("860", withCh.getString("cli_ch"))
        assertFalse(noCh.has("cli_ch"))
    }

    @Test
    fun `subbrand included when non-null and omitted when null`() {
        val withSub = base(subbrand = "MySub").toJson()
        val noSub   = base(subbrand = null).toJson()

        assertTrue(withSub.has("subbrand"))
        assertEquals("MySub", withSub.getString("subbrand"))
        assertFalse(noSub.has("subbrand"))
    }
}
