package com.bitmovin.player.integration.nielsen.mediametrie

import org.junit.Assert.*
import org.junit.Test
import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenInitSettings

class NielsenInitSettingsTest {

    @Test
    fun `toJson without debugLogging does not include nol_devDebug`() {
        val settings = NielsenInitSettings(
            appId = "MY_APP_ID",
            optOut = true,
            enableFpid = false,
            debugLogging = false
        )

        val json = settings.toJson()
        assertEquals("MY_APP_ID", json.getString("appid"))
        assertTrue(json.getBoolean("optout"))
        assertFalse(json.getBoolean("enableFpid"))
        assertFalse(json.has("nol_devDebug"))
    }

    @Test
    fun `toJson with debugLogging includes nol_devDebug`() {
        val settings = NielsenInitSettings(
            appId = "MY_APP_ID",
            optOut = false,
            enableFpid = true,
            debugLogging = true
        )

        val json = settings.toJson()
        assertEquals("MY_APP_ID",   json.getString("appid"))
        assertFalse(json.getBoolean("optout"))
        assertTrue( json.getBoolean("enableFpid"))
        assertEquals("DEBUG",       json.getString("nol_devDebug"))
    }
}