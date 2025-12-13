package com.example.basicmusicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.example.basicmusicplayer.R
import com.example.basicmusicplayer.ui.theme.SoftWhite
import com.example.basicmusicplayer.ui.theme.WXYCTheme
import data.PlayCutDetails
import data.PlaylistDetails
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun PlaylistItem(
    item: PlaylistDetails,
    modifier: Modifier = Modifier
) {
    when (item.entryType) {
        "talkset" -> TalksetItem(modifier = modifier)
        "breakpoint" -> BreakpointItem(hour = item.hour, modifier = modifier)
        else -> SongItem(playcut = item.playcut, modifier = modifier)
    }
}

@Composable
private fun SongItem(
    playcut: PlayCutDetails,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(playcut.imageURL.takeIf { !it.isNullOrEmpty() })
                    .crossfade(true)
                    .build(),
                contentDescription = "Album art for ${playcut.songTitle}",
                placeholder = painterResource(id = R.drawable.wxyc_placeholder),
                error = painterResource(id = R.drawable.wxyc_placeholder),
                fallback = painterResource(id = R.drawable.wxyc_placeholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(300.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = playcut.songTitle,
                color = SoftWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = playcut.artistName,
                color = SoftWhite,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TalksetItem(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(
            text = "talkset",
            color = Color(0xFF999999),
            fontSize = 16.sp
        )
    }
}

@Composable
private fun BreakpointItem(
    hour: Long,
    modifier: Modifier = Modifier
) {
    val formattedTime = convertTime(hour)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(
            text = formattedTime,
            color = Color(0xFF999999),
            fontSize = 16.sp
        )
    }
}

private fun convertTime(timestampMillis: Long): String {
    val date = Date(timestampMillis)
    val formatter = SimpleDateFormat("h a", Locale.getDefault())
    formatter.timeZone = TimeZone.getTimeZone("America/New_York")
    return formatter.format(date)
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun SongItemPreview() {
    WXYCTheme {
        SongItem(
            playcut = PlayCutDetails(
                rotation = "",
                request = "",
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
private fun TalksetItemPreview() {
    WXYCTheme {
        TalksetItem()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun BreakpointItemPreview() {
    WXYCTheme {
        BreakpointItem(hour = System.currentTimeMillis())
    }
}
