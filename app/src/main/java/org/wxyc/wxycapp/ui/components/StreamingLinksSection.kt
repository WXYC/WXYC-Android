package org.wxyc.wxycapp.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.wxyc.wxycapp.data.PlaycutMetadata
import org.wxyc.wxycapp.data.StreamingService

/**
 * Grid of streaming service buttons
 */
@Composable
fun StreamingLinksSection(
    metadata: PlaycutMetadata,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    onServiceTapped: (StreamingService) -> Unit = {}
) {
    val services = listOf(
        StreamingService.Spotify to metadata.spotifyURL,
        StreamingService.AppleMusic to metadata.appleMusicURL,
        StreamingService.YouTubeMusic to metadata.youtubeMusicURL,
        StreamingService.Bandcamp to metadata.bandcampURL,
        StreamingService.SoundCloud to metadata.soundcloudURL
    )
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Add it to your library",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(200.dp) // Fixed height for 2.5 rows
            ) {
                items(services) { (service, url) ->
                    StreamingServiceButton(
                        service = service,
                        url = url,
                        isLoading = isLoading,
                        onTap = { onServiceTapped(service) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingServiceButton(
    service: StreamingService,
    url: String?,
    isLoading: Boolean,
    onTap: () -> Unit
) {
    val context = LocalContext.current
    val isEnabled = url != null
    
    Button(
        onClick = {
            onTap()
            url?.let { 
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it))
                context.startActivity(intent)
            }
        },
        enabled = isEnabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = service.brandColor.copy(alpha = if (isEnabled) 1f else 0.3f),
            contentColor = Color.White,
            disabledContainerColor = service.brandColor.copy(alpha = 0.3f),
            disabledContentColor = Color.White.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // TODO: Replace with actual icons once exported from iOS
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            
            Text(
                text = service.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
