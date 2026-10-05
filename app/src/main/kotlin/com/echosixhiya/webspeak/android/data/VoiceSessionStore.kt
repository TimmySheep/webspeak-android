package com.echosixhiya.webspeak.android.data

import com.echosixhiya.webspeak.android.model.ConnectionPhase
import com.echosixhiya.webspeak.android.model.VoiceSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Process-local observable state. The foreground service remains the owner of network/media resources. */
object VoiceSessionStore {
    private val mutableState = MutableStateFlow(VoiceSessionState())
    val state: StateFlow<VoiceSessionState> = mutableState.asStateFlow()

    fun update(transform: (VoiceSessionState) -> VoiceSessionState) {
        mutableState.update(transform)
    }

    fun reset() {
        mutableState.value = VoiceSessionState(phase = ConnectionPhase.Disconnected)
    }
}
