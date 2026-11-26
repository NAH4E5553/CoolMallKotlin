package com.joker.coolmall.feature.goods.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.joker.coolmall.core.flutter.FlutterPage
import com.joker.coolmall.feature.goods.viewmodel.GoodsDetailViewModel
import com.joker.coolmall.navigation.goods.GoodsRoutes
import com.joker.coolmall.navigation.navigateBack

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun FlutterGoodsDetailRoute(navKey: GoodsRoutes.Detail) {
    val model = hiltViewModel<GoodsDetailViewModel, GoodsDetailViewModel.Factory>(
        creationCallback = { it.create(navKey) },
    )
    var native by rememberSaveable(navKey.goodsId) { mutableStateOf(false) }
    if (native) {
        GoodsDetailRoute(navKey, model)
        return
    }
    FlutterPage(
        route = "/goods/detail",
        arguments = mapOf("goodsId" to navKey.goodsId),
        onClose = { navigateBack() },
        onFailure = { native = true },
        connect = { connection ->
            GoodsDetailChannel(connection, model) { native = true }
        },
    )
}
