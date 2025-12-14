package org.wxyc.wxycapp.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import org.wxyc.wxycapp.ui.PlayerUiState
import org.wxyc.wxycapp.ui.components.PlaylistItem
import org.wxyc.wxycapp.ui.components.PlayerControls
import org.wxyc.wxycapp.ui.theme.WXYCTheme
import data.Playcut


@Composable
fun PlayerScreen(
    uiState: PlayerUiState,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Playlist
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                item {
                    PlayerControls(
                        isMuted = uiState.isMuted,
                        onTogglePlayback = onTogglePlayback
                    )
                }

                items(
                    items = uiState.playlist,
                    key = { it.id }
                ) { item ->
                    PlaylistItem(item = item)
                }
            }
        }


    }
}

@Preview(showBackground = true)
@Composable
private fun PlayerScreenPreview() {
    WXYCTheme {
        PlayerScreen(
            uiState = PlayerUiState(
                isLoading = false,
                isPlaying = true,
                isMuted = false,
                playlist = listOf(
                    Playcut(
                        id = 1,
                        entryType = "playcut",
                        rotation = "",
                        request = "",
                        songTitle = "Sample Song",
                        labelName = "Label",
                        artistName = "Sample Artist",
                        releaseTitle = "Album",
                        imageURL = "",
                        hour = System.currentTimeMillis(),
                        chronOrderID = 1
                    )
                )
            ),
            onTogglePlayback = {}
        )
    }
}


