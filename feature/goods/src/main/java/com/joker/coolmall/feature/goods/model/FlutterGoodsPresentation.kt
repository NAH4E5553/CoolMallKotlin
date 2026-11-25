package com.joker.coolmall.feature.goods.model

import com.joker.coolmall.core.model.entity.GoodsDetail
import com.joker.coolmall.core.model.entity.GoodsSpec

internal fun GoodsSpec.presentation() = mapOf(
    "id" to id,
    "name" to name,
    "price" to price,
    "stock" to stock,
    "image" to images?.firstOrNull(),
)

internal fun GoodsDetail.presentation(selected: GoodsSpec?): Map<String, Any?> = mapOf(
    "selected" to selected?.presentation(),
    "title" to goodsInfo.title,
    "subtitle" to goodsInfo.subTitle,
    "price" to goodsInfo.price,
    "sold" to goodsInfo.sold,
    "available" to (goodsInfo.status == 1),
    "images" to resolveGoodsBannerImages(goodsInfo.pics, goodsInfo.mainPic),
    "details" to resolveGoodsContentImages(goodsInfo.contentPics),
    "coupons" to coupon.map {
        mapOf(
            "id" to it.id,
            "title" to it.title,
            "amount" to it.amount,
            "minimum" to it.condition?.fullAmount,
            "description" to it.description,
            "endTime" to it.endTime,
        )
    },
    "comments" to comment.map {
        mapOf(
            "author" to it.nickName,
            "text" to it.content,
            "stars" to it.starCount,
            "avatar" to it.avatarUrl,
            "images" to it.pics.orEmpty(),
        )
    },
)
