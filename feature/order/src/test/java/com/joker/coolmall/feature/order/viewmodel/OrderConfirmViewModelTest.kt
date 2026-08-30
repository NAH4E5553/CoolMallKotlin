package com.joker.coolmall.feature.order.viewmodel

import android.content.Context
import com.joker.coolmall.core.data.repository.CartRepository
import com.joker.coolmall.core.data.repository.OrderRepository
import com.joker.coolmall.core.data.repository.PageRepository
import com.joker.coolmall.core.model.entity.Address
import com.joker.coolmall.core.model.entity.Cart
import com.joker.coolmall.core.model.entity.CartGoodsSpec
import com.joker.coolmall.core.model.entity.ConfirmOrder
import com.joker.coolmall.core.model.entity.Order
import com.joker.coolmall.core.model.entity.SelectedGoods
import com.joker.coolmall.core.model.response.NetworkResponse
import com.joker.coolmall.core.navigation.user.SelectAddressResultKey
import com.joker.coolmall.core.util.log.LogUtils
import com.joker.coolmall.core.util.storage.MMKVUtils
import com.joker.coolmall.core.util.toast.ToastUtils
import com.joker.coolmall.feature.order.R
import com.joker.coolmall.navigation.NavigationService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderConfirmViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var orderRepository: OrderRepository
    private lateinit var cartRepository: CartRepository
    private lateinit var pageRepository: PageRepository

    @Before
    fun setUp() {
        context = mockk()
        every { context.getString(any()) } returns "Purchase goods"
        orderRepository = mockk()
        cartRepository = mockk()
        pageRepository = mockk()
        every { pageRepository.getConfirmOrder() } returns
            flowOf(NetworkResponse(data = ConfirmOrder(defaultAddress = Address(id = ADDRESS_ID))))

        mockkObject(MMKVUtils)
        every { MMKVUtils.remove(any()) } just runs

        mockkObject(ToastUtils)
        every { ToastUtils.showError(any<Int>()) } just runs
        every { ToastUtils.showError(any<CharSequence>()) } just runs
        every { ToastUtils.showWarning(any<Int>()) } just runs

        mockkObject(LogUtils)
        every { LogUtils.e(any<String>(), any<String>(), any<Throwable>()) } just runs

        mockkObject(NavigationService)
        every { NavigationService.resultEvents(SelectAddressResultKey) } returns emptyFlow()
        every { NavigationService.navigateAndCloseCurrent(any(), any()) } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `duplicate submit creates only one order while request is active`() = runTest(mainDispatcherRule.dispatcher) {
        cacheCheckoutData(selectedGoods = SELECTED_GOODS)
        val createResponse = CompletableDeferred<NetworkResponse<Order>>()
        every { orderRepository.createOrder(any()) } returns flow { emit(createResponse.await()) }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSubmitOrderClick()
        viewModel.onSubmitOrderClick()
        runCurrent()

        verify(exactly = 1) { orderRepository.createOrder(any()) }
        assertTrue(viewModel.isSubmitting.value)

        createResponse.complete(NetworkResponse(data = CREATED_ORDER))
        advanceUntilIdle()

        assertFalse(viewModel.isSubmitting.value)
        verify(exactly = 1) { NavigationService.navigateAndCloseCurrent(any(), any()) }
    }

    @Test
    fun `navigation waits until purchased cart items are removed`() = runTest(mainDispatcherRule.dispatcher) {
        cacheCheckoutData(selectedGoods = SELECTED_GOODS, carts = PURCHASED_CARTS)
        every { orderRepository.createOrder(any()) } returns flowOf(NetworkResponse(data = CREATED_ORDER))
        val cartLookup = CompletableDeferred<Cart?>()
        coEvery { cartRepository.getCartByGoodsId(GOODS_ID) } coAnswers { cartLookup.await() }
        coEvery { cartRepository.removeFromCart(GOODS_ID) } just runs
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSubmitOrderClick()
        runCurrent()

        verify(exactly = 0) { NavigationService.navigateAndCloseCurrent(any(), any()) }

        cartLookup.complete(PURCHASED_CARTS.single())
        advanceUntilIdle()

        coVerify(exactly = 1) { cartRepository.removeFromCart(GOODS_ID) }
        verify(exactly = 1) { NavigationService.navigateAndCloseCurrent(any(), any()) }
    }

    @Test
    fun `cart cleanup failure is visible and does not encourage duplicate order`() =
        runTest(mainDispatcherRule.dispatcher) {
            cacheCheckoutData(selectedGoods = SELECTED_GOODS, carts = PURCHASED_CARTS)
            every { orderRepository.createOrder(any()) } returns flowOf(NetworkResponse(data = CREATED_ORDER))
            coEvery { cartRepository.getCartByGoodsId(GOODS_ID) } throws IllegalStateException("db failed")
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onSubmitOrderClick()
            advanceUntilIdle()

            verify(exactly = 1) { ToastUtils.showWarning(R.string.cart_cleanup_failed) }
            verify(exactly = 1) { NavigationService.navigateAndCloseCurrent(any(), any()) }
            assertFalse(viewModel.isSubmitting.value)
        }

    @Test
    fun `empty checkout goods prevents order creation`() = runTest(mainDispatcherRule.dispatcher) {
        cacheCheckoutData(selectedGoods = emptyList())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSubmitOrderClick()
        runCurrent()

        verify(exactly = 0) { orderRepository.createOrder(any()) }
        verify(exactly = 1) { ToastUtils.showError(R.string.order_goods_empty) }
        assertFalse(viewModel.isSubmitting.value)
    }

    @Test
    fun `order creation failure restores submit state without navigation`() = runTest(mainDispatcherRule.dispatcher) {
        cacheCheckoutData(selectedGoods = SELECTED_GOODS)
        every { orderRepository.createOrder(any()) } returns
            flowOf(NetworkResponse(code = 400, message = "create failed"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSubmitOrderClick()
        advanceUntilIdle()

        assertFalse(viewModel.isSubmitting.value)
        verify(exactly = 1) { ToastUtils.showError("create failed") }
        verify(exactly = 0) { NavigationService.navigateAndCloseCurrent(any(), any()) }
    }

    private fun cacheCheckoutData(selectedGoods: List<SelectedGoods>, carts: List<Cart>? = null) {
        every { MMKVUtils.getString("selectedGoodsList", "") } returns Json.encodeToString(selectedGoods)
        every { MMKVUtils.getString("carts", "") } returns carts?.let { Json.encodeToString(it) }.orEmpty()
    }

    private fun createViewModel() = OrderConfirmViewModel(
        context = context,
        orderRepository = orderRepository,
        cartRepository = cartRepository,
        pageRepository = pageRepository,
    )

    private companion object {
        const val ADDRESS_ID = 11L
        const val GOODS_ID = 21L
        const val SPEC_ID = 31L
        val SELECTED_GOODS = listOf(SelectedGoods(goodsId = GOODS_ID, count = 1))
        val PURCHASED_CARTS = listOf(
            Cart(
                goodsId = GOODS_ID,
                spec = listOf(CartGoodsSpec(id = SPEC_ID, goodsId = GOODS_ID, count = 1)),
            ),
        )
        val CREATED_ORDER = Order(id = 41L, price = 100, discountPrice = 10)
    }
}
