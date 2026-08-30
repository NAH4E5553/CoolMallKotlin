package com.joker.coolmall.feature.order.viewmodel

import androidx.lifecycle.viewModelScope
import com.joker.coolmall.core.common.base.viewmodel.BaseViewModel
import com.joker.coolmall.core.data.repository.OrderRepository
import com.joker.coolmall.core.util.log.LogUtils
import com.joker.coolmall.core.util.toast.ToastUtils
import com.joker.coolmall.feature.order.R
import com.joker.coolmall.feature.order.model.Alipay
import com.joker.coolmall.navigation.RefreshResult
import com.joker.coolmall.navigation.navigate
import com.joker.coolmall.navigation.navigateBack
import com.joker.coolmall.navigation.order.OrderRoutes
import com.joker.coolmall.navigation.order.PaymentCompletedResultKey
import com.joker.coolmall.navigation.popBackStackWithResult
import com.joker.coolmall.result.ResultHandler
import com.joker.coolmall.result.asResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 订单支付 ViewModel
 *
 * @param navKey 路由参数
 * @param orderRepository 订单仓库
 * @author Joker.X
 */
@HiltViewModel(assistedFactory = OrderPayViewModel.Factory::class)
class OrderPayViewModel @AssistedInject constructor(
    @Assisted navKey: OrderRoutes.Pay,
    private val orderRepository: OrderRepository,
) : BaseViewModel() {

    /**
     * 订单支付路由参数
     */
    private val _orderPayRoute = MutableStateFlow(navKey)
    val orderPayRoute: StateFlow<OrderRoutes.Pay> = _orderPayRoute.asStateFlow()

    /**
     * 支付宝支付参数
     */
    private val _alipayPayInfo = MutableStateFlow("")
    val alipayPayInfo = _alipayPayInfo.asStateFlow()

    /**
     * 是否正在获取支付参数、等待支付宝结果或向服务端确认支付状态。
     */
    private val _isPaymentInProgress = MutableStateFlow(false)
    val isPaymentInProgress: StateFlow<Boolean> = _isPaymentInProgress.asStateFlow()

    private var paymentVerificationJob: Job? = null

    /**
     * 发起支付宝支付
     *
     * @author Joker.X
     */
    fun startAlipayPayment() {
        if (_isPaymentInProgress.value) return

        _isPaymentInProgress.value = true
        ResultHandler.handleResultWithData(
            scope = viewModelScope,
            flow = orderRepository.alipayAppPay(mapOf("orderId" to _orderPayRoute.value.orderId))
                .asResult(),
            showToast = true,
            onData = { data ->
                if (data.isBlank()) {
                    _isPaymentInProgress.value = false
                    ToastUtils.showError(R.string.payment_failed)
                } else {
                    _alipayPayInfo.value = data
                }
            },
            onError = { _, _ -> _isPaymentInProgress.value = false },
        )
    }

    /**
     * 处理支付宝支付结果
     *
     * @param param 支付结果参数
     * @author Joker.X
     */
    fun processAlipayResult(param: Map<String, String>) {
        if (!_isPaymentInProgress.value) return

        _alipayPayInfo.value = ""
        val result = Alipay(param)
        when (result.resultStatus) {
            Alipay.RESULT_STATUS_CANCEL -> {
                _isPaymentInProgress.value = false
                ToastUtils.showError(R.string.payment_cancel)
                // 支付取消后，根据来源判断是否需要跳转到订单详情
                handleBackAfterPayment(false)
            }

            Alipay.RESULT_STATUS_SUCCESS -> {
                confirmPaymentWithServer()
            }

            else -> {
                _isPaymentInProgress.value = false
                ToastUtils.showError(R.string.payment_failed)
                // 支付失败后，根据来源判断是否需要跳转到订单详情
                handleBackAfterPayment(false)
            }
        }
    }

    /**
     * 支付宝 SDK 调用异常时恢复状态并按支付失败处理。
     */
    fun processAlipayLaunchError(exception: Throwable) {
        if (!_isPaymentInProgress.value) return

        _alipayPayInfo.value = ""
        _isPaymentInProgress.value = false
        LogUtils.e(TAG, "Failed to launch Alipay", exception)
        ToastUtils.showError(R.string.payment_failed)
        handleBackAfterPayment(false)
    }

    /**
     * SDK 成功只代表客户端流程完成；有限次查询服务端订单状态后再给出最终结论。
     */
    private fun confirmPaymentWithServer() {
        if (paymentVerificationJob?.isActive == true) return

        paymentVerificationJob = viewModelScope.launch {
            try {
                repeat(PAYMENT_CONFIRM_ATTEMPTS) { attempt ->
                    val order = try {
                        val response = orderRepository.getOrderInfo(_orderPayRoute.value.orderId).first()
                        response.data?.takeIf { response.isSucceeded }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        LogUtils.e(TAG, "Failed to confirm payment status", exception)
                        null
                    }

                    if (order?.status in PAID_ORDER_STATUSES) {
                        ToastUtils.showSuccess(R.string.payment_success)
                        handleBackAfterPayment(true)
                        return@launch
                    }

                    if (order?.status == CLOSED_ORDER_STATUS) {
                        ToastUtils.showError(R.string.payment_failed)
                        // 服务端已给出关闭状态，返回后刷新订单，不再把它当成确认延迟。
                        handleBackAfterPayment(true)
                        return@launch
                    }

                    if (attempt < PAYMENT_CONFIRM_ATTEMPTS - 1) {
                        delay(PAYMENT_CONFIRM_INTERVAL_MS)
                    }
                }

                ToastUtils.showWarning(R.string.payment_confirmation_pending)
                // 支付状态可能稍后落库，返回后刷新订单并允许用户在详情页继续确认。
                handleBackAfterPayment(true)
            } finally {
                _isPaymentInProgress.value = false
                paymentVerificationJob = null
            }
        }
    }

    /**
     * 处理系统返回按钮点击
     *
     * @author Joker.X
     */
    fun handleBackClick() {
        handleBackAfterPayment(false)
    }

    /**
     * 处理支付后的返回逻辑
     *
     * @param shouldRefreshOrder 返回订单列表时是否需要刷新
     * @author Joker.X
     */
    private fun handleBackAfterPayment(shouldRefreshOrder: Boolean) {
        // 如果来源是确认订单页面，无论支付是否成功，都跳转到订单详情页面
        if (_orderPayRoute.value.from == "confirm") {
            // 返回上一级(确认订单页面)
            navigateBack()
            // 导航到订单详情页面
            navigate(OrderRoutes.Detail(orderId = _orderPayRoute.value.orderId))
        } else {
            // 其他情况正常返回
            if (shouldRefreshOrder) {
                // 服务端状态已变化或仍在确认，使用 NavigationResult 回传刷新信号
                popBackStackWithResult(PaymentCompletedResultKey, RefreshResult(refresh = true))
            } else {
                navigateBack()
            }
        }
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
        fun create(navKey: OrderRoutes.Pay): OrderPayViewModel
    }

    private companion object {
        const val TAG = "OrderPay"
        const val PAYMENT_CONFIRM_ATTEMPTS = 3
        const val PAYMENT_CONFIRM_INTERVAL_MS = 1_000L
        const val CLOSED_ORDER_STATUS = 7
        val PAID_ORDER_STATUSES = 1..6
    }
}
