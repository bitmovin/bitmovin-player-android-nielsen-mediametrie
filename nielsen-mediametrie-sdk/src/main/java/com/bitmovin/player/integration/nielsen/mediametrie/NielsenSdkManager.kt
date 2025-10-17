package com.bitmovin.player.integration.nielsen.mediametrie

import android.content.Context
import android.util.Log
import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenInitSettings
import com.nielsen.app.sdk.AppSdk
import com.nielsen.app.sdk.IAppNotifier
import org.json.JSONObject

/**
 * Factory function to create Nielsen AppSdk instance with proper error handling
 * @param context Application context
 * @param settings Nielsen initialization settings
 * @return Result containing AppSdk on success or Throwable on failure
 */
public fun createNielsenSdk(context: Context, settings: NielsenInitSettings): Result<AppSdk> {
    return try {
        val cfg = settings.toJson()
        val notifier = object : IAppNotifier {
            override fun onAppSdkEvent(positionMs: Long, eventCode: Int, message: String?) {
                Log.d("NielsenSdk", "event $eventCode @ $positionMs → $message")
            }
        }

        val sdk = AppSdk(context.applicationContext, cfg, notifier)
        Log.i("NielsenSdk", "Nielsen SDK initialized successfully")
        Result.success(sdk)
    } catch (t: Throwable) {
        Log.e("NielsenSdk", "Nielsen SDK initialization failed", t)
        Result.failure(t)
    }
}