package com.joker.coolmall.feature.order.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joker.coolmall.core.flutter.FlutterPage
import com.joker.coolmall.feature.order.model.flutterFilter
import com.joker.coolmall.feature.order.model.orderTab
import com.joker.coolmall.feature.order.viewmodel.FlutterOrderViewModel
import com.joker.coolmall.navigation.navigateBack
import com.joker.coolmall.navigation.order.OrderRoutes

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun FlutterOrderListRoute(navKey: OrderRoutes.List, model: FlutterOrderViewModel = hiltViewModel()) {
    val userId by model.userId.collectAsStateWithLifecycle()
    val isLoggedIn by model.isLoggedIn.collectAsStateWithLifecycle()
    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) navigateBack()
    }
    if (!isLoggedIn || userId <= 0) return

    var native by rememberSaveable(navKey) { mutableStateOf(false) }
    if (native) {
        OrderListRoute(navKey)
        return
    }
    key(userId) {
        FlutterPage(
            route = "/orders/list",
            arguments = mapOf("filter" to orderTab(navKey.tab).flutterFilter),
            onClose = { navigateBack() },
            onFailure = { native = true },
            connect = { FlutterOrderChannel(it, userId, model) },
        )
    }
}
