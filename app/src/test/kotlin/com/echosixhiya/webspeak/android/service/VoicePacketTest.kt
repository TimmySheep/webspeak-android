package com.echosixhiya.webspeak.android.service

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoicePacketTest {
    @Test
    fun parsesOpusClientIdAsBigEndian() {
        val frame = parseOpusVoiceFrame(byteArrayOf(4, 0x12, 0x34, 7, 8, 9))

        requireNotNull(frame)
        assertEquals(0x1234, frame.clientId)
        assertArrayEquals(byteArrayOf(7, 8, 9), frame.payload)
    }

    @Test
    fun acceptsBothTeamSpeakOpusCodecs() {
        assertEquals(0x0102, requireNotNull(parseOpusVoiceFrame(byteArrayOf(4, 1, 2, 3, 4, 5))).clientId)
        assertEquals(0x0102, requireNotNull(parseOpusVoiceFrame(byteArrayOf(5, 1, 2, 3, 4, 5))).clientId)
        assertNull(parseOpusVoiceFrame(byteArrayOf(6, 1, 2, 3, 4, 5)))
    }

    @Test
    fun ignoresUnsupportedAndTruncatedFrames() {
        assertNull(parseOpusVoiceFrame(byteArrayOf(3, 0, 1, 7, 8, 9)))
        assertNull(parseOpusVoiceFrame(byteArrayOf(4, 0, 1, 7, 8)))
        assertNull(parseOpusVoiceFrame(byteArrayOf(4, 0, 0, 7, 8, 9)))
    }
}
