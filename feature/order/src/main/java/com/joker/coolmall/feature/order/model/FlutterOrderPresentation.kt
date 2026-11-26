package com.joker.coolmall.feature.order.model

import com.joker.coolmall.core.model.entity.Address
import com.joker.coolmall.core.model.entity.Order
import com.joker.coolmall.core.model.entity.OrderGoods
import com.joker.coolmall.navigation.order.OrderRoutes
import java.math.BigDecimal

internal val OrderStatus.flutterFilter: String
    get() = when (this) {
        OrderStatus.ALL -> "all"
        OrderStatus.UNPAID -> "unpaid"
        OrderStatus.UNSHIPPED -> "unshipped"
        OrderStatus.UNRECEIVED -> "unreceived"
        OrderStatus.AFTER_SALE -> "afterSale"
        OrderStatus.UNEVALUATED -> "unevaluated"
        OrderStatus.COMPLETED -> "completed"
    }

internal enum class FlutterOrderAction(val method: String, val detailAction: OrderRoutes.DetailAction? = null) {
    CANCEL("cancel", OrderRoutes.DetailAction.CANCEL),
    PAY("pay"),
    REFUND("refund"),
    CONFIRM_RECEIPT("confirmReceipt", OrderRoutes.DetailAction.CONFIRM_RECEIPT),
    LOGISTICS("logistics"),
    REVIEW("review", OrderRoutes.DetailAction.REVIEW),
    REBUY("rebuy", OrderRoutes.DetailAction.REBUY),
}

internal fun Order.flutterActions(): List<FlutterOrderAction> = buildList {
    if (status == 0) {
        add(FlutterOrderAction.CANCEL)
        add(FlutterOrderAction.PAY)
    }
    if (status == 1 || status == 2) add(FlutterOrderAction.REFUND)
    if (status == 2) add(FlutterOrderAction.CONFIRM_RECEIPT)
    if (status in 2..6) add(FlutterOrderAction.LOGISTICS)
    if (status in 3..4 && hasUncommentedGoods()) add(FlutterOrderAction.REVIEW)
    if (status in 1..7 && status != 2 && goodsList.orEmpty().any { it.goodsId > 0 }) {
        add(FlutterOrderAction.REBUY)
    }
}

internal fun Order.flutterSnapshot(detail: Boolean = false): Map<String, Any> = buildMap {
    put("id", id.toString())
    put("status", status)
    put("number", orderNum)
    title?.let { put("title", it) }
    put("subtotalMinor", price.toLong() * 100)
    put("discountMinor", discountPrice.toLong() * 100)
    put("payableMinor", (price.toLong() - discountPrice) * 100)
    put("lines", goodsList.orEmpty().map(OrderGoods::flutterSnapshot))
    put("actions", flutterActions().map(FlutterOrderAction::method))

    if (detail) {
        createTime?.let { put("createdAt", it) }
        payTime?.let { put("paidAt", it) }
        put("payType", payType)
        remark?.let { put("remark", it) }
        closeRemark?.let { put("closeRemark", it) }
        discountSource?.info?.title?.let { put("discountTitle", it) }
        address?.let { put("address", it.flutterSnapshot()) }
        refund?.let { put("refund", it.flutterSnapshot()) }
    }
}

private fun Address.flutterSnapshot(): Map<String, Any> {
    val region = listOf(province, city, district).joinToString("")
    return mapOf(
        "contact" to contact,
        "phone" to phone,
        "location" to region + address,
        "region" to region,
        "street" to address,
        "isDefault" to isDefault,
    )
}

private fun OrderGoods.flutterSnapshot(): Map<String, Any> = buildMap {
    goodsInfo?.title?.let { put("title", it) }
    spec?.name?.let { put("specification", it) }
    (spec?.images?.firstOrNull() ?: goodsInfo?.mainPic)?.let { put("image", it) }
    put("quantity", count)
    price.minorUnits()?.let { put("unitPriceMinor", it) }
}

private fun Order.Refund.flutterSnapshot(): Map<String, Any> = buildMap {
    (realAmount ?: amount)?.minorUnits()?.let { put("amountMinor", it) }
    status?.let { put("status", it) }
    reason?.let { put("reason", it) }
    refuseReason?.let { put("refusedReason", it) }
    applyTime?.let { put("appliedAt", it) }
}

private fun Double.minorUnits(): Long? =
    if (isFinite()) runCatching { BigDecimal.valueOf(this).movePointRight(2).longValueExact() }.getOrNull() else null
