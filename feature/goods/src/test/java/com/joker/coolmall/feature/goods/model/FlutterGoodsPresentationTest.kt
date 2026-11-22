package com.joker.coolmall.feature.goods.model

import com.joker.coolmall.core.model.entity.Comment
import com.joker.coolmall.core.model.entity.Goods
import com.joker.coolmall.core.model.entity.GoodsDetail
import com.joker.coolmall.core.model.entity.GoodsSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlutterGoodsPresentationTest {
    @Test
    fun `detail preserves native price units and restored sku identity`() {
        val detail = GoodsDetail(Goods(id = 9, title = "Cup", mainPic = "main", price = 129, status = 1))
        val sku = GoodsSpec(id = 41, goodsId = 9, name = "Blue", stock = 2, price = 139, images = listOf("sku"))
        val payload = detail.presentation(sku)
        assertEquals(129, payload["price"])
        assertEquals(true, payload["available"])
        assertEquals(listOf("main"), payload["images"])
        assertEquals(
            mapOf("id" to 41L, "name" to "Blue", "stock" to 2, "price" to 139, "image" to "sku"),
            payload["selected"],
        )
        assertNull(detail.presentation(null)["selected"])
    }

    @Test
    fun `only displayed review information crosses the channel`() {
        val comment = Comment(id = 8, userId = 32, orderId = 74, content = "Sample", starCount = 4, avatarUrl = "avatar", pics = listOf("review"))
        val detail = GoodsDetail(goodsInfo = Goods(), comment = listOf(comment))
        assertEquals(
            listOf(mapOf("author" to null, "text" to "Sample", "stars" to 4, "avatar" to "avatar", "images" to listOf("review"))),
            detail.presentation(null)["comments"],
        )
    }
}
