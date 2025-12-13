package data.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import org.jtransforms.fft.DoubleFFT_1D
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class FftAudioProcessor : BaseAudioProcessor() {

    companion object {
        private const val FFT_SIZE = 2048
        private const val SAMPLE_RATE = 44100 // Default, updated dynamically
    }

    private var sampleRate = SAMPLE_RATE
    private var fftBuffer = FloatArray(FFT_SIZE) // Real input
    private var fftBufferIndex = 0
    
    // Window function (Hann)
    private val window = FloatArray(FFT_SIZE) { i ->
        0.5f * (1 - cos(2.0 * Math.PI * i / (FFT_SIZE - 1))).toFloat()
    }

    // FFT instance
    private var fft: DoubleFFT_1D? = null
    
    // Normalizer
    private val normalizer = PerBandEMANormalizer()
    
    // Bands
    private val bandBoundaries = IntArray(VisualizerConstants.BAR_AMOUNT + 1)
    private val bandGains = FloatArray(VisualizerConstants.BAR_AMOUNT)

    init {
        computeBandBoundariesAndGains()
    }

    private fun computeBandBoundariesAndGains() {
        val fftBins = FFT_SIZE / 2
        val minBin = 2 // Skip DC
        val maxBin = fftBins - 1
        
        val logMin = ln(minBin.toFloat())
        val logMax = ln(maxBin.toFloat())
        
        for (i in 0..VisualizerConstants.BAR_AMOUNT) {
            val logBin = logMin + (logMax - logMin) * i / VisualizerConstants.BAR_AMOUNT
            val bin = exp(logBin).toInt().coerceIn(minBin, maxBin)
            bandBoundaries[i] = bin
        }
        
        // Ensure strictly increasing
        for (i in 1..VisualizerConstants.BAR_AMOUNT) {
            if (bandBoundaries[i] <= bandBoundaries[i-1]) {
                bandBoundaries[i] = bandBoundaries[i-1] + 1
            }
        }
        
        // Gains (Frequency Weighting 1.0)
        for (i in 0 until VisualizerConstants.BAR_AMOUNT) {
            val centerBin = (bandBoundaries[i] + bandBoundaries[i+1]) / 2f
            val freqRatio = centerBin / minBin
            bandGains[i] = freqRatio // exponent 1.0 => linear
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        // Re-init FFT if needed (usually size doesn't change, just rate)
        if (fft == null) {
             fft = DoubleFFT_1D(FFT_SIZE.toLong())
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // If muted, clear visualizer and just pass through audio
        if (AudioVisualizerState.isMuted) {
             AudioVisualizerState.reset()
             replaceOutputBuffer(remaining).put(inputBuffer).flip()
             return
        }

        // Assume PCM 16-bit
        // We need to read samples.
        // Copy to output immediately (passthrough)
        val bufferToProcess = inputBuffer.duplicate()
        bufferToProcess.order(ByteOrder.nativeOrder())
        
        replaceOutputBuffer(remaining).put(inputBuffer).flip()
        
        // Process for FFT
        while (bufferToProcess.remaining() >= 2) {
            val sample = bufferToProcess.short / 32768.0f // Normalize to -1..1
            if (fftBufferIndex < FFT_SIZE) {
                fftBuffer[fftBufferIndex++] = sample
            }
            
            if (fftBufferIndex >= FFT_SIZE) {
                processFFT()
                fftBufferIndex = 0
            }
        }
    }

    private fun processFFT() {
        // 1. Copy and Window
        // JTransforms requires double array of size 2*N for complex or N for real? 
        // realForward takes double[] of size N (results in N/2 complex pairs) or 2*N??
        // Documentation: realForward(double[] a) computes DFT of real data. 
        // a must be length n. Output is packed.
        
        val fftData = DoubleArray(FFT_SIZE)
        for (i in 0 until FFT_SIZE) {
            fftData[i] = (fftBuffer[i] * window[i]).toDouble()
        }

        fft?.realForward(fftData)

        // 2. Compute Magnitudes
        // Layout: [Re(0), Re(N/2), Re(1), Im(1), Re(2), Im(2), ...]
        // We ignore DC (idx 0) and Nyquist (idx 1) for the bands usually starting > 0
        
        val magnitudes = FloatArray(VisualizerConstants.BAR_AMOUNT)
        
        for (i in 0 until VisualizerConstants.BAR_AMOUNT) {
            val startBin = bandBoundaries[i]
            val endBin = bandBoundaries[i+1]
            
            var sum = 0.0
            var count = 0
            
            // JTransforms packing:
            // bin k (0 < k < n/2): Re is at 2*k, Im is at 2*k+1
            
            for (bin in startBin until endBin) {
                if (bin >= FFT_SIZE / 2) break
                val re = fftData[2 * bin]
                val im = fftData[2 * bin + 1]
                val mag = sqrt(re * re + im * im)
                sum += mag
                count++
            }
            
            magnitudes[i] = if (count > 0) (sum / count).toFloat() else 0f
            
            // Apply Gain
            magnitudes[i] *= bandGains[i]
        }
        
        // 3. Normalize
        normalizer.normalize(magnitudes, VisualizerConstants.MAGNITUDE_LIMIT)
        
        // 4. Update State
        AudioVisualizerState.updateMagnitudes(magnitudes)
        // println("FFT Processed: ${magnitudes.joinToString { "%.2f".format(it) }}") // Commented out to reduce noise
    }
}
