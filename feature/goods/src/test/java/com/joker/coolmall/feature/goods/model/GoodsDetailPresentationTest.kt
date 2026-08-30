package com.joker.coolmall.feature.goods.model

import com.joker.coolmall.core.model.entity.Condition
import com.joker.coolmall.core.model.entity.Coupon
import org.junit.Assert.assertEquals
import org.junit.Test

class GoodsDetailPresentationTest {
    @Test
    fun `banner images keep non-blank gallery images`() {
        val result = resolveGoodsBannerImages(
            pics = listOf("gallery-1", "", "gallery-2"),
            mainPic = "main",
        )

        assertEquals(listOf("gallery-1", "gallery-2"), result)
    }

    @Test
    fun `banner images fall back to main image when gallery is missing`() {
        val result = resolveGoodsBannerImages(pics = null, mainPic = "main")

        assertEquals(listOf("main"), result)
    }

    @Test
    fun `banner images are empty when gallery and main image are blank`() {
        val result = resolveGoodsBannerImages(pics = listOf(" "), mainPic = "")

        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `content images remove blank urls`() {
        val result = resolveGoodsContentImages(listOf("detail-1", " ", "detail-2"))

        assertEquals(listOf("detail-1", "detail-2"), result)
    }

    @Test
    fun `coupon with condition uses threshold presentation`() {
        val result = Coupon(
            amount = 20.0,
            condition = Condition(fullAmount = 100.0),
        ).toCouponTagPresentation()

        assertEquals(CouponTagPresentation.Threshold(100, 20), result)
    }

    @Test
    fun `coupon without condition uses no-threshold presentation`() {
        val result = Coupon(amount = 20.0).toCouponTagPresentation()

        assertEquals(CouponTagPresentation.NoThreshold(20), result)
    }
}
