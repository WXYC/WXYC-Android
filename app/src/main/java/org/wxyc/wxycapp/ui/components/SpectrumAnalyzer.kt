package org.wxyc.wxycapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import data.audio.AudioVisualizerState
import data.audio.VisualizerConstants

@Composable
fun SpectrumAnalyzerView(
    modifier: Modifier = Modifier,
    barCount: Int = VisualizerConstants.BAR_AMOUNT, // 16
    segmentsPerBar: Int = 8,
    spacing: Float = 8f
) {
    val magnitudes by AudioVisualizerState.fftMagnitudes.collectAsState()

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        val totalSpacing = (barCount - 1) * spacing
        val barWidth = (width - totalSpacing) / barCount
        val segmentHeight = (height - (segmentsPerBar - 1) * 2f) / segmentsPerBar // 2f gap
        
        // Colors
        val activeColorStart = Color(0xFFFFFF00) // Yellow
        val activeColorEnd = Color(0xFFFF0000)   // Red
        val inactiveColor = Color.DarkGray.copy(alpha = 0.3f)

        for (i in 0 until barCount) {
            val magnitude = if (i < magnitudes.size) magnitudes[i] else 0f
            // Normalize magnitude to 0..1 based on limit (64f from constants)
            // But magnitudes are already normalized to limit in processor? 
            // Processor: normalizer.normalize(magnitudes, VisualizerConstants.MAGNITUDE_LIMIT)
            // So magnitude is 0..64.
            
            val normalizedMag = (magnitude / VisualizerConstants.MAGNITUDE_LIMIT).coerceIn(0f, 1f)
            val activeSegments = (normalizedMag * segmentsPerBar).toInt()

            val x = i * (barWidth + spacing)

            for (j in 0 until segmentsPerBar) {
                // j=0 is bottom
                val y = height - ((j + 1) * segmentHeight + j * 2f)
                
                val isActive = j < activeSegments
                
                val color = if (isActive) {
                    val fraction = j.toFloat() / segmentsPerBar
                     // Simple linear lerp manually or just constant
                     // Let's use a solid yellow for now as per "LCD" description usually implies single color or simple gradient
                     // iOS had gradient? "Gradient color and glow". 
                     // Let's interpolate Yellow to Red
                     androidx.compose.ui.graphics.lerp(activeColorStart, activeColorEnd, fraction)
                } else {
                    inactiveColor
                }

                drawRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, segmentHeight)
                )
            }
        }
    }
}
