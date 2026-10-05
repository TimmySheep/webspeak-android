package com.echosixhiya.webspeak.android.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.echosixhiya.webspeak.android.R
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echosixhiya.webspeak.android.data.VoiceSessionStore
import com.echosixhiya.webspeak.android.model.ConnectionPhase
import com.echosixhiya.webspeak.android.model.JoinRequest
import com.echosixhiya.webspeak.android.service.VoiceSessionService
import kotlinx.coroutines.launch

@Composable
fun WebSpeakApp() {
    val context = LocalContext.current
    val state by VoiceSessionStore.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingJoin by remember { mutableStateOf<JoinRequest?>(null) }
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val projectionData = result.data
        if (result.resultCode == Activity.RESULT_OK && projectionData != null) {
            VoiceSessionService.startScreenShare(context, projectionData)
        }
    }

    fun requestScreenCapture() {
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val microphoneGranted = result[Manifest.permission.RECORD_AUDIO]
            ?: hasPermission(context, Manifest.permission.RECORD_AUDIO)
        val request = pendingJoin
        pendingJoin = null
        if (microphoneGranted && request != null) {
            VoiceSessionService.connect(context, request)
        } else if (!microphoneGranted) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.error_microphone_permission)) }
        }
    }

    fun requestConnect(request: JoinRequest) {
        val missing = requiredVoicePermissions().filterNot { hasPermission(context, it) }
        if (missing.isEmpty()) {
            VoiceSessionService.connect(context, request)
        } else {
            pendingJoin = request
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        when (state.phase) {
            ConnectionPhase.Connected,
            ConnectionPhase.Reconnecting,
            -> VoiceWorkspaceScreen(
                state = state,
                onDisconnect = { VoiceSessionService.disconnect(context) },
                onToggleMicrophone = {
                    VoiceSessionService.setMicrophoneMuted(context, !state.microphoneMuted)
                },
                onSwitchChannel = { channelId, password -> VoiceSessionService.switchChannel(context, channelId, password) },
                onSendMessage = { scopeType, targetId, message ->
                    VoiceSessionService.sendTextMessage(context, scopeType, targetId, message)
                },
                onSetAway = { away -> VoiceSessionService.setAway(context, away) },
                onPoke = { clientId -> VoiceSessionService.sendPoke(context, clientId) },
                onSetWhisperTargets = { clientIds -> VoiceSessionService.setWhisperTargets(context, clientIds) },
                onWhisperActive = { active -> VoiceSessionService.setWhisperActive(context, active) },
                onMemberVolume = { clientId, volume -> VoiceSessionService.setMemberVolume(context, clientId, volume) },
                onMoveMember = { clientId, channelId -> VoiceSessionService.moveMember(context, clientId, channelId) },
                onOutputVolume = { volume -> VoiceSessionService.setOutputVolume(context, volume) },
                onToggleSpeaker = { VoiceSessionService.toggleSpeaker(context) },
                onLatencyProbe = { VoiceSessionService.requestLatency(context) },
                onClearChatHistory = { VoiceSessionService.clearChatHistory(context) },
                onStartScreenShare = ::requestScreenCapture,
                onStopScreenShare = { VoiceSessionService.stopScreenShare(context) },
                onJoinScreenShare = { streamId -> VoiceSessionService.joinScreenShare(context, streamId) },
                onLeaveScreenShare = { streamId -> VoiceSessionService.leaveScreenShare(context, streamId) },
            )

            else -> ConnectionScreen(
                connecting = state.phase == ConnectionPhase.Connecting,
                errorCode = state.errorCode,
                errorMessage = state.errorMessage,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                onConnect = ::requestConnect,
                onCancel = { VoiceSessionService.disconnect(context) },
            )
        }
    }
}

private fun requiredVoicePermissions(): List<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
