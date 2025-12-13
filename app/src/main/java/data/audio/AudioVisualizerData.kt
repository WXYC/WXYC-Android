package data.audio

import kotlin.math.max
import kotlin.math.ln
import kotlin.math.exp

// Constants ported from VisualizerConstants
object VisualizerConstants {
    const val UPDATE_INTERVAL = 0.01
    const val BAR_AMOUNT = 16
    const val HISTORY_LENGTH = 8
    const val MAGNITUDE_LIMIT = 64f
    const val PEAK_HISTORY_SIZE = 6000
}

enum class NormalizationMode {
    NONE,
    EMA,
    CIRCULAR_BUFFER,
    PER_BAND_EMA
}

interface Normalizer {
    fun normalize(values: FloatArray, outputScale: Float)
    fun reset()
}

class NoNormalizer : Normalizer {
    override fun normalize(values: FloatArray, outputScale: Float) {
        // No-op
    }
    override fun reset() {}
}

class EMANormalizer : Normalizer {
    private var runningPeak = 0.001f
    private val peakDecay = 0.99983f
    private val normalizationFloor = 0.001f

    override fun normalize(values: FloatArray, outputScale: Float) {
        var currentPeak = 0f
        for (v in values) {
            if (v > currentPeak) currentPeak = v
        }

        runningPeak = max(currentPeak, runningPeak * peakDecay)
        runningPeak = max(runningPeak, normalizationFloor)

        for (i in values.indices) {
            values[i] = (values[i] / runningPeak) * outputScale
        }
    }

    override fun reset() {
        runningPeak = normalizationFloor
    }
}

class PerBandEMANormalizer(bandCount: Int = VisualizerConstants.BAR_AMOUNT) : Normalizer {
    private val runningPeaks = FloatArray(bandCount) { 0.001f }
    private val peakDecay = 0.9997f
    private val normalizationFloor = 0.001f

    override fun normalize(values: FloatArray, outputScale: Float) {
        val count = minOf(values.size, runningPeaks.size)

        for (i in 0 until count) {
            runningPeaks[i] = max(values[i], runningPeaks[i] * peakDecay)
            runningPeaks[i] = max(runningPeaks[i], normalizationFloor)
            
            values[i] = (values[i] / runningPeaks[i]) * outputScale
        }
    }

    override fun reset() {
        for (i in runningPeaks.indices) {
            runningPeaks[i] = normalizationFloor
        }
    }
}

// Global visualizer state holder
object AudioVisualizerState {
    var fftMagnitudes: FloatArray = FloatArray(0)
}
