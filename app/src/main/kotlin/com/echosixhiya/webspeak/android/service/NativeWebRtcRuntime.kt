package com.echosixhiya.webspeak.android.service

import android.content.Context
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory

/** Process-wide libwebrtc initialization and EGL context shared by video renderers. */
object NativeWebRtcRuntime {
    private var initialized = false
    private var eglBase: EglBase? = null

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        val options = PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        initialized = true
    }

    @Synchronized
    fun eglContext(): EglBase.Context {
        eglBase?.let { return it.eglBaseContext }
        val created = EglBase.create()
        eglBase = created
        return created.eglBaseContext
    }
}
