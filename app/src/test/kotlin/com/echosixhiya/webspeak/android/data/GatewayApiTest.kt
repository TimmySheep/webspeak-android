package com.echosixhiya.webspeak.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GatewayApiTest {
    private val api = GatewayApi()

    @Test
    fun bareGatewayHostDefaultsToHttps() {
        assertEquals("https://voice.example.com/", api.parseGatewayUrl("voice.example.com").toString())
    }

    @Test
    fun preservesExplicitHttpsPort() {
        assertEquals("https://voice.example.com:8443/", api.parseGatewayUrl("https://voice.example.com:8443").toString())
    }

    @Test
    fun websocketUsesSecureSchemeAndTicketParameter() {
        val base = api.parseGatewayUrl("https://voice.example.com")
        val socket = GatewayApi.webSocketUrl(base, "one-time-ticket")
        // OkHttp upgrades the HTTPS endpoint to a secure WebSocket transport.
        assertEquals("https", socket.scheme)
        assertEquals("/ws/voice", socket.encodedPath)
        assertEquals("one-time-ticket", socket.queryParameter("ticket"))
    }

    @Test
    fun rejectsCleartextGatewayEvenWhenExplicit() {
        val error = assertThrows(GatewayException::class.java) { api.parseGatewayUrl("http://localhost:3040") }
        assertEquals("INSECURE_GATEWAY_URL", error.code)
    }

    @Test
    fun rejectsPathCredentialsAndQueryValues() {
        val subpath = assertThrows(GatewayException::class.java) {
            api.parseGatewayUrl("https://voice.example.com/client")
        }
        assertEquals("INVALID_GATEWAY_SUBPATH", subpath.code)

        listOf(
            "https://name:password@voice.example.com",
            "https://voice.example.com/?token=secret",
            "https://voice.example.com/#fragment",
        ).forEach { input ->
            val error = assertThrows(GatewayException::class.java) { api.parseGatewayUrl(input) }
            assertEquals("INVALID_GATEWAY_CREDENTIALS", error.code)
        }
    }
}
