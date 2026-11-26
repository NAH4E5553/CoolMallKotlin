package com.joker.coolmall.feature.order.view

import com.joker.coolmall.core.flutter.FlutterContainerConnection
import com.joker.coolmall.feature.order.model.FlutterOrderAction
import com.joker.coolmall.feature.order.model.OrderStatus
import com.joker.coolmall.feature.order.model.flutterFilter
import com.joker.coolmall.feature.order.model.flutterSnapshot
import com.joker.coolmall.feature.order.viewmodel.FlutterOrderViewModel
import com.joker.coolmall.navigation.navigate
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class FlutterOrderChannel(
    connection: FlutterContainerConnection,
    private val accountId: Long,
    private val model: FlutterOrderViewModel,
) : AutoCloseable {
    private val scope = connection.scope
    private val closePage = connection.close
    private val channel = MethodChannel(connection.messenger, "coolmall/orders/${connection.id}")
    private val requests = mutableSetOf<Job>()
    private var actionPending = false

    init {
        channel.setMethodCallHandler { call, reply ->
            val work = scope.launch { handle(call, reply) }
            requests.add(work)
            work.invokeOnCompletion { requests.remove(work) }
        }
    }

    override fun close() {
        channel.setMethodCallHandler(null)
        requests.toList().forEach { it.cancel() }
    }

    private suspend fun handle(call: MethodCall, reply: MethodChannel.Result) {
        try {
            when (call.method) {
                "list" -> {
                    val filter = OrderStatus.entries.first { it.flutterFilter == call.argument<String>("filter") }
                    val page = model.page(
                        accountId = accountId,
                        filter = filter,
                        page = requireNotNull(call.argument<Int>("page")),
                        size = requireNotNull(call.argument<Int>("pageSize")),
                    )
                    reply.success(
                        mapOf(
                            "orders" to page.orders.map {
                                it.flutterSnapshot()
                            },
                            "hasMore" to page.hasMore,
                        ),
                    )
                }

                "detail" -> reply.success(model.detail(accountId, call.orderId()).flutterSnapshot(detail = true))

                "action" -> openNativeAction(call, reply)

                "close" -> {
                    closePage()
                    reply.success(null)
                }

                else -> reply.notImplemented()
            }
        } catch (cancelled: CancellationException) {
            reply.error("closed", null, null)
            throw cancelled
        } catch (_: Exception) {
            reply.error("unavailable", null, null)
        }
    }

    private suspend fun openNativeAction(call: MethodCall, reply: MethodChannel.Result) {
        check(!actionPending)
        actionPending = true
        try {
            val action = FlutterOrderAction.entries.first { it.method == call.argument<String>("action") }
            val route = model.action(accountId, call.orderId(), action)
            // Native operations own their confirmations and result. The Flutter entry reloads
            // from the repository when its container is mounted again after native navigation.
            navigate(route)
            reply.success(false)
        } finally {
            actionPending = false
        }
    }

    private fun MethodCall.orderId(): Long {
        val raw = requireNotNull(argument<String>("id"))
        val id = raw.toLong()
        require(id > 0 && id.toString() == raw)
        return id
    }
}
