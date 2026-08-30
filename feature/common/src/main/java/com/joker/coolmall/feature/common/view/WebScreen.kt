@file:Suppress("FunctionName", "ktlint:standard:function-naming")

package com.joker.coolmall.feature.common.view

import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joker.coolmall.core.designsystem.component.FullScreenBox
import com.joker.coolmall.core.designsystem.theme.AppTheme
import com.joker.coolmall.core.designsystem.theme.CommonIcon
import com.joker.coolmall.core.ui.component.scaffold.AppScaffold
import com.joker.coolmall.feature.common.R
import com.joker.coolmall.feature.common.model.WebViewData
import com.joker.coolmall.feature.common.util.SecureWebViewClient
import com.joker.coolmall.feature.common.util.WebUrlDestination
import com.joker.coolmall.feature.common.util.WebUrlPolicy
import com.joker.coolmall.feature.common.util.applySecureDefaults
import com.joker.coolmall.feature.common.util.openExternalUrl
import com.joker.coolmall.feature.common.util.releaseFromComposition
import com.joker.coolmall.feature.common.viewmodel.WebViewModel
import com.joker.coolmall.navigation.common.CommonRoutes
import com.joker.coolmall.navigation.navigateBack

/**
 * 网页路由
 *
 * @param navKey 路由参数
 * @param viewModel 网页 ViewModel
 * @author Joker.X
 */
@Composable
internal fun WebRoute(
    navKey: CommonRoutes.Web,
    viewModel: WebViewModel = hiltViewModel<WebViewModel, WebViewModel.Factory>(
        creationCallback = { factory ->
            factory.create(navKey)
        },
    ),
) {
    // 收集WebView数据
    val webViewData by viewModel.webViewData.collectAsStateWithLifecycle()
    // 收集页面标题
    val pageTitle by viewModel.pageTitle.collectAsStateWithLifecycle()
    // 收集当前加载进度
    val currentProgress by viewModel.currentProgress.collectAsStateWithLifecycle()
    // 收集页面刷新状态
    val shouldRefresh by viewModel.shouldRefresh.collectAsStateWithLifecycle()
    // 收集下拉菜单显示状态
    val showDropdownMenu by viewModel.showDropdownMenu.collectAsStateWithLifecycle()

    WebScreen(
        webViewData = webViewData,
        pageTitle = pageTitle,
        currentProgress = currentProgress,
        shouldRefresh = shouldRefresh,
        showDropdownMenu = showDropdownMenu,
        onTitleChange = viewModel::updatePageTitle,
        onProgressChange = viewModel::updateProgress,
        onRefreshClick = viewModel::refreshPage,
        onResetRefreshState = viewModel::resetRefreshState,
        onShowDropdownMenu = viewModel::showDropdownMenu,
        onDismissDropdownMenu = viewModel::dismissDropdownMenu,
    )
}

/**
 * 网页界面
 *
 * @param webViewData WebView 数据
 * @param pageTitle 页面标题
 * @param currentProgress 当前加载进度
 * @param shouldRefresh 是否应该刷新页面
 * @param showDropdownMenu 是否显示下拉菜单
 * @param onTitleChange 标题变化回调
 * @param onProgressChange 进度变化回调
 * @param onRefreshClick 刷新按钮回调
 * @param onResetRefreshState 重置刷新状态回调
 * @param onShowDropdownMenu 显示下拉菜单回调
 * @param onDismissDropdownMenu 隐藏下拉菜单回调
 * @author Joker.X
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebScreen(
    webViewData: WebViewData = WebViewData(),
    pageTitle: String = "",
    currentProgress: Int = 0,
    shouldRefresh: Boolean = false,
    showDropdownMenu: Boolean = false,
    onTitleChange: (String) -> Unit = {},
    onProgressChange: (Int) -> Unit = {},
    onRefreshClick: () -> Unit = {},
    onResetRefreshState: () -> Unit = {},
    onShowDropdownMenu: () -> Unit = {},
    onDismissDropdownMenu: () -> Unit = {},
) {
    val context = LocalContext.current
    val openInBrowser = {
        openExternalUrl(context = context, url = webViewData.url)
        onDismissDropdownMenu()
    }

    AppScaffold(
        titleText = pageTitle.ifBlank { stringResource(id = R.string.web_title) },
        onBackClick = { navigateBack() },
        topBarActions = {
            WebScreenTopBarActions(
                showDropdownMenu = showDropdownMenu,
                onShowDropdownMenu = onShowDropdownMenu,
                onDismissDropdownMenu = onDismissDropdownMenu,
                onRefreshClick = onRefreshClick,
                onOpenInBrowser = openInBrowser,
            )
        },
    ) {
        WebViewContent(
            url = webViewData.url,
            currentProgress = currentProgress,
            shouldRefresh = shouldRefresh,
            onTitleChange = onTitleChange,
            onProgressChange = onProgressChange,
            onResetRefreshState = onResetRefreshState,
            onOpenExternal = openInBrowser,
        )
    }
}

/**
 * WebScreen 顶部栏操作按钮组件
 *
 * @param showDropdownMenu 是否显示下拉菜单
 * @param onShowDropdownMenu 显示下拉菜单回调
 * @param onDismissDropdownMenu 隐藏下拉菜单回调
 * @param onRefreshClick 刷新按钮回调
 * @param onOpenInBrowser 用浏览器打开回调
 * @author Joker.X
 */
@Composable
private fun WebScreenTopBarActions(
    showDropdownMenu: Boolean,
    onShowDropdownMenu: () -> Unit,
    onDismissDropdownMenu: () -> Unit,
    onRefreshClick: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    // 溢出菜单按钮
    IconButton(onClick = onShowDropdownMenu) {
        CommonIcon(
            resId = R.drawable.ic_more_vertical,
            contentDescription = stringResource(id = R.string.web_more_options),
        )
    }

    // 下拉菜单
    DropdownMenu(
        expanded = showDropdownMenu,
        onDismissRequest = onDismissDropdownMenu,
    ) {
        // 刷新选项
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.web_menu_refresh)) },
            onClick = onRefreshClick,
        )

        // 用浏览器打开选项
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.web_menu_open_browser)) },
            onClick = onOpenInBrowser,
        )
    }
}

/**
 * WebView 内容组件
 *
 * @param url 要加载的网页URL
 * @param currentProgress 当前加载进度(0-100)
 * @param shouldRefresh 是否应该刷新页面
 * @param onTitleChange 标题变化回调
 * @param onProgressChange 进度变化回调
 * @param onResetRefreshState 重置刷新状态回调
 * @param onOpenExternal 使用系统应用打开回调
 * @author Joker.X
 */
@Composable
private fun WebViewContent(
    url: String,
    currentProgress: Int,
    shouldRefresh: Boolean,
    onTitleChange: (String) -> Unit,
    onProgressChange: (Int) -> Unit,
    onResetRefreshState: () -> Unit,
    onOpenExternal: () -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val destination = remember(url) { WebUrlPolicy.classify(url) }

    // 处理刷新逻辑
    LaunchedEffect(shouldRefresh) {
        if (shouldRefresh) {
            webView?.reload()
            onResetRefreshState()
        }
    }

    when (destination) {
        is WebUrlDestination.InApp -> FullScreenBox {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webView = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.apply {
                            applySecureDefaults(
                                javaScriptAllowed = destination.javaScriptEnabled,
                            )
                            loadsImagesAutomatically = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                        }

                        webViewClient = SecureWebViewClient(context = context)

                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                super.onReceivedTitle(view, title)
                                title?.let { onTitleChange(it) }
                            }

                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                onProgressChange(newProgress)
                            }
                        }

                        loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { releasedWebView ->
                    if (webView === releasedWebView) {
                        webView = null
                    }
                    releasedWebView.releaseFromComposition()
                },
            )

            if (currentProgress < 100) {
                LinearProgressIndicator(
                    progress = { currentProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1.dp),
                )
            }
        }

        WebUrlDestination.External -> WebUrlUnavailable(
            message = stringResource(id = R.string.web_external_only),
            onOpenExternal = onOpenExternal,
        )

        WebUrlDestination.Blocked -> WebUrlUnavailable(
            message = stringResource(id = R.string.web_url_blocked),
        )
    }
}

@Composable
private fun WebUrlUnavailable(message: String, onOpenExternal: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message)
        if (onOpenExternal != null) {
            Button(
                onClick = onOpenExternal,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(text = stringResource(id = R.string.web_menu_open_browser))
            }
        }
    }
}

/**
 * 网页界面浅色主题预览
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
internal fun WebScreenPreview() {
    AppTheme {
        WebScreen(
            webViewData = WebViewData(
                url = "https://github.com/Joker-x-dev/CoolMallKotlin",
                title = "示例网页",
            ),
            pageTitle = "示例网页",
            currentProgress = 50,
        )
    }
}

/**
 * 网页界面深色主题预览
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
internal fun WebScreenPreviewDark() {
    AppTheme(darkTheme = true) {
        WebScreen(
            webViewData = WebViewData(
                url = "https://github.com/Joker-x-dev/CoolMallKotlin",
                title = "示例网页",
            ),
            pageTitle = "示例网页",
            currentProgress = 50,
        )
    }
}
