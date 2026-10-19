package com.echosixhiya.webspeak.android.service

import com.echosixhiya.webspeak.android.model.NoiseSuppressionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class VoiceInputProcessorTest {
    @Test
    fun strongerSuppressionAttenuatesSteadyBackgroundMore() {
        fun processedRms(level: NoiseSuppressionLevel): Double {
            val processor = VoiceInputProcessor(level, autoGainControlEnabled = false)
            var frame = FloatArray(480)
            repeat(300) {
                frame = FloatArray(480) { if (it % 2 == 0) 0.01f else -0.01f }
                processor.processFloatSamples(frame)
            }
            return sqrt(frame.sumOf { (it * it).toDouble() } / frame.size)
        }

        val off = processedRms(NoiseSuppressionLevel.Off)
        val light = processedRms(NoiseSuppressionLevel.Light)
        val moderate = processedRms(NoiseSuppressionLevel.Moderate)
        val strong = processedRms(NoiseSuppressionLevel.Strong)

        assertEquals(0.01, off, 0.0001)
        assertTrue(light < off)
        assertTrue(moderate < light)
        assertTrue(strong < moderate)
    }

    @Test
    fun automaticGainRaisesQuietSpeechButDoesNotClip() {
        val processor = VoiceInputProcessor(NoiseSuppressionLevel.Off, autoGainControlEnabled = true)
        var frame = FloatArray(480)
        repeat(40) {
            frame = FloatArray(480) { if (it % 2 == 0) 0.02f else -0.02f }
            processor.processFloatSamples(frame)
        }

        assertTrue(frame.maxOf { kotlin.math.abs(it) } > 0.02f)
        assertTrue(frame.maxOf { kotlin.math.abs(it) } <= 1f)
    }

    @Test
    fun automaticGainDoesNotBoostVeryQuietInputNoise() {
        val processor = VoiceInputProcessor(NoiseSuppressionLevel.Off, autoGainControlEnabled = true)
        var frame = FloatArray(480)
        repeat(40) {
            frame = FloatArray(480) { if (it % 2 == 0) 0.001f else -0.001f }
            processor.processFloatSamples(frame)
        }

        assertEquals(0.001, frame.maxOf { kotlin.math.abs(it) }.toDouble(), 0.0001)
    }

    @Test
    fun offSettingsPreserveWebRtcFloatAndCompatibilityPcmSamples() {
        val processor = VoiceInputProcessor(NoiseSuppressionLevel.Off, autoGainControlEnabled = false)
        val webRtcBuffer = ByteBuffer.allocateDirect(2 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        webRtcBuffer.putFloat(0, 0.25f)
        webRtcBuffer.putFloat(Float.SIZE_BYTES, -0.25f)
        val compatibilityFrame = byteArrayOf(0x00, 0x40, 0x00, 0xc0.toByte())

        processor.processFloatBuffer(webRtcBuffer)
        processor.processPcm16Frame(compatibilityFrame)

        assertEquals(0.25f, webRtcBuffer.getFloat(0), 0f)
        assertEquals(-0.25f, webRtcBuffer.getFloat(Float.SIZE_BYTES), 0f)
        assertTrue(compatibilityFrame.contentEquals(byteArrayOf(0x00, 0x40, 0x00, 0xc0.toByte())))
    }
}
