package com.bitmovin.player.integration.nielsen.mediametrie

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bitmovin.player.integration.nielsen.mediametrie.ui.PlayerView

@Composable
fun PlaybackScreen(vm: PlaybackViewModel = viewModel()) {
    Box(Modifier.fillMaxSize()) {
        PlayerView(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            player = vm.player
        )
    }
}