package com.joker.coolmall.feature.cs.util

import com.joker.coolmall.core.model.entity.CsMsg
import com.joker.coolmall.core.util.log.LogUtils
import com.joker.coolmall.feature.cs.state.WebSocketConnectionState
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

private const val TAG = "WebSocketManager"
private const val WEB_SOCKET_URL = "wss://mall.dusksnow.top/socket.io/?EIO=4&transport=websocket"
private val DEFAULT_RECONNECT_DELAYS_MILLIS = listOf(1_000L, 2_000L, 3_000L)

/**
 * 客服 WebSocket 连接管理器。
 *
 * 同一个实例只维护一条期望连接。异常断开时使用最近一次有效 Token 执行有限次、可取消重连；
 * 主动断开后不会重连。
 */
class WebSocketManager internal constructor(
    private val webSocketFactory: WebSocket.Factory,
    private val reconnectDelaysMillis: List<Long>,
) {
    constructor() : this(
        webSocketFactory = createWebSocketClient(),
        reconnectDelaysMillis = DEFAULT_RECONNECT_DELAYS_MILLIS,
    )

    private val lock = Any()
    private val request = Request.Builder().url(WEB_SOCKET_URL).build()

    private val _connectionState =
        MutableStateFlow<WebSocketConnectionState>(WebSocketConnectionState.Disconnected)
    val connectionState: StateFlow<WebSocketConnectionState> = _connectionState.asStateFlow()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var connectionScope: CoroutineScope? = null
    private var token: String? = null
    private var retryCount = 0
    private var connectionGeneration = 0L
    private var connectionRequested = false
    private var disposed = false

    private var onMessageReceived: ((CsMsg) -> Unit)? = null

    fun setOnMessageReceived(callback: (CsMsg) -> Unit) {
        onMessageReceived = callback
    }

    fun connect(token: String, scope: CoroutineScope) {
        if (token.isBlank()) {
            closeConnection(dispose = false)
            updateConnectionState(WebSocketConnectionState.Error("认证信息缺失"))
            return
        }

        var staleSocket: WebSocket? = null
        val shouldConnect = synchronized(lock) {
            if (disposed ||
                _connectionState.value == WebSocketConnectionState.Connecting ||
                _connectionState.value == WebSocketConnectionState.Connected
            ) {
                false
            } else {
                connectionRequested = true
                connectionScope = scope
                this.token = token
                retryCount = 0
                reconnectJob?.cancel()
                reconnectJob = null
                staleSocket = webSocket
                webSocket = null
                true
            }
        }

        if (shouldConnect) {
            staleSocket?.cancel()
            openConnection()
        } else {
            LogUtils.d(TAG, "忽略重复或已释放的连接请求")
        }
    }

    private fun openConnection() {
        val connection = synchronized(lock) {
            val currentToken = token
            val scope = connectionScope
            if (!connectionRequested || disposed || currentToken == null || scope?.isActive != true) {
                return
            }

            connectionGeneration++
            ConnectionAttempt(
                generation = connectionGeneration,
                token = currentToken,
            )
        }

        if (!updateConnectionStateIfCurrent(
                connection.generation,
                WebSocketConnectionState.Connecting,
            )
        ) {
            return
        }
        LogUtils.d(TAG, "开始建立 WebSocket 连接")

        val listener = createListener(connection)
        val newWebSocket = runCatching {
            webSocketFactory.newWebSocket(request, listener)
        }.getOrElse { error ->
            handleConnectionFailure(connection.generation, error)
            return
        }

        synchronized(lock) {
            val state = _connectionState.value
            if (isCurrentConnectionLocked(connection.generation) &&
                (
                    state == WebSocketConnectionState.Connecting ||
                        state == WebSocketConnectionState.Connected
                    )
            ) {
                webSocket = newWebSocket
            } else {
                newWebSocket.cancel()
            }
        }
    }

    private fun createListener(connection: ConnectionAttempt): WebSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!isCurrentConnection(connection.generation)) return

            LogUtils.d(TAG, "WebSocket 传输连接成功: code=${response.code}")
            val sent = webSocket.send(WebSocketProtocol.authenticationFrame(connection.token))
            if (!sent) {
                webSocket.cancel()
                handleConnectionFailure(
                    generation = connection.generation,
                    error = IllegalStateException("认证消息发送失败"),
                )
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!isCurrentConnection(connection.generation)) return

            when (val event = WebSocketProtocol.parse(text)) {
                WebSocketEvent.Heartbeat -> {
                    if (!webSocket.send("3")) {
                        LogUtils.e(TAG, "WebSocket 心跳响应发送失败")
                    }
                }

                WebSocketEvent.Authenticated -> {
                    synchronized(lock) {
                        if (isCurrentConnectionLocked(connection.generation)) {
                            retryCount = 0
                        }
                    }
                    LogUtils.d(TAG, "WebSocket 认证成功")
                    updateConnectionStateIfCurrent(
                        connection.generation,
                        WebSocketConnectionState.Connected,
                    )
                }

                is WebSocketEvent.Message -> {
                    LogUtils.d(
                        TAG,
                        "收到客服消息: id=${event.value.id}, type=${event.value.type}",
                    )
                    onMessageReceived?.invoke(event.value)
                }

                WebSocketEvent.Ignored -> Unit
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            val isCurrent = synchronized(lock) {
                if (!isCurrentConnectionLocked(connection.generation)) return@synchronized false
                this@WebSocketManager.webSocket = null
                true
            }
            if (!isCurrent) return

            LogUtils.d(TAG, "WebSocket 连接关闭: code=$code")
            if (updateConnectionStateIfCurrent(
                    connection.generation,
                    WebSocketConnectionState.Disconnected,
                )
            ) {
                scheduleReconnect(connection.generation)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!isCurrentConnection(connection.generation)) return

            LogUtils.e(
                TAG,
                "WebSocket 连接失败: httpCode=${response?.code ?: "none"}",
                t,
            )
            handleConnectionFailure(connection.generation, t)
        }
    }

    private fun handleConnectionFailure(generation: Long, error: Throwable) {
        val isCurrent = synchronized(lock) {
            if (!isCurrentConnectionLocked(generation)) return@synchronized false
            webSocket = null
            true
        }
        if (!isCurrent) return

        if (updateConnectionStateIfCurrent(
                generation,
                WebSocketConnectionState.Error(error.message ?: "连接错误"),
            )
        ) {
            scheduleReconnect(generation)
        }
    }

    private fun scheduleReconnect(failedGeneration: Long) {
        val reconnect = synchronized(lock) {
            val scope = connectionScope
            if (!isCurrentConnectionLocked(failedGeneration) ||
                scope?.isActive != true ||
                retryCount >= reconnectDelaysMillis.size ||
                reconnectJob?.isActive == true
            ) {
                return
            }

            val delayMillis = reconnectDelaysMillis[retryCount]
            retryCount++
            ReconnectAttempt(
                scope = scope,
                failedGeneration = failedGeneration,
                attempt = retryCount,
                delayMillis = delayMillis,
            )
        }

        LogUtils.d(
            TAG,
            "WebSocket 将在 ${reconnect.delayMillis}ms 后进行第 ${reconnect.attempt} 次重连",
        )

        val job = reconnect.scope.launch(start = CoroutineStart.LAZY) {
            delay(reconnect.delayMillis)
            val shouldReconnect = synchronized(lock) {
                reconnectJob = null
                isCurrentConnectionLocked(reconnect.failedGeneration)
            }
            if (shouldReconnect) {
                openConnection()
            }
        }

        val shouldStart = synchronized(lock) {
            if (isCurrentConnectionLocked(reconnect.failedGeneration)) {
                reconnectJob = job
                true
            } else {
                false
            }
        }
        if (shouldStart) job.start() else job.cancel()
    }

    fun sendMessage(sessionId: Long, content: String, type: String = "text"): Boolean {
        if (_connectionState.value != WebSocketConnectionState.Connected) {
            LogUtils.e(TAG, "发送消息失败: WebSocket 未连接")
            return false
        }

        val frame = WebSocketProtocol.sendMessageFrame(sessionId, content, type)
        val success = webSocket?.send(frame) ?: false
        if (!success) {
            LogUtils.e(TAG, "消息发送失败: sessionId=$sessionId")
            updateConnectionState(WebSocketConnectionState.Error("消息发送失败"))
        } else {
            LogUtils.d(TAG, "消息发送成功: sessionId=$sessionId")
        }
        return success
    }

    fun disconnect() {
        closeConnection(dispose = false)
    }

    fun dispose() {
        closeConnection(dispose = true)
        (webSocketFactory as? OkHttpClient)?.let { client ->
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private fun closeConnection(dispose: Boolean) {
        val socket = synchronized(lock) {
            if (dispose) disposed = true
            connectionRequested = false
            connectionGeneration++
            retryCount = 0
            reconnectJob?.cancel()
            reconnectJob = null
            token = null
            connectionScope = null
            if (dispose) onMessageReceived = null
            _connectionState.value = WebSocketConnectionState.Disconnected
            webSocket.also { webSocket = null }
        }
        socket?.close(1000, "normal closure")
        LogUtils.d(TAG, "WebSocket 已主动断开")
    }

    fun isConnected(): Boolean = _connectionState.value == WebSocketConnectionState.Connected

    private fun isCurrentConnection(generation: Long): Boolean = synchronized(lock) {
        isCurrentConnectionLocked(generation)
    }

    private fun isCurrentConnectionLocked(generation: Long): Boolean =
        connectionRequested && !disposed && generation == connectionGeneration

    private fun updateConnectionState(state: WebSocketConnectionState) {
        synchronized(lock) {
            _connectionState.value = state
        }
    }

    private fun updateConnectionStateIfCurrent(generation: Long, state: WebSocketConnectionState): Boolean =
        synchronized(lock) {
            if (!isCurrentConnectionLocked(generation)) return@synchronized false
            _connectionState.value = state
            true
        }

    private data class ConnectionAttempt(val generation: Long, val token: String)

    private data class ReconnectAttempt(
        val scope: CoroutineScope,
        val failedGeneration: Long,
        val attempt: Int,
        val delayMillis: Long,
    )

    companion object {
        private fun createWebSocketClient(): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(0, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
