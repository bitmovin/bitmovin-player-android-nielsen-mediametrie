package com.bitmovin.player.integration.nielsen.mediametrie.api

import android.content.Context
import com.bitmovin.player.integration.nielsen.mediametrie.createNielsenSdk
import com.bitmovin.player.integration.nielsen.mediametrie.internal.DefaultBitmovinNielsenAnalytics
import com.bitmovin.player.integration.nielsen.mediametrie.internal.Logger
import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenAppInformation
import com.nielsen.app.sdk.IAppNotifier

public object BitmovinNielsenAnalyticsFactory {
    public fun create(
        context: Context,
        appInformation: NielsenAppInformation,
        notifier: IAppNotifier? = null
    ): Result<BitmovinNielsenAnalytics> {
        return createNielsenSdk(context, appInformation, notifier).map { sdk ->
            DefaultBitmovinNielsenAnalytics(
                appSdk = sdk,
                logger = Logger(appInformation.debugLogging)
            )
        }
    }
}
