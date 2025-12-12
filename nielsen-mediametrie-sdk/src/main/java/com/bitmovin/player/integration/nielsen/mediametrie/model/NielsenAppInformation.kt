package com.bitmovin.player.integration.nielsen.mediametrie.model

import org.json.JSONObject

public data class NielsenAppInformation(
    val appId: String,
    val optOut: Boolean,
    val appName: String? = null,
    val appVersion: String? = null,
    val hemSha1: String? = null,
    val uid2: String? = null,
    val hemSha256: String? = null,
    val enableFpid: Boolean? = null,
    val debugLogging: Boolean = false
) {
    public fun toJson(): JSONObject = JSONObject().apply {
        put("appid", appId)
        put("optout", optOut)
        appName?.let { put("appname", it) }
        appVersion?.let { put("appversion", it) }
        hemSha1?.let { put("hem_sha1", it) }
        uid2?.let { put("uid2", it) }
        hemSha256?.let { put("hem_sha256", it) }
        enableFpid?.let { put("enableFpid", it) }
        if (debugLogging) {
            put("nol_devDebug", "DEBUG")
        }
    }
}

@Deprecated(
    message = "Use NielsenAppInformation instead",
    replaceWith = ReplaceWith("NielsenAppInformation")
)
public typealias NielsenInitSettings = NielsenAppInformation
