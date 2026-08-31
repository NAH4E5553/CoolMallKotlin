package com.joker.coolmall.feature.order.viewmodel

import com.joker.coolmall.core.data.repository.CommonRepository
import com.joker.coolmall.core.data.repository.OrderRepository
import com.joker.coolmall.core.model.entity.Order
import com.joker.coolmall.core.model.entity.OrderGoods
import com.joker.coolmall.core.model.response.NetworkPageData
import com.joker.coolmall.core.model.response.NetworkResponse
import com.joker.coolmall.navigation.NavigationService
import com.joker.coolmall.navigation.order.OrderChangedResultKey
import com.joker.coolmall.navigation.order.OrderRoutes
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var orderRepository: OrderRepository
    private lateinit var commonRepository: CommonRepository

    @Before
    fun setUp() {
        orderRepository = mockk()
        commonRepository = mockk()
        every { orderRepository.getOrderPage(any()) } returns flowOf(
            NetworkResponse(data = NetworkPageData(list = emptyList())),
        )

        mockkObject(NavigationService)
        every { NavigationService.resultEvents(OrderChangedResultKey) } returns emptyFlow()
        every { NavigationService.navigate(any(), any()) } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `all commented goods prevent opening comment flow`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleOrderComment(orderWithGoods(commentedGoods(COMMENTED_GOODS_ID)))

        assertTrue(viewModel.commentCartList.value.isEmpty())
        assertNull(viewModel.commentCurrentOrder.value)
        assertFalse(viewModel.commentModalVisible.value)
        verify(exactly = 0) { NavigationService.navigate(any(), any()) }
    }

    @Test
    fun `single uncommented goods navigates directly and excludes commented goods`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            val order = orderWithGoods(
                commentedGoods(COMMENTED_GOODS_ID),
                uncommentedGoods(FIRST_UNCOMMENTED_GOODS_ID),
            )

            viewModel.handleOrderComment(order)

            assertTrue(viewModel.commentCartList.value.isEmpty())
            assertNull(viewModel.commentCurrentOrder.value)
            assertFalse(viewModel.commentModalVisible.value)
            verify(exactly = 1) {
                NavigationService.navigate(
                    OrderRoutes.Comment(
                        orderId = ORDER_ID,
                        goodsId = FIRST_UNCOMMENTED_GOODS_ID,
                    ),
                    null,
                )
            }
        }

    @Test
    fun `multiple uncommented goods open selection modal with only commentable goods`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            val order = orderWithGoods(
                commentedGoods(COMMENTED_GOODS_ID),
                uncommentedGoods(FIRST_UNCOMMENTED_GOODS_ID),
                uncommentedGoods(SECOND_UNCOMMENTED_GOODS_ID),
            )

            viewModel.handleOrderComment(order)

            assertEquals(order, viewModel.commentCurrentOrder.value)
            assertEquals(
                listOf(FIRST_UNCOMMENTED_GOODS_ID, SECOND_UNCOMMENTED_GOODS_ID),
                viewModel.commentCartList.value.map { it.goodsId },
            )
            assertTrue(viewModel.commentModalVisible.value)
            verify(exactly = 0) { NavigationService.navigate(any(), any()) }
        }

    private fun createViewModel() = OrderListViewModel(
        navKey = OrderRoutes.List(),
        orderRepository = orderRepository,
        commonRepository = commonRepository,
    )

    private fun orderWithGoods(vararg goods: OrderGoods) = Order(
        id = ORDER_ID,
        goodsList = goods.toList(),
    )

    private fun commentedGoods(goodsId: Long) = OrderGoods(goodsId = goodsId, isComment = 1)

    private fun uncommentedGoods(goodsId: Long) = OrderGoods(goodsId = goodsId, isComment = 0)

    private companion object {
        const val ORDER_ID = 41L
        const val COMMENTED_GOODS_ID = 51L
        const val FIRST_UNCOMMENTED_GOODS_ID = 61L
        const val SECOND_UNCOMMENTED_GOODS_ID = 71L
    }
}
