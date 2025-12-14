package org.wxyc.wxycapp.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.wxyc.wxycapp.data.PlaycutMetadata

/**
 * External information links (Discogs and Wikipedia)
 */
@Composable
fun ExternalLinksSection(
    metadata: PlaycutMetadata,
    modifier: Modifier = Modifier,
    onLinkTapped: (String) -> Unit = {}
) {
    val hasLinks = metadata.discogsURL != null || metadata.wikipediaURL != null
    
    if (!hasLinks) return
    
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
                text = "More Info",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                metadata.discogsURL?.let { url ->
                    ExternalLinkButton(
                        title = "Discogs",
                        url = url,
                        modifier = Modifier.weight(1f),
                        onTap = { onLinkTapped("Discogs") }
                    )
                }
                
                metadata.wikipediaURL?.let { url ->
                    ExternalLinkButton(
                        title = "Wikipedia",
                        url = url,
                        modifier = Modifier.weight(1f),
                        onTap = { onLinkTapped("Wikipedia") }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExternalLinkButton(
    title: String,
    url: String,
    modifier: Modifier = Modifier,
    onTap: () -> Unit
) {
    val context = LocalContext.current
    
    Button(
        onClick = {
            onTap()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.15f),
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        modifier = modifier
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // TODO: Replace with actual icons once exported from iOS
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
