package com.joker.coolmall.feature.cs.util

import com.joker.coolmall.core.model.entity.CsMsg
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put

private const val NAMESPACE = "/cs"

internal sealed interface WebSocketEvent {
    data object Authenticated : WebSocketEvent

    data object Heartbeat : WebSocketEvent

    data class Message(val value: CsMsg) : WebSocketEvent

    data object Ignored : WebSocketEvent
}

internal object WebSocketProtocol {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun authenticationFrame(token: String): String {
        val payload = buildJsonObject {
            put("isAdmin", false)
            put("token", token)
        }
        return "40$NAMESPACE,$payload"
    }

    fun sendMessageFrame(sessionId: Long, content: String, type: String): String {
        val payload = buildJsonObject {
            put("sessionId", sessionId)
            put(
                "content",
                buildJsonObject {
                    put("type", type)
                    put("data", content)
                },
            )
        }
        val event = buildJsonArray {
            add(JsonPrimitive("send"))
            add(payload)
        }
        return "42$NAMESPACE,$event"
    }

    fun parse(frame: String): WebSocketEvent = when {
        frame == "2" -> WebSocketEvent.Heartbeat
        frame.startsWith("40$NAMESPACE,") -> WebSocketEvent.Authenticated
        frame.startsWith("42$NAMESPACE,") -> parseEvent(frame.substringAfter("42$NAMESPACE,"))
        frame.startsWith("42[") -> parseEvent(frame.removePrefix("42"))
        else -> WebSocketEvent.Ignored
    }

    private fun parseEvent(payload: String): WebSocketEvent {
        val event = runCatching {
            json.parseToJsonElement(payload) as? JsonArray
        }.getOrNull() ?: return WebSocketEvent.Ignored

        return when ((event.getOrNull(0) as? JsonPrimitive)?.contentOrNull) {
            "msg" -> {
                val messagePayload = event.getOrNull(1) ?: return WebSocketEvent.Ignored
                runCatching {
                    WebSocketEvent.Message(json.decodeFromJsonElement<CsMsg>(messagePayload))
                }.getOrDefault(WebSocketEvent.Ignored)
            }

            "message" -> {
                if ((event.getOrNull(1) as? JsonPrimitive)?.contentOrNull == "连接成功") {
                    WebSocketEvent.Authenticated
                } else {
                    WebSocketEvent.Ignored
                }
            }

            else -> WebSocketEvent.Ignored
        }
    }
}
