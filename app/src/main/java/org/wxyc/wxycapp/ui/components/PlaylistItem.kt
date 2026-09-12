package org.wxyc.wxycapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.wxyc.wxycapp.R
import org.wxyc.wxycapp.ui.theme.SoftWhite
import org.wxyc.wxycapp.ui.theme.WXYCTheme
import org.wxyc.wxycapp.data.Playcut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info

@Composable
fun PlaylistItem(
    item: Playcut,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    // "track" is the only row type with a detail sheet (see PlayerScreen); every
    // other entry_type (show_start/show_end/dj_join/dj_leave/talkset/breakpoint/
    // message) is a marker row rendered from the server's own label text.
    when (item.entryType) {
        "track" -> SongItem(playcut = item, modifier = modifier, onClick = onClick)
        else -> MarkerItem(text = item.displayMessage ?: item.entryType.uppercase(), modifier = modifier)
    }
}

@Composable
private fun SongItem(
    playcut: Playcut,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp), // iOS padding 12.0
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(0.4f)
                    .aspectRatio(1f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)) // iOS cornerRadius 6.0
                    .background(Color.Gray.copy(alpha = 0.3f))
            ) {
                // Background logo (always shown) - White VectorDrawable
                Image(
                    painter = painterResource(id = R.drawable.wxyc_logo_white),
                    contentDescription = "WXYC Logo",
                    modifier = Modifier
                        .matchParentSize()
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )
                
                // Album art overlay (shown when available)
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(playcut.imageURL?.takeIf { it.isNotEmpty() })
                        .crossfade(true)
                        .build(),
                    contentDescription = "Album art for ${playcut.songTitle}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(0.6f)
            ) {
                Text(
                    text = playcut.songTitle ?: "",
                    color = Color.White, // iOS .foregroundStyle(.white)
                    fontSize = 17.sp, // Approximate SwiftUI Body size
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = playcut.artistName ?: "",
                    color = Color.White, // iOS .foregroundStyle(.white)
                    fontSize = 15.sp, // Slightly smaller than title but distinct
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Info indicator
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Outlined.Info,
                contentDescription = "Details",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .padding(start = 8.dp, end = 4.dp)
                    .size(24.dp) // Standard icon size
            )
        }
    }
}

@Composable
private fun MarkerItem(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = androidx.compose.foundation.shape.CircleShape
                )
                .padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun SongItemPreview() {
    WXYCTheme {
        SongItem(
            playcut = Playcut(
                id = 1,
                entryType = "track",
                hour = System.currentTimeMillis(),
                playOrder = 1,
                rotation = "",
                songTitle = "Sample Song Title",
                labelName = "Sample Label",
                artistName = "Sample Artist",
                releaseTitle = "Sample Album",
                imageURL = ""
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun MarkerItemPreview() {
    WXYCTheme {
        MarkerItem(text = "4:00 PM Breakpoint")
    }
}
