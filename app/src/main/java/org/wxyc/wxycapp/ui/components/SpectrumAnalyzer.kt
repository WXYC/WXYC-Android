package org.wxyc.wxycapp.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import data.audio.AudioVisualizerState
import data.audio.VisualizerConstants
import kotlin.math.min

@Composable
fun SpectrumAnalyzerView(
    modifier: Modifier = Modifier,
    barCount: Int = VisualizerConstants.BAR_AMOUNT, // 16
    segmentsPerBar: Int = 8,
    spacing: Float = 8f
) {
    // Hue: 23.0 / 360.0 ~ 0.0638 => 23 degrees
    // Saturation: 0.75
    // Brightness: 0.9 (Base)
    val containerColor = Color.hsv(23f, 0.75f, 0.9f, 0.16f)
    
    Box(
        modifier = modifier
            .background(containerColor, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .innerShadow(
                color = Color.Black.copy(alpha = 0.5f),
                cornersRadius = 10.dp,
                spread = 2.dp,
                blur = 10.dp
            )
            .padding(vertical = 12.dp, horizontal = 12.dp)
    ) {
        SpectrumAnalyzerCanvas(
            modifier = Modifier.fillMaxSize(),
            barCount = barCount,
            segmentsPerBar = segmentsPerBar
        )
    }
}

@Composable
fun SpectrumAnalyzerCanvas(
    modifier: Modifier,
    barCount: Int,
    segmentsPerBar: Int
) {
    val magnitudes by AudioVisualizerState.fftMagnitudes.collectAsState()

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        val horizontalGap = (width / barCount) * 0.15f
        val totalHorizontalSpacing = (barCount - 1) * horizontalGap
        val barWidth = (width - totalHorizontalSpacing) / barCount
        
        val verticalGap = (height / segmentsPerBar) * 0.15f
        val totalVerticalSpacing = (segmentsPerBar - 1) * verticalGap
        val segmentHeight = (height - totalVerticalSpacing) / segmentsPerBar
        
        // Ensure valid dimensions
        if (barWidth <= 0 || segmentHeight <= 0) return@Canvas
        
        val cornerRadius = min(barWidth, segmentHeight) * 0.2f

        val paint = Paint().asFrameworkPaint()

        for (i in 0 until barCount) {
            val magnitude = if (i < magnitudes.size) magnitudes[i] else 0f
            val normalizedMag = (magnitude / VisualizerConstants.MAGNITUDE_LIMIT).coerceIn(0f, 1f)
            val activeSegments = (normalizedMag * segmentsPerBar).toInt()

            val x = i * (barWidth + horizontalGap)

            for (j in 0 until segmentsPerBar) {
                // j=0 is bottom
                val y = height - ((j + 1) * segmentHeight + j * verticalGap)
                
                val isActive = j < activeSegments
                
                val minBrightness = 0.80f
                val maxBrightness = 1.0f
                val brightnessSpan = maxBrightness - minBrightness
                val progress = j.toFloat() / (segmentsPerBar - 1).coerceAtLeast(1).toFloat()
                val multiplier = maxBrightness - (brightnessSpan * progress)
                
                val activeBaseBrightness = 1.5f 
                val inactiveBaseBrightness = 0.9f 
                
                val baseHue = 23f
                val baseSat = 0.75f
                
                // Color mapping:
                // Active: Using activeBaseBrightness * multiplier. 
                // Since HSV 'value' is max 1.0, we just use 1.0 for active but maybe adjust alpha or sat if needed.
                // Or we can rely on Android's Color.HSVtoColor to handle >1 if we were mapping raw RGB, but Color.hsv doesn't.
                
                val segmentColor = if (isActive) {
                     // Active
                     Color.hsv(baseHue, baseSat, 1.0f, 1.0f)
                } else {
                    // Inactive
                    // Dimmer and slightly transparent to blend
                    Color.hsv(baseHue, baseSat, 0.5f * multiplier, 0.4f) 
                }

                val glowColor = if (isActive) {
                     Color.hsv(baseHue, baseSat, 1.0f, 0.6f)
                } else {
                    Color.Transparent
                }

                drawIntoCanvas { canvas ->
                    // Draw Glow
                    if (isActive) {
                        paint.color = glowColor.toArgb()
                        paint.maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
                        canvas.nativeCanvas.drawRoundRect(
                            x, y, x + barWidth, y + segmentHeight,
                            cornerRadius, cornerRadius,
                            paint
                        )
                    }

                    // Draw Segment
                    paint.maskFilter = null
                    paint.color = segmentColor.toArgb()
                    canvas.nativeCanvas.drawRoundRect(
                        x, y, x + barWidth, y + segmentHeight,
                        cornerRadius, cornerRadius,
                        paint
                    )
                }
            }
        }
    }
}

fun Modifier.innerShadow(
    color: Color = Color.Black,
    cornersRadius: androidx.compose.ui.unit.Dp = 0.dp,
    spread: androidx.compose.ui.unit.Dp = 0.dp,
    blur: androidx.compose.ui.unit.Dp = 0.dp,
    offsetY: androidx.compose.ui.unit.Dp = 0.dp,
    offsetX: androidx.compose.ui.unit.Dp = 0.dp
) = drawWithContent {
    drawContent()
    val shadowBlur = blur.toPx()
    val shadowSpread = spread.toPx()
    val corner = cornersRadius.toPx()
    
    drawIntoCanvas { canvas ->
        val paint = Paint().asFrameworkPaint()
        paint.color = color.toArgb()
        paint.maskFilter = BlurMaskFilter(shadowBlur, BlurMaskFilter.Blur.NORMAL)
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = shadowBlur // Draw stroke on edge
        
        canvas.nativeCanvas.drawRoundRect(
            0f, 0f, 
            size.width, size.height, 
            corner, corner, 
            paint
        )
    }
}
