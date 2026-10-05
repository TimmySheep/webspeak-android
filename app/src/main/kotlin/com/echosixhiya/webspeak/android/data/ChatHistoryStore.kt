package com.echosixhiya.webspeak.android.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.echosixhiya.webspeak.android.model.ChatMessage
import com.echosixhiya.webspeak.android.model.ChatScope

/** Private, bounded per-gateway chat cache. It stores no connection credentials or TeamSpeak identity. */
class ChatHistoryStore(context: Context) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE $TABLE_NAME (
                gateway_key TEXT NOT NULL,
                server_key TEXT NOT NULL,
                message_id TEXT NOT NULL,
                scope TEXT NOT NULL,
                target_id TEXT,
                conversation_id TEXT,
                sender_id INTEGER,
                sender_name TEXT NOT NULL,
                message TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                is_self INTEGER NOT NULL,
                PRIMARY KEY (gateway_key, server_key, message_id)
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX chat_history_by_time ON $TABLE_NAME(gateway_key, server_key, timestamp)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Keep migrations additive. A future schema change must preserve locally collected user data.
    }

    @Synchronized
    fun load(gatewayKey: String, serverKey: String, limit: Int = MAX_MESSAGES): List<ChatMessage> {
        val boundedLimit = limit.coerceIn(1, MAX_MESSAGES)
        val db = readableDatabase
        val result = mutableListOf<ChatMessage>()
        db.query(
            TABLE_NAME,
            COLUMNS,
            "gateway_key = ? AND server_key = ?",
            arrayOf(gatewayKey, serverKey),
            null,
            null,
            "timestamp DESC, rowid DESC",
            boundedLimit.toString(),
        ).use { cursor ->
            val scopeIndex = cursor.getColumnIndexOrThrow("scope")
            while (cursor.moveToNext()) {
                val scope = when (cursor.getString(scopeIndex)) {
                    "channel" -> ChatScope.Channel
                    "server" -> ChatScope.Server
                    "private" -> ChatScope.Private
                    else -> ChatScope.System
                }
                result += ChatMessage(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("message_id")),
                    scope = scope,
                    targetId = cursor.stringOrNull("target_id"),
                    conversationId = cursor.stringOrNull("conversation_id"),
                    senderId = cursor.nullableLong("sender_id")?.toInt(),
                    senderName = cursor.getString(cursor.getColumnIndexOrThrow("sender_name")),
                    message = cursor.getString(cursor.getColumnIndexOrThrow("message")),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                    isSelf = cursor.getInt(cursor.getColumnIndexOrThrow("is_self")) != 0,
                )
            }
        }
        return result.asReversed()
    }

    @Synchronized
    fun save(gatewayKey: String, serverKey: String, message: ChatMessage) {
        val values = ContentValues().apply {
            put("gateway_key", gatewayKey)
            put("server_key", serverKey)
            put("message_id", message.id)
            put("scope", message.scope.name.lowercase())
            put("target_id", message.targetId)
            put("conversation_id", message.conversationId)
            if (message.senderId == null) putNull("sender_id") else put("sender_id", message.senderId)
            put("sender_name", message.senderName.take(120))
            put("message", message.message.take(500))
            put("timestamp", message.timestamp)
            put("is_self", if (message.isSelf) 1 else 0)
        }
        val db = writableDatabase
        db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.delete(
            TABLE_NAME,
            "rowid IN (SELECT rowid FROM $TABLE_NAME WHERE gateway_key = ? AND server_key = ? ORDER BY timestamp DESC, rowid DESC LIMIT -1 OFFSET ?)",
            arrayOf(gatewayKey, serverKey, MAX_MESSAGES.toString()),
        )
    }

    @Synchronized
    fun clear(gatewayKey: String, serverKey: String) {
        writableDatabase.delete(TABLE_NAME, "gateway_key = ? AND server_key = ?", arrayOf(gatewayKey, serverKey))
    }

    private fun android.database.Cursor.stringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun android.database.Cursor.nullableLong(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }

    companion object {
        private const val DATABASE_NAME = "webspeak_chat_history.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "chat_history"
        private const val MAX_MESSAGES = 2_000
        private val COLUMNS = arrayOf(
            "message_id", "scope", "target_id", "conversation_id", "sender_id", "sender_name", "message", "timestamp", "is_self",
        )
    }
}
