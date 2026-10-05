package com.echosixhiya.webspeak.android.data

import com.echosixhiya.webspeak.android.model.JoinRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class PublicGatewayConfig(
    val initialized: Boolean,
    val accessMode: String,
    val defaultTarget: String,
    val siteName: String,
    val version: String,
)

data class TicketResult(
    val ticket: String,
    val baseUrl: HttpUrl,
)

class GatewayApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun loadPublicConfig(rawGatewayUrl: String): PublicGatewayConfig = withContext(Dispatchers.IO) {
        val base = parseGatewayUrl(rawGatewayUrl)
        val request = Request.Builder()
            .url(base.newBuilder().encodedPath("/api/public-config").query(null).build())
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw GatewayException("GATEWAY_CONFIG_FAILED", "网关配置读取失败（HTTP ${response.code}）")
            val json = JSONObject(response.body.string())
            PublicGatewayConfig(
                initialized = json.optBoolean("initialized", false),
                accessMode = json.optString("accessMode", "fixed"),
                defaultTarget = json.optString("target", ""),
                siteName = json.optString("siteName", "WebSpeak").ifBlank { "WebSpeak" },
                version = json.optString("version", ""),
            )
        }
    }

    suspend fun createJoinTicket(request: JoinRequest): TicketResult = withContext(Dispatchers.IO) {
        val base = parseGatewayUrl(request.gatewayUrl)
        val body = JSONObject()
            .put("nickname", request.nickname.trim())
            .put("channel", request.channel.trim())
            .put("serverPassword", request.serverPassword)
            .put("rememberIdentity", request.rememberIdentity)
        if (request.rememberIdentity && request.identity.isNotBlank()) body.put("identity", request.identity)
        if (request.teamSpeakTarget.isNotBlank()) body.put("target", request.teamSpeakTarget.trim())
        if (request.inviteToken.isNotBlank()) body.put("invite", request.inviteToken.trim())

        // The current gateway uses same-origin validation as browser CSRF protection.
        // This header identifies the configured gateway origin; it is not authentication.
        val origin = base.newBuilder().encodedPath("/").query(null).build().toString().removeSuffix("/")
        val ticketUrl = base.newBuilder().encodedPath("/api/join-ticket").query(null).build()
        val httpRequest = Request.Builder()
            .url(ticketUrl)
            .header("Origin", origin)
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val result = runCatching { JSONObject(response.body.string()) }.getOrDefault(JSONObject())
            val ticket = result.optString("ticket", "")
            if (!response.isSuccessful || ticket.isBlank()) {
                val code = result.optString("code", "JOIN_TICKET_FAILED").ifBlank { "JOIN_TICKET_FAILED" }
                val detail = result.optString("detail", "")
                val message = when (code) {
                    "ORIGIN_REJECTED" -> "网关拒绝了连接来源，请检查网关地址和 HTTPS 配置"
                    "NOT_INITIALIZED" -> "WebSpeak 网关尚未完成配置"
                    "RATE_LIMITED" -> "连接请求过于频繁，请稍后再试"
                    "TARGET_NOT_ALLOWED" -> "该 TeamSpeak 地址不在网关允许范围内"
                    "ACCELERATION_UNAVAILABLE" -> "当前中继加速不可用"
                    "INVALID_NICKNAME" -> "请输入有效昵称"
                    "INVITE_INVALID" -> "邀请链接已失效或已撤销"
                    else -> detail.ifBlank { "连接请求失败（HTTP ${response.code}）" }
                }
                throw GatewayException(code, message)
            }
            TicketResult(ticket = ticket, baseUrl = base)
        }
    }

    internal fun parseGatewayUrl(raw: String): HttpUrl {
        val normalized = raw.trim().let { value ->
            when {
                value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true) -> value
                value.isBlank() -> value
                else -> "https://$value"
            }
        }
        val url = normalized.toHttpUrlOrNull()
            ?: throw GatewayException("INVALID_GATEWAY_URL", "请输入有效的 WebSpeak 网关地址")
        if (url.encodedPath != "/" && url.encodedPath.isNotBlank()) {
            throw GatewayException("INVALID_GATEWAY_SUBPATH", "网关地址暂不支持子路径，请填写站点根地址")
        }
        if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null) {
            throw GatewayException("INVALID_GATEWAY_CREDENTIALS", "网关地址不能包含账号、密码、查询参数或片段")
        }
        if (url.scheme != "https") {
            throw GatewayException("INSECURE_GATEWAY_URL", "为保护连接票据、身份数据、聊天和语音，网关必须使用有效 HTTPS 证书")
        }
        return url
    }

    companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun webSocketUrl(base: HttpUrl, ticket: String): HttpUrl = base.newBuilder()
            .encodedPath("/ws/voice")
            .query(null)
            .addQueryParameter("ticket", ticket)
            .build()
    }
}

class GatewayException(
    val code: String,
    override val message: String,
) : IOException(message)
