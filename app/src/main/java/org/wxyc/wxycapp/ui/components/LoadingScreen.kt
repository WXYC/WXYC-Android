package org.wxyc.wxycapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wxyc.wxycapp.R
import org.wxyc.wxycapp.ui.theme.WXYCTheme

@Composable
fun LoadingScreen(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.wxyc_lowqual),
                contentDescription = "WXYC Logo",
                modifier = Modifier.size(300.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            LinearProgressIndicator(
                modifier = Modifier.width(200.dp),
                color = Color.White,
                trackColor = Color.DarkGray
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingScreenPreview() {
    WXYCTheme {
        LoadingScreen()
    }
}
