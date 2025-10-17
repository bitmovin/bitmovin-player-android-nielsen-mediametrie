package com.bitmovin.player.integration.nielsen.mediametrie

import com.nielsen.app.sdk.AppSdk

public interface NielsenSdkProvider {
    val nielsenSdk: AppSdk?
}
