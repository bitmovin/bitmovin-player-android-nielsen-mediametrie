package com.bitmovin.player.integration.nielsen.mediametrie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.bitmovin.player.integration.nielsen.mediametrie.ui.theme.NielsenSampleTheme
import androidx.compose.material3.ExperimentalMaterial3Api

class MainActivity : ComponentActivity() {
    private val vm by viewModels<PlaybackViewModel>()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NielsenSampleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(Modifier.padding(innerPadding)) {
                        PlaybackScreen()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.resumeSdk()
    }

    override fun onPause() {
        super.onPause()
        vm.pauseSdk()
    }

    override fun onDestroy() {
        vm.endSdk()
        super.onDestroy()
    }
}
