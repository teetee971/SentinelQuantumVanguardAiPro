package com.sentinel.quantum.voice

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Streaming, allocation-bounded voice transformer for Sentinel-owned VoIP audio.
 *
 * Contract:
 * - input/output are mono PCM16 frames at the same sample rate and frame length;
 * - no network, file, carrier/SIM or Telecom API access exists here;
 * - the transformed frame is intended to be inserted before Sentinel VoIP encoding;
 * - state is kept only in memory and can be reset between calls.
 *
 * The pitch shifter uses two modulated delay taps with complementary crossfades.
 * It is deliberately small enough to run in the real-time audio path while keeping
 * the transport boundary explicit. Device/audio quality still requires physical tests.
 */
class LiveVoiceTransformEngine(
    val sampleRateHz: Int,
    maxDelayMs: Int = 40
) {
    init {
        require(sampleRateHz in 8_000..48_000) { "Unsupported sample rate" }
        require(maxDelayMs in 20..80) { "Delay window must stay bounded" }
    }

    private val ringSize = (sampleRateHz * maxDelayMs / 1_000).coerceAtLeast(256)
    private val ring = FloatArray(ringSize)
    private var writeIndex = 0
    private var phase = 0.0

    fun reset() {
        ring.fill(0f)
        writeIndex = 0
        phase = 0.0
    }

    fun processPcm16(
        input: ShortArray,
        effect: VoiceAddonPolicy.Effect
    ): ShortArray =
        ShortArray(input.size).also { output ->
            processPcm16Into(input, output, effect)
        }

    /**
     * Allocation-bounded variant used by realtime audio SDK adapters.
     * The caller owns both arrays and may reuse them between 10 ms audio frames.
     */
    fun processPcm16Into(
        input: ShortArray,
        output: ShortArray,
        effect: VoiceAddonPolicy.Effect
    ) {
        require(output.size >= input.size) { "Output buffer too small" }
        if (input.isEmpty()) return

        val ratio = effect.pitch.toDouble().coerceIn(0.60, 1.60)
        if (ratio == 1.0) {
            input.copyInto(output, endIndex = input.size)
            input.forEach(::pushHistory)
            return
        }

        val usableDelay = (ringSize - 4).coerceAtLeast(8).toDouble()
        val phaseStep = abs(1.0 - ratio) / usableDelay

        input.forEachIndexed { index, sample ->
            ring[writeIndex] = sample.toFloat()

            val p1 = phase
            val p2 = (phase + 0.5) % 1.0
            val first = readInterpolated(writeIndex - delayFor(p1, ratio, usableDelay))
            val second = readInterpolated(writeIndex - delayFor(p2, ratio, usableDelay))

            val firstWeight = 0.5 - 0.5 * cos(2.0 * PI * p1)
            val secondWeight = 1.0 - firstWeight
            val mixed = first * firstWeight + second * secondWeight

            output[index] = mixed
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()

            writeIndex = (writeIndex + 1) % ringSize
            phase = (phase + phaseStep) % 1.0
        }
    }

    private fun pushHistory(sample: Short) {
        ring[writeIndex] = sample.toFloat()
        writeIndex = (writeIndex + 1) % ringSize
    }

    private fun delayFor(phase: Double, ratio: Double, usableDelay: Double): Double {
        val minimumDelay = 2.0
        return if (ratio > 1.0) {
            minimumDelay + (1.0 - phase) * usableDelay
        } else {
            minimumDelay + phase * usableDelay
        }
    }

    private fun readInterpolated(position: Double): Float {
        var wrapped = position % ringSize
        if (wrapped < 0.0) wrapped += ringSize
        val left = floor(wrapped).toInt()
        val right = (left + 1) % ringSize
        val fraction = (wrapped - left).toFloat()
        return ring[left] * (1f - fraction) + ring[right] * fraction
    }
}
