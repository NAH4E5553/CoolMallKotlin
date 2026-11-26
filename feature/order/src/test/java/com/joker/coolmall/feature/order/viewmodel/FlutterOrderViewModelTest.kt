package com.joker.coolmall.feature.order.viewmodel

import com.joker.coolmall.core.data.repository.OrderRepository
import com.joker.coolmall.core.data.state.AppState
import com.joker.coolmall.core.model.entity.Auth
import com.joker.coolmall.core.model.entity.Order
import com.joker.coolmall.core.model.request.OrderPageRequest
import com.joker.coolmall.core.model.response.NetworkPageData
import com.joker.coolmall.core.model.response.NetworkPageMeta
import com.joker.coolmall.core.model.response.NetworkResponse
import com.joker.coolmall.feature.order.model.FlutterOrderAction
import com.joker.coolmall.feature.order.model.OrderStatus
import com.joker.coolmall.navigation.order.OrderRoutes
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlutterOrderViewModelTest {
    private val currentUserId = MutableStateFlow(17L)
    private val currentAuth = MutableStateFlow<Auth?>(Auth(createdAt = 1))
    private val appState = mockk<AppState> {
        every { userId } returns currentUserId
        every { isLoggedIn } returns MutableStateFlow(true)
        every { auth } returns currentAuth
    }
    private val repository = mockk<OrderRepository>()
    private val model = FlutterOrderViewModel(repository, appState)
    private val order = Order(id = 9007199254740993L, userId = 17L, status = 2, price = 3999)

    @Test
    fun `list translates the native filter and honors the backend total`() = runTest {
        val request = OrderPageRequest(page = 2, size = 20, status = listOf(5, 6))
        every { repository.getOrderPage(request) } returns flowOf(
            NetworkResponse(
                NetworkPageData(list = listOf(order.copy(status = 6)), pagination = NetworkPageMeta(21, 10, 2)),
            ),
        )

        val page = model.page(17, OrderStatus.AFTER_SALE, 2, 20)

        assertEquals(listOf(order.copy(status = 6)), page.orders)
        assertTrue(page.hasMore)
        verify(exactly = 1) { repository.getOrderPage(request) }
    }

    @Test
    fun `empty results are valid and business errors are failures`() = runTest {
        val request = OrderPageRequest(page = 1, size = 20)
        every { repository.getOrderPage(request) } returnsMany listOf(
            flowOf(NetworkResponse()),
            flowOf(NetworkResponse(code = 1001)),
        )

        val empty = model.page(17, OrderStatus.ALL, 1, 20)
        assertTrue(empty.orders.isEmpty())
        assertFalse(empty.hasMore)
        assertTrue(runCatching { model.page(17, OrderStatus.ALL, 1, 20) }.isFailure)
    }

    @Test
    fun `detail rejects a mismatched order or account`() = runTest {
        every { repository.getOrderInfo(order.id) } returnsMany listOf(
            flowOf(NetworkResponse(order.copy(userId = 18L))),
            flowOf(NetworkResponse(order.copy(id = 1L))),
        )

        assertTrue(runCatching { model.detail(17, order.id) }.isFailure)
        assertTrue(runCatching { model.detail(17, order.id) }.isFailure)
    }

    @Test
    fun `a response from a replaced session is cancelled`() = runTest {
        val pending = CompletableDeferred<Order>()
        every { repository.getOrderInfo(order.id) } returns flow { emit(NetworkResponse(pending.await())) }

        val request = async { model.detail(17, order.id) }
        yield()
        currentAuth.value = Auth(createdAt = 2)
        pending.complete(order)

        assertTrue(runCatching { request.await() }.exceptionOrNull() is CancellationException)
    }

    @Test
    fun `actions use the latest status to resolve a native destination`() = runTest {
        every { repository.getOrderInfo(order.id) } returns flowOf(NetworkResponse(order))

        assertEquals(
            OrderRoutes.Detail(order.id, OrderRoutes.DetailAction.CONFIRM_RECEIPT),
            model.action(17, order.id, FlutterOrderAction.CONFIRM_RECEIPT),
        )
        assertEquals(OrderRoutes.Logistics(order.id), model.action(17, order.id, FlutterOrderAction.LOGISTICS))
        assertTrue(runCatching { model.action(17, order.id, FlutterOrderAction.PAY) }.isFailure)
    }
}
