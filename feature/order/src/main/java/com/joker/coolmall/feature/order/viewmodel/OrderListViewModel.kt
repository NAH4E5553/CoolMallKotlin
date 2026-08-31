package com.joker.coolmall.feature.order.viewmodel

import androidx.lifecycle.viewModelScope
import com.joker.coolmall.core.common.base.state.BaseNetWorkListUiState
import com.joker.coolmall.core.common.base.state.BaseNetWorkUiState
import com.joker.coolmall.core.common.base.state.LoadMoreState
import com.joker.coolmall.core.common.base.viewmodel.BaseViewModel
import com.joker.coolmall.core.data.repository.CommonRepository
import com.joker.coolmall.core.data.repository.OrderRepository
import com.joker.coolmall.core.model.entity.Cart
import com.joker.coolmall.core.model.entity.CartGoodsSpec
import com.joker.coolmall.core.model.entity.DictItem
import com.joker.coolmall.core.model.entity.Order
import com.joker.coolmall.core.model.entity.OrderGoods
import com.joker.coolmall.core.model.request.CancelOrderRequest
import com.joker.coolmall.core.model.request.DictDataRequest
import com.joker.coolmall.core.model.request.OrderPageRequest
import com.joker.coolmall.core.model.response.NetworkPageData
import com.joker.coolmall.feature.order.model.OrderStatus
import com.joker.coolmall.feature.order.model.uncommentedGoods
import com.joker.coolmall.navigation.RefreshResult
import com.joker.coolmall.navigation.goods.GoodsNavigator
import com.joker.coolmall.navigation.navigate
import com.joker.coolmall.navigation.order.OrderChangedResultKey
import com.joker.coolmall.navigation.order.OrderRoutes
import com.joker.coolmall.navigation.resultEvents
import com.joker.coolmall.result.ResultHandler
import com.joker.coolmall.result.asResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 订单列表视图模型
 *
 * @param navKey 路由参数
 * @param orderRepository 订单仓库
 * @param commonRepository 通用仓库
 * @author Joker.X
 */
@HiltViewModel(assistedFactory = OrderListViewModel.Factory::class)
class OrderListViewModel @AssistedInject constructor(
    @Assisted navKey: OrderRoutes.List,
    private val orderRepository: OrderRepository,
    private val commonRepository: CommonRepository,
) : BaseViewModel() {
    private enum class LoadType {
        Initial,
        Refresh,
        SilentRefresh,
        LoadMore,
    }

    /**
     * 刷新结果监听任务
     */
    private var refreshObserveJob: Job? = null

    /** 每个标签页当前执行中的分页请求。 */
    private val requestJobs = MutableList<Job?>(OrderStatus.entries.size) { null }

    /** 每个标签页加载成功提示对应的延迟提交任务。 */
    private val stateUpdateJobs = MutableList<Job?>(OrderStatus.entries.size) { null }

    /** 每个标签页相互隔离的已提交页码和请求序号。 */
    private val pagingRequestTracker = OrderPagingRequestTracker(OrderStatus.entries.size)

    /**
     * 当前选中的标签索引
     */
    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()

    /**
     * 是否正在进行标签切换动画
     */
    private val _isAnimatingTabChange = MutableStateFlow(false)
    val isAnimatingTabChange: StateFlow<Boolean> = _isAnimatingTabChange.asStateFlow()

    /**
     * 标记每个标签页是否已加载过数据
     */
    private val tabDataLoaded = MutableList(OrderStatus.entries.size) { false }

    /**
     * 每页大小
     */
    private val pageSize = 10

    /**
     * 每个标签页的网络请求UI状态
     */
    private val _uiStates = OrderStatus.entries.map {
        MutableStateFlow<BaseNetWorkListUiState>(BaseNetWorkListUiState.Loading)
    }
    val uiStates: List<StateFlow<BaseNetWorkListUiState>> = _uiStates.map { it.asStateFlow() }

    /**
     * 每个标签页的列表数据
     */
    private val _listDataMap = OrderStatus.entries.map {
        MutableStateFlow<List<Order>>(emptyList())
    }
    val listDataMap: List<StateFlow<List<Order>>> = _listDataMap.map { it.asStateFlow() }

    /**
     * 每个标签页的加载更多状态
     */
    private val _loadMoreStates = OrderStatus.entries.map {
        MutableStateFlow<LoadMoreState>(LoadMoreState.PullToLoad)
    }
    val loadMoreStates: List<StateFlow<LoadMoreState>> = _loadMoreStates.map { it.asStateFlow() }

    /**
     * 每个标签页的下拉刷新状态
     */
    private val _refreshingStates = OrderStatus.entries.map {
        MutableStateFlow(false)
    }

    /**
     * 每个标签页的下拉刷新状态
     */
    val refreshingStates: List<StateFlow<Boolean>> = _refreshingStates.map { it.asStateFlow() }

    /**
     * 取消原因选择弹窗的显示状态
     */
    private val _cancelModalVisible = MutableStateFlow(false)
    val cancelModalVisible: StateFlow<Boolean> = _cancelModalVisible.asStateFlow()

    /**
     * 取消原因弹出层 ui 状态
     */
    private val _cancelReasonsModalUiState =
        MutableStateFlow<BaseNetWorkUiState<List<DictItem>>>(BaseNetWorkUiState.Loading)
    val cancelReasonsModalUiState: StateFlow<BaseNetWorkUiState<List<DictItem>>> =
        _cancelReasonsModalUiState.asStateFlow()

    /**
     * 选中的取消原因
     */
    private val _selectedCancelReason = MutableStateFlow<DictItem?>(null)
    val selectedCancelReason: StateFlow<DictItem?> = _selectedCancelReason.asStateFlow()

    /**
     * 当前要取消的订单ID
     */
    private var currentCancelOrderId: Long = 0L

    /**
     * 确认收货弹窗的显示状态
     */
    private val _showConfirmDialog = MutableStateFlow(false)
    val showConfirmDialog: StateFlow<Boolean> = _showConfirmDialog.asStateFlow()

    /**
     * 当前要确认收货的订单ID
     */
    private var currentConfirmOrderId: Long = 0L

    /**
     * 再次购买弹窗的显示状态
     */
    private val _rebuyModalVisible = MutableStateFlow(false)
    val rebuyModalVisible: StateFlow<Boolean> = _rebuyModalVisible.asStateFlow()

    /**
     * 商品评论弹窗的显示状态
     */
    private val _commentModalVisible = MutableStateFlow(false)
    val commentModalVisible: StateFlow<Boolean> = _commentModalVisible.asStateFlow()

    /**
     * 再次购买弹窗的购物车列表
     */
    private val _rebuyCartList = MutableStateFlow<List<Cart>>(emptyList())
    val rebuyCartList = _rebuyCartList.asStateFlow()

    /**
     * 再次购买弹窗的当前订单
     */
    private val _rebuyCurrentOrder = MutableStateFlow<Order?>(null)
    val rebuyCurrentOrder: StateFlow<Order?> = _rebuyCurrentOrder.asStateFlow()

    /**
     * 商品评论弹窗的购物车列表
     */
    private val _commentCartList = MutableStateFlow<List<Cart>>(emptyList())
    val commentCartList = _commentCartList.asStateFlow()

    /**
     * 商品评论弹窗的当前订单
     */
    private val _commentCurrentOrder = MutableStateFlow<Order?>(null)
    val commentCurrentOrder: StateFlow<Order?> = _commentCurrentOrder.asStateFlow()

    init {
        // 从路由获取 tab 参数
        navKey.tab?.toIntOrNull()?.let { tabIndex ->
            if (tabIndex in OrderStatus.entries.indices) {
                _selectedTabIndex.value = tabIndex
            }
        }

        // 加载当前选中标签页的数据
        observeRefreshState()
        loadTabDataIfNeeded(_selectedTabIndex.value)
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
        fun create(navKey: OrderRoutes.List): OrderListViewModel
    }

    /**
     * 观察来自其他页面的刷新状态
     * 当从其他页面返回时，如果刷新标志为true，则刷新订单列表
     *
     * @author Joker.X
     */
    fun observeRefreshState() {
        if (refreshObserveJob != null) return
        refreshObserveJob = viewModelScope.launch {
            resultEvents(OrderChangedResultKey).collect { refreshResult: RefreshResult ->
                if (refreshResult.refresh == true) {
                    // 刷新全部标签页
                    refreshSpecificTabs(OrderStatus.entries.indices.toList())
                }
            }
        }
    }

    /**
     * 重置指定标签页的加载状态
     */
    private fun resetTabLoadState(tabIndex: Int) {
        if (tabIndex in tabDataLoaded.indices) {
            cancelTabRequest(tabIndex)
            tabDataLoaded[tabIndex] = false
        }
    }

    /**
     * 刷新指定的标签页
     * @param tabIndices 需要刷新的标签页索引列表
     */
    private fun refreshSpecificTabs(tabIndices: List<Int>) {
        // 重置指定标签页的加载状态
        tabIndices.forEach { tabIndex ->
            if (tabIndex in tabDataLoaded.indices) {
                resetTabLoadState(tabIndex)
            }
        }

        // 如果当前显示的标签页在刷新列表中，则立即刷新
        val currentTab = _selectedTabIndex.value
        if (currentTab in tabIndices) {
            tabDataLoaded[currentTab] = true
            startListRequest(
                tabIndex = currentTab,
                requestPage = 1,
                loadType = LoadType.SilentRefresh,
                cancelCurrent = false,
            )
        }
    }

    /**
     * 如果标签页数据尚未加载，则加载数据
     */
    private fun loadTabDataIfNeeded(tabIndex: Int) {
        if (tabIndex !in tabDataLoaded.indices) return

        if (!tabDataLoaded[tabIndex]) {
            // 标记该标签页已尝试加载数据
            tabDataLoaded[tabIndex] = true
            // 加载该标签页的数据
            startListRequest(
                tabIndex = tabIndex,
                requestPage = 1,
                loadType = LoadType.Initial,
                cancelCurrent = true,
            )
        }
    }

    /**
     * 取消指定标签页的分页请求和延迟状态任务。
     */
    private fun cancelTabRequest(tabIndex: Int) {
        pagingRequestTracker.invalidate(tabIndex)
        requestJobs[tabIndex]?.cancel()
        requestJobs[tabIndex] = null
        stateUpdateJobs[tabIndex]?.cancel()
        stateUpdateJobs[tabIndex] = null
    }

    /**
     * 使用不可变页码快照加载指定标签页的数据。
     */
    private fun startListRequest(tabIndex: Int, requestPage: Int, loadType: LoadType, cancelCurrent: Boolean) {
        if (cancelCurrent) {
            cancelTabRequest(tabIndex)
        } else if (requestJobs[tabIndex]?.isActive == true) {
            return
        }

        val requestToken = pagingRequestTracker.startRequest(tabIndex, requestPage)
        val previousLoadMoreState = _loadMoreStates[tabIndex].value

        when (loadType) {
            LoadType.Initial -> {
                _uiStates[tabIndex].value = BaseNetWorkListUiState.Loading
                _loadMoreStates[tabIndex].value = LoadMoreState.Loading
            }

            LoadType.Refresh -> _refreshingStates[tabIndex].value = true

            LoadType.SilentRefresh -> Unit

            LoadType.LoadMore -> _loadMoreStates[tabIndex].value = LoadMoreState.Loading
        }

        requestJobs[tabIndex] = ResultHandler.handleResult(
            showToast = false,
            scope = viewModelScope,
            flow = orderRepository.getOrderPage(
                OrderPageRequest(
                    page = requestPage,
                    size = pageSize,
                    status = getStatusFilter(tabIndex),
                ),
            ).asResult(),
            onSuccess = { response ->
                if (pagingRequestTracker.isCurrent(requestToken)) {
                    handleSuccess(
                        requestToken = requestToken,
                        loadType = loadType,
                        data = response.data,
                    )
                }
            },
            onError = { _, _ ->
                if (pagingRequestTracker.isCurrent(requestToken)) {
                    handleError(
                        tabIndex = tabIndex,
                        loadType = loadType,
                        previousLoadMoreState = previousLoadMoreState,
                    )
                }
            },
        )
    }

    /**
     * 处理成功响应
     */
    private fun handleSuccess(requestToken: OrderPageRequestToken, loadType: LoadType, data: NetworkPageData<Order>?) {
        val tabIndex = requestToken.tabIndex
        val requestPage = requestToken.page
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

        when (loadType) {
            LoadType.Initial, LoadType.Refresh, LoadType.SilentRefresh -> {
                // 刷新或首次加载 - 重置列表
                if (!pagingRequestTracker.commit(requestToken)) return
                _listDataMap[tabIndex].value = newList
                _refreshingStates[tabIndex].value = false

                // 更新加载状态
                if (newList.isEmpty()) {
                    _uiStates[tabIndex].value = BaseNetWorkListUiState.Empty
                    _loadMoreStates[tabIndex].value = LoadMoreState.NoMore
                } else {
                    _uiStates[tabIndex].value = BaseNetWorkListUiState.Success
                    _loadMoreStates[tabIndex].value =
                        if (hasNextPage) LoadMoreState.PullToLoad else LoadMoreState.NoMore
                }
            }

            LoadType.LoadMore -> {
                // 加载更多 - 先显示加载成功，延迟更新数据
                stateUpdateJobs[tabIndex] = viewModelScope.launch {
                    _loadMoreStates[tabIndex].value = LoadMoreState.Success
                    delay(400)
                    if (pagingRequestTracker.commit(requestToken)) {
                        _listDataMap[tabIndex].value = _listDataMap[tabIndex].value + newList
                        _loadMoreStates[tabIndex].value =
                            if (hasNextPage) LoadMoreState.PullToLoad else LoadMoreState.NoMore
                    }
                }
            }
        }
    }

    /**
     * 处理错误响应
     */
    private fun handleError(tabIndex: Int, loadType: LoadType, previousLoadMoreState: LoadMoreState) {
        _refreshingStates[tabIndex].value = false

        when (loadType) {
            LoadType.Initial, LoadType.Refresh, LoadType.SilentRefresh -> {
                // 首次加载或刷新失败
                if (_listDataMap[tabIndex].value.isEmpty()) {
                    _uiStates[tabIndex].value = BaseNetWorkListUiState.Error
                } else {
                    _uiStates[tabIndex].value = BaseNetWorkListUiState.Success
                }
                _loadMoreStates[tabIndex].value = when (previousLoadMoreState) {
                    LoadMoreState.Loading, LoadMoreState.Success -> LoadMoreState.PullToLoad
                    else -> previousLoadMoreState
                }
            }

            LoadType.LoadMore -> _loadMoreStates[tabIndex].value = LoadMoreState.Error
        }
    }

    /**
     * 重试加载
     *
     * @author Joker.X
     */
    fun retryRequest(tabIndex: Int = _selectedTabIndex.value) {
        if (!pagingRequestTracker.isValidTab(tabIndex)) return

        tabDataLoaded[tabIndex] = true
        startListRequest(
            tabIndex = tabIndex,
            requestPage = 1,
            loadType = LoadType.Initial,
            cancelCurrent = true,
        )
    }

    /**
     * 触发下拉刷新
     *
     * @author Joker.X
     */
    fun onRefresh(tabIndex: Int = _selectedTabIndex.value) {
        if (!pagingRequestTracker.isValidTab(tabIndex)) return

        tabDataLoaded[tabIndex] = true
        startListRequest(
            tabIndex = tabIndex,
            requestPage = 1,
            loadType = LoadType.Refresh,
            cancelCurrent = true,
        )
    }

    /**
     * 加载更多数据
     *
     * @author Joker.X
     */
    fun onLoadMore(tabIndex: Int = _selectedTabIndex.value) {
        if (!pagingRequestTracker.isValidTab(tabIndex)) return

        // 只有在可加载更多和加载失败状态下才能触发加载
        if (requestJobs[tabIndex]?.isActive == true ||
            _loadMoreStates[tabIndex].value == LoadMoreState.Loading ||
            _loadMoreStates[tabIndex].value == LoadMoreState.NoMore ||
            _loadMoreStates[tabIndex].value == LoadMoreState.Success
        ) {
            return
        }

        startListRequest(
            tabIndex = tabIndex,
            requestPage = pagingRequestTracker.nextPage(tabIndex),
            loadType = LoadType.LoadMore,
            cancelCurrent = false,
        )
    }

    /**
     * 判断是否应该触发加载更多
     *
     * @author Joker.X
     */
    fun shouldTriggerLoadMore(lastIndex: Int, totalCount: Int, tabIndex: Int = _selectedTabIndex.value): Boolean =
        lastIndex >= totalCount - 3 &&
            _loadMoreStates[tabIndex].value != LoadMoreState.Loading &&
            _loadMoreStates[tabIndex].value != LoadMoreState.NoMore &&
            _listDataMap[tabIndex].value.isNotEmpty()

    /**
     * 更新选中的标签
     *
     * @author Joker.X
     */
    fun updateSelectedTab(index: Int) {
        if (_selectedTabIndex.value != index) {
            _selectedTabIndex.value = index
            _isAnimatingTabChange.value = true

            // 当切换到新标签页时，检查并按需加载数据
            loadTabDataIfNeeded(index)
        }
    }

    /**
     * 通知标签切换动画已完成
     *
     * @author Joker.X
     */
    fun notifyAnimationCompleted() {
        _isAnimatingTabChange.value = false
    }

    /**
     * 根据页面滑动更新选中的标签
     *
     * @author Joker.X
     */
    fun updateTabByPage(index: Int) {
        if (!_isAnimatingTabChange.value) {
            _selectedTabIndex.value = index

            // 当滑动到新标签页时，检查并按需加载数据
            loadTabDataIfNeeded(index)
        }
    }

    /**
     * 获取指定标签的状态过滤条件
     */
    private fun getStatusFilter(tabIndex: Int): List<Int>? = when (OrderStatus.entries[tabIndex]) {
        OrderStatus.ALL -> null
        OrderStatus.UNPAID -> listOf(0)
        OrderStatus.UNSHIPPED -> listOf(1)
        OrderStatus.UNRECEIVED -> listOf(2)
        OrderStatus.AFTER_SALE -> listOf(5, 6)
        OrderStatus.UNEVALUATED -> listOf(3)
        OrderStatus.COMPLETED -> listOf(4)
    }

    /**
     * 取消订单
     *
     * @author Joker.X
     */
    fun cancelOrder(orderId: Long) {
        currentCancelOrderId = orderId
        showCancelModal()
    }

    /**
     * 取消订单弹窗展开完成后加载取消原因
     *
     * 由 View 层在弹窗动画完成后调用，避免在动画过程中网络请求卡顿UI
     *
     * @author Joker.X
     */
    fun onCancelModalExpanded() {
        loadCancelReasons()
    }

    /**
     * 加载取消原因字典数据
     *
     * @author Joker.X
     */
    fun loadCancelReasons() {
        // 如果 ui 状态为成功，则不重复加载
        if (_cancelReasonsModalUiState.value is BaseNetWorkUiState.Success) {
            return
        }
        ResultHandler.handleResultWithData(
            scope = viewModelScope,
            flow = commonRepository.getDictData(
                DictDataRequest(
                    types = listOf("orderCancelReason"),
                ),
            ).asResult(),
            showToast = false,
            onLoading = {
                _selectedCancelReason.value = null
                _cancelReasonsModalUiState.value = BaseNetWorkUiState.Loading
            },
            onData = { data ->
                _selectedCancelReason.value = null
                _cancelReasonsModalUiState.value = requiredDictionaryUiState(data.orderCancelReason)
            },
            onError = { _, _ ->
                _selectedCancelReason.value = null
                _cancelReasonsModalUiState.value = BaseNetWorkUiState.Error()
            },
        )
    }

    /**
     * 显示取消原因选择弹窗
     *
     * @author Joker.X
     */
    fun showCancelModal() {
        _cancelModalVisible.value = true
    }

    /**
     * 隐藏取消原因选择弹窗
     *
     * @author Joker.X
     */
    fun hideCancelModal() {
        _cancelModalVisible.value = false
        _selectedCancelReason.value = null
    }

    /**
     * 选择取消原因
     *
     * @author Joker.X
     */
    fun selectCancelReason(reason: DictItem) {
        _selectedCancelReason.value = reason
    }

    /**
     * 确认取消订单
     *
     * @author Joker.X
     */
    fun confirmCancelOrder() {
        ResultHandler.handleResultWithData(
            scope = viewModelScope,
            flow = orderRepository.cancelOrder(
                CancelOrderRequest(
                    orderId = currentCancelOrderId,
                    remark = _selectedCancelReason.value?.name ?: "",
                ),
            ).asResult(),
            onData = { _ ->
                // 取消订单会影响：全部(0)、待付款(1)、待发货(2)
                refreshSpecificTabs(listOf(0, 1, 2))
            },
        )
    }

    /**
     * 显示确认收货弹窗
     *
     * @author Joker.X
     */
    fun showConfirmReceiveDialog(orderId: Long) {
        currentConfirmOrderId = orderId
        _showConfirmDialog.value = true
    }

    /**
     * 隐藏确认收货弹窗
     *
     * @author Joker.X
     */
    fun hideConfirmReceiveDialog() {
        _showConfirmDialog.value = false
        currentConfirmOrderId = 0L
    }

    /**
     * 确认收货
     *
     * @author Joker.X
     */
    fun confirmReceiveOrder() {
        ResultHandler.handleResultWithData(
            scope = viewModelScope,
            flow = orderRepository.confirmReceive(currentConfirmOrderId).asResult(),
            onData = { _ ->
                // 隐藏弹窗
                hideConfirmReceiveDialog()

                // 确认收货会影响：全部(0)、待收货(3)、待评价(5)
                refreshSpecificTabs(listOf(0, 3, 5))
            },
        )
    }

    /**
     * 显示再次购买弹窗
     *
     * @author Joker.X
     */
    fun showRebuyModal() {
        _rebuyModalVisible.value = true
    }

    /**
     * 隐藏再次购买弹窗
     *
     * @author Joker.X
     */
    fun hideRebuyModal() {
        _rebuyModalVisible.value = false
        // 不立即清空数据，避免弹窗关闭时数据消失
    }

    /**
     * 显示商品评论弹窗
     *
     * @author Joker.X
     */
    fun showCommentModal() {
        _commentModalVisible.value = true
    }

    /**
     * 隐藏商品评论弹窗
     *
     * @author Joker.X
     */
    fun hideCommentModal() {
        _commentModalVisible.value = false
        // 不立即清空数据，避免弹窗关闭时数据消失
    }

    /**
     * 处理再次购买逻辑
     *
     * @author Joker.X
     */
    fun handleRebuy(order: Order) {
        // 清除上一次的数据
        _rebuyCurrentOrder.value = null
        _rebuyCartList.value = emptyList()

        val cartList = convertOrderGoodsToCart(order)
        if (cartList.size > 1) {
            // 多个商品时显示弹窗让用户选择
            _rebuyCurrentOrder.value = order
            _rebuyCartList.value = cartList
            showRebuyModal()
        } else {
            // 单个商品时直接跳转
            val goodsId = cartList.firstOrNull()?.goodsId ?: 0L
            toGoodsDetail(goodsId)
        }
    }

    /**
     * 跳转到商品详情页面（再次购买）
     *
     * @param goodsId 商品ID
     * @author Joker.X
     */
    fun toGoodsDetail(goodsId: Long) {
        // 隐藏弹窗
        hideRebuyModal()
        GoodsNavigator.toDetail(goodsId)
    }

    /**
     * 处理商品评论逻辑
     *
     * @author Joker.X
     */
    fun handleOrderComment(order: Order) {
        // 清除上一次的数据
        _commentCurrentOrder.value = null
        _commentCartList.value = emptyList()

        val cartList = convertOrderGoodsToCart(order.uncommentedGoods())
        if (cartList.isEmpty()) return
        if (cartList.size > 1) {
            // 多个商品时显示弹窗让用户选择
            _commentCurrentOrder.value = order
            _commentCartList.value = cartList
            showCommentModal()
        } else {
            // 单个商品时直接跳转
            val orderId = order.id
            val goodsId = cartList.firstOrNull()?.goodsId ?: 0L
            toOrderCommentForGoods(orderId, goodsId)
        }
    }

    /**
     * 跳转到指定商品的订单评价页面
     *
     * @param orderId 订单ID
     * @param goodsId 商品ID
     * @author Joker.X
     */
    fun toOrderCommentForGoods(orderId: Long, goodsId: Long) {
        // 隐藏弹窗
        hideCommentModal()
        navigate(OrderRoutes.Comment(orderId = orderId, goodsId = goodsId))
    }

    /**
     * 将Order中的goodsList转换为Cart类型的列表
     * 参考OrderConfirmViewModel中的处理方法
     */
    private fun convertOrderGoodsToCart(order: Order): List<Cart> = convertOrderGoodsToCart(order.goodsList.orEmpty())

    private fun convertOrderGoodsToCart(goodsList: List<OrderGoods>): List<Cart> = goodsList.let {
        // 按商品ID分组
        val groupedGoods = it.groupBy { goods -> goods.goodsId }

        // 为每个商品ID创建一个Cart对象
        groupedGoods.map { (goodsId, items) ->
            val firstItem = items.first()

            Cart().apply {
                this.goodsId = goodsId
                this.goodsName = firstItem.goodsInfo?.title ?: ""
                this.goodsMainPic = firstItem.goodsInfo?.mainPic ?: ""

                // 收集该商品的所有规格
                val allSpecs = mutableListOf<CartGoodsSpec>()

                // 遍历该商品的所有选中项
                items.forEach { orderGoods ->
                    // 如果有规格信息，转换为CartGoodsSpec并添加
                    orderGoods.spec?.let { spec ->
                        val cartSpec = CartGoodsSpec(
                            id = spec.id,
                            goodsId = spec.goodsId,
                            name = spec.name,
                            price = spec.price,
                            stock = spec.stock,
                            count = orderGoods.count,
                            images = spec.images,
                        )
                        allSpecs.add(cartSpec)
                    }
                }

                // 设置规格列表
                this.spec = allSpecs
            }
        }
    }
}
