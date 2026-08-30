package com.joker.coolmall.feature.cs.util

import com.joker.coolmall.core.util.log.LogUtils
import com.joker.coolmall.feature.cs.state.WebSocketConnectionState
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WebSocketManagerTest {
    @Before
    fun setUp() {
        mockkObject(LogUtils)
        every { LogUtils.d(any<String>(), any<String>()) } returns Unit
        every { LogUtils.e(any<String>(), any<String>()) } returns Unit
        every {
            LogUtils.e(any<String>(), any<String>(), any<Throwable>())
        } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(LogUtils)
    }

    @Test
    fun `connection failure opens a new socket after each configured delay`() = runTest {
        val factory = RecordingWebSocketFactory()
        val manager = WebSocketManager(factory, listOf(100L, 200L))
        manager.connect("token", this)

        assertEquals(1, factory.connectionCount)
        factory.fail(0)
        advanceTimeBy(99)
        runCurrent()
        assertEquals(1, factory.connectionCount)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, factory.connectionCount)
        factory.fail(1)

        advanceTimeBy(200)
        runCurrent()
        assertEquals(3, factory.connectionCount)
        factory.fail(2)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(3, factory.connectionCount)
        manager.dispose()
    }

    @Test
    fun `manual disconnect cancels pending reconnect`() = runTest {
        val factory = RecordingWebSocketFactory()
        val manager = WebSocketManager(factory, listOf(100L))
        manager.connect("token", this)
        factory.fail(0)

        manager.disconnect()
        advanceTimeBy(100)
        runCurrent()

        assertEquals(1, factory.connectionCount)
        manager.dispose()
    }

    @Test
    fun `connected manager ignores duplicate connect request`() = runTest {
        val factory = RecordingWebSocketFactory()
        val manager = WebSocketManager(factory, emptyList())
        manager.connect("token", this)
        factory.message(0, """40/cs,{"sid":"session"}""")

        manager.connect("another-token", this)

        assertEquals(1, factory.connectionCount)
        manager.dispose()
    }

    @Test
    fun `blank token does not open a socket`() = runTest {
        val factory = RecordingWebSocketFactory()
        val manager = WebSocketManager(factory, emptyList())

        manager.connect("  ", this)

        assertEquals(0, factory.connectionCount)
        assertTrue(manager.connectionState.value is WebSocketConnectionState.Error)
        manager.dispose()
    }

    private class RecordingWebSocketFactory : WebSocket.Factory {
        private val connections = mutableListOf<Connection>()
        val connectionCount: Int get() = connections.size

        override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
            val socket = FakeWebSocket(request)
            connections += Connection(socket, listener)
            return socket
        }

        fun fail(index: Int) {
            val connection = connections[index]
            connection.listener.onFailure(
                connection.socket,
                IllegalStateException("network unavailable"),
                null,
            )
        }

        fun message(index: Int, frame: String) {
            val connection = connections[index]
            connection.listener.onMessage(connection.socket, frame)
        }

        private data class Connection(val socket: FakeWebSocket, val listener: WebSocketListener)
    }

    private class FakeWebSocket(private val request: Request) : WebSocket {
        override fun request(): Request = request

        override fun queueSize(): Long = 0

        override fun send(text: String): Boolean = true

        override fun send(bytes: ByteString): Boolean = true

        override fun close(code: Int, reason: String?): Boolean = true

        override fun cancel() = Unit
    }
}
