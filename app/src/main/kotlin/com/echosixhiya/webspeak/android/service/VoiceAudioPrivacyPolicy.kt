package com.echosixhiya.webspeak.android.service

/** Keeps microphone capture closed whenever remote voice playback is disabled. */
internal object VoiceAudioPrivacyPolicy {
    fun mayUnmuteMicrophone(speakerEnabled: Boolean): Boolean = speakerEnabled

    fun microphoneMutedAfterSpeakerChange(microphoneMuted: Boolean, speakerEnabled: Boolean): Boolean =
        microphoneMuted || !speakerEnabled

    fun speakerEnabledAfterOutputVolumeChange(
        speakerEnabled: Boolean,
        previousVolume: Float,
        newVolume: Float,
    ): Boolean = when {
        newVolume <= 0f -> false
        previousVolume <= 0f -> true
        else -> speakerEnabled
    }
}
