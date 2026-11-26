package com.joker.coolmall.feature.order.model

import androidx.annotation.StringRes
import com.joker.coolmall.feature.order.R

/**
 * 订单状态枚举
 *
 * @param labelRes 状态显示文本资源ID
 * @author Joker.X
 */
enum class OrderStatus(@param:StringRes val labelRes: Int) {
    ALL(R.string.order_status_all),
    UNPAID(R.string.order_status_unpaid),
    UNSHIPPED(R.string.order_status_unshipped),
    UNRECEIVED(R.string.order_status_unreceived),
    AFTER_SALE(R.string.order_status_after_sale),
    UNEVALUATED(R.string.order_status_unevaluated),
    COMPLETED(R.string.order_status_completed_tab),
}

/** Maps the account page's saved tab index to the shared order filter. */
internal fun orderTab(tab: String?): OrderStatus =
    OrderStatus.entries.getOrNull(tab?.toIntOrNull() ?: 0) ?: OrderStatus.ALL

internal fun OrderStatus.wireStatuses(): List<Int>? = when (this) {
    OrderStatus.ALL -> null
    OrderStatus.UNPAID -> listOf(0)
    OrderStatus.UNSHIPPED -> listOf(1)
    OrderStatus.UNRECEIVED -> listOf(2)
    OrderStatus.AFTER_SALE -> listOf(5, 6)
    OrderStatus.UNEVALUATED -> listOf(3)
    OrderStatus.COMPLETED -> listOf(4)
}
