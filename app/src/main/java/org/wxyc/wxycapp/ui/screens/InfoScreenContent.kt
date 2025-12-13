package org.wxyc.wxycapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.wxyc.wxycapp.R
import org.wxyc.wxycapp.ui.theme.BlueButton
import org.wxyc.wxycapp.ui.theme.GreenButton
import org.wxyc.wxycapp.ui.theme.RedButton
import org.wxyc.wxycapp.ui.theme.WXYCTheme

@Composable
fun InfoScreenContent(
    onDialDJ: () -> Unit,
    onMakeRequest: (String) -> Unit,
    onSendFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showRequestDialog by remember { mutableStateOf(false) }
    var requestText by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(30.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "You're tuned in.",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(id = R.string.info_description),
                color = Color.White,
                fontSize = 17.sp,
                textAlign = TextAlign.Justify,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(30.dp))

            Button(
                onClick = onDialDJ,
                colors = ButtonDefaults.buttonColors(containerColor = GreenButton),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Dial a DJ", color = Color.White)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { showRequestDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = BlueButton),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Make a Request", color = Color.White)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onSendFeedback,
                colors = ButtonDefaults.buttonColors(containerColor = RedButton),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Send us feedback on the app", color = Color.White)
            }
        }
    }

    if (showRequestDialog) {
        AlertDialog(
            onDismissRequest = {
                showRequestDialog = false
                requestText = ""
            },
            title = { Text("What would you like to request?") },
            text = {
                Column {
                    Text("Please include song title and artist.")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = requestText,
                        onValueChange = { requestText = it },
                        placeholder = { Text("Type your request...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BlueButton,
                            cursorColor = BlueButton
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (requestText.isNotBlank()) {
                            onMakeRequest(requestText.trim())
                            showRequestDialog = false
                            requestText = ""
                        }
                    }
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRequestDialog = false
                        requestText = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InfoScreenContentPreview() {
    WXYCTheme {
        InfoScreenContent(
            onDialDJ = {},
            onMakeRequest = {},
            onSendFeedback = {}
        )
    }
}
