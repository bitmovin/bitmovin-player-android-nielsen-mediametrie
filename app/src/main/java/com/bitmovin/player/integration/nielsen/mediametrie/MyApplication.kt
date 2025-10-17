package com.bitmovin.player.integration.nielsen.mediametrie

import android.app.Application
import android.util.Log
import com.bitmovin.player.integration.nielsen.mediametrie.model.NielsenInitSettings
import com.nielsen.app.sdk.AppSdk

class MyApplication : Application(), NielsenSdkProvider {
    override var nielsenSdk: AppSdk? = null
        private set
    override fun onCreate() {
        super.onCreate()
        val settings = NielsenInitSettings(
            appId = "PXXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX",
            optOut = false,
            enableFpid = true,
        )

        val sdkResult = createNielsenSdk(this, settings)
        sdkResult.fold(
            onSuccess = { sdk ->
                nielsenSdk = sdk
                Log.i("MyApplication", "Nielsen SDK initialized successfully")
            },
            onFailure = { error ->
                Log.e("MyApplication", "Failed to initialize Nielsen SDK", error)
                nielsenSdk = null
            }
        )
    }
}
