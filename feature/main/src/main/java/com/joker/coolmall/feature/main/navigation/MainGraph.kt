package com.joker.coolmall.feature.main.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.joker.coolmall.feature.main.view.CartRoute
import com.joker.coolmall.feature.main.view.MainRoute
import com.joker.coolmall.navigation.main.MainRoutes

/**
 * 主模块导航图
 *
 * @param sharedTransitionScope 共享转换作用域
 * @param onHomeContent 由应用装配层提供的首页组合内容
 * @author Joker.X
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun EntryProviderScope<NavKey>.mainGraph(
    sharedTransitionScope: SharedTransitionScope,
    onHomeContent: @Composable () -> Unit = {},
) {
    entry<MainRoutes.Main> {
        MainRoute(
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = LocalNavAnimatedContentScope.current,
        )
        onHomeContent()
    }
    entry<MainRoutes.Cart> { key ->
        CartRoute(navKey = key)
    }
}
