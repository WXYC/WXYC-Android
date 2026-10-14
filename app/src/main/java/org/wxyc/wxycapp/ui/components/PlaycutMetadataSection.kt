package org.wxyc.wxycapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.wxyc.wxycapp.data.PlaycutMetadata
import org.wxyc.wxycapp.data.bio.BioToken

/**
 * Metadata section showing label, year, and artist bio. [bioTokens] is the server's
 * pre-parsed bio, preferred over [PlaycutMetadata.artistBio] once it arrives.
 */
@Composable
fun PlaycutMetadataSection(
    metadata: PlaycutMetadata,
    modifier: Modifier = Modifier,
    bioTokens: List<BioToken>? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Label and Year Grid
            if (metadata.label != null || metadata.releaseYear != null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    metadata.label?.let { label ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Label",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        }
                    }
                    
                    metadata.releaseYear?.let { year ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Year",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = year.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
            
            // Artist Bio
            metadata.artistBio?.let { bio -> ArtistBioSection(bio = bio, tokens = bioTokens) }
        }
    }
}
