package com.echosixhiya.webspeak.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteServerProfileTest {
    @Test
    fun codecRoundTripsUnicodeAndEmptyTarget() {
        val profile = FavoriteServerProfile("https://gateway.example.com", "小羊", "")

        val normalized = FavoriteServerProfile("https://gateway.example.com/", "小羊", "")
        assertEquals(normalized, FavoriteServerProfileCodec.decode(FavoriteServerProfileCodec.encode(profile)))
    }

    @Test
    fun codecRejectsMalformedAndTrailingData() {
        assertNull(FavoriteServerProfileCodec.decode("not-base64!"))

        val encoded = FavoriteServerProfileCodec.encode(FavoriteServerProfile("https://gateway.example.com", "小羊", ""))
        val withTrailingData = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            java.util.Base64.getUrlDecoder().decode(encoded) + byteArrayOf(1),
        )
        assertNull(FavoriteServerProfileCodec.decode(withTrailingData))
    }

    @Test
    fun upsertReplacesSameDestinationAndMovesItToFront() {
        val existing = FavoriteServerProfile("https://gateway.example.com/", "旧昵称", "voice.example.com:9987")
        val other = FavoriteServerProfile("https://other.example.com", "其他", "")
        val updated = FavoriteServerProfile("https://gateway.example.com/", "新昵称", "VOICE.EXAMPLE.COM:9987")

        assertEquals(listOf(updated, other), FavoriteServerProfiles.upsert(listOf(existing, other), updated))
    }

    @Test
    fun upsertCapsListAtTwentyAndRejectsBlankRequiredFields() {
        val profiles = (1..FavoriteServerProfiles.MAX_FAVORITES).map {
            FavoriteServerProfile("https://gateway$it.example.com", "昵称$it", "")
        }
        val overflow = FavoriteServerProfile("https://overflow.example.com", "溢出", "")

        assertEquals(FavoriteServerProfiles.MAX_FAVORITES, FavoriteServerProfiles.upsert(profiles, overflow).size)
        assertEquals(FavoriteServerProfileCodec.sanitize(overflow), FavoriteServerProfiles.upsert(profiles, overflow).first())
        assertNull(FavoriteServerProfileCodec.sanitize(FavoriteServerProfile("", "昵称", "")))
        assertNull(FavoriteServerProfileCodec.sanitize(FavoriteServerProfile("https://gateway.example.com", "", "")))
    }

    @Test
    fun removeDeletesOnlyMatchingDestination() {
        val one = FavoriteServerProfile("https://gateway.example.com", "一", "voice.example.com")
        val two = FavoriteServerProfile("https://gateway.example.com", "二", "other.example.com")

        assertEquals(listOf(two), FavoriteServerProfiles.remove(listOf(one, two), one))
    }

    @Test
    fun sanitizeRejectsInsecureOrCredentialBearingGatewayUrls() {
        val unsafeUrls = listOf(
            "http://gateway.example.com",
            "https://user:secret@gateway.example.com",
            "https://gateway.example.com/?invite=secret",
            "https://gateway.example.com/client",
        )

        unsafeUrls.forEach { url ->
            assertNull(FavoriteServerProfileCodec.sanitize(FavoriteServerProfile(url, "昵称", "")))
        }
    }
}
