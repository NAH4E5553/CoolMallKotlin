package com.joker.coolmall.feature.goods.model

import com.joker.coolmall.core.model.entity.Coupon

internal fun resolveGoodsBannerImages(pics: List<String>?, mainPic: String): List<String> = pics
    .orEmpty()
    .filter(String::isNotBlank)
    .ifEmpty { listOfNotNull(mainPic.takeIf(String::isNotBlank)) }

internal fun resolveGoodsContentImages(contentPics: List<String>?): List<String> =
    contentPics.orEmpty().filter(String::isNotBlank)

internal sealed interface CouponTagPresentation {
    val discountAmount: Int

    data class Threshold(val fullAmount: Int, override val discountAmount: Int) : CouponTagPresentation

    data class NoThreshold(override val discountAmount: Int) : CouponTagPresentation
}

internal fun Coupon.toCouponTagPresentation(): CouponTagPresentation {
    val discountAmount = amount.toInt()
    return condition?.let {
        CouponTagPresentation.Threshold(
            fullAmount = it.fullAmount.toInt(),
            discountAmount = discountAmount,
        )
    } ?: CouponTagPresentation.NoThreshold(discountAmount)
}
