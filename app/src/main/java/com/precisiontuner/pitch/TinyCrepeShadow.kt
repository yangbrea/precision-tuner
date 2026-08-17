package com.precisiontuner.pitch

import android.content.Context
import android.util.Log
import com.precisiontuner.BuildConfig
import org.tensorflow.lite.InterpreterApi
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

data class TinyCrepeResult(
    val frequency: Double,
    val confidence: Double,
    val salience: FloatArray,
    val inferenceMs: Double,
)

/** Offline Tiny CREPE inference backend used by hybrid and primary modes. */
class TinyCrepeShadow private constructor(private val interpreter: InterpreterApi) : AutoCloseable {
    private val input = Array(1) { FloatArray(INPUT_SIZE) }
    private val output = Array(1) { FloatArray(OUTPUT_SIZE) }

    fun infer(pcm: ShortArray, sourceRate: Int): TinyCrepeResult? = runCatching {
        prepareInput(pcm, sourceRate, input[0])
        val started = System.nanoTime()
        interpreter.run(input, output)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000.0
        val salience = output[0].copyOf()
        if (salience.any { !it.isFinite() }) return null
        val peak = salience.indices.maxBy { salience[it] }
        val from = (peak - 4).coerceAtLeast(0)
        val endExclusive = (peak + 5).coerceAtMost(salience.size)
        var weightedCents = 0.0
        var weight = 0.0
        for (bin in from until endExclusive) {
            val activation = salience[bin].toDouble()
            weightedCents += activation * (CENTS_OFFSET + bin * CENTS_PER_BIN)
            weight += activation
        }
        val cents = if (weight > 1e-12) weightedCents / weight else CENTS_OFFSET + peak * CENTS_PER_BIN
        TinyCrepeResult(
            frequency = 10.0 * 2.0.pow(cents / 1200.0),
            confidence = salience[peak].toDouble(),
            salience = salience,
            inferenceMs = elapsedMs,
        )
    }.getOrNull()

    override fun close() = interpreter.close()

    companion object {
        /** Asset name selected at build time via -PcrepeModel (tiny/small/full). */
        const val MODEL_ASSET = BuildConfig.CREPE_MODEL_ASSET
        const val INPUT_SIZE = 1024
        const val OUTPUT_SIZE = 360
        private const val CENTS_OFFSET = 1997.3794084376191
        private const val CENTS_PER_BIN = 20.0

        fun create(context: Context): TinyCrepeShadow? {
            if (!BuildConfig.TINY_CREPE_ENABLED) {
                Log.d(TAG, "Tiny CREPE model disabled by build flag")
                return null
            }
            return runCatching {
                val model = loadModel(context)
                val interpreter = InterpreterApi.create(
                    model,
                    InterpreterApi.Options().apply { setNumThreads(1) },
                )
                require(interpreter.getInputTensor(0).shape().contentEquals(intArrayOf(1, INPUT_SIZE)))
                require(interpreter.getOutputTensor(0).shape().contentEquals(intArrayOf(1, OUTPUT_SIZE)))
                TinyCrepeShadow(interpreter).also {
                    Log.d(TAG, "Tiny CREPE model loaded: input=1024 output=360 threads=1")
                }
            }.onFailure {
                Log.e(TAG, "Tiny CREPE model unavailable; DSP fallback remains active", it)
            }.getOrNull()
        }

        internal fun prepareInput(pcm: ShortArray, sourceRate: Int, destination: FloatArray) {
            require(sourceRate > 0 && pcm.isNotEmpty() && destination.size == INPUT_SIZE)
            val step = sourceRate.toDouble() / 16_000.0
            val first = (pcm.lastIndex - (INPUT_SIZE - 1) * step).coerceAtLeast(0.0)
            var mean = 0.0
            for (index in destination.indices) {
                val position = (first + index * step).coerceAtMost(pcm.lastIndex.toDouble())
                val lower = position.toInt()
                val upper = (lower + 1).coerceAtMost(pcm.lastIndex)
                val fraction = position - lower
                val sample = (pcm[lower] * (1.0 - fraction) + pcm[upper] * fraction) / 32768.0
                destination[index] = sample.toFloat()
                mean += sample
            }
            mean /= destination.size
            var variance = 0.0
            destination.forEach { variance += (it - mean) * (it - mean) }
            val deviation = sqrt(variance / destination.size).coerceAtLeast(1e-8)
            destination.indices.forEach { destination[it] = ((destination[it] - mean) / deviation).toFloat() }
        }

        private fun loadModel(context: Context): MappedByteBuffer {
            val descriptor = context.assets.openFd(MODEL_ASSET)
            return FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
            }.also { descriptor.close() }
        }

        private const val TAG = "TinyCrepe"
    }
}

data class TinyCrepeMetricsSnapshot(
    val frames: Int,
    val agreement: Int,
    val octaveConflict: Int,
    val neuralUnvoiced: Int,
    val dspUnvoiced: Int,
    val p50Ms: Double,
    val p95Ms: Double,
    val maxMs: Double,
)

class TinyCrepeShadowMetrics(private val capacity: Int = 256) {
    private val times = ArrayDeque<Double>()
    private var frames = 0
    private var agreement = 0
    private var octaveConflict = 0
    private var neuralUnvoiced = 0
    private var dspUnvoiced = 0

    fun observe(neural: TinyCrepeResult?, dspFrequency: Double?): TinyCrepeMetricsSnapshot {
        frames++
        if (neural == null || neural.confidence < VOICED_THRESHOLD) neuralUnvoiced++
        if (dspFrequency == null) dspUnvoiced++
        if (neural != null) {
            times.addLast(neural.inferenceMs)
            while (times.size > capacity) times.removeFirst()
            if (neural.confidence >= VOICED_THRESHOLD && dspFrequency != null) {
                val cents = abs(1200.0 * ln(neural.frequency / dspFrequency) / ln(2.0))
                if (cents <= AGREEMENT_CENTS) agreement++
                if (abs(cents - 1200.0) <= OCTAVE_CENTS || abs(cents - 2400.0) <= OCTAVE_CENTS) {
                    octaveConflict++
                }
            }
        }
        return snapshot()
    }

    fun snapshot(): TinyCrepeMetricsSnapshot {
        val sorted = times.sorted()
        fun percentile(fraction: Double): Double = if (sorted.isEmpty()) 0.0 else {
            sorted[((sorted.size - 1) * fraction).toInt().coerceIn(sorted.indices)]
        }
        return TinyCrepeMetricsSnapshot(
            frames, agreement, octaveConflict, neuralUnvoiced, dspUnvoiced,
            percentile(0.50), percentile(0.95), sorted.lastOrNull() ?: 0.0,
        )
    }

    private companion object {
        const val VOICED_THRESHOLD = 0.5
        const val AGREEMENT_CENTS = 50.0
        const val OCTAVE_CENTS = 100.0
    }
}
