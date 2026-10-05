package com.echosixhiya.webspeak.android.model

enum class ConnectionPhase {
    Disconnected,
    Connecting,
    Connected,
    Reconnecting,
    Failed,
}

enum class ChatScope {
    Channel,
    Server,
    Private,
    System,
}

enum class ClientTab {
    Voice,
    Channels,
    Chat,
    Settings,
}

enum class VoiceAudioTransport {
    None,
    WebRtc,
    Compatibility,
}

data class VoiceMember(
    val id: Int,
    val nickname: String,
    val uid: String = "",
    val channelId: String = "",
    val avatar: String? = null,
    val away: Boolean = false,
    val inputMuted: Boolean = false,
    val outputMuted: Boolean = false,
    val channelCommander: Boolean = false,
    val speaking: Boolean = false,
    val isSelf: Boolean = false,
    val volume: Double = 1.0,
)

data class VoiceChannel(
    val id: String,
    val name: String,
    val parentId: String = "0",
    val order: String = "0",
    val depth: Int = 0,
    val topic: String = "",
    val passwordProtected: Boolean = false,
    val members: List<VoiceMember> = emptyList(),
)

data class ChatMessage(
    val id: String,
    val scope: ChatScope,
    val targetId: String? = null,
    val conversationId: String? = null,
    val senderId: Int? = null,
    val senderName: String,
    val message: String,
    val timestamp: Long,
    val isSelf: Boolean = false,
)

data class ServerEvent(
    val id: String,
    val kind: String,
    val message: String,
    val timestamp: Long,
)

data class PokeAlert(
    val id: String,
    val invokerId: Int,
    val invokerName: String,
    val message: String,
    val timestamp: Long,
)

data class ScreenShareViewer(
    val peerId: String,
    val nickname: String,
    val avatar: String? = null,
)

data class ScreenShareStream(
    val streamId: String,
    val source: String,
    val ownerPeerId: String,
    val ownerClientId: Int? = null,
    val ownerNickname: String,
    val name: String,
    val audio: Boolean,
    val createdAt: Long,
    val viewerCount: Int,
    val viewers: List<ScreenShareViewer> = emptyList(),
)

data class VoiceSessionState(
    val phase: ConnectionPhase = ConnectionPhase.Disconnected,
    val gatewayUrl: String = "",
    val gatewayName: String = "WebSpeak",
    val teamSpeakTarget: String = "",
    val nickname: String = "",
    val selfClientId: Int = 0,
    val currentChannelId: String = "",
    val channels: List<VoiceChannel> = emptyList(),
    val members: List<VoiceMember> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val events: List<ServerEvent> = emptyList(),
    val pokes: List<PokeAlert> = emptyList(),
    val screenShares: List<ScreenShareStream> = emptyList(),
    val activeScreenShareId: String = "",
    val watchingScreenShareId: String = "",
    val screenShareStarting: Boolean = false,
    val screenShareError: String = "",
    val whisperTargetIds: Set<Int> = emptySet(),
    val whisperActive: Boolean = false,
    val microphoneMuted: Boolean = false,
    val outputVolume: Float = 1f,
    val away: Boolean = false,
    val webrtcAvailable: Boolean = false,
    val audioReady: Boolean = false,
    val audioTransport: VoiceAudioTransport = VoiceAudioTransport.None,
    val audioError: String = "",
    val errorCode: String = "",
    val errorMessage: String = "",
    val reconnectAttempt: Int = 0,
    val latencyRttMs: Long? = null,
    val teamSpeakLatencyMs: Long? = null,
    val teamSpeakReachable: Boolean? = null,
    val pendingChannelId: String = "",
)

data class JoinRequest(
    val gatewayUrl: String,
    val nickname: String,
    val teamSpeakTarget: String = "",
    val channel: String = "",
    val serverPassword: String = "",
    val inviteToken: String = "",
    val rememberIdentity: Boolean = true,
    val identity: String = "",
)
