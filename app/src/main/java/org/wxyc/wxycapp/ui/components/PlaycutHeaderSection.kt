package org.wxyc.wxycapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.wxyc.wxycapp.data.Playcut

/**
 * Header section for playcut detail sheet
 * Displays artwork, song title, artist name, and release title
 */
@Composable
fun PlaycutHeaderSection(
    playcut: Playcut,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    onArtworkTap: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Artwork
        if (artworkUrl != null) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = "Album artwork",
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            // Placeholder for missing artwork
            Surface(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(12.dp)),
                color = Color.White.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "No Artwork",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Song info
        Column(
            modifier = Modifier.padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = playcut.songTitle ?: "",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = playcut.artistName ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            playcut.releaseTitle?.let { releaseTitle ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = releaseTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
