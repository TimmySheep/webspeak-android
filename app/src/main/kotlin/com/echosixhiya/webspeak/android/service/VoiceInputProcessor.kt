package com.echosixhiya.webspeak.android.service

import com.echosixhiya.webspeak.android.model.NoiseSuppressionLevel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.roundToInt

/** Stateful, low-cost adaptive expander and software AGC for 10–20 ms microphone frames. */
internal class VoiceInputProcessor(
    noiseSuppressionLevel: NoiseSuppressionLevel = NoiseSuppressionLevel.Moderate,
    autoGainControlEnabled: Boolean = true,
) {
    @Volatile
    private var settings = Settings(noiseSuppressionLevel, autoGainControlEnabled)

    private var noiseFloorRms = INITIAL_NOISE_FLOOR_RMS
    private var gateGain = 1f
    private var agcGain = 1f

    fun configure(noiseSuppressionLevel: NoiseSuppressionLevel, autoGainControlEnabled: Boolean) {
        settings = Settings(noiseSuppressionLevel, autoGainControlEnabled)
    }

    @Synchronized
    fun reset() {
        noiseFloorRms = INITIAL_NOISE_FLOOR_RMS
        gateGain = 1f
        agcGain = 1f
    }

    @Synchronized
    fun processFloatBuffer(buffer: ByteBuffer, sampleCount: Int = buffer.capacity() / FLOAT_BYTES) {
        val count = sampleCount.coerceIn(0, buffer.capacity() / FLOAT_BYTES)
        if (count == 0) return
        val samples = buffer.order(ByteOrder.nativeOrder())
        var sumSquares = 0.0
        for (index in 0 until count) {
            val sample = samples.getFloat(index * FLOAT_BYTES).takeIf(Float::isFinite) ?: 0f
            sumSquares += sample * sample
        }
        val gain = outputGain(sqrt(sumSquares / count).toFloat())
        for (index in 0 until count) {
            val offset = index * FLOAT_BYTES
            val sample = samples.getFloat(offset).takeIf(Float::isFinite) ?: 0f
            samples.putFloat(offset, (sample * gain).coerceIn(-1f, 1f))
        }
    }

    @Synchronized
    fun processFloatSamples(samples: FloatArray) {
        if (samples.isEmpty()) return
        var sumSquares = 0.0
        for (sample in samples) {
            val finiteSample = sample.takeIf(Float::isFinite) ?: 0f
            sumSquares += finiteSample * finiteSample
        }
        val gain = outputGain(sqrt(sumSquares / samples.size).toFloat())
        for (index in samples.indices) {
            val sample = samples[index].takeIf(Float::isFinite) ?: 0f
            samples[index] = (sample * gain).coerceIn(-1f, 1f)
        }
    }

    @Synchronized
    fun processPcm16Frame(frame: ByteArray) {
        val sampleCount = frame.size / Short.SIZE_BYTES
        if (sampleCount == 0) return
        var sumSquares = 0.0
        for (index in 0 until sampleCount) {
            val sample = readPcm16(frame, index * Short.SIZE_BYTES) / PCM16_SCALE
            sumSquares += sample * sample
        }
        val gain = outputGain(sqrt(sumSquares / sampleCount).toFloat())
        for (index in 0 until sampleCount) {
            val offset = index * Short.SIZE_BYTES
            val scaled = (readPcm16(frame, offset) / PCM16_SCALE * gain).coerceIn(-1f, 0.9999695f)
            val value = (scaled * PCM16_SCALE).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            frame[offset] = value.toByte()
            frame[offset + 1] = (value shr 8).toByte()
        }
    }

    private fun outputGain(frameRms: Float): Float {
        val currentSettings = settings
        updateNoiseFloor(frameRms)

        val level = currentSettings.noiseSuppressionLevel
        if (level == NoiseSuppressionLevel.Off) {
            gateGain = 1f
        } else {
            val threshold = (noiseFloorRms * level.thresholdMultiplier).coerceIn(MIN_GATE_THRESHOLD_RMS, MAX_GATE_THRESHOLD_RMS)
            val ratio = (frameRms / threshold).coerceIn(0f, 1f)
            val targetGateGain = if (frameRms >= threshold) {
                1f
            } else {
                level.minimumGateGain + (1f - level.minimumGateGain) * ratio * ratio
            }
            gateGain += (targetGateGain - gateGain) * if (targetGateGain > gateGain) GATE_OPENING_SMOOTHING else GATE_CLOSING_SMOOTHING
        }

        if (currentSettings.autoGainControlEnabled) {
            val speechFloor = max(noiseFloorRms * AGC_NOISE_MULTIPLIER, MIN_SPEECH_RMS)
            if (frameRms > speechFloor) {
                val targetAgcGain = (TARGET_SPEECH_RMS / frameRms).coerceIn(MIN_AGC_GAIN, MAX_AGC_GAIN)
                val smoothing = if (targetAgcGain < agcGain) AGC_GAIN_REDUCTION_SMOOTHING else AGC_GAIN_INCREASE_SMOOTHING
                agcGain += (targetAgcGain - agcGain) * smoothing
            } else {
                agcGain += (1f - agcGain) * AGC_IDLE_RELEASE_SMOOTHING
            }
        } else {
            agcGain = 1f
        }

        return (gateGain * agcGain).coerceIn(0f, MAX_AGC_GAIN)
    }

    private fun updateNoiseFloor(frameRms: Float) {
        if (!frameRms.isFinite() || frameRms > max(noiseFloorRms * NOISE_FLOOR_UPDATE_RATIO, MAX_NOISE_FLOOR_TRAINING_RMS)) return
        val smoothing = if (frameRms < noiseFloorRms) NOISE_FLOOR_FALL_SMOOTHING else NOISE_FLOOR_RISE_SMOOTHING
        noiseFloorRms = max(MIN_NOISE_FLOOR_RMS, noiseFloorRms + (frameRms - noiseFloorRms) * smoothing)
    }

    private fun readPcm16(frame: ByteArray, offset: Int): Float {
        val value = (frame[offset].toInt() and 0xff) or (frame[offset + 1].toInt() shl 8)
        return value.toShort().toFloat()
    }

    private data class Settings(
        val noiseSuppressionLevel: NoiseSuppressionLevel,
        val autoGainControlEnabled: Boolean,
    )

    private companion object {
        const val INITIAL_NOISE_FLOOR_RMS = 0.004f
        const val MIN_NOISE_FLOOR_RMS = 0.0007f
        const val MIN_GATE_THRESHOLD_RMS = 0.003f
        const val MAX_GATE_THRESHOLD_RMS = 0.08f
        const val MAX_NOISE_FLOOR_TRAINING_RMS = 0.015f
        const val NOISE_FLOOR_UPDATE_RATIO = 3.5f
        const val NOISE_FLOOR_FALL_SMOOTHING = 0.12f
        const val NOISE_FLOOR_RISE_SMOOTHING = 0.025f
        const val GATE_OPENING_SMOOTHING = 0.75f
        const val GATE_CLOSING_SMOOTHING = 0.32f
        const val AGC_NOISE_MULTIPLIER = 1.5f
        const val MIN_SPEECH_RMS = 0.006f
        const val TARGET_SPEECH_RMS = 0.14f
        const val MIN_AGC_GAIN = 0.55f
        const val MAX_AGC_GAIN = 4f
        const val AGC_GAIN_REDUCTION_SMOOTHING = 0.35f
        const val AGC_GAIN_INCREASE_SMOOTHING = 0.08f
        const val AGC_IDLE_RELEASE_SMOOTHING = 0.18f
        const val PCM16_SCALE = 32_768f
        const val FLOAT_BYTES = 4
    }
}

/** Shared by WebRTC's capture callback and the PCM compatibility transport. */
internal object VoiceInputEffects {
    private val processor = VoiceInputProcessor()

    fun configure(noiseSuppressionLevel: NoiseSuppressionLevel, autoGainControlEnabled: Boolean) =
        processor.configure(noiseSuppressionLevel, autoGainControlEnabled)

    fun reset() = processor.reset()

    fun processFloatBuffer(buffer: ByteBuffer) = processor.processFloatBuffer(buffer)

    fun processPcm16Frame(frame: ByteArray) = processor.processPcm16Frame(frame)
}
