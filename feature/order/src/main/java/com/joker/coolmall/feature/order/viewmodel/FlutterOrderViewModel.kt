package com.joker.coolmall.feature.order.viewmodel

import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavKey
import com.joker.coolmall.core.data.repository.OrderRepository
import com.joker.coolmall.core.data.state.AppState
import com.joker.coolmall.core.model.entity.Order
import com.joker.coolmall.core.model.request.OrderPageRequest
import com.joker.coolmall.core.model.response.NetworkResponse
import com.joker.coolmall.feature.order.model.FlutterOrderAction
import com.joker.coolmall.feature.order.model.OrderStatus
import com.joker.coolmall.feature.order.model.flutterActions
import com.joker.coolmall.feature.order.model.wireStatuses
import com.joker.coolmall.navigation.order.OrderRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

internal data class FlutterOrderPage(val orders: List<Order>, val hasMore: Boolean)

@HiltViewModel
internal class FlutterOrderViewModel @Inject constructor(
    private val repository: OrderRepository,
    private val appState: AppState,
) : ViewModel() {
    val userId = appState.userId
    val isLoggedIn = appState.isLoggedIn

    suspend fun page(accountId: Long, filter: OrderStatus, page: Int, size: Int): FlutterOrderPage {
        require(page > 0 && size == 20)
        val response = withAccount(accountId) {
            repository.getOrderPage(OrderPageRequest(page, size, filter.wireStatuses())).first()
        }
        check(response.isSucceeded)
        val data = response.data ?: return FlutterOrderPage(emptyList(), hasMore = false)
        val orders = data.list.orEmpty()
        check(orders.all { it.belongsTo(accountId) })
        val pagination = data.pagination
        val returnedPage = pagination?.page ?: page
        val returnedSize = pagination?.size ?: size
        check(returnedPage == page && returnedSize in 1..size)
        val hasMore = pagination?.total?.let { total ->
            check(total >= 0)
            returnedPage.toLong() * returnedSize < total
        } ?: (orders.size == returnedSize)
        return FlutterOrderPage(orders, hasMore)
    }

    suspend fun detail(accountId: Long, orderId: Long): Order {
        require(orderId > 0)
        val order = withAccount(accountId) { repository.getOrderInfo(orderId).first().requireData() }
        check(order.id == orderId && order.belongsTo(accountId))
        return order
    }

    suspend fun action(accountId: Long, orderId: Long, action: FlutterOrderAction): NavKey {
        val order = detail(accountId, orderId)
        check(action in order.flutterActions())
        action.detailAction?.let { return OrderRoutes.Detail(order.id, it) }
        return when (action) {
            FlutterOrderAction.PAY -> {
                val price = Math.subtractExact(order.price, order.discountPrice)
                check(price >= 0)
                OrderRoutes.Pay(order.id, price)
            }

            FlutterOrderAction.REFUND -> OrderRoutes.Refund(order.id)

            FlutterOrderAction.LOGISTICS -> OrderRoutes.Logistics(order.id)

            else -> error("Invalid order action")
        }
    }

    private suspend fun <T> withAccount(accountId: Long, request: suspend () -> T): T {
        if (accountId <= 0 || userId.value != accountId || !isLoggedIn.value) throw CancellationException()
        val auth = appState.auth.value ?: throw CancellationException()
        val result = request()
        if (userId.value != accountId || !isLoggedIn.value || appState.auth.value != auth) {
            throw CancellationException()
        }
        return result
    }

    private fun Order.belongsTo(accountId: Long) = userId == 0L || userId == accountId

    private fun <T> NetworkResponse<T>.requireData(): T {
        check(isSucceeded)
        return checkNotNull(data)
    }
}
