package org.wxyc.wxycapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import org.wxyc.wxycapp.data.Playcut
import org.wxyc.wxycapp.data.PlaycutMetadata
import org.wxyc.wxycapp.data.StreamingService

/**
 * Bottom sheet displaying detailed information about a playcut
 * Includes artwork, metadata, streaming links, and external info links
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaycutDetailSheet(
    playcut: Playcut,
    artworkUrl: String?,
    metadata: PlaycutMetadata,
    isLoadingMetadata: Boolean,
    onDismiss: () -> Unit,
    onStreamingServiceTapped: (StreamingService) -> Unit = {},
    onExternalLinkTapped: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = androidx.compose.ui.graphics.Color.White,
        dragHandle = null,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = 28.dp,
            topEnd = 28.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .drawBehind {
                    val gradient = androidx.compose.ui.graphics.Brush.verticalGradient(
                        0.0f to androidx.compose.ui.graphics.Color(0xFF7E85C1),
                        0.08f to androidx.compose.ui.graphics.Color(0xFF7E85C1),
                        0.66f to androidx.compose.ui.graphics.Color(0xFFE27DB2),
                        0.72f to androidx.compose.ui.graphics.Color(0xFFE98C8C),
                        1.0f to androidx.compose.ui.graphics.Color(0xFFE6A1BF),
                        startY = 0f,
                        endY = size.height
                    )
                    drawRect(brush = gradient)
                }
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header: Artwork and basic info
            PlaycutHeaderSection(
                playcut = playcut,
                artworkUrl = artworkUrl
            )
            
            // Metadata section (label, year, bio)
            if (isLoadingMetadata) {
                LoadingSection()
            } else if (metadata.label != null || metadata.releaseYear != null || metadata.artistBio != null) {
                PlaycutMetadataSection(metadata = metadata)
            }
            
            // Streaming links
            if (metadata.hasStreamingLinks || !isLoadingMetadata) {
                StreamingLinksSection(
                    metadata = metadata,
                    isLoading = isLoadingMetadata,
                    onServiceTapped = onStreamingServiceTapped
                )
            }
            
            // External links (Discogs, Wikipedia)
            if (metadata.discogsURL != null || metadata.wikipediaURL != null) {
                ExternalLinksSection(
                    metadata = metadata,
                    onLinkTapped = onExternalLinkTapped
                )
            }
            
            // Bottom spacing
            Spacer(modifier = Modifier.height(40.dp))
        }
        }
    }
}

@Composable
private fun LoadingSection() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            CircularProgressIndicator(
                color = androidx.compose.ui.graphics.Color.White
            )
        }
    }
}
