package com.joker.coolmall.feature.main.view

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.joker.coolmall.core.designsystem.theme.AppTheme
import com.joker.coolmall.feature.main.component.BottomNavigationBar
import com.joker.coolmall.feature.main.model.TopLevelDestination
import com.joker.coolmall.navigation.main.MainRoutes
import kotlinx.coroutines.launch

/**
 * 主界面路由入口
 *
 * @param sharedTransitionScope 共享转场作用域
 * @param animatedContentScope 动画内容作用域
 * @author Joker.X
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun MainRoute(
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null,
) {
    MainScreen(
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
    )
}

/**
 * 主界面
 * 包含底部导航栏和四个主要页面（首页、分类、购物车、我的）
 *
 * @param sharedTransitionScope 共享转场作用域
 * @param animatedContentScope 动画内容作用域
 * @author Joker.X
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun MainScreen(
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null,
) {
    // 协程作用域
    val scope = rememberCoroutineScope()
    val destinations = TopLevelDestination.entries

    // 创建分页器状态
    val pageState = rememberPagerState(
        initialPage = 0
    ) {
        destinations.size
    }

    Scaffold(
        // 排除顶部导航栏边距
        contentWindowInsets = ScaffoldDefaults
            .contentWindowInsets
            .exclude(WindowInsets.statusBars),
        bottomBar = {
            BottomNavigationBar(
                destinations = destinations,
                onNavigateToDestination = { index ->
                    if (index in destinations.indices && index != pageState.currentPage) {
                        scope.launch {
                            pageState.scrollToPage(index)
                        }
                    }
                },
                currentPageIndex = pageState.currentPage,
                modifier = Modifier
            )
        }
    ) { paddingValues ->
        MainScreenContentView(
            pageState = pageState,
            paddingValues = paddingValues,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = animatedContentScope
        )
    }
}

/**
 * 主界面内容视图
 *
 * @param pageState 分页器状态
 * @param paddingValues 内边距
 * @param sharedTransitionScope 共享转场作用域
 * @param animatedContentScope 动画内容作用域
 * @author Joker.X
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MainScreenContentView(
    pageState: PagerState,
    paddingValues: PaddingValues,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null,
) {
    HorizontalPager(
        state = pageState,
        modifier = Modifier
            .padding(paddingValues)
    ) { page: Int ->
        when (TopLevelDestination.entries[page]) {
            TopLevelDestination.HOME -> HomeRoute(
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = animatedContentScope
            )

            TopLevelDestination.CATEGORY -> CategoryRoute()
            TopLevelDestination.CART -> CartRoute(navKey = MainRoutes.Cart())
            TopLevelDestination.ME -> MeRoute(
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = animatedContentScope
            )
        }
    }
}

/**
 * 主界面浅色主题预览
 *
 * @author Joker.X
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    AppTheme {
        MainScreen()
    }
}
