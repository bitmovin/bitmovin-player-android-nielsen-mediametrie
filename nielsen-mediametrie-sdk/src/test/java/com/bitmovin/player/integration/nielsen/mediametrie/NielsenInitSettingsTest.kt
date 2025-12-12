package com.bitmovin.player.integration.nielsen.mediametrie

import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenAppInformation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NielsenInitSettingsTest {

    @Test
    fun `toJson without debugLogging does not include nol_devDebug`() {
        val settings = NielsenAppInformation(
            appId = "MY_APP_ID",
            optOut = true,
            enableFpid = null,
            debugLogging = false
        )

        val json = settings.toJson()
        assertEquals("MY_APP_ID", json.getString("appid"))
        assertTrue(json.getBoolean("optout"))
        assertFalse(json.has("enableFpid"))
        assertFalse(json.has("nol_devDebug"))
    }

    @Test
    fun `toJson with debugLogging includes nol_devDebug`() {
        val settings = NielsenAppInformation(
            appId = "MY_APP_ID",
            optOut = false,
            enableFpid = true,
            debugLogging = true,
            appName = "DemoApp",
            appVersion = "1.0.0",
            uid2 = "uid-2",
            hemSha1 = "sha1",
            hemSha256 = "sha256"
        )

        val json = settings.toJson()
        assertEquals("MY_APP_ID",   json.getString("appid"))
        assertFalse(json.getBoolean("optout"))
        assertTrue( json.getBoolean("enableFpid"))
        assertEquals("DEBUG",       json.getString("nol_devDebug"))
        assertEquals("DemoApp",     json.getString("appname"))
        assertEquals("1.0.0",       json.getString("appversion"))
        assertEquals("uid-2",       json.getString("uid2"))
        assertEquals("sha1",        json.getString("hem_sha1"))
        assertEquals("sha256",      json.getString("hem_sha256"))
    }
}
