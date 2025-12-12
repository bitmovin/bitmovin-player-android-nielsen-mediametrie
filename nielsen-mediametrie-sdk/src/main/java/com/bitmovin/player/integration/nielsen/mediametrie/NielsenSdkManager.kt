package com.bitmovin.player.integration.nielsen.mediametrie

import android.content.Context
import com.bitmovin.player.integration.nielsen.mediametrie.internal.Logger
import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenAppInformation
import com.nielsen.app.sdk.AppSdk
import com.nielsen.app.sdk.IAppNotifier

/**
 * Factory function to create Nielsen AppSdk instance with proper error handling
 * @param context Application context
 * @param settings Nielsen initialization settings
 * @param appNotifier Optional Nielsen [IAppNotifier] for SDK callbacks
 * @return Result containing AppSdk on success or Throwable on failure
 */
public fun createNielsenSdk(
    context: Context,
    settings: NielsenAppInformation,
    appNotifier: IAppNotifier? = null
): Result<AppSdk> {
    val logger = Logger(settings.debugLogging)
    return try {
        val cfg = settings.toJson()
        val notifier = appNotifier ?: IAppNotifier { positionMs, eventCode, message ->
            logger.debug("Nielsen SDK event $eventCode @ $positionMs → $message")
        }

        val sdk = AppSdk(context.applicationContext, cfg, notifier)
        logger.info("Nielsen SDK initialized successfully")
        Result.success(sdk)
    } catch (t: Throwable) {
        logger.error("Nielsen SDK initialization failed", t)
        Result.failure(t)
    }
}
