package com.joker.coolmall.feature.order.model

import com.joker.coolmall.core.model.entity.Address
import com.joker.coolmall.core.model.entity.Order
import com.joker.coolmall.core.model.entity.OrderGoods
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FlutterOrderPresentationTest {
    @Test
    fun `bridge preserves IDs prices and detail fields`() {
        val order = Order(
            id = 9007199254740993L,
            price = 3999,
            discountPrice = 30,
            status = 6,
            address = Address(contact = "测试联系人", province = "测试省", address = "测试路", isDefault = true),
            goodsList = listOf(OrderGoods(price = 15.02, count = 2)),
            refund = Order.Refund(amount = 15.02, status = 1),
        )

        val listCard = order.flutterSnapshot()
        val detail = order.flutterSnapshot(detail = true)
        assertEquals("9007199254740993", listCard["id"])
        assertEquals(396900L, listCard["payableMinor"])
        assertEquals(listOf(mapOf<String, Any>("quantity" to 2, "unitPriceMinor" to 1502L)), listCard["lines"])
        assertFalse(listCard.containsKey("address"))
        assertEquals(
            mapOf(
                "contact" to "测试联系人",
                "phone" to "",
                "location" to "测试省测试路",
                "region" to "测试省",
                "street" to "测试路",
                "isDefault" to true,
            ),
            detail["address"],
        )
        assertEquals(mapOf<String, Any>("amountMinor" to 1502L, "status" to 1), detail["refund"])
    }

    @Test
    fun `profile tabs select the matching Flutter filter`() {
        assertEquals("all", orderTab(null).flutterFilter)
        assertEquals("afterSale", orderTab("4").flutterFilter)
        assertEquals("unevaluated", orderTab("5").flutterFilter)
        assertEquals("all", orderTab("99").flutterFilter)
    }
}
