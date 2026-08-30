package com.joker.coolmall.feature.cs.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSocketProtocolTest {
    @Test
    fun `authentication frame escapes token as json`() {
        val token = "token\"with\\special\ncharacters"

        val frame = WebSocketProtocol.authenticationFrame(token)
        val payload = Json.parseToJsonElement(frame.substringAfter("40/cs,")).jsonObject

        assertEquals(false, payload.getValue("isAdmin").jsonPrimitive.boolean)
        assertEquals(token, payload.getValue("token").jsonPrimitive.content)
    }

    @Test
    fun `send frame preserves message special characters`() {
        val content = "hello \"customer\"\\next\nline"

        val frame = WebSocketProtocol.sendMessageFrame(
            sessionId = 42,
            content = content,
            type = "text",
        )
        val event = Json.parseToJsonElement(frame.substringAfter("42/cs,")).jsonArray
        val payload = event[1].jsonObject

        assertEquals("send", event[0].jsonPrimitive.content)
        assertEquals(42L, payload.getValue("sessionId").jsonPrimitive.long)
        assertEquals(
            content,
            payload.getValue("content").jsonObject.getValue("data").jsonPrimitive.content,
        )
    }

    @Test
    fun `namespaced message frame is decoded`() {
        val event = WebSocketProtocol.parse(
            """42/cs,["msg",{"id":7,"content":{"type":"text","data":"hello"},"type":1}]""",
        )

        assertTrue(event is WebSocketEvent.Message)
        val message = (event as WebSocketEvent.Message).value
        assertEquals(7L, message.id)
        assertEquals(1, message.type)
        assertEquals("hello", message.content.data)
    }

    @Test
    fun `malformed event is ignored`() {
        assertEquals(WebSocketEvent.Ignored, WebSocketProtocol.parse("42/cs,[broken"))
        assertEquals(WebSocketEvent.Ignored, WebSocketProtocol.parse("42/cs,[{}]"))
    }
}
