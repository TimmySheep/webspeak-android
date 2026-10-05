package com.echosixhiya.webspeak.android.service

internal data class OpusVoiceFrame(
    val clientId: Int,
    val payload: ByteArray,
)

/** WebSpeak binary downlink: codec byte, big-endian client id, then the encoded frame. */
internal fun parseOpusVoiceFrame(packet: ByteArray): OpusVoiceFrame? {
    if (packet.size < MIN_VOICE_PACKET_BYTES || !isOpusVoiceCodec(packet[0].toInt() and 0xff)) return null
    val clientId = ((packet[1].toInt() and 0xff) shl 8) or (packet[2].toInt() and 0xff)
    if (clientId <= 0) return null
    return OpusVoiceFrame(clientId, packet.copyOfRange(3, packet.size))
}

internal fun isOpusVoiceCodec(codec: Int): Boolean = codec == OPUS_CODEC || codec == OPUS_MUSIC_CODEC

internal const val OPUS_CODEC = 4
internal const val OPUS_MUSIC_CODEC = 5
private const val MIN_VOICE_PACKET_BYTES = 6
