package com.echosixhiya.webspeak.android.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAudioPrivacyPolicyTest {
    @Test
    fun microphoneCannotBeUnmutedWhenPlaybackIsOff() {
        assertFalse(VoiceAudioPrivacyPolicy.mayUnmuteMicrophone(speakerEnabled = false))
        assertTrue(VoiceAudioPrivacyPolicy.mayUnmuteMicrophone(speakerEnabled = true))
    }

    @Test
    fun disablingPlaybackForcesMicrophoneMutedButEnablingDoesNotUnmuteIt() {
        assertTrue(VoiceAudioPrivacyPolicy.microphoneMutedAfterSpeakerChange(microphoneMuted = false, speakerEnabled = false))
        assertTrue(VoiceAudioPrivacyPolicy.microphoneMutedAfterSpeakerChange(microphoneMuted = true, speakerEnabled = true))
        assertFalse(VoiceAudioPrivacyPolicy.microphoneMutedAfterSpeakerChange(microphoneMuted = false, speakerEnabled = true))
    }

    @Test
    fun changingVolumeDoesNotResumePlaybackThatWasMutedSeparately() {
        assertFalse(VoiceAudioPrivacyPolicy.speakerEnabledAfterOutputVolumeChange(speakerEnabled = false, previousVolume = 0.7f, newVolume = 0.4f))
        assertTrue(VoiceAudioPrivacyPolicy.speakerEnabledAfterOutputVolumeChange(speakerEnabled = false, previousVolume = 0f, newVolume = 0.4f))
        assertFalse(VoiceAudioPrivacyPolicy.speakerEnabledAfterOutputVolumeChange(speakerEnabled = true, previousVolume = 0.4f, newVolume = 0f))
    }
}
