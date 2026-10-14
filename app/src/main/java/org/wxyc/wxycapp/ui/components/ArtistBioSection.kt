package org.wxyc.wxycapp.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import org.wxyc.wxycapp.data.bio.BioToken
import org.wxyc.wxycapp.data.bio.DiscogsMarkupParser
import org.wxyc.wxycapp.util.launchIntentSafely

private const val COLLAPSED_LINES = 4

/**
 * The "About the Artist" block: the artist bio with its Discogs markup rendered, collapsed
 * to four lines behind a "Read More" toggle that appears only when the rendered text
 * actually overflows.
 *
 * @param tokens the server's pre-parsed bio, when it has arrived; see [bioTokensToShow].
 */
@Composable
fun ArtistBioSection(bio: String, modifier: Modifier = Modifier, tokens: List<BioToken>? = null) {
    val linkColor = Color.White.copy(alpha = 0.7f)
    val rendered = remember(bio, tokens) { bioTokensToShow(bio, tokens).toBioAnnotatedString(linkColor) }
    // A bio made only of dropped id references renders as nothing; skip the header too.
    if (rendered.isBlank()) return
    var expanded by remember(bio) { mutableStateOf(false) }
    var overflows by remember(bio) { mutableStateOf(false) }
    // Bio links open through launchIntentSafely so a device with no browser gets a toast,
    // not the ActivityNotFoundException the default UriHandler would throw.
    val context = LocalContext.current
    val uriHandler = remember(context) {
        object : UriHandler {
            override fun openUri(uri: String) =
                launchIntentSafely(context, Intent(Intent.ACTION_VIEW, uri.toUri()), "No browser found")
        }
    }

    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "About the Artist",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = rendered,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
                overflow = TextOverflow.Ellipsis,
                // Measured only while collapsed: once expanded nothing overflows, but the
                // toggle must stay so the reader can collapse it again.
                onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow }
            )
            if (overflows) {
                TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                    Text(
                        text = if (expanded) "Show Less" else "Read More",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * The server's [tokens] when there are any, as iOS prefers them; otherwise [bio] parsed
 * with [DiscogsMarkupParser].
 */
fun bioTokensToShow(bio: String, tokens: List<BioToken>?): List<BioToken> =
    tokens?.takeIf { it.isNotEmpty() } ?: DiscogsMarkupParser.parse(bio)

/**
 * Renders parsed bio tokens: bold, italic and underline runs are styled, and artist and URL
 * links become tappable [LinkAnnotation.Url]s drawn underlined in [linkColor].
 */
fun List<BioToken>.toBioAnnotatedString(linkColor: Color): AnnotatedString = buildAnnotatedString {
    val linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    for (token in this@toBioAnnotatedString) {
        when (token) {
            is BioToken.PlainText, is BioToken.LabelName -> append(token.text)
            is BioToken.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(token.text) }
            is BioToken.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(token.text) }
            is BioToken.Underline, is BioToken.UrlLink -> {
                val href = (token as? BioToken.UrlLink)?.href
                if (href != null) {
                    withLink(LinkAnnotation.Url(href, linkStyles)) { append(token.text) }
                } else {
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(token.text) }
                }
            }
            is BioToken.ArtistLink -> withLink(LinkAnnotation.Url(token.url, linkStyles)) { append(token.text) }
        }
    }
}
