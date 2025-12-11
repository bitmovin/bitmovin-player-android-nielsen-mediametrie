package com.bitmovin.player.integration.nielsen.mediametrie

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bitmovin.player.api.Player
import com.bitmovin.player.api.PlayerConfig
import com.bitmovin.player.api.source.SourceConfig
import org.json.JSONObject
import com.bitmovin.player.integration.nielsen.mediametrie.tracking.NielsenPlayerTracker
import com.bitmovin.player.integration.nielsen.mediametrie.utils.MediametrieStreamingType
import com.bitmovin.player.api.advertising.AdvertisingConfig
import com.bitmovin.player.api.advertising.AdItem
import com.bitmovin.player.api.advertising.AdSource
import com.bitmovin.player.api.advertising.AdSourceType
import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenContentMetadata
import com.nielsen.app.sdk.AppSdk


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

    private var nielsenTracker: NielsenPlayerTracker? = null
    private var contentMetadata: JSONObject? = null
    
    private val nielsenSdk: AppSdk? get() = (getApplication() as? NielsenSdkProvider)?.nielsenSdk
    
    init {
        val sourceUrl = "https://storage.googleapis.com/shaka-demo-assets/bbb-dark-truths-hls/hls.m3u8"
//        val sourceUrl = "https://storage.googleapis.com/shaka-live-assets/player-source.mpd"
        val sourceConfig = SourceConfig.fromUrl(sourceUrl)

        val playerConfig = PlayerConfig(
            key = BuildConfig.PLAYER_KEY,
            advertisingConfig = AdvertisingConfig(preRollAd) // just pre-roll
        )

        player = Player.create(app, playerConfig)

        nielsenSdk?.let { sdk ->
            nielsenTracker = NielsenPlayerTracker(sdk, viewModelScope)
            
            nielsenTracker?.attachTo(player!!) { isLive, duration ->
        
                if (!isLive) {
                    player?.scheduleAd(midRollAd)
                    player?.scheduleAd(postRollAd)
                }

                val streamType = if (isLive) MediametrieStreamingType.LIVE else MediametrieStreamingType.VOD

                NielsenContentMetadata(
                    type = "content",
                    assetId = "video123",
                    program = "My Program Title",
                    title = "My Program Title",
                    length = if (duration.isFinite() && duration > 0) duration else null,
                    isLivestn = isLive,
                    cli_md = streamType,
                    cli_ch = "my-channel",
                    subbrand = "my-subbrand"
                )
            }
            
            Log.d("PlaybackViewModel", "Nielsen Tracker attached to player with auto-start")
        } ?: run {
            Log.w("PlaybackViewModel", "Nielsen SDK not available, tracking disabled")
        }

        player?.load(sourceConfig)
    }

    fun setContentMetadata(metadata: JSONObject) {
        contentMetadata = metadata
        Log.d("PlaybackViewModel", "Content metadata set: $contentMetadata")
    }

    override fun onCleared() {
        super.onCleared()
        nielsenTracker?.detach()
        player?.destroy()
    }

    fun resumeSdk() {
        nielsenTracker?.resume()
    }

    fun pauseSdk() {
        nielsenTracker?.pause()
    }

    fun endSdk() {
        nielsenTracker?.stopTracking()
    }
}
