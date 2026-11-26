package com.joker.coolmall.feature.order.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.joker.coolmall.feature.order.view.FlutterOrderListRoute
import com.joker.coolmall.feature.order.view.OrderCommentRoute
import com.joker.coolmall.feature.order.view.OrderConfirmRoute
import com.joker.coolmall.feature.order.view.OrderDetailRoute
import com.joker.coolmall.feature.order.view.OrderLogisticsRoute
import com.joker.coolmall.feature.order.view.OrderPayRoute
import com.joker.coolmall.feature.order.view.OrderRefundRoute
import com.joker.coolmall.navigation.order.OrderRoutes

/**
 * 订单模块导航图
 *
 * @author Joker.X
 */
fun EntryProviderScope<NavKey>.orderGraph() {
    entry<OrderRoutes.List> { key ->
        FlutterOrderListRoute(navKey = key)
    }
    entry<OrderRoutes.Confirm> {
        OrderConfirmRoute()
    }
    entry<OrderRoutes.Detail> { key ->
        OrderDetailRoute(navKey = key)
    }
    entry<OrderRoutes.Pay> { key ->
        OrderPayRoute(navKey = key)
    }
    entry<OrderRoutes.Logistics> { key ->
        OrderLogisticsRoute(navKey = key)
    }
    entry<OrderRoutes.Refund> { key ->
        OrderRefundRoute(navKey = key)
    }
    entry<OrderRoutes.Comment> { key ->
        OrderCommentRoute(navKey = key)
    }
}
