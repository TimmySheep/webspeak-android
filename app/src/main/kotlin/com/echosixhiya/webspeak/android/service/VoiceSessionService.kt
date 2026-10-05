package com.echosixhiya.webspeak.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.echosixhiya.webspeak.android.MainActivity
import com.echosixhiya.webspeak.android.R
import com.echosixhiya.webspeak.android.data.ConnectionProfileStore
import com.echosixhiya.webspeak.android.data.ChatHistoryStore
import com.echosixhiya.webspeak.android.data.GatewayApi
import com.echosixhiya.webspeak.android.data.GatewayException
import com.echosixhiya.webspeak.android.data.GatewayMessageParser
import com.echosixhiya.webspeak.android.data.IdentityVault
import com.echosixhiya.webspeak.android.data.ScreenShareMediaStore
import com.echosixhiya.webspeak.android.data.VoiceSessionStore
import com.echosixhiya.webspeak.android.model.ChatMessage
import com.echosixhiya.webspeak.android.model.ChatScope
import com.echosixhiya.webspeak.android.model.ConnectionPhase
import com.echosixhiya.webspeak.android.model.JoinRequest
import com.echosixhiya.webspeak.android.model.PokeAlert
import com.echosixhiya.webspeak.android.model.ServerEvent
import com.echosixhiya.webspeak.android.model.VoiceAudioTransport
import com.echosixhiya.webspeak.android.model.VoiceMember
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.PeerConnection
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Owns the authenticated gateway socket and native voice WebRTC session while the UI is backgrounded. */
class VoiceSessionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }
    private val gatewayApi by lazy { GatewayApi(httpClient) }
    private val profileStore by lazy { ConnectionProfileStore(this) }
    private val chatHistoryStore by lazy { ChatHistoryStore(this) }
    private val identityVault by lazy { IdentityVault(this) }
    private var socket: WebSocket? = null
    private var rtc: NativeVoiceRtc? = null
    private var screenShareClient: NativeScreenShareClient? = null
    private var screenShareIceServers: List<PeerConnection.IceServer> = defaultScreenShareIceServers()
    private var pendingScreenShareStartId = ""
    private var connectJob: Job? = null
    private var handshakeTimeoutJob: Job? = null
    private var activeRequest: JoinRequest? = null
    private var generation = 0L
    private var intentionalStop = false
    private var microphoneMuted = false
    private var microphoneCueToneGenerator: ToneGenerator? = null
    private var outputVolume = 1f
    private var historyGatewayKey = ""
    private var historyServerKey = ""
    private val requestSequence = AtomicLong()
    private val pendingLatencyProbes = mutableMapOf<String, Long>()
    private var compatibilityAudio: CompatibilityVoiceTransport? = null
    private var audioConnectionTimeoutJob: Job? = null
    private var voiceAudioHealthJob: Job? = null
    private var fallbackAudioGeneration = -1L
    @Volatile private var lastRemoteAudibleVoiceActivityAtMs = 0L

    override fun onCreate() {
        super.onCreate()
        microphoneMuted = getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean(KEY_MIC_MUTED, false)
        outputVolume = getSharedPreferences(PREFERENCES, MODE_PRIVATE).getFloat(KEY_OUTPUT_VOLUME, 1f).coerceIn(0f, 1f)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val request = intent.toJoinRequest()
                if (request == null) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                try {
                    startVoiceForeground(buildNotification(getString(R.string.notification_voice_connecting)))
                } catch (error: SecurityException) {
                    failWithoutForeground("MICROPHONE_PERMISSION_REQUIRED", "需要授予麦克风权限后才能加入语音")
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                connectJob?.cancel()
                val currentGeneration = ++generation
                intentionalStop = false
                lastRemoteAudibleVoiceActivityAtMs = 0L
                activeRequest = request
                closeCurrentTransport(sendStop = true)
                VoiceSessionStore.update {
                    it.copy(
                        phase = ConnectionPhase.Connecting,
                        gatewayUrl = request.gatewayUrl.trim(),
                        gatewayName = "WebSpeak",
                        teamSpeakTarget = request.teamSpeakTarget,
                        nickname = request.nickname.trim(),
                        currentChannelId = "",
                        channels = emptyList(),
                        members = emptyList(),
                        errorCode = "",
                        errorMessage = "",
                        audioError = "",
                        reconnectAttempt = 0,
                        audioReady = false,
                        audioTransport = VoiceAudioTransport.None,
                        outputVolume = outputVolume,
                        screenShares = emptyList(),
                        activeScreenShareId = "",
                        watchingScreenShareId = "",
                        screenShareStarting = false,
                        screenShareError = "",
                    )
                }
                connectJob = serviceScope.launch { connectToGateway(request, currentGeneration) }
            }

            ACTION_DISCONNECT -> stopSession()
            ACTION_SET_MICROPHONE_MUTED -> setMutedInternal(intent.getBooleanExtra(EXTRA_MUTED, !microphoneMuted))
            ACTION_SWITCH_CHANNEL -> switchChannelInternal(
                intent.getStringExtra(EXTRA_CHANNEL_ID).orEmpty(),
                intent.getStringExtra(EXTRA_CHANNEL_PASSWORD).orEmpty(),
            )
            ACTION_SEND_TEXT -> sendTextInternal(
                intent.getStringExtra(EXTRA_SCOPE).orEmpty(),
                intent.getIntExtra(EXTRA_TARGET_CLIENT_ID, 0),
                intent.getStringExtra(EXTRA_MESSAGE).orEmpty(),
            )
            ACTION_SET_AWAY -> sendCommand("setAway", JSONObject().put("away", intent.getBooleanExtra(EXTRA_AWAY, false)))
            ACTION_SEND_POKE -> sendCommand(
                "poke",
                JSONObject().put("clientId", intent.getIntExtra(EXTRA_TARGET_CLIENT_ID, 0))
                    .put("message", intent.getStringExtra(EXTRA_MESSAGE).orEmpty().take(200)),
            )
            ACTION_SET_WHISPER_TARGETS -> {
                val ids = intent.getIntArrayExtra(EXTRA_TARGET_IDS) ?: intArrayOf()
                val array = JSONArray().also { result -> ids.distinct().take(8).forEach(result::put) }
                sendCommand("setWhisperTargets", JSONObject().put("targetIds", array))
            }
            ACTION_SET_WHISPER_ACTIVE -> sendCommand("setWhisperActive", JSONObject().put("active", intent.getBooleanExtra(EXTRA_ACTIVE, false)))
            ACTION_SET_MEMBER_VOLUME -> {
                val clientId = intent.getIntExtra(EXTRA_TARGET_CLIENT_ID, 0)
                val volume = intent.getFloatExtra(EXTRA_VOLUME, 1f).coerceIn(0f, 4f)
                setMemberVolumeInternal(clientId, volume)
                sendCommand("setMemberVolume", JSONObject().put("clientId", clientId).put("volume", volume.toDouble()))
            }
            ACTION_MOVE_MEMBER -> sendCommand(
                "moveClient",
                JSONObject()
                    .put("clientId", intent.getIntExtra(EXTRA_TARGET_CLIENT_ID, 0))
                    .put("channelId", intent.getStringExtra(EXTRA_CHANNEL_ID).orEmpty())
                    .put("password", intent.getStringExtra(EXTRA_CHANNEL_PASSWORD).orEmpty()),
            )
            ACTION_SET_OUTPUT_VOLUME -> setOutputVolumeInternal(intent.getFloatExtra(EXTRA_VOLUME, outputVolume))
            ACTION_LATENCY_PROBE -> sendLatencyProbe()
            ACTION_CLEAR_CHAT_HISTORY -> clearChatHistory()
            ACTION_START_SCREEN_SHARE -> startScreenShare(intent.projectionData())
            ACTION_STOP_SCREEN_SHARE -> stopActiveScreenShare()
            ACTION_JOIN_SCREEN_SHARE -> joinScreenShare(intent.getStringExtra(EXTRA_STREAM_ID).orEmpty())
            ACTION_LEAVE_SCREEN_SHARE -> leaveScreenShare(intent.getStringExtra(EXTRA_STREAM_ID).orEmpty())
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        intentionalStop = true
        generation++
        connectJob?.cancel()
        handshakeTimeoutJob?.cancel()
        closeCurrentTransport(sendStop = true)
        serviceScope.cancel()
        microphoneCueToneGenerator?.release()
        microphoneCueToneGenerator = null
        super.onDestroy()
    }

    private suspend fun connectToGateway(request: JoinRequest, currentGeneration: Long) {
        try {
            val config = gatewayApi.loadPublicConfig(request.gatewayUrl)
            if (!config.initialized) throw GatewayException("NOT_INITIALIZED", "WebSpeak 网关尚未完成配置")
            val accessMode = config.accessMode.lowercase().ifBlank { "fixed" }
            val resolvedTarget = request.teamSpeakTarget.trim().ifBlank { config.defaultTarget }
            if (accessMode == "open" && resolvedTarget.isBlank()) {
                throw GatewayException("INVALID_TARGET", "该网关允许自选服务器，请填写 TeamSpeak 地址")
            }
            if (!request.rememberIdentity) identityVault.clear()
            val effectiveRequest = request.copy(
                teamSpeakTarget = resolvedTarget,
                identity = if (request.rememberIdentity) identityVault.read().orEmpty() else "",
            )
            profileStore.save(request.gatewayUrl.trim(), request.nickname.trim(), resolvedTarget)
            if (currentGeneration != generation) return

            val ticket = gatewayApi.createJoinTicket(effectiveRequest)
            if (currentGeneration != generation) return
            historyGatewayKey = ticket.baseUrl.toString().removeSuffix("/").lowercase()
            historyServerKey = resolvedTarget.trim().lowercase().ifBlank { "fixed-gateway-target" }
            val websocketUrl = GatewayApi.webSocketUrl(ticket.baseUrl, ticket.ticket)
            val origin = ticket.baseUrl.newBuilder().encodedPath("/").query(null).build().toString().removeSuffix("/")
            val requestBuilder = Request.Builder()
                .url(websocketUrl)
                .header("Origin", origin)
                .header("Accept", "application/json")
            var createdSocket: WebSocket? = null
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (!isCurrent(currentGeneration, webSocket)) {
                        webSocket.close(NORMAL_CLOSE, "superseded")
                        return
                    }
                    VoiceSessionStore.update {
                        it.copy(
                            gatewayUrl = request.gatewayUrl.trim(),
                            gatewayName = config.siteName,
                            teamSpeakTarget = resolvedTarget,
                            nickname = request.nickname.trim(),
                            phase = ConnectionPhase.Connecting,
                        )
                    }
                    handshakeTimeoutJob?.cancel()
                    handshakeTimeoutJob = serviceScope.launch {
                        kotlinx.coroutines.delay(HANDSHAKE_TIMEOUT_MS)
                        if (isCurrent(currentGeneration, webSocket)
                            && VoiceSessionStore.state.value.phase == ConnectionPhase.Connecting
                        ) {
                            failSession("GATEWAY_HANDSHAKE_TIMEOUT", "网关连接超时，请检查服务器状态和网络")
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (isCurrent(currentGeneration, webSocket)) handleGatewayMessage(text, request, currentGeneration)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    if (isCurrent(currentGeneration, webSocket)) compatibilityAudio?.receive(bytes)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (isCurrent(currentGeneration, webSocket) && !intentionalStop) {
                        handleUnexpectedSocketEnd(code, reason)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (isCurrent(currentGeneration, webSocket) && !intentionalStop) {
                        handleUnexpectedSocketEnd(response?.code ?: 1006, t.message.orEmpty())
                    }
                }
            }
            createdSocket = httpClient.newWebSocket(requestBuilder.build(), listener)
            socket = createdSocket
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (currentGeneration == generation && !intentionalStop) {
                val gatewayError = error as? GatewayException
                failSession(gatewayError?.code ?: "GATEWAY_CONNECTION_FAILED", error.message ?: "无法连接 WebSpeak 网关")
            }
        }
    }

    private fun handleGatewayMessage(raw: String, request: JoinRequest, currentGeneration: Long) {
        val message = GatewayMessageParser.parse(raw) ?: return
        when (message.optString("type")) {
            "connected" -> {
                handshakeTimeoutJob?.cancel()
                val selfId = message.optInt("tsClientId", 0)
                val members = GatewayMessageParser.members(message.optJSONArray("members"), selfId)
                val events = parseEvents(message.optJSONArray("serverEventLog"))
                val webrtcAvailable = message.optBoolean("webrtcAvailable", false)
                screenShareIceServers = parseScreenShareIceServers(message.optJSONArray("screenShareIceServers"))
                ensureScreenShareClient()
                val inferredChannel = members.firstOrNull { it.isSelf }?.channelId.orEmpty()
                VoiceSessionStore.update { previous ->
                    previous.copy(
                        phase = ConnectionPhase.Connected,
                        selfClientId = selfId,
                        currentChannelId = inferredChannel.ifBlank { previous.currentChannelId },
                        away = members.firstOrNull { it.id == selfId }?.away ?: previous.away,
                        members = members,
                        events = events,
                        whisperTargetIds = parseIntSet(message.optJSONArray("whisperTargetIds")),
                        whisperActive = message.optBoolean("whisperActive", false),
                        webrtcAvailable = webrtcAvailable,
                        audioReady = false,
                        audioTransport = if (webrtcAvailable) VoiceAudioTransport.WebRtc else VoiceAudioTransport.None,
                        audioError = "",
                        screenShareError = "",
                        errorCode = "",
                        errorMessage = "",
                        reconnectAttempt = 0,
                    )
                }
                updateNotification(getString(R.string.notification_voice_connected))
                sendCommand("setMicrophoneMuted", JSONObject().put("muted", microphoneMuted))
                if (webrtcAvailable) startNativeVoiceRtc(currentGeneration)
                else startCompatibilityVoice(currentGeneration, "网关未启用 WebRTC")
                sendJson(JSONObject().put("type", "screenShareList"))
                val identity = message.optString("identity", "")
                if (request.rememberIdentity && identity.length in 1..8192) runCatching { identityVault.write(identity) }
                val gatewayKey = historyGatewayKey
                val serverKey = historyServerKey
                serviceScope.launch(Dispatchers.IO) {
                    val history = runCatching { chatHistoryStore.load(gatewayKey, serverKey) }.getOrDefault(emptyList())
                    VoiceSessionStore.update { previous ->
                        val combined = (history + previous.messages).distinctBy { it.id }.sortedBy { it.timestamp }.takeLast(MAX_MESSAGES)
                        previous.copy(messages = combined)
                    }
                }
            }

            "channelList" -> {
                val selfId = VoiceSessionStore.state.value.selfClientId
                val channels = GatewayMessageParser.channels(message.optJSONArray("channels"), selfId)
                val channelMembers = channels.flatMap { it.members }.distinctBy { it.id }
                VoiceSessionStore.update { previous ->
                    val mergedMembers = mergeMembers(previous.members, channelMembers)
                    val selfChannel = channels.firstOrNull { channel -> channel.members.any { it.id == selfId } }?.id
                    previous.copy(
                        channels = channels,
                        members = mergedMembers,
                        currentChannelId = selfChannel ?: previous.currentChannelId,
                        away = mergedMembers.firstOrNull { it.id == selfId }?.away ?: previous.away,
                    )
                }
                updateNotification(getString(R.string.notification_voice_connected))
            }

            "memberEnter" -> {
                val member = GatewayMessageParser.member(message, VoiceSessionStore.state.value.selfClientId)
                if (member.id > 0) VoiceSessionStore.update { previous ->
                    previous.copy(members = mergeMembers(previous.members, listOf(member)))
                }
            }

            "memberLeave" -> {
                val clientId = message.optInt("id", 0)
                VoiceSessionStore.update { previous ->
                    previous.copy(
                        members = previous.members.filterNot { it.id == clientId },
                        channels = previous.channels.map { channel -> channel.copy(members = channel.members.filterNot { it.id == clientId }) },
                        whisperTargetIds = previous.whisperTargetIds - clientId,
                    )
                }
            }

            "memberAvatar" -> {
                val clientId = message.optInt("id", 0)
                val avatar = message.optString("avatar", "").take(128 * 1024).ifBlank { null }
                VoiceSessionStore.update { previous ->
                    previous.copy(
                        members = previous.members.map { if (it.id == clientId) it.copy(avatar = avatar) else it },
                        channels = previous.channels.map { channel ->
                            channel.copy(members = channel.members.map { if (it.id == clientId) it.copy(avatar = avatar) else it })
                        },
                    )
                }
            }

            "chatMessage" -> {
                val chatMessage = GatewayMessageParser.message(message, VoiceSessionStore.state.value.selfClientId) ?: return
                VoiceSessionStore.update { previous ->
                    previous.copy(messages = (previous.messages + chatMessage).takeLast(MAX_MESSAGES))
                }
                persistMessage(chatMessage)
            }

            "serverEvent" -> {
                val event = message.optJSONObject("event")?.let(GatewayMessageParser::event) ?: return
                appendEvent(event)
            }

            "pokeReceived" -> {
                val poke = PokeAlert(
                    id = "poke-${message.optLong("timestamp", System.currentTimeMillis())}-${message.optInt("invokerId")}",
                    invokerId = message.optInt("invokerId", 0),
                    invokerName = message.optString("invokerName", "未知用户").take(120),
                    message = message.optString("message", "").take(200),
                    timestamp = message.optLong("timestamp", System.currentTimeMillis()),
                )
                VoiceSessionStore.update { previous -> previous.copy(pokes = (previous.pokes + poke).takeLast(100)) }
            }

            "voiceActivity" -> {
                val speaking = parseIntSet(message.optJSONArray("clientIds"))
                val current = VoiceSessionStore.state.value
                if (outputVolume > 0f && speaking.any { id ->
                        id != current.selfClientId && current.members.firstOrNull { it.id == id }?.volume?.let { it > 0f } != false
                    }
                ) {
                    lastRemoteAudibleVoiceActivityAtMs = SystemClock.elapsedRealtime()
                }
                VoiceSessionStore.update { previous ->
                    previous.copy(
                        members = previous.members.map { it.copy(speaking = it.id in speaking) },
                        channels = previous.channels.map { channel -> channel.copy(members = channel.members.map { it.copy(speaking = it.id in speaking) }) },
                    )
                }
            }

            "channelSwitched" -> {
                val channelId = message.stringValue("channelId")
                VoiceSessionStore.update { previous ->
                    previous.copy(currentChannelId = channelId, pendingChannelId = "", errorCode = "", errorMessage = "")
                }
                sendJson(JSONObject().put("type", "screenShareList"))
            }

            "whisperTargets" -> {
                val targets = parseIntSet(message.optJSONArray("targetIds"))
                VoiceSessionStore.update {
                    it.copy(whisperTargetIds = targets, whisperActive = message.optBoolean("active", false))
                }
            }

            "webrtcAnswer" -> {
                val description = message.optJSONObject("payload")?.optJSONObject("sdp")
                val type = description?.optString("type", "answer") ?: "answer"
                val sdp = description?.optString("sdp", "").orEmpty()
                if (sdp.isNotBlank()) serviceScope.launch {
                    runCatching { rtc?.setRemoteAnswer(type, sdp) }
                        .onFailure { error -> startCompatibilityVoice(currentGeneration, "语音协商失败：${error.message.orEmpty()}") }
                }
            }

            "webrtcError" -> {
                startCompatibilityVoice(
                    currentGeneration,
                    "WebRTC 语音连接失败（${safeCode(message.optString("code"))}）",
                )
            }

            "audioError" -> VoiceSessionStore.update {
                it.copy(audioError = "服务器音频处理失败（${safeCode(message.optString("code"))}）。")
            }

            "reconnecting" -> {
                audioConnectionTimeoutJob?.cancel()
                voiceAudioHealthJob?.cancel()
                fallbackAudioGeneration = -1L
                compatibilityAudio?.close()
                compatibilityAudio = null
                rtc?.close()
                rtc = null
                VoiceSessionStore.update {
                    it.copy(
                        phase = ConnectionPhase.Reconnecting,
                        reconnectAttempt = message.optInt("attempt", it.reconnectAttempt + 1),
                        audioReady = false,
                        audioTransport = VoiceAudioTransport.None,
                    )
                }
                updateNotification("语音网络正在恢复")
            }

            "reconnected" -> {
                VoiceSessionStore.update { it.copy(phase = ConnectionPhase.Connected, reconnectAttempt = 0) }
                if (VoiceSessionStore.state.value.webrtcAvailable) startNativeVoiceRtc(currentGeneration)
                else startCompatibilityVoice(currentGeneration, "重连后使用兼容音频")
            }

            "disconnected" -> {
                audioConnectionTimeoutJob?.cancel()
                voiceAudioHealthJob?.cancel()
                fallbackAudioGeneration = -1L
                compatibilityAudio?.close()
                compatibilityAudio = null
                rtc?.close()
                rtc = null
                if (message.optBoolean("recoverable", true)) {
                    VoiceSessionStore.update { it.copy(phase = ConnectionPhase.Reconnecting, audioReady = false, audioTransport = VoiceAudioTransport.None) }
                } else {
                    VoiceSessionStore.update {
                        it.copy(
                            errorCode = "TEAM_SPEAK_DISCONNECTED",
                            errorMessage = "TeamSpeak 连接已断开，网关无法自动恢复。",
                            audioReady = false,
                            audioTransport = VoiceAudioTransport.None,
                        )
                    }
                }
            }

            "reconnectFailed", "connectionFailed" -> {
                val code = safeCode(message.optString("code").ifBlank { "CONNECTION_FAILED" })
                val detail = message.optString("detail", "").take(240)
                failSession(code, failureMessage(code, detail))
            }

            "error" -> {
                val payload = message.optJSONObject("error")
                val code = safeCode(payload?.optString("code").orEmpty().ifBlank { "OPERATION_FAILED" })
                val detail = payload?.optString("message", "操作失败")?.take(240).orEmpty()
                VoiceSessionStore.update { previous ->
                    previous.copy(
                        errorCode = code,
                        errorMessage = detail,
                        pendingChannelId = if (code == "CHANNEL_PASSWORD_REQUIRED") previous.pendingChannelId else "",
                    )
                }
            }

            "screenShareList" -> VoiceSessionStore.update {
                it.copy(screenShares = GatewayMessageParser.screenShares(message.optJSONArray("streams")))
            }

            "screenShareStarted" -> {
                val stream = message.optJSONObject("stream")?.let {
                    GatewayMessageParser.screenShares(JSONArray().put(it)).firstOrNull()
                } ?: return
                val isOwner = message.optBoolean("owner", false)
                if (isOwner) {
                    if (pendingScreenShareStartId.isBlank() || message.optString("requestId") != pendingScreenShareStartId) {
                        sendJson(JSONObject().put("type", "screenShareStop").put("streamId", stream.streamId))
                        screenShareClient?.stopStream(stream.streamId)
                        return
                    }
                    pendingScreenShareStartId = ""
                    screenShareClient?.bindOwnerStream(stream.streamId)
                }
                VoiceSessionStore.update { previous ->
                    previous.copy(
                        screenShares = mergeScreenShares(previous.screenShares, stream),
                        activeScreenShareId = if (isOwner) stream.streamId else previous.activeScreenShareId,
                        screenShareStarting = if (isOwner) false else previous.screenShareStarting,
                        screenShareError = "",
                    )
                }
            }

            "screenShareJoined" -> {
                val stream = message.optJSONObject("stream")?.let {
                    GatewayMessageParser.screenShares(JSONArray().put(it)).firstOrNull()
                } ?: return
                VoiceSessionStore.update { previous ->
                    previous.copy(
                        screenShares = mergeScreenShares(previous.screenShares, stream),
                        watchingScreenShareId = stream.streamId,
                        screenShareError = "",
                    )
                }
                if (stream.source == "browser" && stream.ownerPeerId.isNotBlank()) {
                    serviceScope.launch {
                        runCatching { ensureScreenShareClient().createOffer(stream.streamId, stream.ownerPeerId, publishVideo = false) }
                            .onFailure { setScreenShareError("无法建立屏幕共享观看连接：${it.message.orEmpty()}") }
                    }
                }
            }

            "screenShareNativeViewerJoined" -> {
                val streamId = message.optString("streamId", "")
                val peerId = message.optString("viewerPeerId", "")
                if (streamId.isNotBlank() && peerId.isNotBlank()
                    && VoiceSessionStore.state.value.activeScreenShareId == streamId
                ) {
                    serviceScope.launch {
                        runCatching { ensureScreenShareClient().createOffer(streamId, peerId, publishVideo = true) }
                            .onFailure { setScreenShareError("无法向观看者发送屏幕画面：${it.message.orEmpty()}") }
                    }
                }
            }

            "screenShareSignal" -> {
                val streamId = message.optString("streamId", "")
                val peerId = message.optString("fromPeerId", "")
                val signal = message.optJSONObject("signal") ?: return
                val ownsStream = VoiceSessionStore.state.value.activeScreenShareId == streamId
                if (streamId.isNotBlank() && peerId.isNotBlank()) serviceScope.launch {
                    runCatching {
                        ensureScreenShareClient().handleSignal(streamId, peerId, signal, publishVideo = ownsStream)
                    }.onFailure { setScreenShareError("屏幕共享信令失败：${it.message.orEmpty()}") }
                }
            }

            "screenShareViewerLeft" -> {
                val streamId = message.optString("streamId", "")
                val peerId = message.optString("viewerPeerId", "")
                if (streamId.isNotBlank() && peerId.isNotBlank()) screenShareClient?.closePeer(streamId, peerId)
            }

            "screenShareViewerCount" -> {
                val streamId = message.optString("streamId", "")
                val viewerCount = message.optInt("viewerCount", 0).coerceIn(0, 1000)
                val viewers = GatewayMessageParser.screenShares(JSONArray().put(
                    JSONObject()
                        .put("streamId", streamId)
                        .put("source", "browser")
                        .put("ownerNickname", "")
                        .put("viewers", message.optJSONArray("viewers") ?: JSONArray())
                        .put("viewerCount", viewerCount),
                )).firstOrNull()?.viewers.orEmpty()
                VoiceSessionStore.update { previous ->
                    previous.copy(screenShares = previous.screenShares.map {
                        if (it.streamId == streamId) it.copy(viewerCount = viewerCount, viewers = viewers) else it
                    })
                }
            }

            "screenShareStopped", "screenShareLeft" -> {
                val streamId = message.optString("streamId", "")
                val wasLocalPublisher = VoiceSessionStore.state.value.activeScreenShareId == streamId
                screenShareClient?.stopStream(streamId)
                if (wasLocalPublisher) updateForegroundServiceType(mediaProjection = false)
                VoiceSessionStore.update { previous ->
                    previous.copy(
                        screenShares = previous.screenShares.filterNot { it.streamId == streamId },
                        activeScreenShareId = if (previous.activeScreenShareId == streamId) "" else previous.activeScreenShareId,
                        watchingScreenShareId = if (previous.watchingScreenShareId == streamId) "" else previous.watchingScreenShareId,
                        screenShareStarting = if (previous.activeScreenShareId == streamId) false else previous.screenShareStarting,
                    )
                }
            }

            "screenShareError" -> {
                val code = safeCode(message.optString("code"))
                val detail = message.optString("message", "屏幕共享操作失败").take(240)
                if (VoiceSessionStore.state.value.screenShareStarting) {
                    pendingScreenShareStartId = ""
                    screenShareClient?.stopAll()
                    updateForegroundServiceType(mediaProjection = false)
                }
                VoiceSessionStore.update { it.copy(screenShareStarting = false, screenShareError = "$detail（$code）") }
            }

            "screenShareCompleted" -> Unit

            "latencyPong" -> {
                val receivedAt = System.currentTimeMillis()
                val sequence = message.optString("sequence", "")
                val gatewaySent = pendingLatencyProbes.remove(sequence)
                val teamSpeakLatency = message.optLong("teamSpeakLatencyMs", -1).takeIf { it >= 0 }
                VoiceSessionStore.update {
                    it.copy(
                        latencyRttMs = gatewaySent?.let { started -> (receivedAt - started).coerceAtLeast(0) } ?: it.latencyRttMs,
                        teamSpeakLatencyMs = teamSpeakLatency,
                        teamSpeakReachable = message.optBoolean("teamSpeakReachable", false),
                    )
                }
            }
        }
    }

    private fun startNativeVoiceRtc(currentGeneration: Long) {
        audioConnectionTimeoutJob?.cancel()
        voiceAudioHealthJob?.cancel()
        compatibilityAudio?.close()
        compatibilityAudio = null
        rtc?.close()
        lateinit var nativeRtc: NativeVoiceRtc
        nativeRtc = NativeVoiceRtc(
            context = this,
            sendOffer = { type, sdp, muted ->
                if (currentGeneration == generation) {
                    val description = JSONObject().put("type", type).put("sdp", sdp)
                    val payload = JSONObject()
                        .put("sdp", description)
                        .put("muted", muted)
                        .put("accompanimentActive", false)
                    sendJson(JSONObject().put("type", "webrtcOffer").put("payload", payload))
                }
            },
            onConnectionState = { connectionState ->
                serviceScope.launch {
                    if (currentGeneration != generation || compatibilityAudio != null) return@launch
                    val connected = nativeRtc.isConnected()
                    val audioReady = connected && nativeRtc.hasRemoteAudioTrack()
                    VoiceSessionStore.update { previous ->
                        previous.copy(
                            audioReady = audioReady,
                            audioTransport = if (connected) VoiceAudioTransport.WebRtc else previous.audioTransport,
                        )
                    }
                    if (audioReady) {
                        audioConnectionTimeoutJob?.cancel()
                        startVoiceAudioHealthMonitor(currentGeneration, nativeRtc)
                    } else if (connectionState == "failed") {
                        startCompatibilityVoice(currentGeneration, "WebRTC ICE 连接失败")
                    }
                }
            },
        )
        rtc = nativeRtc
        nativeRtc.setOutputVolume(outputVolume.toDouble())
        audioConnectionTimeoutJob = serviceScope.launch {
            kotlinx.coroutines.delay(VOICE_RTC_CONNECTION_TIMEOUT_MS)
            if (currentGeneration == generation && rtc === nativeRtc
                && (!nativeRtc.isConnected() || !nativeRtc.hasRemoteAudioTrack())
            ) {
                startCompatibilityVoice(currentGeneration, "WebRTC 音频连接或远端音轨建立超时")
            }
        }
        serviceScope.launch {
            runCatching { nativeRtc.start(microphoneMuted) }
                .onFailure { error -> startCompatibilityVoice(currentGeneration, error.message ?: "无法启动 Android WebRTC 音频") }
        }
    }

    private fun startCompatibilityVoice(currentGeneration: Long, reason: String) {
        if (currentGeneration != generation || intentionalStop || compatibilityAudio != null) return
        if (fallbackAudioGeneration == currentGeneration) return
        fallbackAudioGeneration = currentGeneration
        audioConnectionTimeoutJob?.cancel()
        voiceAudioHealthJob?.cancel()
        sendJson(JSONObject().put("type", "webrtcStop"))
        rtc?.close()
        rtc = null
        VoiceSessionStore.update {
            it.copy(audioReady = false, audioTransport = VoiceAudioTransport.None)
        }

        lateinit var transport: CompatibilityVoiceTransport
        transport = CompatibilityVoiceTransport(
            context = this,
            sendPcmFrame = { frame -> currentGeneration == generation && socket?.send(frame) == true },
            memberVolume = { clientId ->
                VoiceSessionStore.state.value.members.firstOrNull { it.id == clientId }?.volume?.toFloat() ?: 1f
            },
            outputVolume = { outputVolume },
            onError = { detail ->
                serviceScope.launch {
                    if (currentGeneration == generation && compatibilityAudio === transport) {
                        VoiceSessionStore.update { it.copy(audioReady = false, audioError = detail) }
                    }
                }
            },
        )
        try {
            // Apply the persisted mute state before AudioRecord can deliver its first frame.
            transport.setMuted(microphoneMuted)
            transport.start()
            if (currentGeneration != generation) {
                transport.close()
                return
            }
            compatibilityAudio = transport
            val message = "WebRTC 不可用，已切换兼容音频（$reason）"
            VoiceSessionStore.update {
                it.copy(
                    audioReady = true,
                    audioTransport = VoiceAudioTransport.Compatibility,
                    audioError = message.take(300),
                )
            }
        } catch (error: Throwable) {
            transport.close()
            val detail = error.message?.take(240) ?: "无法启动 Android 兼容音频传输"
            VoiceSessionStore.update {
                it.copy(audioReady = false, audioTransport = VoiceAudioTransport.None, audioError = detail)
            }
        }
    }

    private fun startVoiceAudioHealthMonitor(currentGeneration: Long, nativeRtc: NativeVoiceRtc) {
        if (voiceAudioHealthJob?.isActive == true) return
        voiceAudioHealthJob = serviceScope.launch {
            var inboundBytes: Long? = null
            var outboundBytes: Long? = null
            var inboundProgressAt = SystemClock.elapsedRealtime()
            var outboundProgressAt = inboundProgressAt
            while (currentGeneration == generation && rtc === nativeRtc && compatibilityAudio == null) {
                delay(VOICE_AUDIO_STATS_INTERVAL_MS)
                if (!nativeRtc.isConnected() || !nativeRtc.hasRemoteAudioTrack()) continue
                val stats = withTimeoutOrNull(VOICE_AUDIO_STATS_TIMEOUT_MS) {
                    runCatching { nativeRtc.collectAudioStats() }.getOrNull()
                } ?: continue
                val now = SystemClock.elapsedRealtime()
                stats.inboundBytesReceived?.let { currentBytes ->
                    if (inboundBytes?.let { currentBytes > it } != false) inboundProgressAt = now
                    inboundBytes = currentBytes
                }
                stats.outboundBytesSent?.let { currentBytes ->
                    if (outboundBytes?.let { currentBytes > it } != false) outboundProgressAt = now
                    outboundBytes = currentBytes
                }

                val recentRemoteSpeech = lastRemoteAudibleVoiceActivityAtMs > 0L && outputVolume > 0f
                    && now - lastRemoteAudibleVoiceActivityAtMs <= VOICE_ACTIVITY_GRACE_MS
                if (recentRemoteSpeech && inboundBytes != null && now - inboundProgressAt >= AUDIO_RTP_STALL_TIMEOUT_MS) {
                    startCompatibilityVoice(currentGeneration, "遠端正在發言，但 WebRTC 沒有收到音訊資料")
                    break
                }
                val recentLocalSpeech = !microphoneMuted && nativeRtc.isLocalVoiceActiveWithin(VOICE_ACTIVITY_GRACE_MS)
                if (recentLocalSpeech && outboundBytes != null && now - outboundProgressAt >= AUDIO_RTP_STALL_TIMEOUT_MS) {
                    startCompatibilityVoice(currentGeneration, "偵測到本地發言，但 WebRTC 沒有傳送音訊資料")
                    break
                }
            }
        }
    }

    private fun handleUnexpectedSocketEnd(code: Int, reason: String) {
        handshakeTimeoutJob?.cancel()
        rtc?.close()
        rtc = null
        val phase = VoiceSessionStore.state.value.phase
        if (phase == ConnectionPhase.Failed || phase == ConnectionPhase.Disconnected) return
        val normalizedReason = safeCode(reason)
        val failureCode = when {
            normalizedReason.isNotBlank() && normalizedReason != "UNKNOWN" -> normalizedReason
            code == 4001 -> "JOIN_TICKET_REQUIRED"
            code == 4002 -> "INVALID_TARGET"
            code == 4003 -> "IDENTITY_REJECTED"
            code == 4004 -> "SERVER_REJECTED"
            code == 4005 -> "IDENTITY_IN_USE"
            code == 4006 -> "ACCELERATION_UNAVAILABLE"
            code == 1006 -> "GATEWAY_NETWORK_LOST"
            code == 1011 -> "GATEWAY_SESSION_ENDED"
            else -> "GATEWAY_CONNECTION_CLOSED"
        }
        failSession(failureCode, failureMessage(failureCode, ""))
    }

    private fun failSession(code: String, message: String) {
        handshakeTimeoutJob?.cancel()
        audioConnectionTimeoutJob?.cancel()
        voiceAudioHealthJob?.cancel()
        compatibilityAudio?.close()
        compatibilityAudio = null
        rtc?.close()
        rtc = null
        screenShareClient?.stopAll()
        screenShareClient = null
        ScreenShareMediaStore.clear()
        VoiceSessionStore.update {
            it.copy(
                phase = ConnectionPhase.Failed,
                audioReady = false,
                errorCode = safeCode(code),
                errorMessage = message.take(400),
            )
        }
        updateNotification("语音连接已结束")
        socket?.close(NORMAL_CLOSE, "session-failed")
        socket = null
        activeRequest = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun failWithoutForeground(code: String, message: String) {
        VoiceSessionStore.update { it.copy(phase = ConnectionPhase.Failed, errorCode = code, errorMessage = message) }
    }

    private fun stopSession() {
        intentionalStop = true
        generation++
        connectJob?.cancel()
        handshakeTimeoutJob?.cancel()
        closeCurrentTransport(sendStop = true)
        activeRequest = null
        VoiceSessionStore.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun closeCurrentTransport(sendStop: Boolean) {
        if (sendStop) {
            sendJson(JSONObject().put("type", "webrtcStop"))
            val activeStreamId = VoiceSessionStore.state.value.activeScreenShareId
            if (activeStreamId.isNotBlank()) sendJson(JSONObject().put("type", "screenShareStop").put("streamId", activeStreamId))
        }
        audioConnectionTimeoutJob?.cancel()
        audioConnectionTimeoutJob = null
        voiceAudioHealthJob?.cancel()
        voiceAudioHealthJob = null
        lastRemoteAudibleVoiceActivityAtMs = 0L
        compatibilityAudio?.close()
        compatibilityAudio = null
        fallbackAudioGeneration = -1L
        rtc?.close()
        rtc = null
        screenShareClient?.stopAll()
        screenShareClient = null
        pendingScreenShareStartId = ""
        ScreenShareMediaStore.clear()
        socket?.close(NORMAL_CLOSE, "client-disconnect")
        socket = null
    }

    private fun startVoiceForeground(notification: Notification, mediaProjection: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                if (mediaProjection) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
            startForeground(
                NOTIFICATION_ID,
                notification,
                serviceTypes,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateForegroundServiceType(mediaProjection: Boolean): Boolean {
        if (VoiceSessionStore.state.value.phase == ConnectionPhase.Disconnected) return false
        return runCatching {
            startVoiceForeground(
                buildNotification(if (mediaProjection) "正在共享屏幕 · 语音连接已保持" else getString(R.string.notification_voice_connected)),
                mediaProjection,
            )
            true
        }.getOrElse { error ->
            if (mediaProjection) setScreenShareError("无法启动屏幕共享前台服务：${error.message.orEmpty()}")
            false
        }
    }

    private fun startScreenShare(projectionData: Intent?) {
        if (projectionData == null) {
            setScreenShareError("没有取得系统屏幕录制授权")
            return
        }
        if (VoiceSessionStore.state.value.phase != ConnectionPhase.Connected) {
            setScreenShareError("请先连接语音服务器，再开始屏幕共享")
            return
        }
        if (VoiceSessionStore.state.value.activeScreenShareId.isNotBlank()
            || VoiceSessionStore.state.value.screenShareStarting
        ) return
        if (!updateForegroundServiceType(mediaProjection = true)) return
        val requestId = UUID.randomUUID().toString()
        pendingScreenShareStartId = requestId
        VoiceSessionStore.update { it.copy(screenShareStarting = true, screenShareError = "") }
        serviceScope.launch {
            runCatching {
                ensureScreenShareClient().startCapture(projectionData)
                check(sendJson(
                    JSONObject()
                        .put("type", "screenShareStart")
                        .put("requestId", requestId)
                        .put("name", "${VoiceSessionStore.state.value.nickname} 的屏幕".take(120))
                        .put("audio", false),
                )) { "WebSpeak 网关连接已中断，未能发起屏幕共享" }
            }.onFailure { error ->
                pendingScreenShareStartId = ""
                screenShareClient?.stopAll()
                updateForegroundServiceType(mediaProjection = false)
                setScreenShareError("无法开始屏幕采集：${error.message.orEmpty()}")
            }
        }
    }

    private fun stopActiveScreenShare() {
        val streamId = VoiceSessionStore.state.value.activeScreenShareId
        if (streamId.isNotBlank()) sendJson(JSONObject().put("type", "screenShareStop").put("streamId", streamId))
        pendingScreenShareStartId = ""
        screenShareClient?.let { client ->
            if (streamId.isNotBlank()) client.stopStream(streamId) else client.stopAll()
        }
        VoiceSessionStore.update { previous ->
            previous.copy(
                screenShares = previous.screenShares.filterNot { it.streamId == streamId },
                activeScreenShareId = "",
                screenShareStarting = false,
            )
        }
        updateForegroundServiceType(mediaProjection = false)
    }

    private fun joinScreenShare(streamId: String) {
        if (streamId.isBlank() || streamId.length > 128) return
        if (VoiceSessionStore.state.value.watchingScreenShareId.isNotBlank()) leaveScreenShare(VoiceSessionStore.state.value.watchingScreenShareId)
        VoiceSessionStore.update { it.copy(screenShareError = "") }
        sendJson(JSONObject().put("type", "screenShareJoin").put("requestId", UUID.randomUUID().toString()).put("streamId", streamId))
    }

    private fun leaveScreenShare(streamId: String) {
        if (streamId.isBlank()) return
        sendJson(JSONObject().put("type", "screenShareLeave").put("requestId", UUID.randomUUID().toString()).put("streamId", streamId))
        screenShareClient?.stopStream(streamId)
        ScreenShareMediaStore.remove(streamId)
        VoiceSessionStore.update { previous ->
            previous.copy(watchingScreenShareId = if (previous.watchingScreenShareId == streamId) "" else previous.watchingScreenShareId)
        }
    }

    private fun ensureScreenShareClient(): NativeScreenShareClient {
        screenShareClient?.let { return it }
        return NativeScreenShareClient(
            context = this,
            iceServers = screenShareIceServers,
            sendSignal = { streamId, targetPeerId, signal ->
                sendJson(
                    JSONObject()
                        .put("type", "screenShareSignal")
                        .put("streamId", streamId)
                        .put("targetPeerId", targetPeerId)
                        .put("signal", signal),
                )
            },
            onCaptureEnded = { serviceScope.launch { stopActiveScreenShare() } },
            onMediaError = ::setScreenShareError,
        ).also { screenShareClient = it }
    }

    private fun parseScreenShareIceServers(array: JSONArray?): List<PeerConnection.IceServer> {
        if (array == null) return defaultScreenShareIceServers()
        val parsed = mutableListOf<PeerConnection.IceServer>()
        for (index in 0 until array.length().coerceAtMost(8)) {
            val item = array.optJSONObject(index) ?: continue
            val rawUrls = item.opt("urls")
            val urls = when (rawUrls) {
                is String -> listOf(rawUrls)
                is JSONArray -> buildList {
                    for (urlIndex in 0 until rawUrls.length().coerceAtMost(8)) rawUrls.optString(urlIndex).takeIf(String::isNotBlank)?.let(::add)
                }
                else -> emptyList()
            }.map(String::trim)
                .filter { url -> url.length <= 512 && url.startsWith("stun:", true)
                    || url.length <= 512 && url.startsWith("stuns:", true)
                    || url.length <= 512 && url.startsWith("turn:", true)
                    || url.length <= 512 && url.startsWith("turns:", true) }
            if (urls.isEmpty()) continue
            val username = item.optString("username", "").take(512)
            val credential = item.optString("credential", "").take(512)
            val builder = PeerConnection.IceServer.builder(urls)
            if (username.isNotBlank()) builder.setUsername(username)
            if (credential.isNotBlank()) builder.setPassword(credential)
            parsed += builder.createIceServer()
        }
        return parsed.ifEmpty { defaultScreenShareIceServers() }
    }

    private fun setScreenShareError(message: String) {
        VoiceSessionStore.update { it.copy(screenShareStarting = false, screenShareError = message.take(400)) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_voice),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "在语音频道通话期间保持麦克风服务并显示会话状态"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(content: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val toggleIntent = Intent(this, VoiceSessionService::class.java)
            .setAction(ACTION_SET_MICROPHONE_MUTED)
            .putExtra(EXTRA_MUTED, !microphoneMuted)
        val togglePending = PendingIntent.getService(
            this,
            REQUEST_TOGGLE_MIC,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnectIntent = PendingIntent.getService(
            this,
            REQUEST_DISCONNECT,
            Intent(this, VoiceSessionService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_voice_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(notificationContent(content))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(
                if (microphoneMuted) R.drawable.ic_voice_notification else R.drawable.ic_voice_notification,
                getString(if (microphoneMuted) R.string.notification_unmute else R.string.notification_mute),
                togglePending,
            )
            .addAction(R.drawable.ic_voice_notification, getString(R.string.notification_disconnect), disconnectIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        if (VoiceSessionStore.state.value.phase == ConnectionPhase.Disconnected) return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(content))
    }

    private fun notificationContent(fallback: String): String {
        val state = VoiceSessionStore.state.value
        if (state.phase != ConnectionPhase.Connected) return fallback
        val channelName = state.channels.firstOrNull { it.id == state.currentChannelId }?.name
            ?.takeIf { it.isNotBlank() }
            ?: return fallback
        return getString(R.string.notification_connected_to_channel, channelName)
    }

    private fun setMutedInternal(muted: Boolean) {
        val stateChanged = microphoneMuted != muted
        microphoneMuted = muted
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean(KEY_MIC_MUTED, muted).apply()
        rtc?.setMicrophoneMuted(muted)
        compatibilityAudio?.setMuted(muted)
        VoiceSessionStore.update { it.copy(microphoneMuted = muted) }
        if (stateChanged) playMicrophoneStateCue(muted)
        sendCommand("setMicrophoneMuted", JSONObject().put("muted", muted))
        if (VoiceSessionStore.state.value.phase != ConnectionPhase.Disconnected) updateNotification(
            if (muted) "语音连接已保持 · 麦克风静音" else getString(R.string.notification_voice_connected),
        )
    }

    private fun playMicrophoneStateCue(muted: Boolean) {
        runCatching {
            val generator = microphoneCueToneGenerator
                ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, MICROPHONE_CUE_VOLUME).also {
                    microphoneCueToneGenerator = it
                }
            generator.stopTone()
            generator.startTone(
                if (muted) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_ACK,
                MICROPHONE_CUE_DURATION_MS,
            )
        }
    }

    private fun switchChannelInternal(channelId: String, password: String) {
        if (!channelId.matches(Regex("\\d{1,20}"))) return
        VoiceSessionStore.update { it.copy(pendingChannelId = channelId, errorCode = "", errorMessage = "") }
        val payload = JSONObject().put("channelId", channelId)
        if (password.isNotEmpty()) payload.put("password", password.take(512))
        sendCommand("switchChannel", payload)
    }

    private fun sendTextInternal(scope: String, targetClientId: Int, text: String) {
        val message = text.trim().take(500)
        if (message.isBlank()) return
        when (scope) {
            "server" -> sendCommand("sendServerMessage", JSONObject().put("message", message))
            "private" -> {
                if (targetClientId <= 0) return
                sendCommand("sendPrivateMessage", JSONObject().put("clientId", targetClientId).put("message", message))
            }
            else -> sendCommand("sendTextMessage", JSONObject().put("message", message))
        }
        val current = VoiceSessionStore.state.value
        val localMessage = ChatMessage(
            id = "local-${System.currentTimeMillis()}-${requestSequence.incrementAndGet()}",
            scope = when (scope) {
                "server" -> ChatScope.Server
                "private" -> ChatScope.Private
                else -> ChatScope.Channel
            },
            targetId = current.currentChannelId.takeIf { scope == "channel" },
            conversationId = targetClientId.takeIf { scope == "private" && it > 0 }?.toString(),
            senderId = current.selfClientId.takeIf { it > 0 },
            senderName = current.nickname,
            message = message,
            timestamp = System.currentTimeMillis(),
            isSelf = true,
        )
        VoiceSessionStore.update { previous -> previous.copy(messages = (previous.messages + localMessage).takeLast(MAX_MESSAGES)) }
        persistMessage(localMessage)
    }

    private fun sendCommand(type: String, payload: JSONObject) {
        val id = "android-${requestSequence.incrementAndGet()}"
        sendJson(JSONObject().put("type", type).put("payload", payload).put("requestId", id))
    }

    private fun setOutputVolumeInternal(value: Float) {
        outputVolume = value.coerceIn(0f, 1f)
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putFloat(KEY_OUTPUT_VOLUME, outputVolume).apply()
        rtc?.setOutputVolume(outputVolume.toDouble())
        VoiceSessionStore.update { it.copy(outputVolume = outputVolume) }
    }

    private fun setMemberVolumeInternal(clientId: Int, value: Float) {
        if (clientId <= 0) return
        VoiceSessionStore.update { previous ->
            previous.copy(
                members = previous.members.map { if (it.id == clientId) it.copy(volume = value.toDouble()) else it },
                channels = previous.channels.map { channel ->
                    channel.copy(members = channel.members.map { if (it.id == clientId) it.copy(volume = value.toDouble()) else it })
                },
            )
        }
    }

    private fun sendLatencyProbe() {
        val sequence = "android-${requestSequence.incrementAndGet()}"
        pendingLatencyProbes[sequence] = System.currentTimeMillis()
        sendJson(
            JSONObject()
                .put("type", "latencyProbe")
                .put("requestId", sequence)
                .put("payload", JSONObject().put("sequence", sequence)),
        )
    }

    private fun persistMessage(message: ChatMessage) {
        val gatewayKey = historyGatewayKey
        val serverKey = historyServerKey
        if (gatewayKey.isBlank() || serverKey.isBlank()) return
        serviceScope.launch(Dispatchers.IO) {
            runCatching { chatHistoryStore.save(gatewayKey, serverKey, message) }
        }
    }

    private fun clearChatHistory() {
        val gatewayKey = historyGatewayKey
        val serverKey = historyServerKey
        if (gatewayKey.isNotBlank() && serverKey.isNotBlank()) {
            serviceScope.launch(Dispatchers.IO) { runCatching { chatHistoryStore.clear(gatewayKey, serverKey) } }
        }
        VoiceSessionStore.update { it.copy(messages = emptyList()) }
    }

    private fun sendJson(message: JSONObject): Boolean {
        val currentSocket = socket ?: return false
        return runCatching { currentSocket.send(message.toString()) }.getOrDefault(false)
    }

    private fun isCurrent(expectedGeneration: Long, webSocket: WebSocket): Boolean =
        expectedGeneration == generation && socket === webSocket

    private fun appendEvent(event: ServerEvent) {
        VoiceSessionStore.update { previous -> previous.copy(events = (previous.events + event).takeLast(MAX_EVENTS)) }
    }

    private fun mergeMembers(existing: List<VoiceMember>, incoming: List<VoiceMember>): List<VoiceMember> {
        val updates = incoming.associateBy { it.id }
        val merged = existing.map { previous -> updates[previous.id]?.let { it.copy(speaking = previous.speaking) } ?: previous }
        val known = merged.mapTo(HashSet()) { it.id }
        return (merged + incoming.filter { it.id !in known }).take(MAX_MEMBERS)
    }

    private fun mergeScreenShares(existing: List<com.echosixhiya.webspeak.android.model.ScreenShareStream>, stream: com.echosixhiya.webspeak.android.model.ScreenShareStream) =
        (existing.filterNot { it.streamId == stream.streamId } + stream).take(64)

    private fun parseEvents(array: JSONArray?): List<ServerEvent> = buildList {
        if (array != null) for (index in 0 until array.length().coerceAtMost(MAX_EVENTS)) {
            array.optJSONObject(index)?.let(GatewayMessageParser::event)?.let(::add)
        }
    }

    private fun parseIntSet(array: JSONArray?): Set<Int> = buildSet {
        if (array != null) for (index in 0 until array.length().coerceAtMost(MAX_MEMBERS)) {
            array.optInt(index, 0).takeIf { it > 0 }?.let(::add)
        }
    }

    private fun safeCode(raw: String): String = raw.trim().uppercase().replace(Regex("[^A-Z0-9_-]+"), "_").take(64).ifBlank { "UNKNOWN" }

    private fun failureMessage(code: String, detail: String): String {
        val specific = when (code) {
            "SERVER_PASSWORD_REQUIRED" -> "服务器需要密码，请输入密码后重试。"
            "INVALID_SERVER_PASSWORD" -> "服务器密码错误，请检查后重试。"
            "IDENTITY_IN_USE" -> "此 TeamSpeak 身份已在其他会话中使用。"
            "IDENTITY_REJECTED", "IDENTITY_INVALID" -> "保存的 TeamSpeak 身份无效；可关闭身份保留后重新连接。"
            "SERVER_REJECTED", "CHANNEL_FULL" -> "服务器或频道拒绝了本次连接。"
            "TARGET_NOT_ALLOWED", "INVALID_TARGET" -> "此 TeamSpeak 地址不允许连接。"
            "JOIN_TICKET_REQUIRED" -> "连接票据无效或已过期，请重新加入。"
            "GATEWAY_NETWORK_LOST", "GATEWAY_SESSION_ENDED" -> "与 WebSpeak 网关的连接已中断，请检查网络后重新加入。"
            "TEAM_SPEAK_CLIENT_UNAVAILABLE" -> "网关的 TeamSpeak 客户端暂不可用。"
            "WEBRTC_NEGOTIATION_FAILED" -> "WebRTC 语音协商失败；请检查网关语音设置和网络。"
            else -> "TeamSpeak 连接失败（$code）。"
        }
        return if (detail.isBlank() || specific.contains(detail, ignoreCase = true)) specific else "$specific $detail"
    }

    private fun JSONObject.stringValue(key: String): String {
        val value = opt(key)
        return when (value) {
            is String -> value
            is Number -> value.toString()
            else -> ""
        }
    }

    private fun Intent.toJoinRequest(): JoinRequest? {
        val gateway = getStringExtra(EXTRA_GATEWAY).orEmpty().trim()
        val nickname = getStringExtra(EXTRA_NICKNAME).orEmpty().trim()
        if (gateway.isBlank() || nickname.isBlank()) return null
        return JoinRequest(
            gatewayUrl = gateway.take(512),
            nickname = nickname.take(30),
            teamSpeakTarget = getStringExtra(EXTRA_TARGET).orEmpty().take(255),
            channel = getStringExtra(EXTRA_CHANNEL).orEmpty().take(100),
            serverPassword = getStringExtra(EXTRA_PASSWORD).orEmpty().take(512),
            inviteToken = getStringExtra(EXTRA_INVITE).orEmpty().take(128),
            rememberIdentity = getBooleanExtra(EXTRA_REMEMBER_IDENTITY, true),
        )
    }

    @Suppress("DEPRECATION")
    private fun Intent.projectionData(): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
    } else {
        getParcelableExtra(EXTRA_PROJECTION_DATA)
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "webspeak.voice.session"
        private const val NOTIFICATION_ID = 6201
        private const val REQUEST_OPEN_APP = 6202
        private const val REQUEST_TOGGLE_MIC = 6203
        private const val REQUEST_DISCONNECT = 6204
        private const val NORMAL_CLOSE = 1000
        private const val HANDSHAKE_TIMEOUT_MS = 35_000L
        private const val VOICE_RTC_CONNECTION_TIMEOUT_MS = 15_000L
        private const val VOICE_AUDIO_STATS_INTERVAL_MS = 2_000L
        private const val VOICE_AUDIO_STATS_TIMEOUT_MS = 1_500L
        private const val VOICE_ACTIVITY_GRACE_MS = 1_800L
        private const val AUDIO_RTP_STALL_TIMEOUT_MS = 5_000L
        private const val MICROPHONE_CUE_VOLUME = 40
        private const val MICROPHONE_CUE_DURATION_MS = 150
        private const val MAX_MESSAGES = 2000
        private const val MAX_EVENTS = 500
        private const val MAX_MEMBERS = 1024
        private const val PREFERENCES = "webspeak_voice_preferences"
        private const val KEY_MIC_MUTED = "microphone_muted"
        private const val KEY_OUTPUT_VOLUME = "output_volume"

        private const val ACTION_CONNECT = "com.echosixhiya.webspeak.android.action.CONNECT"
        private const val ACTION_DISCONNECT = "com.echosixhiya.webspeak.android.action.DISCONNECT"
        private const val ACTION_SET_MICROPHONE_MUTED = "com.echosixhiya.webspeak.android.action.SET_MICROPHONE_MUTED"
        private const val ACTION_SWITCH_CHANNEL = "com.echosixhiya.webspeak.android.action.SWITCH_CHANNEL"
        private const val ACTION_SEND_TEXT = "com.echosixhiya.webspeak.android.action.SEND_TEXT"
        private const val ACTION_SET_AWAY = "com.echosixhiya.webspeak.android.action.SET_AWAY"
        private const val ACTION_SEND_POKE = "com.echosixhiya.webspeak.android.action.SEND_POKE"
        private const val ACTION_SET_WHISPER_TARGETS = "com.echosixhiya.webspeak.android.action.SET_WHISPER_TARGETS"
        private const val ACTION_SET_WHISPER_ACTIVE = "com.echosixhiya.webspeak.android.action.SET_WHISPER_ACTIVE"
        private const val ACTION_SET_MEMBER_VOLUME = "com.echosixhiya.webspeak.android.action.SET_MEMBER_VOLUME"
        private const val ACTION_MOVE_MEMBER = "com.echosixhiya.webspeak.android.action.MOVE_MEMBER"
        private const val ACTION_SET_OUTPUT_VOLUME = "com.echosixhiya.webspeak.android.action.SET_OUTPUT_VOLUME"
        private const val ACTION_LATENCY_PROBE = "com.echosixhiya.webspeak.android.action.LATENCY_PROBE"
        private const val ACTION_CLEAR_CHAT_HISTORY = "com.echosixhiya.webspeak.android.action.CLEAR_CHAT_HISTORY"
        private const val ACTION_START_SCREEN_SHARE = "com.echosixhiya.webspeak.android.action.START_SCREEN_SHARE"
        private const val ACTION_STOP_SCREEN_SHARE = "com.echosixhiya.webspeak.android.action.STOP_SCREEN_SHARE"
        private const val ACTION_JOIN_SCREEN_SHARE = "com.echosixhiya.webspeak.android.action.JOIN_SCREEN_SHARE"
        private const val ACTION_LEAVE_SCREEN_SHARE = "com.echosixhiya.webspeak.android.action.LEAVE_SCREEN_SHARE"

        private const val EXTRA_GATEWAY = "gateway"
        private const val EXTRA_NICKNAME = "nickname"
        private const val EXTRA_TARGET = "target"
        private const val EXTRA_CHANNEL = "channel"
        private const val EXTRA_PASSWORD = "password"
        private const val EXTRA_INVITE = "invite"
        private const val EXTRA_REMEMBER_IDENTITY = "rememberIdentity"
        private const val EXTRA_MUTED = "muted"
        private const val EXTRA_CHANNEL_ID = "channelId"
        private const val EXTRA_CHANNEL_PASSWORD = "channelPassword"
        private const val EXTRA_SCOPE = "scope"
        private const val EXTRA_TARGET_CLIENT_ID = "targetClientId"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_AWAY = "away"
        private const val EXTRA_TARGET_IDS = "targetIds"
        private const val EXTRA_ACTIVE = "active"
        private const val EXTRA_VOLUME = "volume"
        private const val EXTRA_PROJECTION_DATA = "projectionData"
        private const val EXTRA_STREAM_ID = "streamId"

        private fun defaultScreenShareIceServers() = listOf(
            PeerConnection.IceServer.builder("stun:turn.teamspeak.com:3478").createIceServer(),
            PeerConnection.IceServer.builder("stun:turn2.teamspeak.com:3478").createIceServer(),
        )

        fun connect(context: Context, request: JoinRequest) {
            val intent = Intent(context, VoiceSessionService::class.java)
                .setAction(ACTION_CONNECT)
                .putExtra(EXTRA_GATEWAY, request.gatewayUrl)
                .putExtra(EXTRA_NICKNAME, request.nickname)
                .putExtra(EXTRA_TARGET, request.teamSpeakTarget)
                .putExtra(EXTRA_CHANNEL, request.channel)
                .putExtra(EXTRA_PASSWORD, request.serverPassword)
                .putExtra(EXTRA_INVITE, request.inviteToken)
                .putExtra(EXTRA_REMEMBER_IDENTITY, request.rememberIdentity)
            ContextCompat.startForegroundService(context, intent)
        }

        fun disconnect(context: Context) = startCommand(context, ACTION_DISCONNECT)

        fun setMicrophoneMuted(context: Context, muted: Boolean) = startCommand(context, ACTION_SET_MICROPHONE_MUTED) {
            putExtra(EXTRA_MUTED, muted)
        }

        fun switchChannel(context: Context, channelId: String, password: String = "") = startCommand(context, ACTION_SWITCH_CHANNEL) {
            putExtra(EXTRA_CHANNEL_ID, channelId)
            putExtra(EXTRA_CHANNEL_PASSWORD, password)
        }

        fun sendTextMessage(context: Context, scope: String, targetId: String, message: String) = startCommand(context, ACTION_SEND_TEXT) {
            putExtra(EXTRA_SCOPE, scope)
            putExtra(EXTRA_TARGET_CLIENT_ID, targetId.toIntOrNull() ?: 0)
            putExtra(EXTRA_MESSAGE, message)
        }

        fun setAway(context: Context, away: Boolean) = startCommand(context, ACTION_SET_AWAY) { putExtra(EXTRA_AWAY, away) }

        fun sendPoke(context: Context, clientId: Int, message: String = "") = startCommand(context, ACTION_SEND_POKE) {
            putExtra(EXTRA_TARGET_CLIENT_ID, clientId)
            putExtra(EXTRA_MESSAGE, message)
        }

        fun setWhisperTargets(context: Context, clientIds: IntArray) = startCommand(context, ACTION_SET_WHISPER_TARGETS) {
            putExtra(EXTRA_TARGET_IDS, clientIds)
        }

        fun setWhisperActive(context: Context, active: Boolean) = startCommand(context, ACTION_SET_WHISPER_ACTIVE) {
            putExtra(EXTRA_ACTIVE, active)
        }

        fun setMemberVolume(context: Context, clientId: Int, volume: Float) = startCommand(context, ACTION_SET_MEMBER_VOLUME) {
            putExtra(EXTRA_TARGET_CLIENT_ID, clientId)
            putExtra(EXTRA_VOLUME, volume)
        }

        fun moveMember(context: Context, clientId: Int, channelId: String, password: String = "") = startCommand(context, ACTION_MOVE_MEMBER) {
            putExtra(EXTRA_TARGET_CLIENT_ID, clientId)
            putExtra(EXTRA_CHANNEL_ID, channelId)
            putExtra(EXTRA_CHANNEL_PASSWORD, password)
        }

        fun setOutputVolume(context: Context, volume: Float) = startCommand(context, ACTION_SET_OUTPUT_VOLUME) {
            putExtra(EXTRA_VOLUME, volume)
        }

        fun requestLatency(context: Context) = startCommand(context, ACTION_LATENCY_PROBE)

        fun clearChatHistory(context: Context) = startCommand(context, ACTION_CLEAR_CHAT_HISTORY)

        fun startScreenShare(context: Context, projectionData: Intent) = startCommand(context, ACTION_START_SCREEN_SHARE) {
            putExtra(EXTRA_PROJECTION_DATA, projectionData)
        }

        fun stopScreenShare(context: Context) = startCommand(context, ACTION_STOP_SCREEN_SHARE)

        fun joinScreenShare(context: Context, streamId: String) = startCommand(context, ACTION_JOIN_SCREEN_SHARE) {
            putExtra(EXTRA_STREAM_ID, streamId)
        }

        fun leaveScreenShare(context: Context, streamId: String) = startCommand(context, ACTION_LEAVE_SCREEN_SHARE) {
            putExtra(EXTRA_STREAM_ID, streamId)
        }

        private inline fun startCommand(context: Context, action: String, extras: Intent.() -> Unit = {}) {
            val intent = Intent(context, VoiceSessionService::class.java).setAction(action).apply(extras)
            context.startService(intent)
        }
    }
}
