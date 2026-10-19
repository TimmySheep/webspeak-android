package com.echosixhiya.webspeak.android.service

import org.webrtc.ExternalAudioProcessingFactory
import java.nio.ByteBuffer

/**
 * WebRTC keeps this factory process-wide in the SDK. Reuse it across voice sessions rather than
 * calling destroy(), which would invalidate the SDK's shared native audio-processing instance.
 */
internal object NativeVoiceAudioProcessing {
    val factory: ExternalAudioProcessingFactory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ExternalAudioProcessingFactory().apply {
            setCapturePostProcessing(object : ExternalAudioProcessingFactory.AudioProcessing {
                @Suppress("UNUSED_PARAMETER")
                override fun initialize(sampleRateHz: Int, numChannels: Int) {
                    VoiceInputEffects.reset()
                }

                @Suppress("UNUSED_PARAMETER")
                override fun reset(newRate: Int) {
                    VoiceInputEffects.reset()
                }

                override fun process(numBands: Int, numFrames: Int, buffer: ByteBuffer) {
                    VoiceInputEffects.processFloatBuffer(buffer)
                }
            })
        }
    }
}
