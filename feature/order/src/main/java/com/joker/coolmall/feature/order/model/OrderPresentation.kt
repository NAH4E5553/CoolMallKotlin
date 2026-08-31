package com.joker.coolmall.feature.order.model

import com.joker.coolmall.core.model.entity.Order
import com.joker.coolmall.core.model.entity.OrderGoods

/**
 * 订单中实际购买的商品总件数。
 */
internal fun Order.totalGoodsQuantity(): Int = goodsList.orEmpty().sumOf { it.count.coerceAtLeast(0) }

/**
 * 当前订单中仍允许评价的商品。
 */
internal fun Order.uncommentedGoods(): List<OrderGoods> = goodsList.orEmpty().filter { it.isComment == 0 }

/**
 * 当前订单是否仍有允许评价的商品。
 */
internal fun Order.hasUncommentedGoods(): Boolean = goodsList.orEmpty().any { it.isComment == 0 }
