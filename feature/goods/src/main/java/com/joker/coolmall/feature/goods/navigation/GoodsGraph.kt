package com.joker.coolmall.feature.goods.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.joker.coolmall.feature.goods.view.FlutterGoodsDetailRoute
import com.joker.coolmall.feature.goods.view.GoodsCategoryRoute
import com.joker.coolmall.feature.goods.view.GoodsCommentRoute
import com.joker.coolmall.feature.goods.view.GoodsSearchRoute
import com.joker.coolmall.navigation.goods.GoodsRoutes

/**
 * 商品模块导航图
 *
 * @author Joker.X
 */
fun EntryProviderScope<NavKey>.goodsGraph() {
    entry<GoodsRoutes.Detail> { key ->
        FlutterGoodsDetailRoute(navKey = key)
    }
    entry<GoodsRoutes.Search> {
        GoodsSearchRoute()
    }
    entry<GoodsRoutes.Comment> { key ->
        GoodsCommentRoute(navKey = key)
    }
    entry<GoodsRoutes.Category> { key ->
        GoodsCategoryRoute(navKey = key)
    }
}
