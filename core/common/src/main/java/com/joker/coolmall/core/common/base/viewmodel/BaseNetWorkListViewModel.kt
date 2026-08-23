package com.joker.coolmall.core.common.base.viewmodel

import androidx.lifecycle.viewModelScope
import com.joker.coolmall.core.common.base.state.BaseNetWorkListUiState
import com.joker.coolmall.core.common.base.state.LoadMoreState
import com.joker.coolmall.core.model.response.NetworkPageData
import com.joker.coolmall.core.model.response.NetworkResponse
import com.joker.coolmall.navigation.NavigationResultKey
import com.joker.coolmall.navigation.RefreshResult
import com.joker.coolmall.navigation.resultEvents
import com.joker.coolmall.result.ResultHandler
import com.joker.coolmall.result.asResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 网络请求列表ViewModel基类
 *
 * 专门处理列表数据的加载、分页、刷新和加载更多功能
 * 封装了常见的列表操作逻辑，简化子类实现
 *
 * @param T 列表项数据类型
 * @author Joker.X
 */
abstract class BaseNetWorkListViewModel<T : Any> : BaseViewModel() {
    private enum class LoadType {
        Initial,
        Refresh,
        LoadMore,
    }

    /**
     * 刷新结果监听任务
     *
     * 用于保证只注册一次刷新结果监听，避免重复 collect 导致重复刷新和内存浪费。
     * 当该任务不为 null 时，表示当前 ViewModel 已经建立监听。
     */
    private var refreshObserveJob: Job? = null

    /** 当前分页请求，刷新或重试时用于取消旧请求。 */
    private var requestJob: Job? = null

    /** 最少加载时间对应的延迟状态任务。 */
    private var stateUpdateJob: Job? = null

    /** 请求序号，用于阻止旧请求提交延迟状态。 */
    private var requestSequence = 0L

    /**
     * 当前页码
     */
    protected var currentPage = 1

    /**
     * 每页数量
     */
    protected val pageSize = 10

    /**
     * 网络请求UI状态
     */
    protected val _uiState =
        MutableStateFlow<BaseNetWorkListUiState>(BaseNetWorkListUiState.Loading)
    val uiState: StateFlow<BaseNetWorkListUiState> = _uiState.asStateFlow()

    /**
     * 列表数据
     */
    protected val _listData = MutableStateFlow<List<T>>(emptyList())
    val listData: StateFlow<List<T>> = _listData.asStateFlow()

    /**
     * 加载更多状态
     */
    protected val _loadMoreState = MutableStateFlow<LoadMoreState>(LoadMoreState.PullToLoad)
    val loadMoreState: StateFlow<LoadMoreState> = _loadMoreState.asStateFlow()

    /**
     * 下拉刷新状态 (仅用于PullToRefresh组件)
     */
    protected val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * 是否启用最少加载时间（240毫秒）
     * 子类可重写此属性以启用最少加载时间功能
     */
    protected open val enableMinLoadingTime: Boolean = false

    /**
     * 请求开始时间，用于计算最少加载时间（仅首次加载）
     */
    /** 子类使用本次请求的页码快照构造请求，不能再次读取共享页码。 */
    protected abstract fun requestListData(
        page: Int,
        pageSize: Int,
    ): Flow<NetworkResponse<NetworkPageData<T>>>

    /**
     * 初始化函数，在子类init块中调用
     */
    protected fun initLoad() {
        startRequest(page = 1, loadType = LoadType.Initial, cancelCurrent = true)
    }

    /** 取消分页请求，供首页等组合接口刷新时停止加载更多。 */
    protected fun cancelListRequest() {
        requestSequence++
        requestJob?.cancel()
        requestJob = null
        stateUpdateJob?.cancel()
        stateUpdateJob = null
    }

    /** 重置组合页面维护的分页游标。 */
    protected fun resetPaging() {
        currentPage = 1
    }

    /** 发起一次携带不可变页码快照的分页请求。 */
    private fun startRequest(
        page: Int,
        loadType: LoadType,
        cancelCurrent: Boolean,
    ) {
        if (cancelCurrent) cancelListRequest()

        val requestId = ++requestSequence
        val requestStartTime = System.currentTimeMillis()

        when (loadType) {
            LoadType.Initial -> {
                _uiState.value = BaseNetWorkListUiState.Loading
                _loadMoreState.value = LoadMoreState.Loading
            }

            LoadType.Refresh -> _isRefreshing.value = true
            LoadType.LoadMore -> _loadMoreState.value = LoadMoreState.Loading
        }

        requestJob = ResultHandler.handleResult(
            showToast = false,
            scope = viewModelScope,
            flow = requestListData(page = page, pageSize = pageSize).asResult(),
            onSuccess = { response ->
                if (requestId == requestSequence) {
                    handleSuccess(
                        data = response.data,
                        requestPage = page,
                        loadType = loadType,
                        requestId = requestId,
                        requestStartTime = requestStartTime,
                    )
                }
            },
            onError = { message, exception ->
                if (requestId == requestSequence) {
                    handleError(message, exception, loadType)
                }
            }
        )
    }

    /**
     * 处理成功响应
     */
    private fun handleSuccess(
        data: NetworkPageData<T>?,
        requestPage: Int,
        loadType: LoadType,
        requestId: Long,
        requestStartTime: Long,
    ) {
        val newList = data?.list ?: emptyList()
        val pagination = data?.pagination

        // 计算是否还有下一页数据
        val hasNextPage = if (pagination != null) {
            val total = pagination.total ?: 0
            val size = pagination.size ?: pageSize
            val currentPageNum = pagination.page ?: requestPage

            // 当前页的数据量 * 当前页码 < 总数据量，说明还有下一页
            size * currentPageNum < total
        } else {
            false
        }

        currentPage = requestPage

        when (loadType) {
            LoadType.Initial, LoadType.Refresh -> {
                // 刷新或首次加载 - 重置列表
                _listData.value = newList
                _isRefreshing.value = false

                // 判断是否需要最少加载时间延迟
                if (enableMinLoadingTime) {
                    val elapsedTime = System.currentTimeMillis() - requestStartTime
                    val minLoadingTime = 240L

                    if (elapsedTime < minLoadingTime) {
                        // 延迟设置成功状态
                        stateUpdateJob = viewModelScope.launch {
                            delay(minLoadingTime - elapsedTime)
                            if (requestId == requestSequence) {
                                setFirstLoadSuccessState(newList, hasNextPage)
                            }
                        }
                    } else {
                        setFirstLoadSuccessState(newList, hasNextPage)
                    }
                } else {
                    setFirstLoadSuccessState(newList, hasNextPage)
                }
            }

            LoadType.LoadMore -> {
                _listData.value += newList
                _loadMoreState.value =
                    if (hasNextPage) LoadMoreState.PullToLoad else LoadMoreState.NoMore
            }
        }
    }

    /**
     * 处理错误响应
     */
    private fun handleError(
        message: String?,
        exception: Throwable?,
        loadType: LoadType,
    ) {
        _isRefreshing.value = false

        when (loadType) {
            LoadType.Initial, LoadType.Refresh -> {
                if (_listData.value.isEmpty()) {
                    _uiState.value = BaseNetWorkListUiState.Error
                }
                _loadMoreState.value = LoadMoreState.PullToLoad
            }

            LoadType.LoadMore -> _loadMoreState.value = LoadMoreState.Error
        }
    }

    /**
     * 重试请求
     */
    fun retryRequest() {
        startRequest(page = 1, loadType = LoadType.Initial, cancelCurrent = true)
    }

    /**
     * 触发下拉刷新
     */
    open fun onRefresh() {
        startRequest(page = 1, loadType = LoadType.Refresh, cancelCurrent = true)
    }

    /**
     * 加载更多数据
     */
    open fun onLoadMore() {
        // 只有在可加载更多和加载失败状态下才能触发加载
        if (requestJob?.isActive == true ||
            _loadMoreState.value == LoadMoreState.Loading ||
            _loadMoreState.value == LoadMoreState.NoMore ||
            _loadMoreState.value == LoadMoreState.Success
        ) {
            return
        }

        startRequest(
            page = currentPage + 1,
            loadType = LoadType.LoadMore,
            cancelCurrent = false,
        )
    }

    /**
     * 判断是否应该触发加载更多
     * 显示的最后一项索引接近列表末尾（倒数第3个）
     *
     * @param lastIndex 当前可见的最后一项索引
     * @param totalCount 列表总项数
     * @return 是否应该触发加载更多
     */
    fun shouldTriggerLoadMore(lastIndex: Int, totalCount: Int): Boolean {
        return lastIndex >= totalCount - 3 &&
                loadMoreState.value != LoadMoreState.Loading &&
                loadMoreState.value != LoadMoreState.NoMore &&
                listData.value.isNotEmpty()
    }

    /**
     * 设置首次加载成功状态
     */
    private fun setFirstLoadSuccessState(newList: List<T>, hasNextPage: Boolean) {
        // 更新加载状态
        if (newList.isEmpty()) {
            _uiState.value = BaseNetWorkListUiState.Empty
            _loadMoreState.value = LoadMoreState.NoMore
        } else {
            _uiState.value = BaseNetWorkListUiState.Success
            _loadMoreState.value =
                if (hasNextPage) LoadMoreState.PullToLoad else LoadMoreState.NoMore
        }
    }

    /**
     * 注册页面刷新信号监听（基于 NavigationResultKey）。
     *
     * 推荐在 ViewModel 的 `init` 中调用一次，不依赖 View 层 `LaunchedEffect`。
     * 内部已做去重：重复调用不会重复注册。
     *
     * @param key 当前业务域的类型安全刷新 Key
     */
    fun observeRefreshState(
        key: NavigationResultKey<RefreshResult>,
    ) {
        if (refreshObserveJob != null) return
        refreshObserveJob = viewModelScope.launch {
            resultEvents(key).collect { refreshResult ->
                if (refreshResult.refresh == true) {
                    onRefresh()
                }
            }
        }
    }
}
