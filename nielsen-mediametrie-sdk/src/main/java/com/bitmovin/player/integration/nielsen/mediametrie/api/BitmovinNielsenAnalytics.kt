package com.bitmovin.player.integration.nielsen.mediametrie.api

import com.bitmovin.player.api.Player
import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenChannelMetadata
import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenContentMetadata

public interface BitmovinNielsenAnalytics {
    public fun setContentMetadata(metadata: NielsenContentMetadata)
    public fun setChannelMetadata(metadata: NielsenChannelMetadata)
    public fun attach(player: Player)
    public fun detach()
    public fun pause()
    public fun end()
}
