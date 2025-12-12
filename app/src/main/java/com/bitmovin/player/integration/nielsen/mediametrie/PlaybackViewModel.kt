package com.bitmovin.player.integration.nielsen.mediametrie

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.bitmovin.player.api.Player
import com.bitmovin.player.api.PlayerConfig
import com.bitmovin.player.api.advertising.AdItem
import com.bitmovin.player.api.advertising.AdSource
import com.bitmovin.player.api.advertising.AdSourceType
import com.bitmovin.player.api.advertising.AdvertisingConfig
import com.bitmovin.player.api.event.SourceEvent
import com.bitmovin.player.api.source.SourceConfig
import com.bitmovin.player.integration.nielsen.mediametrie.api.BitmovinNielsenAnalytics
import com.bitmovin.player.integration.nielsen.mediametrie.api.BitmovinNielsenAnalyticsFactory
import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenAppInformation
import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenChannelMetadata
import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenContentMetadata

class PlaybackViewModel(app: Application) : AndroidViewModel(app) {

    private val skippableAdSource = AdSource(
        type = AdSourceType.Bitmovin,
        tag = "https://pubads.g.doubleclick.net/gampad/ads?sz=640x480&iu=/124319096/external/single_ad_samples&ciu_szs=300x250&impl=s&gdfp_req=1&env=vp&output=vast&unviewed_position_start=1&cust_params=deployment%3Ddevsite%26sample_ct%3Dskippablelinear&correlator="
    )
    private val redirectErrorAdSource = AdSource(
        type = AdSourceType.Ima,
        tag = "https://pubads.g.doubleclick.net/gampad/ads?sz=640x480&iu=/124319096/external/single_ad_samples&ciu_szs=300x250&impl=s&gdfp_req=1&env=vp&output=vast&unviewed_position_start=1&cust_params=deployment%3Ddevsite%26sample_ct%3Dredirecterror&nofb=1&correlator="
    )
    private val linearAdSource = AdSource(
        type = AdSourceType.Ima,
        tag = "https://pubads.g.doubleclick.net/gampad/ads?sz=640x480&iu=/124319096/external/single_ad_samples&ciu_szs=300x250&impl=s&gdfp_req=1&env=vp&output=vast&unviewed_position_start=1&cust_params=deployment%3Ddevsite%26sample_ct%3Dlinear&correlator="
    )
    private val progressiveAdSource = AdSource(
        type = AdSourceType.Progressive,
        tag = "https://bitmovin-a.akamaihd.net/content/testing/ads/testad2s.mp4"
    )

    private val preRollAd = AdItem(skippableAdSource)
    private val midRollAd = AdItem("10%", redirectErrorAdSource, linearAdSource)
    private val postRollAd = AdItem("post", progressiveAdSource)

    var player: Player? = null
        private set

    private var nielsenAnalytics: BitmovinNielsenAnalytics? = null

    init {
        val sourceUrl = "https://storage.googleapis.com/shaka-demo-assets/bbb-dark-truths-hls/hls.m3u8"
        val sourceConfig = SourceConfig.fromUrl(sourceUrl)

        val playerConfig = PlayerConfig(
            key = BuildConfig.PLAYER_KEY,
            advertisingConfig = AdvertisingConfig(preRollAd) // just pre-roll
        )

        player = Player(app, playerConfig)

        player?.on(SourceEvent.Loaded::class) { event ->
            val duration = event.source.duration
            val isLive = duration.isInfinite() || duration <= 0.0
            if (!isLive) {
                player?.scheduleAd(midRollAd)
                player?.scheduleAd(postRollAd)
            }
        }

        val analyticsResult = BitmovinNielsenAnalyticsFactory.create(
            context = app,
            appInformation = NielsenAppInformation(
                appId = "PXXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX",
                optOut = false,
                enableFpid = true,
                debugLogging = BuildConfig.DEBUG
            )
        )

        analyticsResult.fold(
            onSuccess = { analytics ->
                nielsenAnalytics = analytics
                analytics.setContentMetadata(
                    NielsenContentMetadata(
                        assetId = "video123",
                        program = "My Program Title",
                        title = "My Program Title",
                        subbrand = "my-subbrand",
                        cli_ch = "my-channel"
                    )
                )
                analytics.setChannelMetadata(NielsenChannelMetadata("my-channel"))
                player?.let { analytics.attach(it) }
                Log.d("PlaybackViewModel", "Nielsen analytics attached to player")
            },
            onFailure = { error ->
                Log.e("PlaybackViewModel", "Failed to initialize Nielsen analytics", error)
            }
        )

        player?.load(sourceConfig)
    }

    override fun onCleared() {
        super.onCleared()
        nielsenAnalytics?.detach()
        player?.destroy()
    }

    fun pauseSdk() {
        nielsenAnalytics?.pause()
    }

    fun endSdk() {
        nielsenAnalytics?.end()
    }
}
