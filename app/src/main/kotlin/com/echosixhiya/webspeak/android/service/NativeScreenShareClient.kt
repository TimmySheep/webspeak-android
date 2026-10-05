package com.echosixhiya.webspeak.android.service

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import com.echosixhiya.webspeak.android.data.ScreenShareMediaStore
import org.json.JSONObject
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SoftwareVideoEncoderFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Screen-share sender/viewer using MediaProjection and the gateway's peer-to-peer signaling relay. */
class NativeScreenShareClient(
    context: Context,
    private val iceServers: List<PeerConnection.IceServer>,
    private val sendSignal: (streamId: String, targetPeerId: String, signal: JSONObject) -> Unit,
    private val onCaptureEnded: () -> Unit,
    private val onMediaError: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private var factory: PeerConnectionFactory? = null
    private var eglContext: org.webrtc.EglBase.Context? = null
    private var capturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var localTrack: VideoTrack? = null
    private var localStreamId: String = ""
    private val peers = ConcurrentHashMap<String, PeerRecord>()
    private val pendingIce = ConcurrentHashMap<String, MutableList<IceCandidate>>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val peerTimeouts = ConcurrentHashMap<String, Runnable>()
    @Volatile private var capturing = false
    @Volatile private var stopping = false

    suspend fun startCapture(permissionResult: Intent) {
        if (capturing) return
        stopping = false
        NativeWebRtcRuntime.initialize(appContext)
        ensureFactory()
        val projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                if (!stopping) onCaptureEnded()
            }
        }
        val screenCapturer = ScreenCapturerAndroid(permissionResult, projectionCallback)
        capturer = screenCapturer
        val egl = eglContext ?: error("WebRTC EGL 尚未初始化")
        val textureHelper = SurfaceTextureHelper.create("WebSpeakScreenCapture", egl)
            ?: error("无法创建屏幕纹理")
        surfaceTextureHelper = textureHelper
        val source = factory?.createVideoSource(screenCapturer.isScreencast)
            ?: error("无法创建屏幕视频源")
        videoSource = source
        screenCapturer.initialize(textureHelper, appContext, source.capturerObserver)
        val track = factory?.createVideoTrack("webspeak-screen", source)
            ?: error("无法创建屏幕视频轨道")
        localTrack = track
        track.setEnabled(true)
        screenCapturer.startCapture(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS)
        capturing = true
    }

    fun bindOwnerStream(streamId: String) {
        localStreamId = streamId
        localTrack?.let { ScreenShareMediaStore.publish(streamId, it) }
    }

    suspend fun createOffer(streamId: String, peerId: String, publishVideo: Boolean) {
        closePeer(streamId, peerId)
        val peer = createPeer(streamId, peerId, publishVideo)
        val offer = createOfferDescription(peer.connection)
        setLocalDescription(peer.connection, offer)
        val localDescription = peer.connection.localDescription ?: offer
        sendSignal(streamId, peerId, JSONObject().put("kind", "offer").put("sdp", localDescription.description))
    }

    suspend fun handleSignal(streamId: String, peerId: String, signal: JSONObject, publishVideo: Boolean) {
        when (signal.optString("kind")) {
            "close" -> closePeer(streamId, peerId)
            "iceCandidate" -> {
                val candidateString = signal.optString("candidate", "")
                if (candidateString.isBlank()) return
                val candidate = IceCandidate(
                    if (signal.isNull("sdpMid")) null else signal.optString("sdpMid"),
                    signal.optInt("sdpMLineIndex", 0),
                    candidateString,
                )
                val peer = peers[key(streamId, peerId)]
                if (peer?.remoteDescriptionSet == true) {
                    runCatching { peer.connection.addIceCandidate(candidate) }
                } else {
                    pendingIce.getOrPut(key(streamId, peerId)) { mutableListOf() }.add(candidate)
                }
            }
            "answer" -> {
                val sdp = signal.optString("sdp", "")
                val peer = peers[key(streamId, peerId)] ?: return
                if (sdp.isBlank()) return
                setRemoteDescription(peer, SessionDescription(SessionDescription.Type.ANSWER, sdp))
                peer.remoteDescriptionSet = true
                flushPendingIce(peer)
            }
            "offer" -> {
                val sdp = signal.optString("sdp", "")
                if (sdp.isBlank()) return
                val peer = peers[key(streamId, peerId)] ?: createPeer(streamId, peerId, publishVideo)
                setRemoteDescription(peer, SessionDescription(SessionDescription.Type.OFFER, sdp))
                peer.remoteDescriptionSet = true
                flushPendingIce(peer)
                val answer = createAnswerDescription(peer.connection)
                setLocalDescription(peer.connection, answer)
                val localDescription = peer.connection.localDescription ?: answer
                sendSignal(streamId, peerId, JSONObject().put("kind", "answer").put("sdp", localDescription.description))
            }
        }
    }

    fun closePeer(streamId: String, peerId: String, notifyPeer: Boolean = false) {
        val key = key(streamId, peerId)
        peerTimeouts.remove(key)?.let(mainHandler::removeCallbacks)
        pendingIce.remove(key)
        val peer = peers.remove(key) ?: return
        runCatching { peer.connection.close() }
        runCatching { peer.connection.dispose() }
        if (notifyPeer) sendSignal(streamId, peerId, JSONObject().put("kind", "close"))
    }

    fun stopStream(streamId: String) {
        if (streamId.isBlank()) return
        peers.values.filter { it.streamId == streamId }.forEach { peer -> closePeer(streamId, peer.peerId, notifyPeer = true) }
        pendingIce.keys.removeAll { it.startsWith("$streamId\u0000") }
        ScreenShareMediaStore.remove(streamId)
        if (localStreamId == streamId) {
            localStreamId = ""
            stopCapture()
        }
    }

    fun stopAll() {
        peers.values.toList().forEach { closePeer(it.streamId, it.peerId, notifyPeer = true) }
        pendingIce.clear()
        if (localStreamId.isNotBlank()) ScreenShareMediaStore.remove(localStreamId)
        localStreamId = ""
        stopCapture()
        runCatching { factory?.dispose() }
        factory = null
    }

    private fun stopCapture() {
        if (stopping) return
        stopping = true
        capturing = false
        runCatching { capturer?.stopCapture() }
        runCatching { capturer?.dispose() }
        capturer = null
        runCatching { localTrack?.dispose() }
        localTrack = null
        runCatching { videoSource?.dispose() }
        videoSource = null
        runCatching { surfaceTextureHelper?.dispose() }
        surfaceTextureHelper = null
        stopping = false
    }

    private fun ensureFactory() {
        if (factory != null) return
        val egl = NativeWebRtcRuntime.eglContext()
        eglContext = egl
        factory = PeerConnectionFactory.builder()
            // Isolate sharing from vendor hardware encoders until device-specific behavior is verified.
            .setVideoEncoderFactory(SoftwareVideoEncoderFactory())
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl))
            .createPeerConnectionFactory()
    }

    private fun createPeer(streamId: String, peerId: String, publishVideo: Boolean): PeerRecord {
        ensureFactory()
        val configuration = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        lateinit var record: PeerRecord
        val connection = factory?.createPeerConnection(configuration, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
            override fun onStandardizedIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                when (state) {
                    PeerConnection.PeerConnectionState.CONNECTED -> mainHandler.post { clearPeerTimeout(streamId, peerId) }
                    PeerConnection.PeerConnectionState.FAILED -> mainHandler.post {
                        if (peers[key(streamId, peerId)] === record) {
                            closePeer(streamId, peerId, notifyPeer = true)
                            onMediaError("屏幕共享直连已中断，请检查网关与设备之间的网络路径")
                        }
                    }
                    else -> Unit
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidate(candidate: IceCandidate) {
                sendSignal(
                    streamId,
                    peerId,
                    JSONObject()
                        .put("kind", "iceCandidate")
                        .put("candidate", candidate.sdp)
                        .put("sdpMid", candidate.sdpMid)
                        .put("sdpMLineIndex", candidate.sdpMLineIndex),
                )
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onAddStream(stream: org.webrtc.MediaStream) {
                stream.videoTracks.firstOrNull()?.let { ScreenShareMediaStore.publish(streamId, it) }
            }
            override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit
            override fun onDataChannel(channel: org.webrtc.DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out org.webrtc.MediaStream>) {
                (receiver.track() as? VideoTrack)?.let { ScreenShareMediaStore.publish(streamId, it) }
            }
            override fun onTrack(transceiver: RtpTransceiver) {
                (transceiver.receiver.track() as? VideoTrack)?.let { ScreenShareMediaStore.publish(streamId, it) }
            }
            override fun onSelectedCandidatePairChanged(event: org.webrtc.CandidatePairChangeEvent) = Unit
        }) ?: throw IllegalStateException("无法建立屏幕共享 WebRTC 对等连接")
        record = PeerRecord(streamId, peerId, connection)
        if (publishVideo) {
            val track = localTrack ?: throw IllegalStateException("屏幕采集尚未启动")
            connection.addTrack(track, listOf("webspeak-screen"))
        } else {
            connection.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
            )
        }
        peers[key(streamId, peerId)] = record
        armPeerTimeout(record)
        return record
    }

    private fun armPeerTimeout(peer: PeerRecord) {
        val peerKey = key(peer.streamId, peer.peerId)
        clearPeerTimeout(peer.streamId, peer.peerId)
        val timeout = Runnable {
            if (peers[peerKey] === peer) {
                closePeer(peer.streamId, peer.peerId, notifyPeer = true)
                onMediaError("屏幕共享直连超时；画面未能在设备与对端之间建立")
            }
        }
        peerTimeouts[peerKey] = timeout
        mainHandler.postDelayed(timeout, PEER_CONNECTION_TIMEOUT_MS)
    }

    private fun clearPeerTimeout(streamId: String, peerId: String) {
        peerTimeouts.remove(key(streamId, peerId))?.let(mainHandler::removeCallbacks)
    }

    private suspend fun flushPendingIce(peer: PeerRecord) {
        val candidates = pendingIce.remove(key(peer.streamId, peer.peerId)).orEmpty()
        candidates.forEach { runCatching { peer.connection.addIceCandidate(it) } }
    }

    private suspend fun createOfferDescription(peer: PeerConnection): SessionDescription =
        suspendCancellableCoroutine { continuation ->
            peer.createOffer(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) { if (continuation.isActive) continuation.resume(description) }
                override fun onSetSuccess() = Unit
                override fun onCreateFailure(error: String) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error)) }
                override fun onSetFailure(error: String) = Unit
            }, MediaConstraints())
        }

    private suspend fun createAnswerDescription(peer: PeerConnection): SessionDescription =
        suspendCancellableCoroutine { continuation ->
            peer.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) { if (continuation.isActive) continuation.resume(description) }
                override fun onSetSuccess() = Unit
                override fun onCreateFailure(error: String) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error)) }
                override fun onSetFailure(error: String) = Unit
            }, MediaConstraints())
        }

    private suspend fun setLocalDescription(peer: PeerConnection, description: SessionDescription) {
        suspendCancellableCoroutine { continuation ->
            peer.setLocalDescription(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) = Unit
                override fun onSetSuccess() { if (continuation.isActive) continuation.resume(Unit) }
                override fun onCreateFailure(error: String) = Unit
                override fun onSetFailure(error: String) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error)) }
            }, description)
        }
    }

    private suspend fun setRemoteDescription(record: PeerRecord, description: SessionDescription) {
        suspendCancellableCoroutine { continuation ->
            record.connection.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) = Unit
                override fun onSetSuccess() { if (continuation.isActive) continuation.resume(Unit) }
                override fun onCreateFailure(error: String) = Unit
                override fun onSetFailure(error: String) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error)) }
            }, description)
        }
    }

    private fun key(streamId: String, peerId: String) = "$streamId\u0000$peerId"

    private data class PeerRecord(
        val streamId: String,
        val peerId: String,
        val connection: PeerConnection,
        @Volatile var remoteDescriptionSet: Boolean = false,
    )

    companion object {
        private const val DEFAULT_WIDTH = 1280
        private const val DEFAULT_HEIGHT = 720
        private const val DEFAULT_FPS = 30
        private const val PEER_CONNECTION_TIMEOUT_MS = 20_000L
    }
}
