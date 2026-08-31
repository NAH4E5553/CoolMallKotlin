package com.joker.coolmall.feature.order.model

import com.joker.coolmall.core.model.entity.Order
import com.joker.coolmall.core.model.entity.OrderGoods
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderPresentationTest {

    @Test
    fun `total quantity sums order goods counts instead of rows`() {
        val order = Order(
            goodsList = listOf(
                OrderGoods(goodsId = 1, count = 16),
                OrderGoods(goodsId = 2, count = 3),
            ),
        )

        assertEquals(19, order.totalGoodsQuantity())
    }

    @Test
    fun `total quantity ignores invalid negative counts and missing goods`() {
        assertEquals(0, Order().totalGoodsQuantity())
        assertEquals(
            2,
            Order(
                goodsList = listOf(
                    OrderGoods(count = -1),
                    OrderGoods(count = 2),
                ),
            ).totalGoodsQuantity(),
        )
    }

    @Test
    fun `only goods with zero comment state remain commentable`() {
        val uncommented = OrderGoods(goodsId = 1, isComment = 0)
        val commented = OrderGoods(goodsId = 2, isComment = 1)
        val unknown = OrderGoods(goodsId = 3, isComment = 2)
        val order = Order(goodsList = listOf(uncommented, commented, unknown))

        assertEquals(listOf(uncommented), order.uncommentedGoods())
        assertTrue(order.hasUncommentedGoods())
        assertFalse(Order(goodsList = listOf(commented, unknown)).hasUncommentedGoods())
        assertFalse(Order().hasUncommentedGoods())
    }
}
