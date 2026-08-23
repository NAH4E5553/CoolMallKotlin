package com.joker.coolmall.feature.main.viewmodel

import com.joker.coolmall.core.model.entity.Cart
import com.joker.coolmall.core.model.entity.CartGoodsSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CartSelectionTest {

    @Test
    fun `select all includes every spec and ignores empty cart groups`() {
        val carts = listOf(
            cart(goodsId = 1, spec(id = 11), spec(id = 12)),
            cart(goodsId = 2, spec(id = 21)),
            cart(goodsId = 3),
        )

        val selection = selectAllCartItems(carts)

        assertEquals(
            mapOf(
                1L to setOf(11L, 12L),
                2L to setOf(21L),
            ),
            selection,
        )
        assertTrue(areAllCartItemsSelected(carts, selection))
    }

    @Test
    fun `empty cart is never all selected`() {
        assertFalse(areAllCartItemsSelected(emptyList(), emptyMap()))
    }

    @Test
    fun `unknown selected id cannot replace missing cart spec`() {
        val carts = listOf(cart(goodsId = 1, spec(id = 11), spec(id = 12)))
        val selection = mapOf(1L to setOf(11L, 999L))

        assertFalse(areAllCartItemsSelected(carts, selection))
    }

    @Test
    fun `toggle selection removes empty goods group`() {
        val selected = toggleCartItemSelection(
            currentSelection = emptyMap(),
            goodsId = 1,
            specId = 11,
        )
        val deselected = toggleCartItemSelection(
            currentSelection = selected,
            goodsId = 1,
            specId = 11,
        )

        assertEquals(mapOf(1L to setOf(11L)), selected)
        assertEquals(emptyMap<Long, Set<Long>>(), deselected)
    }

    @Test
    fun `selected count sums specs across goods`() {
        val selection = mapOf(
            1L to setOf(11L, 12L),
            2L to setOf(21L),
        )

        assertEquals(3, selectedCartItemCount(selection))
    }

    @Test
    fun `total amount uses selected spec price and quantity`() {
        val carts = listOf(
            cart(
                goodsId = 1,
                spec(id = 11, price = 100, count = 2),
                spec(id = 12, price = 40, count = 3),
            ),
            cart(
                goodsId = 2,
                spec(id = 21, price = 25, count = 4),
            ),
        )
        val selection = mapOf(
            1L to setOf(11L),
            2L to setOf(21L),
            99L to setOf(999L),
        )

        assertEquals(300, selectedCartTotalAmount(carts, selection))
    }

    private fun cart(goodsId: Long, vararg specs: CartGoodsSpec): Cart = Cart(
        goodsId = goodsId,
        spec = specs.toList(),
    )

    private fun spec(id: Long, price: Int = 0, count: Int = 0): CartGoodsSpec = CartGoodsSpec(
        id = id,
        price = price,
        count = count,
    )
}
