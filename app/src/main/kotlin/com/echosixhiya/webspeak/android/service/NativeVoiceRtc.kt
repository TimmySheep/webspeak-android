package com.echosixhiya.webspeak.android.service

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.CandidatePairChangeEvent
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.RTCStatsCollectorCallback
import org.webrtc.RTCStatsReport
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.EglBase
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Native libwebrtc client for the gateway's single mixed voice track. */
class NativeVoiceRtc(
    context: Context,
    private val sendOffer: (type: String, sdp: String, muted: Boolean) -> Unit,
    private val onConnectionState: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousAudioMode: Int? = null
    private var previousSpeakerphoneOn: Boolean? = null
    private var previousCommunicationDevice: AudioDeviceInfo? = null
    private var audioModule: JavaAudioDeviceModule? = null
    private var eglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteAudioTrack: AudioTrack? = null
    private var outputVolume = 1.0
    private var gatheringComplete = CompletableDeferred<Unit>()
    @Volatile private var muted = false
    @Volatile private var connected = false
    @Volatile private var lastLocalVoiceActivityAtMs = 0L
    @Volatile private var remoteAudioTrackReady = false
    private val closed = AtomicBoolean(false)

    suspend fun start(initiallyMuted: Boolean) {
        if (peerConnection != null) return
        muted = initiallyMuted
        remoteAudioTrackReady = false
        lastLocalVoiceActivityAtMs = 0L
        closed.set(false)
        configureCommunicationAudio()
        initializeFactory()

        val observer = createPeerObserver()
        val configuration = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val peer = factory?.createPeerConnection(configuration, observer)
            ?: throw IllegalStateException("无法创建 WebRTC 语音连接")
        peerConnection = peer

        val source = factory?.createAudioSource(MediaConstraints())
            ?: throw IllegalStateException("无法创建麦克风音源")
        audioSource = source
        val track = factory?.createAudioTrack("webspeak-microphone", source)
            ?: throw IllegalStateException("无法创建麦克风音轨")
        localAudioTrack = track
        track.setEnabled(!muted)
        peer.addTrack(track, listOf("webspeak-voice"))
        applyPreferredCommunicationDevice()

        val offer = createOffer(peer)
        setLocalDescription(peer, offer)
        awaitIceGathering()
        val localDescription = peer.localDescription
            ?: throw IllegalStateException("WebRTC 本地 SDP 不可用")
        sendOffer(localDescription.type.canonicalForm(), localDescription.description, muted)
    }

    suspend fun setRemoteAnswer(type: String, sdp: String) {
        val peer = peerConnection ?: return
        val sessionType = when (type.lowercase()) {
            "answer" -> SessionDescription.Type.ANSWER
            else -> throw IllegalArgumentException("网关返回了非 answer 的 WebRTC 描述")
        }
        setRemoteDescription(peer, SessionDescription(sessionType, sdp))
    }

    fun setMicrophoneMuted(value: Boolean) {
        muted = value
        localAudioTrack?.setEnabled(!value)
    }

    fun setOutputVolume(value: Double) {
        outputVolume = value.coerceIn(0.0, 1.0)
        remoteAudioTrack?.setVolume(outputVolume)
    }

    fun isConnected(): Boolean = connected

    fun hasRemoteAudioTrack(): Boolean = remoteAudioTrackReady

    fun isLocalVoiceActiveWithin(windowMs: Long): Boolean =
        lastLocalVoiceActivityAtMs > 0L && SystemClock.elapsedRealtime() - lastLocalVoiceActivityAtMs <= windowMs

    internal suspend fun collectAudioStats(): NativeVoiceAudioStats? = suspendCancellableCoroutine { continuation ->
        val peer = peerConnection
        if (peer == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        try {
            peer.getStats(object : RTCStatsCollectorCallback {
                override fun onStatsDelivered(report: RTCStatsReport) {
                    val values = report.statsMap.values
                    val inbound = audioByteTotal(values, "inbound-rtp", "bytesReceived")
                    val outbound = audioByteTotal(values, "outbound-rtp", "bytesSent")
                    if (continuation.isActive) continuation.resume(
                        if (inbound == null && outbound == null) null else NativeVoiceAudioStats(inbound, outbound),
                    )
                }
            })
        } catch (error: Throwable) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        connected = false
        remoteAudioTrackReady = false
        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        peerConnection = null
        runCatching { localAudioTrack?.dispose() }
        localAudioTrack = null
        runCatching { remoteAudioTrack?.dispose() }
        remoteAudioTrack = null
        runCatching { audioSource?.dispose() }
        audioSource = null
        runCatching { factory?.dispose() }
        factory = null
        runCatching { audioModule?.release() }
        audioModule = null
        runCatching { eglBase?.release() }
        eglBase = null
        restoreCommunicationAudio()
        onConnectionState("closed")
    }

    private fun initializeFactory() {
        NativeWebRtcRuntime.initialize(appContext)

        val module = JavaAudioDeviceModule.builder(appContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(AudioFormat.ENCODING_PCM_16BIT)
            .setSamplesReadyCallback { samples ->
                if (!muted && hasVoiceEnergy(samples.data)) {
                    lastLocalVoiceActivityAtMs = SystemClock.elapsedRealtime()
                }
            }
            .createAudioDeviceModule()
        audioModule = module

        // Video factories are supplied here as well because the same native
        // PeerConnectionFactory will later host the screen-share sender/viewer.
        val egl = EglBase.create()
        eglBase = egl
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(module)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
    }

    private fun configureCommunicationAudio() {
        previousAudioMode = audioManager.mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            previousCommunicationDevice = runCatching { audioManager.communicationDevice }.getOrNull()
        } else {
            @Suppress("DEPRECATION")
            run { previousSpeakerphoneOn = audioManager.isSpeakerphoneOn }
        }
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        applyPreferredCommunicationDevice()
    }

    private fun applyPreferredCommunicationDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val devices = audioManager.availableCommunicationDevices
                val preferredDevices = listOfNotNull(
                    devices.firstOrNull { it.type in EXTERNAL_COMMUNICATION_DEVICE_TYPES },
                    devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE },
                    devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER },
                )
                for (device in preferredDevices) {
                    if (audioManager.setCommunicationDevice(device)) break
                }
            }
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                // Prefer a private route during calls instead of exposing audio on speaker.
                audioManager.isSpeakerphoneOn = false
            }
        }
    }

    private fun restoreCommunicationAudio() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val previous = previousCommunicationDevice
                val available = audioManager.availableCommunicationDevices
                if (previous != null && available.any { it.id == previous.id }) audioManager.setCommunicationDevice(previous)
                else audioManager.clearCommunicationDevice()
            }
        } else {
            previousSpeakerphoneOn?.let { old ->
                @Suppress("DEPRECATION")
                runCatching { audioManager.isSpeakerphoneOn = old }
            }
        }
        previousAudioMode?.let { old -> runCatching { audioManager.mode = old } }
        previousSpeakerphoneOn = null
        previousCommunicationDevice = null
        previousAudioMode = null
    }

    private suspend fun createOffer(peer: PeerConnection): SessionDescription =
        suspendCancellableCoroutine { continuation ->
            peer.createOffer(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) {
                    if (continuation.isActive) continuation.resume(description)
                }

                override fun onSetSuccess() = Unit

                override fun onCreateFailure(error: String) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error))
                }

                override fun onSetFailure(error: String) = Unit
            }, MediaConstraints())
        }

    private suspend fun setLocalDescription(peer: PeerConnection, description: SessionDescription) {
        suspendCancellableCoroutine { continuation ->
            peer.setLocalDescription(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) = Unit

                override fun onSetSuccess() {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onCreateFailure(error: String) = Unit

                override fun onSetFailure(error: String) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error))
                }
            }, description)
        }
    }

    private suspend fun setRemoteDescription(peer: PeerConnection, description: SessionDescription) {
        suspendCancellableCoroutine { continuation ->
            peer.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) = Unit

                override fun onSetSuccess() {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onCreateFailure(error: String) = Unit

                override fun onSetFailure(error: String) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error))
                }
            }, description)
        }
    }

    private suspend fun awaitIceGathering() {
        if (peerConnection?.iceGatheringState() == PeerConnection.IceGatheringState.COMPLETE) return
        gatheringComplete = CompletableDeferred()
        if (peerConnection?.iceGatheringState() == PeerConnection.IceGatheringState.COMPLETE) {
            gatheringComplete.complete(Unit)
        }
        withTimeoutOrNull(5_000) { gatheringComplete.await() }
    }

    private fun createPeerObserver(): PeerConnection.Observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            connected = state == PeerConnection.IceConnectionState.CONNECTED || state == PeerConnection.IceConnectionState.COMPLETED
            onConnectionState(state.name.lowercase())
        }

        override fun onStandardizedIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit

        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
            connected = state == PeerConnection.PeerConnectionState.CONNECTED
            onConnectionState(state.name.lowercase())
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
            if (state == PeerConnection.IceGatheringState.COMPLETE) gatheringComplete.complete(Unit)
        }

        override fun onIceCandidate(candidate: IceCandidate) = Unit

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit

        override fun onAddStream(stream: org.webrtc.MediaStream) {
            stream.audioTracks.firstOrNull()?.let(::setRemoteTrack)
        }

        override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit

        override fun onDataChannel(channel: org.webrtc.DataChannel) = Unit

        override fun onRenegotiationNeeded() = Unit

        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out org.webrtc.MediaStream>) {
            (receiver.track() as? AudioTrack)?.let(::setRemoteTrack)
        }

        override fun onTrack(transceiver: RtpTransceiver) {
            (transceiver.receiver.track() as? AudioTrack)?.let(::setRemoteTrack)
        }

        override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent) {
            Log.d(TAG, "Selected voice ICE pair changed: ${event.reason}")
        }
    }

    private fun setRemoteTrack(track: AudioTrack) {
        remoteAudioTrack = track
        remoteAudioTrackReady = true
        track.setEnabled(true)
        track.setVolume(outputVolume)
        onConnectionState("remote-audio-ready")
    }

    companion object {
        private const val TAG = "WebSpeakRtc"
        private const val LOCAL_VOICE_RMS_THRESHOLD = 450.0
        private val EXTERNAL_COMMUNICATION_DEVICE_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )

        private fun hasVoiceEnergy(pcm: ByteArray): Boolean {
            var sumSquares = 0L
            var sampleCount = 0
            var index = 0
            while (index + 1 < pcm.size) {
                val sample = ((pcm[index + 1].toInt() shl 8) or (pcm[index].toInt() and 0xff)).toShort().toInt()
                sumSquares += sample.toLong() * sample
                sampleCount++
                index += 2
            }
            return sampleCount > 0 && sqrt(sumSquares.toDouble() / sampleCount) >= LOCAL_VOICE_RMS_THRESHOLD
        }

        private fun audioByteTotal(
            stats: Collection<org.webrtc.RTCStats>,
            type: String,
            byteKey: String,
        ): Long? {
            val audioStats = stats.filter { stat ->
                stat.type == type && ((stat.members["kind"] as? String)?.equals("audio", true) != false)
                    && ((stat.members["mediaType"] as? String)?.equals("audio", true) != false)
            }
            if (audioStats.isEmpty()) return null
            return audioStats.sumOf { (it.members[byteKey] as? Number)?.toLong() ?: 0L }
        }
    }
}

internal data class NativeVoiceAudioStats(
    val inboundBytesReceived: Long?,
    val outboundBytesSent: Long?,
)
