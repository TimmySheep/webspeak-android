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
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.gatewayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                when (state.phase) {
                                    ConnectionPhase.Reconnecting -> "正在恢复连接"
                                    else -> currentChannel?.name ?: "语音频道"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.phase == ConnectionPhase.Reconnecting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        Box(Modifier.padding(start = 16.dp).size(11.dp).clip(CircleShape).background(
                            if (state.phase == ConnectionPhase.Connected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.tertiary,
                        ))
                    },
                    actions = {
                        IconButton(onClick = onDisconnect) {
                            Icon(Icons.Filled.CallEnd, contentDescription = "断开连接", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar {
                        ClientTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = { NavigationTabIcon(tab, state.messages.count { !it.isSelf }) },
                                label = { Text(tab.label) },
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
                                label = { Text(tab.label) },
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

private val ClientTab.label: String
    get() = when (this) {
        ClientTab.Voice -> "语音"
        ClientTab.Channels -> "频道"
        ClientTab.Chat -> "聊天"
        ClientTab.Settings -> "设置"
    }

@Composable
private fun VoiceHomeContent(
    state: VoiceSessionState,
    currentChannel: VoiceChannel?,
    onToggleMicrophone: () -> Unit,
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        if (state.phase == ConnectionPhase.Reconnecting) {
            item {
                AssistChip(
                    onClick = {},
                    label = { Text("连接中断，正在尝试恢复 · ${state.reconnectAttempt}") },
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
                            Text("语音房间", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                            Spacer(Modifier.height(4.dp))
                            Text(currentChannel?.name ?: "等待频道信息", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("${currentMembers.size} 位成员 · ${state.nickname}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        }
                        Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(27.dp))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = onToggleMicrophone, shape = RoundedCornerShape(16.dp)) {
                            Icon(if (state.microphoneMuted) Icons.Filled.MicOff else Icons.Filled.Mic, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.microphoneMuted) "取消静音" else "静音")
                        }
                        TextButton(onClick = onSwitchToChannels) { Text("切换频道") }
                    }
                    if (state.whisperTargetIds.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = {},
                            modifier = Modifier.pointerInput(state.whisperTargetIds) {
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
                            Text(if (state.whisperActive) "正在私语…" else "按住私语")
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("频道成员", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("正在频道中${currentMembers.count { it.speaking }} 人发言", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(onClick = onSwitchToChannels, label = { Text("全部频道") }, leadingIcon = { Icon(Icons.Filled.Groups, contentDescription = null) })
            }
        }
        if (currentMembers.isEmpty()) {
            item { EmptyStateCard("还没有成员", "频道成员连接后会显示在这里。") }
        } else {
            items(currentMembers, key = { it.id }) { member ->
                MemberCard(
                    member = member,
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
                            Text("屏幕共享", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                     state.screenShareStarting -> stringResource(R.string.screen_share_requesting)
                                     state.activeScreenShareId.isNotBlank() -> "你正在共享屏幕"
                                     state.watchingScreenShareId.isNotBlank() -> "正在观看屏幕共享"
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
                            Text(if (state.activeScreenShareId.isNotBlank() || state.screenShareStarting) "停止" else "开始")
                        }
                    }
                    if (state.screenShareError.isNotBlank()) {
                        Text(state.screenShareError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (state.screenShares.isNotEmpty()) {
            item {
                Text("正在直播", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            items(state.screenShares, key = { it.streamId }) { share ->
                Card(shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WifiTethering, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(share.name, fontWeight = FontWeight.SemiBold)
                            Text("${share.ownerNickname} · ${share.viewerCount} 位观众", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        when {
                            share.streamId == state.activeScreenShareId -> Text("你正在共享", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                            share.streamId == state.watchingScreenShareId -> Text("正在观看", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                            else -> TextButton(onClick = { onJoinScreenShare(share.streamId) }) { Text("观看") }
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
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    if (track == null) {
                        Text("正在连接共享画面…", color = androidx.compose.ui.graphics.Color.White)
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
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                Text(if (member.isSelf) "${member.nickname}（你）" else member.nickname, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        member.speaking -> "正在发言"
                        member.away -> "离开"
                        member.inputMuted -> "麦克风已静音"
                        else -> "已连接"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (member.inputMuted) Icon(Icons.Filled.MicOff, contentDescription = "麦克风静音", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            if (member.channelCommander) Text("管理", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            if (!member.isSelf) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "${member.nickname} 的操作")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false; moveExpanded = false }) {
                        if (moveExpanded) {
                            DropdownMenuItem(text = { Text("返回成员操作") }, onClick = { moveExpanded = false })
                            channels.filterNot { it.id == member.channelId }.forEach { channel ->
                                DropdownMenuItem(
                                    text = { Text("移动到 ${channel.name}") },
                                    onClick = { onMove(channel.id); menuExpanded = false; moveExpanded = false },
                                )
                            }
                        } else {
                            DropdownMenuItem(text = { Text("发送私聊") }, onClick = { onPrivateChat(); menuExpanded = false })
                            DropdownMenuItem(text = { Text("戳一戳") }, onClick = { onPoke(); menuExpanded = false })
                            DropdownMenuItem(
                                text = { Text(if (whisperSelected) "移出私语目标" else "加入私语目标") },
                                onClick = { onToggleWhisper(); menuExpanded = false },
                            )
                            DropdownMenuItem(
                                text = { Text(if (volumeExpanded) "隐藏音量调节" else "调节此成员音量") },
                                onClick = { volumeExpanded = !volumeExpanded; menuExpanded = false },
                            )
                            DropdownMenuItem(text = { Text("移动成员…") }, onClick = { moveExpanded = true })
                        }
                    }
                }
            }
        }
        if (volumeExpanded) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Speaker, contentDescription = "成员音量")
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
                Text("频道", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("选择频道加入语音", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (channels.isEmpty()) {
            item { EmptyStateCard("正在同步频道", "收到网关目录后，频道列表会出现在这里。") }
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
                                Text("${channel.members.size} 位成员", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selected) Text("当前", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                        if (channel.members.isEmpty()) {
                            Text("暂无成员", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                            if (member.isSelf) "${member.nickname}（你）" else member.nickname,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (member.speaking) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                        Text(
                                            when {
                                                member.speaking -> "正在发言"
                                                member.away -> "离开"
                                                member.inputMuted -> "麦克风已静音"
                                                else -> "已连接"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (member.inputMuted) {
                                        Icon(Icons.Filled.MicOff, contentDescription = "麦克风静音", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                                    } else if (member.speaking) {
                                        Icon(Icons.Filled.Mic, contentDescription = "正在发言", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
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
            title = { Text("频道需要密码") },
            text = {
                OutlinedTextField(
                    value = channelPassword,
                    onValueChange = { channelPassword = it.take(512) },
                    label = { Text("频道密码") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSelectChannel(state.pendingChannelId, channelPassword)
                    showPasswordDialog = false
                }) { Text("加入频道") }
            },
            dismissButton = { TextButton(onClick = { showPasswordDialog = false }) { Text("取消") } },
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
                    label = { Text(scope.title) },
                )
            }
        }
        if (chatScope == ChatScope.System) {
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(events, key = { it.id }) { event -> EventCard(event) }
                if (events.isEmpty()) item { EmptyStateCard("暂无事件", "成员加入、离开和连接状态会显示在这里。") }
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
                    item { EmptyStateCard("选择一位成员开始私聊", "打开频道成员操作菜单，然后选择“发送私聊”。") }
                } else if (visible.isEmpty()) {
                    item { EmptyStateCard("这是聊天的开始", "发送一条消息，和频道里的朋友打个招呼吧。") }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("发送消息…") },
                    shape = RoundedCornerShape(22.dp),
                    maxLines = 4,
                    enabled = chatScope != ChatScope.Private || privateChatClientId > 0,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onSend, enabled = draft.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送消息", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private val ChatScope.title: String
    get() = when (this) {
        ChatScope.Channel -> "频道"
        ChatScope.Server -> "服务器"
        ChatScope.Private -> "私聊"
        ChatScope.System -> "事件"
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
                    Text(if (message.isSelf) "你" else message.senderName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(formatTime(message.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(formatTime(event.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        item { Text("语音设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
        item {
            SettingActionCard(
                icon = if (state.microphoneMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                title = if (state.microphoneMuted) "取消麦克风静音" else "麦克风静音",
                subtitle = "语音会话由 Android 前台服务维持",
                action = if (state.microphoneMuted) "开启" else "静音",
                onClick = onToggleMicrophone,
            )
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Speaker, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("语音播放音量", fontWeight = FontWeight.SemiBold)
                            Text("${(state.outputVolume * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Slider(value = state.outputVolume, onValueChange = onOutputVolume, valueRange = 0f..1f)
                }
            }
        }
        item {
            SettingActionCard(
                icon = Icons.Filled.WifiTethering,
                title = "离开状态",
                subtitle = if (state.away) "当前显示为离开" else "当前显示为在线",
                action = if (state.away) "返回" else "设为离开",
                onClick = { onSetAway(!state.away) },
            )
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("连接信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("网关：${state.gatewayUrl}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("TeamSpeak：${state.teamSpeakTarget.ifBlank { "由网关指定" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "语音传输：" + when (state.audioTransport) {
                            VoiceAudioTransport.WebRtc -> if (state.audioReady) "WebRTC 已连接" else "正在建立 WebRTC"
                            VoiceAudioTransport.Compatibility -> "PCM/Opus 兼容传输已启动"
                            VoiceAudioTransport.None -> if (state.webrtcAvailable) "WebRTC 尚未连接" else "兼容音频不可用"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.audioError.isNotBlank()) Text(state.audioError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "网络往返：${state.latencyRttMs?.let { "$it ms" } ?: "尚未测量"} · TeamSpeak：${state.teamSpeakLatencyMs?.let { "$it ms" } ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.teamSpeakReachable == false) Text("TeamSpeak 探测暂不可达", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onLatencyProbe) { Text("测试连接延迟") }
                }
            }
        }
        item {
            SettingActionCard(
                icon = Icons.Filled.ChatBubbleOutline,
                title = "清除本机聊天记录",
                subtitle = "仅清除此网关与服务器在本机保存的 2,000 条以内消息",
                action = "清除",
                onClick = { confirmClearHistory = true },
            )
        }
        item {
            Button(onClick = onDisconnect, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Filled.CallEnd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("离开语音频道")
            }
        }
    }
    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("清除本机聊天记录？") },
            text = { Text("此操作不会影响 TeamSpeak 服务器或其他设备上的记录。WebSpeak 网关也不会提供连接前的旧聊天回放。") },
            confirmButton = {
                TextButton(onClick = { onClearChatHistory(); confirmClearHistory = false }) { Text("清除记录") }
            },
            dismissButton = { TextButton(onClick = { confirmClearHistory = false }) { Text("取消") } },
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
) {
    Card(onClick = onClick, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(action, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, message: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatTime(timestamp: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))
