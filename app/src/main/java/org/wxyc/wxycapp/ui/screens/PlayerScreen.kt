package org.wxyc.wxycapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import org.wxyc.wxycapp.ui.PlayerUiState
import org.wxyc.wxycapp.ui.components.PlaylistItem
import org.wxyc.wxycapp.ui.components.PlayerControls
import org.wxyc.wxycapp.ui.components.PlaycutDetailSheet
import org.wxyc.wxycapp.ui.theme.WXYCTheme
import org.wxyc.wxycapp.data.Playcut
import org.wxyc.wxycapp.data.PlaycutMetadata
import org.wxyc.wxycapp.analytics.PostHogManager
import org.wxyc.wxycapp.analytics.AnalyticsEvents
import org.wxyc.wxycapp.ui.PlayerViewModel




@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val metadata by viewModel.metadata.collectAsState()
    val isLoadingMetadata by viewModel.isLoadingMetadata.collectAsState()
    
    var selectedPlaycut by remember { mutableStateOf<Playcut?>(null) }
    var showDetailSheet by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

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
                PlaylistItem(
                    item = item,
                    onClick = {
                        if (item.entryType == "playcut") {
                            selectedPlaycut = item
                            showDetailSheet = true
                            
                            // Track analytics
                            PostHogManager.capture(
                                AnalyticsEvents.PLAYCUT_DETAIL_VIEW_PRESENTED,
                                mapOf(
                                    "artist" to (item.artistName ?: ""),
                                    "album" to (item.releaseTitle ?: "")
                                )
                            )
                        }
                    }
                )
            }
        }

        // Show error message if present
        uiState.errorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                title = { Text("Error") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("OK")
                    }
                }
            )
        }
    }

    // Show playcut detail sheet
    if (showDetailSheet) {
        selectedPlaycut?.let { playcut ->
            PlaycutDetailSheet(
                playcut = playcut,
                artworkUrl = playcut.imageURL,
                metadata = metadata,
                isLoadingMetadata = isLoadingMetadata,
                onFetchMetadata = { viewModel.fetchMetadata(playcut) },
                onDismiss = {
                    showDetailSheet = false
                    selectedPlaycut = null
                },
                onStreamingServiceTapped = { service ->
                    PostHogManager.capture(
                        AnalyticsEvents.STREAMING_LINK_TAPPED,
                        mapOf(
                            "service" to service.displayName,
                            "artist" to (playcut.artistName ?: ""),
                            "album" to (playcut.releaseTitle ?: "")
                        )
                    )
                },
                onExternalLinkTapped = { url ->
                    PostHogManager.capture(
                        AnalyticsEvents.EXTERNAL_LINK_TAPPED,
                        mapOf(
                            "url" to url,
                            "artist" to (playcut.artistName ?: ""),
                            "album" to (playcut.releaseTitle ?: "")
                        )
                    )
                }
            )
        }
    }
}
