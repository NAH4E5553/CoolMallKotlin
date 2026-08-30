package com.joker.coolmall.feature.common.viewmodel

import com.joker.coolmall.core.common.base.viewmodel.BaseViewModel
import com.joker.coolmall.feature.common.model.WebViewData
import com.joker.coolmall.navigation.common.CommonRoutes
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 网页 ViewModel
 *
 * @param navKey 路由参数
 * @author Joker.X
 */
@HiltViewModel(assistedFactory = WebViewModel.Factory::class)
class WebViewModel @AssistedInject constructor(@Assisted private val navKey: CommonRoutes.Web) : BaseViewModel() {

    /**
     * WebView 数据
     */
    private val _webViewData = MutableStateFlow(WebViewData())
    val webViewData: StateFlow<WebViewData> = _webViewData.asStateFlow()

    /**
     * 从路由获取 WebView 路由参数
     */
    val webViewRoute = navKey

    /**
     * 页面标题
     */
    private val _pageTitle = MutableStateFlow("")
    val pageTitle: StateFlow<String> = _pageTitle.asStateFlow()

    /**
     * 加载进度
     */
    private val _currentProgress = MutableStateFlow(0)
    val currentProgress: StateFlow<Int> = _currentProgress.asStateFlow()

    /**
     * 刷新请求状态
     */
    private val _shouldRefresh = MutableStateFlow(false)
    val shouldRefresh: StateFlow<Boolean> = _shouldRefresh.asStateFlow()

    /**
     * 下拉菜单显示状态
     */
    private val _showDropdownMenu = MutableStateFlow(false)
    val showDropdownMenu: StateFlow<Boolean> = _showDropdownMenu.asStateFlow()

    init {
        // 从路由获取参数
        val url = webViewRoute.url
        val title = webViewRoute.title.orEmpty()

        if (url.isNotEmpty()) {
            _webViewData.value = WebViewData(url = url, title = title)
            _pageTitle.value = title
        }
    }

    /**
     * 更新页面标题
     *
     * @param title 新的页面标题
     * @author Joker.X
     */
    fun updatePageTitle(title: String) {
        _pageTitle.value = title
    }

    /**
     * 更新加载进度
     *
     * @param progress 加载进度(0-100)
     * @author Joker.X
     */
    fun updateProgress(progress: Int) {
        _currentProgress.value = progress
    }

    /**
     * 刷新页面
     *
     * @author Joker.X
     */
    fun refreshPage() {
        _shouldRefresh.value = true
        dismissDropdownMenu()
    }

    /**
     * 重置刷新状态
     *
     * @author Joker.X
     */
    fun resetRefreshState() {
        _shouldRefresh.value = false
    }

    /**
     * 显示下拉菜单
     *
     * @author Joker.X
     */
    fun showDropdownMenu() {
        _showDropdownMenu.value = true
    }

    /**
     * 隐藏下拉菜单
     *
     * @author Joker.X
     */
    fun dismissDropdownMenu() {
        _showDropdownMenu.value = false
    }

    /**
     * Assisted Factory
     *
     * @author Joker.X
     */
    @AssistedFactory
    interface Factory {
        /**
         * 创建 ViewModel 实例
         *
         * @param navKey 路由参数
         * @return ViewModel 实例
         * @author Joker.X
         */
        fun create(navKey: CommonRoutes.Web): WebViewModel
    }
}
