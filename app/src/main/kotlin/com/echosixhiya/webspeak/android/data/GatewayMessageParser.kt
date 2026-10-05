package com.echosixhiya.webspeak.android.data

import com.echosixhiya.webspeak.android.model.ChatMessage
import com.echosixhiya.webspeak.android.model.ChatScope
import com.echosixhiya.webspeak.android.model.ScreenShareStream
import com.echosixhiya.webspeak.android.model.ScreenShareViewer
import com.echosixhiya.webspeak.android.model.ServerEvent
import com.echosixhiya.webspeak.android.model.VoiceChannel
import com.echosixhiya.webspeak.android.model.VoiceMember
import org.json.JSONArray
import org.json.JSONObject

/** Bounded, tolerant decoding for the gateway's JSON control protocol. */
object GatewayMessageParser {
    fun parse(raw: String): JSONObject? {
        if (raw.length > MAX_CONTROL_MESSAGE_CHARS) return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    fun members(array: JSONArray?, selfId: Int = 0, unknownUser: String = "Unknown user"): List<VoiceMember> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length().coerceAtMost(MAX_MEMBERS)) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optInt("id", 0)
            if (id <= 0) continue
            add(member(item, selfId, unknownUser))
        }
    }

    fun member(item: JSONObject, selfId: Int = 0, unknownUser: String = "Unknown user"): VoiceMember {
        val id = item.optInt("id", 0)
        return VoiceMember(
            id = id,
            nickname = item.optString("nickname", unknownUser).take(120).ifBlank { unknownUser },
            uid = item.optString("uid", "").take(256),
            channelId = item.stringValue("channelID", "channelId").take(32),
            avatar = item.optString("avatar", "").take(MAX_AVATAR_CHARS).ifBlank { null },
            away = item.optBoolean("away", false),
            inputMuted = item.optBoolean("inputMuted", false),
            outputMuted = item.optBoolean("outputMuted", false),
            channelCommander = item.optBoolean("channelCommander", false),
            isSelf = item.optBoolean("isSelf", false) || (selfId > 0 && id == selfId),
        )
    }

    fun channels(
        array: JSONArray?,
        selfId: Int = 0,
        unknownUser: String = "Unknown user",
        unnamedChannel: String = "Unnamed channel",
    ): List<VoiceChannel> {
        val parsed = buildList {
            if (array == null) return@buildList
            for (index in 0 until array.length().coerceAtMost(MAX_CHANNELS)) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.stringValue("id").take(32)
            if (id.isBlank()) continue
            add(
                VoiceChannel(
                    id = id,
                    parentId = item.stringValue("parentID", "parentId").ifBlank { "0" }.take(32),
                    order = item.stringValue("order").take(32).ifBlank { "0" },
                    name = item.optString("name", unnamedChannel).take(120).ifBlank { unnamedChannel },
                    topic = item.optString("description", "").take(500),
                    members = members(item.optJSONArray("members"), selfId, unknownUser),
                ),
            )
            }
        }
        return orderChannels(parsed)
    }

    private fun orderChannels(source: List<VoiceChannel>): List<VoiceChannel> {
        if (source.isEmpty()) return source
        val sourceIndex = source.mapIndexed { index, channel -> channel.id to index }.toMap()
        val byId = source.associateBy { it.id }
        val depthCache = mutableMapOf<String, Int>()
        fun depthOf(channel: VoiceChannel, visiting: Set<String> = emptySet()): Int {
            depthCache[channel.id]?.let { return it }
            if (channel.parentId == "0" || channel.parentId.isBlank() || channel.id in visiting) return 0
            val parent = byId[channel.parentId] ?: return 0
            val depth = depthOf(parent, visiting + channel.id) + 1
            depthCache[channel.id] = depth
            return depth
        }
        val enriched = source.map { it.copy(depth = depthOf(it)) }
        val byParent = enriched.groupBy { it.parentId }
        fun orderSiblings(siblings: List<VoiceChannel>): List<VoiceChannel> {
            val siblingIds = siblings.mapTo(HashSet()) { it.id }
            val successors = mutableMapOf<String, MutableList<VoiceChannel>>()
            val roots = mutableListOf<VoiceChannel>()
            siblings.forEach { channel ->
                val predecessorId = channel.order.takeIf { it != "0" && it in siblingIds }
                if (predecessorId == null) roots += channel
                else successors.getOrPut(predecessorId) { mutableListOf() } += channel
            }
            val sourceOrder = compareBy<VoiceChannel> { sourceIndex[it.id] ?: Int.MAX_VALUE }
            roots.sortWith(sourceOrder)
            successors.values.forEach { it.sortWith(sourceOrder) }
            val output = mutableListOf<VoiceChannel>()
            val visited = mutableSetOf<String>()
            fun append(channel: VoiceChannel) {
                if (!visited.add(channel.id)) return
                output += channel
                successors[channel.id].orEmpty().forEach(::append)
            }
            roots.forEach(::append)
            siblings.sortedWith(sourceOrder).forEach(::append)
            return output
        }
        val ordered = mutableListOf<VoiceChannel>()
        val visited = mutableSetOf<String>()
        fun visit(parentId: String) {
            orderSiblings(byParent[parentId].orEmpty()).forEach { channel ->
                if (visited.add(channel.id)) {
                    ordered += channel
                    visit(channel.id)
                }
            }
        }
        visit("0")
        enriched.sortedBy { sourceIndex[it.id] ?: Int.MAX_VALUE }.forEach { channel ->
            if (visited.add(channel.id)) {
                ordered += channel
                visit(channel.id)
            }
        }
        return ordered
    }

    fun message(item: JSONObject, selfId: Int = 0, unknownUser: String = "Unknown user"): ChatMessage? {
        val message = item.optString("message", "").take(500)
        if (message.isBlank()) return null
        val senderId = item.optInt("invokerId", item.optInt("senderId", 0)).takeIf { it > 0 }
        val scope = when (item.optString("scope", "system")) {
            "channel" -> ChatScope.Channel
            "server" -> ChatScope.Server
            "private" -> ChatScope.Private
            else -> ChatScope.System
        }
        val rawTargetId = item.stringValue("targetId").take(32).takeIf { it.isNotBlank() }
        return ChatMessage(
            id = "${item.optLong("timestamp", System.currentTimeMillis())}-${senderId ?: 0}-${message.hashCode()}",
            scope = scope,
            targetId = rawTargetId?.takeUnless { it == "0" },
            conversationId = if (scope == ChatScope.Private) senderId?.toString() else null,
            senderId = senderId,
            senderName = item.optString("invokerName", item.optString("senderName", unknownUser)).take(120),
            message = message,
            timestamp = item.optLong("timestamp", System.currentTimeMillis()),
            isSelf = senderId != null && senderId == selfId,
        )
    }

    fun event(item: JSONObject): ServerEvent? {
        val id = item.optString("id", "").take(128)
        val message = item.optString("message", "").take(500)
        if (id.isBlank() || message.isBlank()) return null
        return ServerEvent(
            id = id,
            kind = item.optString("kind", "event").take(64),
            message = message,
            timestamp = item.optLong("timestamp", System.currentTimeMillis()),
        )
    }

    fun screenShares(
        array: JSONArray?,
        unknownUser: String = "Unknown user",
        screenShareName: String = "Screen share",
    ): List<ScreenShareStream> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length().coerceAtMost(MAX_SCREEN_SHARES)) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("streamId", "").take(128)
            if (id.isBlank()) continue
            val viewers = item.optJSONArray("viewers")
            add(
                ScreenShareStream(
                    streamId = id,
                    source = item.optString("source", "browser").take(32),
                    ownerPeerId = item.optString("ownerPeerId", "").take(128),
                    ownerClientId = item.optInt("ownerClientId", 0).takeIf { it > 0 },
                    ownerNickname = item.optString("ownerNickname", unknownUser).take(120),
                    name = item.optString("name", screenShareName).take(120),
                    audio = item.optBoolean("audio", false),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    viewerCount = item.optInt("viewerCount", 0).coerceIn(0, 1000),
                    viewers = buildList {
                        if (viewers != null) for (viewerIndex in 0 until viewers.length().coerceAtMost(MAX_MEMBERS)) {
                            val viewer = viewers.optJSONObject(viewerIndex) ?: continue
                            val peerId = viewer.optString("peerId", "").take(128)
                            if (peerId.isNotBlank()) add(
                                ScreenShareViewer(
                                    peerId = peerId,
                                    nickname = viewer.optString("nickname", unknownUser).take(120),
                                    avatar = viewer.optString("avatar", "").take(MAX_AVATAR_CHARS).ifBlank { null },
                                ),
                            )
                        }
                    },
                ),
            )
        }
    }

    private fun JSONObject.stringValue(vararg keys: String): String {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            return when (val value = opt(key)) {
                is String -> value
                is Number -> value.toString()
                else -> ""
            }
        }
        return ""
    }

    private const val MAX_CONTROL_MESSAGE_CHARS = 1_048_576
    private const val MAX_CHANNELS = 512
    private const val MAX_MEMBERS = 1024
    private const val MAX_SCREEN_SHARES = 64
    private const val MAX_AVATAR_CHARS = 128 * 1024
}
