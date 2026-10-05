package com.echosixhiya.webspeak.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echosixhiya.webspeak.android.R
import com.echosixhiya.webspeak.android.data.ScreenShareMediaStore
import com.echosixhiya.webspeak.android.model.ChatMessage
import com.echosixhiya.webspeak.android.model.ChatScope
import com.echosixhiya.webspeak.android.model.ClientTab
import com.echosixhiya.webspeak.android.model.ConnectionPhase
import com.echosixhiya.webspeak.android.model.ServerEvent
import com.echosixhiya.webspeak.android.model.VoiceAudioTransport
import com.echosixhiya.webspeak.android.model.VoiceChannel
import com.echosixhiya.webspeak.android.model.VoiceMember
import com.echosixhiya.webspeak.android.model.VoiceSessionState
import org.webrtc.SurfaceViewRenderer
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceWorkspaceScreen(
    state: VoiceSessionState,
    onDisconnect: () -> Unit,
    onToggleMicrophone: () -> Unit,
    onSwitchChannel: (String, String) -> Unit,
    onSendMessage: (String, String, String) -> Unit,
    onSetAway: (Boolean) -> Unit,
    onPoke: (Int) -> Unit,
    onSetWhisperTargets: (IntArray) -> Unit,
    onWhisperActive: (Boolean) -> Unit,
    onMemberVolume: (Int, Float) -> Unit,
    onMoveMember: (Int, String) -> Unit,
    onOutputVolume: (Float) -> Unit,
    onToggleSpeaker: () -> Unit,
    onLatencyProbe: () -> Unit,
    onClearChatHistory: () -> Unit,
    onStartScreenShare: () -> Unit,
    onStopScreenShare: () -> Unit,
    onJoinScreenShare: (String) -> Unit,
    onLeaveScreenShare: (String) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(ClientTab.Voice) }
    var chatScope by rememberSaveable { mutableStateOf(ChatScope.Channel) }
    var draft by rememberSaveable { mutableStateOf("") }
    var privateChatClientId by rememberSaveable { mutableStateOf(0) }
    var confirmScreenShare by rememberSaveable { mutableStateOf(false) }
    val currentChannel = state.channels.firstOrNull { it.id == state.currentChannelId }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar {
                        ClientTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = { NavigationTabIcon(tab, state.messages.count { !it.isSelf }) },
                                label = { Text(stringResource(tab.labelResource)) },
                                alwaysShowLabel = true,
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (useNavigationRail) {
                    NavigationRail {
                        ClientTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = { NavigationTabIcon(tab, state.messages.count { !it.isSelf }) },
                                label = { Text(stringResource(tab.labelResource)) },
                                alwaysShowLabel = true,
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxSize()) {
                    when (selectedTab) {
                        ClientTab.Voice -> VoiceHomeContent(
                            state = state,
                            currentChannel = currentChannel,
                            onToggleMicrophone = onToggleMicrophone,
                            onToggleSpeaker = onToggleSpeaker,
                            onSetAway = onSetAway,
                            onSwitchToChannels = { selectedTab = ClientTab.Channels },
                            onWhisperActive = onWhisperActive,
                            onMemberPrivateChat = { clientId ->
                                privateChatClientId = clientId
                                chatScope = ChatScope.Private
                                selectedTab = ClientTab.Chat
                            },
                            onPoke = onPoke,
                            onSetWhisperTargets = onSetWhisperTargets,
                            onMemberVolume = onMemberVolume,
                            channels = state.channels,
                            onMoveMember = onMoveMember,
                            onStartScreenShare = { confirmScreenShare = true },
                            onStopScreenShare = onStopScreenShare,
                            onJoinScreenShare = onJoinScreenShare,
                        )
                        ClientTab.Channels -> ChannelsContent(
                            channels = state.channels,
                            currentChannelId = state.currentChannelId,
                            state = state,
                            onSelectChannel = onSwitchChannel,
                        )
                        ClientTab.Chat -> ChatContent(
                            messages = state.messages,
                            events = state.events,
                            chatScope = chatScope,
                            privateChatClientId = privateChatClientId,
                            currentChannelId = state.currentChannelId,
                            draft = draft,
                            onScopeChange = { chatScope = it },
                            onDraftChange = { draft = it },
                            onSend = {
                                val text = draft.trim()
                                if (text.isNotEmpty()) {
                                    val wireScope = when (chatScope) {
                                        ChatScope.Channel -> "channel"
                                        ChatScope.Server -> "server"
                                        ChatScope.Private -> "private"
                                        ChatScope.System -> "channel"
                                    }
                                    onSendMessage(wireScope, privateChatClientId.toString(), text)
                                    draft = ""
                                }
                            },
                        )
                        ClientTab.Settings -> SettingsContent(
                            state = state,
                            onToggleMicrophone = onToggleMicrophone,
                            onSetAway = onSetAway,
                            onDisconnect = onDisconnect,
                            onOutputVolume = onOutputVolume,
                            onLatencyProbe = onLatencyProbe,
                            onClearChatHistory = onClearChatHistory,
                        )
                    }
                }
            }
        }
        val watchingStream = state.screenShares.firstOrNull { it.streamId == state.watchingScreenShareId }
        if (watchingStream != null) {
            ScreenShareVideoDialog(
                stream = watchingStream,
                error = state.screenShareError,
                onDismiss = { onLeaveScreenShare(watchingStream.streamId) },
            )
        }
        if (confirmScreenShare) {
            AlertDialog(
                onDismissRequest = { confirmScreenShare = false },
                title = { Text(stringResource(R.string.screen_share_confirm_title)) },
                text = { Text(stringResource(R.string.screen_share_confirm_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmScreenShare = false
                            onStartScreenShare()
                        },
                    ) { Text(stringResource(R.string.screen_share_confirm_continue)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmScreenShare = false }) {
                        Text(stringResource(R.string.screen_share_confirm_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun NavigationTabIcon(tab: ClientTab, unread: Int) {
    when (tab) {
        ClientTab.Voice -> Icon(Icons.Filled.Headphones, contentDescription = null)
        ClientTab.Channels -> Icon(Icons.Filled.Groups, contentDescription = null)
        ClientTab.Chat -> BadgedBox(badge = {
            if (unread > 0) Badge { Text(unread.coerceAtMost(99).toString()) }
        }) { Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null) }
        ClientTab.Settings -> Icon(Icons.Filled.Settings, contentDescription = null)
    }
}

private val ClientTab.labelResource: Int
    get() = when (this) {
        ClientTab.Voice -> R.string.tab_voice
        ClientTab.Channels -> R.string.tab_channels
        ClientTab.Chat -> R.string.tab_chat
        ClientTab.Settings -> R.string.tab_settings
    }

@Composable
private fun VoiceHomeContent(
    state: VoiceSessionState,
    currentChannel: VoiceChannel?,
    onToggleMicrophone: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSetAway: (Boolean) -> Unit,
    onSwitchToChannels: () -> Unit,
    onWhisperActive: (Boolean) -> Unit,
    onMemberPrivateChat: (Int) -> Unit,
    onPoke: (Int) -> Unit,
    onSetWhisperTargets: (IntArray) -> Unit,
    onMemberVolume: (Int, Float) -> Unit,
    channels: List<VoiceChannel>,
    onMoveMember: (Int, String) -> Unit,
    onStartScreenShare: () -> Unit,
    onStopScreenShare: () -> Unit,
    onJoinScreenShare: (String) -> Unit,
) {
    val currentMembers = currentChannel?.members?.ifEmpty { state.members } ?: state.members
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        if (state.phase == ConnectionPhase.Reconnecting) {
            item {
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.workspace_reconnecting, state.reconnectAttempt)) },
                    leadingIcon = { Icon(Icons.Filled.WifiTethering, contentDescription = null) },
                )
            }
        }
        item {
            ElevatedCard(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                             Text(stringResource(R.string.workspace_voice_room), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                            Spacer(Modifier.height(4.dp))
                             Text(currentChannel?.name ?: stringResource(R.string.workspace_waiting_channel), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                             Text(pluralStringResource(R.plurals.member_count, currentMembers.size, currentMembers.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalIconButton(onClick = onToggleSpeaker, modifier = Modifier.size(52.dp)) {
                            Icon(
                                if (state.speakerEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                                contentDescription = stringResource(if (state.speakerEnabled) R.string.notification_playback_off else R.string.notification_playback_on),
                            )
                        }
                        FilledTonalIconButton(onClick = onToggleMicrophone, enabled = state.speakerEnabled, modifier = Modifier.size(52.dp)) {
                            Icon(
                                if (state.microphoneMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                contentDescription = stringResource(if (state.microphoneMuted) R.string.action_unmute else R.string.action_mute),
                            )
                        }
                        FilledTonalIconButton(onClick = onSwitchToChannels, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Filled.Groups, contentDescription = stringResource(R.string.action_switch_channel))
                        }
                        FilledTonalIconButton(onClick = { onSetAway(!state.away) }, modifier = Modifier.size(52.dp)) {
                            Icon(
                                if (state.away) Icons.Filled.Person else Icons.Filled.AccessTime,
                                contentDescription = stringResource(if (state.away) R.string.settings_return else R.string.settings_set_away),
                            )
                        }
                    }
                }
            }
        }
        if (state.whisperTargetIds.isNotEmpty()) {
            item {
                FilledTonalButton(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().pointerInput(state.whisperTargetIds) {
                        detectTapGestures(onPress = {
                            onWhisperActive(true)
                            tryAwaitRelease()
                            onWhisperActive(false)
                        })
                    },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.Headphones, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (state.whisperActive) R.string.whisper_active else R.string.whisper_hold))
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                     Text(stringResource(R.string.workspace_channel_members), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                     Text(pluralStringResource(R.plurals.speaking_count, currentMembers.count { it.speaking }, currentMembers.count { it.speaking }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                 AssistChip(onClick = onSwitchToChannels, label = { Text(stringResource(R.string.workspace_all_channels)) }, leadingIcon = { Icon(Icons.Filled.Groups, contentDescription = null) })
            }
        }
        if (currentMembers.isEmpty()) {
             item { EmptyStateCard(R.string.empty_no_members_title, R.string.empty_no_members_message) }
        } else {
            items(currentMembers, key = { it.id }) { member ->
                MemberCard(
                    member = member,
                    isSharingScreen = state.screenShares.any { share ->
                        share.ownerClientId == member.id || share.ownerNickname == member.nickname
                    },
                    whisperSelected = member.id in state.whisperTargetIds,
                    channels = channels,
                    onPrivateChat = { onMemberPrivateChat(member.id) },
                    onPoke = { onPoke(member.id) },
                    onToggleWhisper = {
                        val next = state.whisperTargetIds.toMutableSet().apply {
                            if (member.id in this) remove(member.id) else add(member.id)
                        }
                        onSetWhisperTargets(next.toIntArray())
                    },
                    onVolumeChange = { onMemberVolume(member.id, it) },
                    onMove = { channelId -> onMoveMember(member.id, channelId) },
                )
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WifiTethering, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                             Text(stringResource(R.string.screen_share_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                     state.screenShareStarting -> stringResource(R.string.screen_share_requesting)
                                      state.activeScreenShareId.isNotBlank() -> stringResource(R.string.screen_share_you_are_sharing)
                                      state.watchingScreenShareId.isNotBlank() -> stringResource(R.string.screen_share_watching)
                                      else -> stringResource(R.string.screen_share_privacy_warning)
                                 },
                                 style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            enabled = state.phase == ConnectionPhase.Connected,
                            onClick = if (state.activeScreenShareId.isNotBlank() || state.screenShareStarting) onStopScreenShare else onStartScreenShare,
                        ) {
                             Text(stringResource(if (state.activeScreenShareId.isNotBlank() || state.screenShareStarting) R.string.action_stop else R.string.action_start))
                        }
                    }
                    if (state.screenShareError.isNotBlank()) {
                        Text(stringResource(R.string.error_screen_share_generic), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (state.screenShares.isNotEmpty()) {
            item {
                 Text(stringResource(R.string.screen_share_live), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            items(state.screenShares, key = { it.streamId }) { share ->
                Card(shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WifiTethering, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(share.name, fontWeight = FontWeight.SemiBold)
                             Text(pluralStringResource(R.plurals.screen_viewer_count, share.viewerCount, share.ownerNickname, share.viewerCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        when {
                             share.streamId == state.activeScreenShareId -> Text(stringResource(R.string.screen_share_you_sharing_short), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                             share.streamId == state.watchingScreenShareId -> Text(stringResource(R.string.screen_share_watching_short), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                             else -> TextButton(onClick = { onJoinScreenShare(share.streamId) }) { Text(stringResource(R.string.action_view)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenShareVideoDialog(
    stream: com.echosixhiya.webspeak.android.model.ScreenShareStream,
    error: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val tracks by ScreenShareMediaStore.tracks.collectAsStateWithLifecycle()
    val track = tracks[stream.streamId]
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stream.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(stream.ownerNickname, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                     TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
                }
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    if (track == null) {
                         Text(stringResource(R.string.screen_share_connecting_view), color = androidx.compose.ui.graphics.Color.White)
                    } else {
                        val renderer = remember(stream.streamId) { SurfaceViewRenderer(context) }
                        AndroidView(factory = { renderer }, modifier = Modifier.fillMaxSize())
                        DisposableEffect(track, renderer) {
                            renderer.init(ScreenShareMediaStore.eglContext(), null)
                            renderer.setEnableHardwareScaler(true)
                            renderer.setMirror(false)
                            track.addSink(renderer)
                            onDispose {
                                track.removeSink(renderer)
                                renderer.release()
                            }
                        }
                    }
                }
                if (error.isNotBlank()) {
                    Text(stringResource(R.string.error_screen_share_generic), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    stringResource(R.string.screen_share_media_limit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MemberCard(
    member: VoiceMember,
    isSharingScreen: Boolean,
    whisperSelected: Boolean,
    channels: List<VoiceChannel>,
    onPrivateChat: () -> Unit,
    onPoke: () -> Unit,
    onToggleWhisper: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onMove: (String) -> Unit,
) {
    var menuExpanded by remember(member.id) { mutableStateOf(false) }
    var volumeExpanded by remember(member.id) { mutableStateOf(false) }
    var moveExpanded by remember(member.id) { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (member.speaking) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(member.nickname.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(if (member.isSelf) stringResource(R.string.member_you_format, member.nickname) else member.nickname, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val awayReason = member.awayMessage.trim()
                Text(
                    when {
                        member.away -> stringResource(R.string.member_away) + if (awayReason.isNotEmpty()) " · $awayReason" else ""
                        member.speaking -> stringResource(R.string.member_speaking)
                        else -> stringResource(R.string.member_connected)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (member.speaking && !member.away) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
                if (isSharingScreen) {
                    Text(stringResource(R.string.member_screen_sharing), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Icon(
                if (member.inputMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = stringResource(if (member.inputMuted) R.string.member_mic_muted else R.string.member_mic_on),
                tint = if (member.inputMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Icon(
                Icons.Filled.Speaker,
                contentDescription = stringResource(if (member.outputMuted) R.string.member_speaker_muted else R.string.member_speaker_on),
                tint = if (member.outputMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            if (member.channelCommander) Text(stringResource(R.string.member_admin), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            if (!member.isSelf) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.member_options_format, member.nickname))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false; moveExpanded = false }) {
                        if (moveExpanded) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.member_back_to_actions)) }, onClick = { moveExpanded = false })
                            channels.filterNot { it.id == member.channelId }.forEach { channel ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.member_move_to_channel_format, channel.name)) },
                                    onClick = { onMove(channel.id); menuExpanded = false; moveExpanded = false },
                                )
                            }
                        } else {
                            DropdownMenuItem(text = { Text(stringResource(R.string.member_send_private_message)) }, onClick = { onPrivateChat(); menuExpanded = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.member_poke)) }, onClick = { onPoke(); menuExpanded = false })
                            DropdownMenuItem(
                                text = { Text(stringResource(if (whisperSelected) R.string.member_remove_whisper else R.string.member_add_whisper)) },
                                onClick = { onToggleWhisper(); menuExpanded = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(if (volumeExpanded) R.string.member_hide_volume else R.string.member_adjust_volume)) },
                                onClick = { volumeExpanded = !volumeExpanded; menuExpanded = false },
                            )
                            DropdownMenuItem(text = { Text(stringResource(R.string.member_move)) }, onClick = { moveExpanded = true })
                        }
                    }
                }
            }
        }
        if (volumeExpanded) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Speaker, contentDescription = stringResource(R.string.member_volume))
                Slider(
                    value = member.volume.toFloat().coerceIn(0f, 4f),
                    onValueChange = onVolumeChange,
                    valueRange = 0f..4f,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                )
                Text("${(member.volume * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ChannelsContent(
    channels: List<VoiceChannel>,
    currentChannelId: String,
    state: VoiceSessionState,
    onSelectChannel: (String, String) -> Unit,
) {
    var showPasswordDialog by remember { mutableStateOf(false) }
    var channelPassword by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.errorCode, state.pendingChannelId) {
        if (state.errorCode == "CHANNEL_PASSWORD_REQUIRED" && state.pendingChannelId.isNotBlank()) {
            showPasswordDialog = true
        }
        if (state.pendingChannelId.isBlank()) {
            showPasswordDialog = false
            channelPassword = ""
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(bottom = 6.dp)) {
                 Text(stringResource(R.string.tab_channels), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                 Text(stringResource(R.string.channel_select_to_join), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (channels.isEmpty()) {
             item { EmptyStateCard(R.string.channel_syncing, R.string.channel_syncing_message) }
        } else {
            items(channels, key = { it.id }) { channel ->
                val selected = channel.id == currentChannelId
                Card(
                    onClick = { onSelectChannel(channel.id, "") },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(start = (16 + channel.depth * 18).dp, end = 16.dp, top = 15.dp, bottom = 15.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(channel.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                 Text(pluralStringResource(R.plurals.channel_member_count, channel.members.size, channel.members.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                             if (selected) Text(stringResource(R.string.channel_current), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                        if (channel.members.isEmpty()) {
                             Text(stringResource(R.string.channel_no_members), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            channel.members.forEach { member ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        Modifier.size(30.dp).clip(CircleShape).background(
                                            if (member.speaking) MaterialTheme.colorScheme.secondaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(member.nickname.take(1).uppercase(), style = MaterialTheme.typography.labelMedium)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                             if (member.isSelf) stringResource(R.string.member_you_format, member.nickname) else member.nickname,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (member.speaking) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                        Text(
                                            when {
                                                 member.speaking -> stringResource(R.string.member_speaking)
                                                 member.away -> stringResource(R.string.member_away)
                                                 member.inputMuted -> stringResource(R.string.member_muted)
                                                 else -> stringResource(R.string.member_connected)
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (member.inputMuted) {
                                         Icon(Icons.Filled.MicOff, contentDescription = stringResource(R.string.member_muted), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                                    } else if (member.speaking) {
                                         Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.member_speaking), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text(stringResource(R.string.channel_password_required)) },
            text = {
                OutlinedTextField(
                    value = channelPassword,
                    onValueChange = { channelPassword = it.take(512) },
                    label = { Text(stringResource(R.string.channel_password_label)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSelectChannel(state.pendingChannelId, channelPassword)
                    showPasswordDialog = false
                }) { Text(stringResource(R.string.channel_join)) }
            },
            dismissButton = { TextButton(onClick = { showPasswordDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ChatContent(
    messages: List<ChatMessage>,
    events: List<ServerEvent>,
    chatScope: ChatScope,
    privateChatClientId: Int,
    currentChannelId: String,
    draft: String,
    onScopeChange: (ChatScope) -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ChatScope.Channel, ChatScope.Server, ChatScope.Private, ChatScope.System).forEach { scope ->
                FilterChip(
                    selected = chatScope == scope,
                    onClick = { onScopeChange(scope) },
                     label = { Text(stringResource(scope.titleResource)) },
                )
            }
        }
        if (chatScope == ChatScope.System) {
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(events, key = { it.id }) { event -> EventCard(event) }
                 if (events.isEmpty()) item { EmptyStateCard(R.string.chat_no_events, R.string.chat_events_empty_message) }
            }
        } else {
            val visible = messages.filter { message ->
                when (chatScope) {
                    ChatScope.Channel -> message.scope == ChatScope.Channel
                        && (message.targetId == null || message.targetId == currentChannelId)
                    ChatScope.Server -> message.scope == ChatScope.Server
                    ChatScope.Private -> message.scope == ChatScope.Private
                        && privateChatClientId > 0
                        && message.conversationId == privateChatClientId.toString()
                    ChatScope.System -> false
                }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visible, key = { it.id }) { message -> MessageCard(message) }
                if (chatScope == ChatScope.Private && privateChatClientId <= 0) {
                     item { EmptyStateCard(R.string.chat_private_select_member, R.string.chat_private_open_member_actions) }
                } else if (visible.isEmpty()) {
                     item { EmptyStateCard(R.string.chat_empty_title, R.string.chat_empty_message) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.chat_message_hint)) },
                    shape = RoundedCornerShape(22.dp),
                    maxLines = 4,
                    enabled = chatScope != ChatScope.Private || privateChatClientId > 0,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onSend, enabled = draft.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send_message), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private val ChatScope.titleResource: Int
    get() = when (this) {
        ChatScope.Channel -> R.string.tab_channels
        ChatScope.Server -> R.string.chat_scope_server
        ChatScope.Private -> R.string.chat_scope_private
        ChatScope.System -> R.string.chat_scope_events
    }

@Composable
private fun MessageCard(message: ChatMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.isSelf) Arrangement.End else Arrangement.Start) {
        Card(
            modifier = Modifier.fillMaxWidth(0.86f),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = if (message.isSelf) 6.dp else 20.dp, bottomStart = if (message.isSelf) 20.dp else 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isSelf) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(Modifier.padding(horizontal = 15.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (message.isSelf) stringResource(R.string.label_you) else message.senderName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(formatTime(message.timestamp, LocalContext.current.resources.configuration.locales[0]), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(message.message, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun EventCard(event: ServerEvent) {
    Card(shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(event.timestamp, LocalContext.current.resources.configuration.locales[0]), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(event.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SettingsContent(
    state: VoiceSessionState,
    onToggleMicrophone: () -> Unit,
    onSetAway: (Boolean) -> Unit,
    onDisconnect: () -> Unit,
    onOutputVolume: (Float) -> Unit,
    onLatencyProbe: () -> Unit,
    onClearChatHistory: () -> Unit,
) {
    var confirmClearHistory by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.settings_voice_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
        item { AppLanguageSelector() }
        item {
            SettingActionCard(
                icon = if (state.microphoneMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                title = stringResource(if (state.microphoneMuted) R.string.settings_unmute_microphone else R.string.settings_mute_microphone),
                subtitle = stringResource(
                    if (state.speakerEnabled) R.string.settings_foreground_service else R.string.settings_microphone_requires_playback,
                ),
                action = stringResource(if (state.microphoneMuted) R.string.settings_enable else R.string.action_mute),
                onClick = onToggleMicrophone,
                enabled = state.speakerEnabled,
            )
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Speaker, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_output_volume), fontWeight = FontWeight.SemiBold)
                            Text(
                                if (state.speakerEnabled) "${(state.outputVolume * 100).toInt()}%" else stringResource(R.string.settings_output_muted),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Slider(value = state.outputVolume, onValueChange = onOutputVolume, valueRange = 0f..1f)
                }
            }
        }
        item {
            SettingActionCard(
                icon = Icons.Filled.WifiTethering,
                title = stringResource(R.string.settings_away_title),
                subtitle = stringResource(if (state.away) R.string.settings_currently_away else R.string.settings_currently_online),
                action = stringResource(if (state.away) R.string.settings_return else R.string.settings_set_away),
                onClick = { onSetAway(!state.away) },
            )
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(stringResource(R.string.settings_connection_info), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.settings_gateway_format, state.gatewayUrl), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.settings_teamspeak_format, state.teamSpeakTarget.ifBlank { stringResource(R.string.settings_gateway_target) }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(R.string.settings_voice_transport_format, stringResource(when (state.audioTransport) {
                            VoiceAudioTransport.WebRtc -> if (state.audioReady) R.string.settings_webrtc_connected else R.string.settings_webrtc_connecting
                            VoiceAudioTransport.Compatibility -> R.string.settings_compatibility_connected
                            VoiceAudioTransport.None -> if (state.webrtcAvailable) R.string.settings_webrtc_disconnected else R.string.settings_compatibility_unavailable
                        })),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.audioError.isNotBlank()) Text(stringResource(R.string.error_audio_generic), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(
                            R.string.settings_latency_format,
                            state.latencyRttMs?.let { "$it ms" } ?: stringResource(R.string.settings_not_measured),
                            state.teamSpeakLatencyMs?.let { "$it ms" } ?: "—",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.teamSpeakReachable == false) Text(stringResource(R.string.settings_teamspeak_unreachable), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onLatencyProbe) { Text(stringResource(R.string.settings_test_latency)) }
                }
            }
        }
        item {
            SettingActionCard(
                icon = Icons.Filled.ChatBubbleOutline,
                title = stringResource(R.string.settings_clear_history_title),
                subtitle = stringResource(R.string.settings_clear_history_description),
                action = stringResource(R.string.settings_clear),
                onClick = { confirmClearHistory = true },
            )
        }
        item {
            Button(onClick = onDisconnect, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Filled.CallEnd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_disconnect))
            }
        }
    }
    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text(stringResource(R.string.settings_confirm_clear_title)) },
            text = { Text(stringResource(R.string.settings_confirm_clear_message)) },
            confirmButton = {
                TextButton(onClick = { onClearChatHistory(); confirmClearHistory = false }) { Text(stringResource(R.string.settings_confirm_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClearHistory = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SettingActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    action: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Card(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                action,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun EmptyStateCard(@androidx.annotation.StringRes title: Int, @androidx.annotation.StringRes message: Int) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatTime(timestamp: Long, locale: java.util.Locale): String =
    DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(timestamp))
