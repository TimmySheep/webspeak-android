package com.echosixhiya.webspeak.android.service

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import okio.ByteString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * WebSpeak's non-WebRTC compatibility path: 20 ms PCM16 microphone frames upstream and
 * per-client Opus packets downstream. Android's platform Opus decoder is available from API 29.
 */
class CompatibilityVoiceTransport(
    context: Context,
    private val sendPcmFrame: (ByteString) -> Boolean,
    private val memberVolume: (Int) -> Float,
    private val outputVolume: () -> Float,
    private val onError: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val captureRunning = AtomicBoolean(false)
    private val muted = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val errorReported = AtomicBoolean(false)
    private val decoders = LinkedHashMap<Int, OpusDecoder>()
    private var previousAudioMode: Int? = null
    private var previousSpeakerphoneOn: Boolean? = null
    private var previousCommunicationDevice: AudioDeviceInfo? = null
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private var audioThread: HandlerThread? = null
    private var audioHandler: Handler? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    fun start() {
        check(!closed.get()) { "兼容音频传输已关闭" }
        check(ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "需要麦克风权限才能启动兼容语音传输"
        }
        check(captureRunning.compareAndSet(false, true)) { "兼容音频传输已经启动" }
        try {
            previousAudioMode = audioManager.mode
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            configureCommunicationDevice()

            val minimumBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            check(minimumBuffer > 0) { "Android 无法创建 48 kHz 麦克风录音器" }
            val recorder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setBufferSizeInBytes(max(minimumBuffer, FRAME_BYTES * 8))
                .build()
            audioRecord = recorder
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Android 麦克风录音器初始化失败" }
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Android 麦克风没有开始录音" }
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(recorder.audioSessionId)?.apply { enabled = true }
            }
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(recorder.audioSessionId)?.apply { enabled = true }
            }

            val thread = HandlerThread("WebSpeakCompatibilityAudio").apply { start() }
            audioThread = thread
            audioHandler = Handler(thread.looper)
            captureThread = Thread(::captureLoop, "WebSpeakPcmCapture").apply {
                isDaemon = true
                start()
            }
        } catch (error: Throwable) {
            captureRunning.set(false)
            releaseCaptureResources()
            restoreCommunicationDevice()
            throw error
        }
    }

    fun setMuted(value: Boolean) {
        muted.set(value)
    }

    fun receive(packet: ByteString) {
        if (!captureRunning.get() || packet.size > MAX_PACKET_BYTES) return
        val bytes = packet.toByteArray()
        audioHandler?.post {
            if (!captureRunning.get()) return@post
            runCatching { decodePacket(bytes) }
                .onFailure { reportError("兼容音频解码失败：${it.message.orEmpty()}") }
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        captureRunning.set(false)
        releaseCaptureResources()
        audioHandler?.post {
            decoders.values.forEach(OpusDecoder::close)
            decoders.clear()
            audioThread?.quitSafely()
            audioThread = null
            audioHandler = null
        } ?: audioThread?.quitSafely()
        restoreCommunicationDevice()
    }

    private fun captureLoop() {
        val recorder = audioRecord ?: return
        val frame = ByteArray(FRAME_BYTES)
        try {
            while (captureRunning.get()) {
                var offset = 0
                while (offset < frame.size && captureRunning.get()) {
                    val count = recorder.read(frame, offset, frame.size - offset, AudioRecord.READ_BLOCKING)
                    if (count > 0) {
                        offset += count
                    } else if (count != AudioRecord.ERROR_DEAD_OBJECT) {
                        throw IllegalStateException("麦克风读取失败（$count）")
                    } else {
                        throw IllegalStateException("麦克风录音器已停止")
                    }
                }
                if (offset == frame.size && !muted.get()) {
                    if (!sendPcmFrame(ByteString.of(*frame))) {
                        reportError("麦克风音频帧未能发送到网关")
                    }
                }
            }
        } catch (error: Throwable) {
            if (captureRunning.get()) reportError("麦克风兼容传输停止：${error.message.orEmpty()}")
        }
    }

    private fun decodePacket(packet: ByteArray) {
        val frame = parseOpusVoiceFrame(packet) ?: return
        val now = System.currentTimeMillis()
        decoders.entries.removeAll { (_, decoder) ->
            if (now - decoder.lastPacketAt <= DECODER_IDLE_TIMEOUT_MS) return@removeAll false
            decoder.close()
            true
        }
        val decoder = decoders[frame.clientId] ?: run {
            if (decoders.size >= MAX_DECODERS) {
                val oldest = decoders.entries.minByOrNull { it.value.lastPacketAt }
                oldest?.value?.close()
                oldest?.key?.let(decoders::remove)
            }
            OpusDecoder().also { decoders[frame.clientId] = it }
        }
        decoder.lastPacketAt = now
        decoder.decode(frame.payload, memberVolume(frame.clientId).coerceIn(0f, 4f) * outputVolume().coerceIn(0f, 1f))
    }

    private fun releaseCaptureResources() {
        runCatching { audioRecord?.stop() }
        captureThread?.interrupt()
        if (Thread.currentThread() !== captureThread) runCatching { captureThread?.join(CAPTURE_JOIN_TIMEOUT_MS) }
        captureThread = null
        runCatching { echoCanceler?.release() }
        runCatching { noiseSuppressor?.release() }
        echoCanceler = null
        noiseSuppressor = null
        runCatching { audioRecord?.release() }
        audioRecord = null
    }

    private fun configureCommunicationDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                previousCommunicationDevice = audioManager.communicationDevice
                val devices = audioManager.availableCommunicationDevices
                val externalDevice = devices.firstOrNull { it.type in EXTERNAL_COMMUNICATION_DEVICE_TYPES }
                val earpiece = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                val speaker = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                val preferredDevice = externalDevice ?: earpiece ?: speaker
                if (preferredDevice != null) audioManager.setCommunicationDevice(preferredDevice)
            }
        } else {
            @Suppress("DEPRECATION")
            run {
                previousSpeakerphoneOn = audioManager.isSpeakerphoneOn
                // Prefer the private earpiece route; Android still selects connected headsets.
                audioManager.isSpeakerphoneOn = false
            }
        }
    }

    private fun restoreCommunicationDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val previous = previousCommunicationDevice
                val available = audioManager.availableCommunicationDevices
                if (previous != null && available.any { it.id == previous.id }) audioManager.setCommunicationDevice(previous)
                else audioManager.clearCommunicationDevice()
            }
        } else {
            previousSpeakerphoneOn?.let { previous ->
                @Suppress("DEPRECATION")
                runCatching { audioManager.isSpeakerphoneOn = previous }
            }
        }
        previousAudioMode?.let { runCatching { audioManager.mode = it } }
        previousAudioMode = null
        previousSpeakerphoneOn = null
        previousCommunicationDevice = null
    }

    private fun reportError(message: String) {
        if (errorReported.compareAndSet(false, true)) onError(message.take(300))
    }

    private class OpusDecoder {
        private val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
        private var audioTrack: AudioTrack? = null
        private var sampleRate = SAMPLE_RATE
        private var channelCount = 1
        private var presentationTimeUs = 0L
        var lastPacketAt: Long = System.currentTimeMillis()

        init {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, SAMPLE_RATE, 1).apply {
                setByteBuffer("csd-0", ByteBuffer.wrap(OPUS_HEAD))
                setByteBuffer("csd-1", zeroDelayCsd())
                setByteBuffer("csd-2", zeroDelayCsd())
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_PACKET_BYTES)
            }
            codec.configure(format, null, null, 0)
            codec.start()
        }

        fun decode(packet: ByteArray, volume: Float) {
            val inputIndex = codec.dequeueInputBuffer(0)
            if (inputIndex >= 0) {
                val input = codec.getInputBuffer(inputIndex)
                if (input == null || packet.size > input.capacity()) {
                    codec.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, 0)
                } else {
                    input.clear()
                    input.put(packet)
                    codec.queueInputBuffer(inputIndex, 0, packet.size, presentationTimeUs, 0)
                    presentationTimeUs += OPUS_FRAME_DURATION_US
                }
            }

            val info = MediaCodec.BufferInfo()
            while (true) {
                when (val outputIndex = codec.dequeueOutputBuffer(info, 0)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> return
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> configureAudioTrack(codec.outputFormat)
                    else -> if (outputIndex >= 0) {
                        try {
                            if (info.size > 0) {
                                val output = codec.getOutputBuffer(outputIndex)
                                if (output != null) {
                                    output.position(info.offset)
                                    output.limit(info.offset + info.size)
                                    val pcm = ByteArray(info.size)
                                    output.get(pcm)
                                    writePcm(pcm, volume)
                                }
                            }
                        } finally {
                            codec.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
            }
        }

        fun close() {
            runCatching { audioTrack?.pause() }
            runCatching { audioTrack?.flush() }
            runCatching { audioTrack?.release() }
            audioTrack = null
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }

        private fun configureAudioTrack(format: MediaFormat) {
            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)
            val channelMask = if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
            val minBuffer = AudioTrack.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
            check(minBuffer > 0) { "Android 无法创建语音播放设备" }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelMask)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(max(minBuffer, sampleRate * channelCount * 2 / 5))
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
            check(track.state == AudioTrack.STATE_INITIALIZED) { "Android 语音播放设备初始化失败" }
            audioTrack?.release()
            audioTrack = track
            track.play()
        }

        private fun writePcm(pcm: ByteArray, volume: Float) {
            val track = audioTrack ?: return
            val pcmEncoding = runCatching { codec.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) }
                .getOrDefault(AudioFormat.ENCODING_PCM_16BIT)
            check(pcmEncoding == AudioFormat.ENCODING_PCM_16BIT) { "Android Opus 解码器输出了不支持的 PCM 格式" }
            val gain = volume.coerceIn(0f, 4f)
            if (gain == 1f) {
                track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
                return
            }
            val adjusted = ByteArray(pcm.size)
            var index = 0
            while (index + 1 < pcm.size) {
                val low = pcm[index].toInt() and 0xff
                val high = pcm[index + 1].toInt()
                val sample = (high shl 8) or low
                val signed = if (sample >= 0x8000) sample - 0x10000 else sample
                val scaled = (signed * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                adjusted[index] = scaled.toByte()
                adjusted[index + 1] = (scaled shr 8).toByte()
                index += 2
            }
            track.write(adjusted, 0, adjusted.size, AudioTrack.WRITE_BLOCKING)
        }
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val OPUS_FRAME_DURATION_US = 20_000L
        const val FRAME_BYTES = 960 * 2
        const val MAX_PACKET_BYTES = 8 * 1024
        const val MAX_DECODERS = 12
        const val DECODER_IDLE_TIMEOUT_MS = 30_000L
        const val CAPTURE_JOIN_TIMEOUT_MS = 300L
        val EXTERNAL_COMMUNICATION_DEVICE_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
        val OPUS_HEAD = byteArrayOf(
            0x4f, 0x70, 0x75, 0x73, 0x48, 0x65, 0x61, 0x64,
            0x01, 0x01, 0x00, 0x00, 0x80.toByte(), 0xbb.toByte(), 0x00, 0x00,
            0x00, 0x00, 0x00,
        )

        fun zeroDelayCsd(): ByteBuffer = ByteBuffer.allocate(java.lang.Long.BYTES)
            .order(ByteOrder.nativeOrder())
            .apply {
                putLong(0L)
                flip()
            }
    }
}
