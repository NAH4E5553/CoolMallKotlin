package com.joker.coolmall.feature.order.viewmodel

import com.joker.coolmall.core.data.repository.OrderRepository
import com.joker.coolmall.core.model.entity.Order
import com.joker.coolmall.core.model.response.NetworkResponse
import com.joker.coolmall.core.util.log.LogUtils
import com.joker.coolmall.core.util.toast.ToastUtils
import com.joker.coolmall.feature.order.R
import com.joker.coolmall.navigation.NavigationService
import com.joker.coolmall.navigation.order.OrderRoutes
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderPayViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var orderRepository: OrderRepository

    @Before
    fun setUp() {
        orderRepository = mockk()

        mockkObject(ToastUtils)
        every { ToastUtils.showSuccess(any<Int>()) } just runs
        every { ToastUtils.showError(any<Int>()) } just runs
        every { ToastUtils.showError(any<CharSequence>()) } just runs
        every { ToastUtils.showWarning(any<Int>()) } just runs

        mockkObject(LogUtils)
        every { LogUtils.e(any<String>()) } just runs
        every { LogUtils.e(any<String>(), any<String>(), any<Throwable>()) } just runs

        mockkObject(NavigationService)
        every { NavigationService.navigateBack() } just runs
        every { NavigationService.navigate(any(), any()) } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `duplicate pay click requests payment parameters only once`() = runTest(mainDispatcherRule.dispatcher) {
        val payInfoResponse = CompletableDeferred<NetworkResponse<String>>()
        every { orderRepository.alipayAppPay(any()) } returns flow { emit(payInfoResponse.await()) }
        val viewModel = createViewModel()

        viewModel.startAlipayPayment()
        viewModel.startAlipayPayment()
        runCurrent()

        verify(exactly = 1) { orderRepository.alipayAppPay(any()) }
        assertTrue(viewModel.isPaymentInProgress.value)

        payInfoResponse.complete(NetworkResponse(data = PAY_INFO))
        advanceUntilIdle()

        assertEquals(PAY_INFO, viewModel.alipayPayInfo.value)
        assertTrue(viewModel.isPaymentInProgress.value)
    }

    @Test
    fun `SDK success is reported only after server confirms paid order`() = runTest(mainDispatcherRule.dispatcher) {
        every { orderRepository.alipayAppPay(any()) } returns flowOf(NetworkResponse(data = PAY_INFO))
        every { orderRepository.getOrderInfo(ORDER_ID) } returnsMany listOf(
            flowOf(NetworkResponse(data = Order(id = ORDER_ID, status = 0))),
            flowOf(NetworkResponse(data = Order(id = ORDER_ID, status = 0))),
            flowOf(NetworkResponse(data = Order(id = ORDER_ID, status = 1))),
        )
        val viewModel = createViewModel()
        startPayment(viewModel)

        viewModel.processAlipayResult(mapOf("resultStatus" to "9000"))
        advanceUntilIdle()

        verify(exactly = 3) { orderRepository.getOrderInfo(ORDER_ID) }
        verify(exactly = 1) { ToastUtils.showSuccess(R.string.payment_success) }
        verify(exactly = 0) { ToastUtils.showWarning(R.string.payment_confirmation_pending) }
        assertFalse(viewModel.isPaymentInProgress.value)
    }

    @Test
    fun `unconfirmed SDK success shows pending result instead of payment success`() =
        runTest(mainDispatcherRule.dispatcher) {
            every { orderRepository.alipayAppPay(any()) } returns flowOf(NetworkResponse(data = PAY_INFO))
            every { orderRepository.getOrderInfo(ORDER_ID) } returns
                flowOf(NetworkResponse(data = Order(id = ORDER_ID, status = 0)))
            val viewModel = createViewModel()
            startPayment(viewModel)

            viewModel.processAlipayResult(mapOf("resultStatus" to "9000"))
            advanceUntilIdle()

            verify(exactly = 3) { orderRepository.getOrderInfo(ORDER_ID) }
            verify(exactly = 0) { ToastUtils.showSuccess(R.string.payment_success) }
            verify(exactly = 1) { ToastUtils.showWarning(R.string.payment_confirmation_pending) }
            assertFalse(viewModel.isPaymentInProgress.value)
        }

    @Test
    fun `closed server order is reported as payment failure without retrying`() =
        runTest(mainDispatcherRule.dispatcher) {
            every { orderRepository.alipayAppPay(any()) } returns flowOf(NetworkResponse(data = PAY_INFO))
            every { orderRepository.getOrderInfo(ORDER_ID) } returns
                flowOf(NetworkResponse(data = Order(id = ORDER_ID, status = 7)))
            val viewModel = createViewModel()
            startPayment(viewModel)

            viewModel.processAlipayResult(mapOf("resultStatus" to "9000"))
            advanceUntilIdle()

            verify(exactly = 1) { orderRepository.getOrderInfo(ORDER_ID) }
            verify(exactly = 1) { ToastUtils.showError(R.string.payment_failed) }
            verify(exactly = 0) { ToastUtils.showSuccess(R.string.payment_success) }
            verify(exactly = 0) { ToastUtils.showWarning(R.string.payment_confirmation_pending) }
            assertFalse(viewModel.isPaymentInProgress.value)
        }

    @Test
    fun `cancelled SDK result skips server confirmation and restores payment state`() =
        runTest(mainDispatcherRule.dispatcher) {
            every { orderRepository.alipayAppPay(any()) } returns flowOf(NetworkResponse(data = PAY_INFO))
            val viewModel = createViewModel()
            startPayment(viewModel)

            viewModel.processAlipayResult(mapOf("resultStatus" to "6001"))
            advanceUntilIdle()

            verify(exactly = 0) { orderRepository.getOrderInfo(any()) }
            verify(exactly = 1) { ToastUtils.showError(R.string.payment_cancel) }
            assertFalse(viewModel.isPaymentInProgress.value)
        }

    @Test
    fun `payment parameter failure restores button state`() = runTest(mainDispatcherRule.dispatcher) {
        every { orderRepository.alipayAppPay(any()) } returns
            flowOf(NetworkResponse(code = 400, message = "pay info failed"))
        val viewModel = createViewModel()

        viewModel.startAlipayPayment()
        advanceUntilIdle()

        assertFalse(viewModel.isPaymentInProgress.value)
        assertEquals("", viewModel.alipayPayInfo.value)
        verify(exactly = 1) { ToastUtils.showError("pay info failed") }
    }

    private suspend fun TestScope.startPayment(viewModel: OrderPayViewModel) {
        viewModel.startAlipayPayment()
        advanceUntilIdle()
        assertTrue(viewModel.isPaymentInProgress.value)
    }

    private fun createViewModel() = OrderPayViewModel(
        navKey = OrderRoutes.Pay(orderId = ORDER_ID, price = 90, from = "confirm"),
        orderRepository = orderRepository,
    )

    private companion object {
        const val ORDER_ID = 41L
        const val PAY_INFO = "signed-pay-info"
    }
}
