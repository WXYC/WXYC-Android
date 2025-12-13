package org.wxyc.wxycapp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import org.wxyc.wxycapp.R
import org.wxyc.wxycapp.ui.PlayerUiState

import org.wxyc.wxycapp.ui.components.PlaylistItem
import org.wxyc.wxycapp.ui.components.SpectrumAnalyzerView
import org.wxyc.wxycapp.ui.theme.WXYCTheme
import data.Playcut

import androidx.compose.ui.draw.drawBehind

@Composable
fun PlayerScreen(
    uiState: PlayerUiState,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val gradient = androidx.compose.ui.graphics.Brush.verticalGradient(
                    0.0f to Color(0xFF7E85C1),
                    0.08f to Color(0xFF7E85C1),
                    0.66f to Color(0xFFE27DB2),
                    0.72f to Color(0xFFE98C8C),
                    1.0f to Color(0xFFE6A1BF),
                    startY = 0f,
                    endY = size.height
                )
                drawRect(brush = gradient)
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Playlist
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(262.dp)
                            .padding(horizontal = 16.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        SpectrumAnalyzerView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(75.dp)
                                .align(Alignment.BottomCenter)
                                .padding(8.dp)
                        )
                    }
                    // Header with logo
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.wxyc_lowqual),
                            contentDescription = "WXYC Logo",
                            modifier = Modifier
                                .width(150.dp)
                                .height(125.dp)
                        )
                    }

                    // Controls row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play/Pause button
                        IconButton(
                            onClick = onTogglePlayback,
                            modifier = Modifier.size(70.dp)
                        ) {
                            Image(
                                painter = painterResource(
                                    id = if (uiState.isMuted) R.drawable.play_button else R.drawable.pause_button
                                ),
                                contentDescription = if (uiState.isMuted) "Play" else "Pause",
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Stream visualization
                        Image(
                            painter = painterResource(
                                id = if (uiState.isMuted) R.drawable.stream_inactive_short else R.drawable.stream_active_short
                            ),
                            contentDescription = "Stream status",
                            modifier = Modifier
                                .weight(1f)
                                .height(70.dp)
                            )
                    }
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


