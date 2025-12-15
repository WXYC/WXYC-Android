package org.wxyc.wxycapp.data.metadata

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * Simplified Discogs markup parser for Android
 * Converts Discogs formatting syntax to Compose AnnotatedString
 * 
 * For now, implements basic formatting without ID resolution
 * Full implementation with DiscogsEntityResolver can be added later
 */
object DiscogsMarkupParser {
    
    /**
     * Parses Discogs markup to AnnotatedString
     * Supports: [b]bold[/b], [i]italic[/i], [u]underline[/u]
     * Artist/label/URL tags are displayed as plain text for now
     */
    fun parse(text: String): AnnotatedString = buildAnnotatedString {
        var remaining = text
        
        while (remaining.isNotEmpty()) {
            // Find next tag
            val tagStart = remaining.indexOf('[')
            if (tagStart == -1) {
                append(remaining)
                break
            }
            
            // Append text before tag
            if (tagStart > 0) {
                append(remaining.substring(0, tagStart))
                remaining = remaining.substring(tagStart)
            }
            
            val tagEnd = remaining.indexOf(']')
            if (tagEnd == -1) {
                append(remaining)
                break
            }
            
            val tag = remaining.substring(1, tagEnd)
            remaining = remaining.substring(tagEnd + 1)
            
            when {
                tag == "b" -> {
                    // Bold text
                    val closeTag = remaining.indexOf("[/b]")
                    if (closeTag != -1) {
                        val content = remaining.substring(0, closeTag)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(content)
                        }
                        remaining = remaining.substring(closeTag + 4)
                    }
                }
                tag == "i" -> {
                    // Italic text
                    val closeTag = remaining.indexOf("[/i]")
                    if (closeTag != -1) {
                        val content = remaining.substring(0, closeTag)
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(content)
                        }
                        remaining = remaining.substring(closeTag + 4)
                    }
                }
                tag == "u" -> {
                    // Underlined text
                    val closeTag = remaining.indexOf("[/u]")
                    if (closeTag != -1) {
                        val content = remaining.substring(0, closeTag)
                        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                            append(content)
                        }
                        remaining = remaining.substring(closeTag + 4)
                    }
                }
                tag.startsWith("a=") -> {
                    // Artist name tag - just display the name
                    val artistName = tag.substring(2)
                    append(stripDisambiguationSuffix(artistName))
                }
                tag.startsWith("l=") -> {
                    // Label name tag - just display the name
                    val labelName = tag.substring(2)
                    append(labelName)
                }
                tag.startsWith("url=") -> {
                    // URL tag - find closing tag and display link text
                    val closeTag = remaining.indexOf("[/url]")
                    if (closeTag != -1) {
                        val linkText = remaining.substring(0, closeTag)
                        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                            append(linkText)
                        }
                        remaining = remaining.substring(closeTag + 6)
                    }
                }
                // Skip ID-based tags and unknown tags
                else -> {
                    // Unknown/unsupported tag - skip it
                }
            }
        }
    }
    
    /**
     * Removes Discogs disambiguation suffix like " (8)" from artist names
     * e.g., "Salamanda (8)" becomes "Salamanda"
     */
    private fun stripDisambiguationSuffix(name: String): String {
        val pattern = Regex(""" \(\d+\)$""")
        return pattern.replace(name, "")
    }
}
