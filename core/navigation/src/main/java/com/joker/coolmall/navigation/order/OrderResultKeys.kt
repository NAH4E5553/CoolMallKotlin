package com.joker.coolmall.navigation.order

import com.joker.coolmall.navigation.NavigationResultKey
import com.joker.coolmall.navigation.RefreshResult

/** 订单内容或状态发生变化后的刷新结果。 */
object OrderChangedResultKey : NavigationResultKey<RefreshResult>

/** 支付成功后通知订单详情刷新的结果。 */
object PaymentCompletedResultKey : NavigationResultKey<RefreshResult>
