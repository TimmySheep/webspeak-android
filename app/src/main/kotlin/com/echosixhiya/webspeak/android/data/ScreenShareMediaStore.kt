package com.echosixhiya.webspeak.android.data

import com.echosixhiya.webspeak.android.service.NativeWebRtcRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.EglBase
import org.webrtc.VideoTrack

/** Bridges service-owned video tracks to the foreground Compose player without transferring capture ownership. */
object ScreenShareMediaStore {
    private val mutableTracks = MutableStateFlow<Map<String, VideoTrack>>(emptyMap())
    val tracks: StateFlow<Map<String, VideoTrack>> = mutableTracks.asStateFlow()

    fun eglContext(): EglBase.Context = NativeWebRtcRuntime.eglContext()

    @Synchronized
    fun publish(streamId: String, track: VideoTrack) {
        if (streamId.isBlank()) return
        mutableTracks.value = mutableTracks.value + (streamId to track)
    }

    @Synchronized
    fun remove(streamId: String) {
        mutableTracks.value = mutableTracks.value - streamId
    }

    @Synchronized
    fun clear() {
        mutableTracks.value = emptyMap()
    }
}
