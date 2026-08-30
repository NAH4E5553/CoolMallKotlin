package com.joker.coolmall.feature.auth.viewmodel

import android.app.Activity
import androidx.lifecycle.viewModelScope
import com.joker.coolmall.core.common.base.viewmodel.BaseViewModel
import com.joker.coolmall.core.common.manager.QQLoginManager
import com.joker.coolmall.core.common.manager.QQLoginResult
import com.joker.coolmall.core.data.repository.AuthRepository
import com.joker.coolmall.core.data.state.AppState
import com.joker.coolmall.core.model.entity.Auth
import com.joker.coolmall.core.model.request.QQLoginRequest
import com.joker.coolmall.core.util.toast.ToastUtils
import com.joker.coolmall.feature.auth.R
import com.joker.coolmall.navigation.NavigationService.navigateBack
import com.joker.coolmall.result.ResultHandler
import com.joker.coolmall.result.asResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * 登录主页 ViewModel
 *
 * @author Joker.X
 */
@HiltViewModel
class LoginViewModel @Inject constructor(private val appState: AppState, private val authRepository: AuthRepository) :
    BaseViewModel() {

    private val qqLoginManager = QQLoginManager.getInstance()

    private val _isQqLoginInProgress = MutableStateFlow(false)
    val isQqLoginInProgress: StateFlow<Boolean> = _isQqLoginInProgress.asStateFlow()

    init {
        observeQQLoginResult()
    }

    /**
     * 启动 QQ 登录
     *
     * @param activity 当前 Activity 实例
     * @author Joker.X
     */
    fun startQQLogin(activity: Activity) {
        if (_isQqLoginInProgress.value) return

        // StateFlow 会重放当前值，新的登录开始前先丢弃历史结果
        qqLoginManager.clearLoginResult()
        if (!_isQqLoginInProgress.compareAndSet(expect = false, update = true)) return

        try {
            // 启动 QQ 登录
            qqLoginManager.startQQLogin(activity)
        } catch (_: Exception) {
            _isQqLoginInProgress.value = false
            ToastUtils.showError(R.string.start_qq_login_failed)
        }
    }

    /**
     * 在 ViewModel 生命周期内只订阅一次 QQ 登录结果
     *
     * @author Joker.X
     */
    private fun observeQQLoginResult() {
        viewModelScope.launch {
            qqLoginManager.loginResult.filterNotNull().collect { result ->
                // 无正在进行的登录时，丢弃单例中遗留的旧结果
                if (!_isQqLoginInProgress.value) {
                    qqLoginManager.clearLoginResult()
                    return@collect
                }

                // 先清空 StateFlow，防止当前结果被后续订阅者重放
                qqLoginManager.clearLoginResult()
                when (result) {
                    is QQLoginResult.Success -> {
                        // QQ 授权成功，继续调用后端登录接口
                        qqLoginSuccess(result.accessToken, result.openId)
                    }

                    is QQLoginResult.Error -> {
                        _isQqLoginInProgress.value = false
                        ToastUtils.showError(R.string.login_failed)
                    }

                    is QQLoginResult.Cancel -> {
                        _isQqLoginInProgress.value = false
                        ToastUtils.showWarning(R.string.login_cancelled)
                    }
                }
            }
        }
    }

    /**
     * QQ 登录成功
     *
     * @param accessToken QQ 登录成功返回的 accessToken
     * @param openId QQ 登录成功返回的 openId
     * @author Joker.X
     */
    private fun qqLoginSuccess(accessToken: String, openId: String) {
        val params = QQLoginRequest(
            accessToken = accessToken,
            openId = openId,
        )
        ResultHandler.handleResultWithData(
            scope = viewModelScope,
            flow = authRepository.loginByQqApp(params).asResult(),
            onData = { authData -> loginSuccess(authData) },
            onFinally = { _isQqLoginInProgress.value = false },
        )
    }

    /**
     * 登录成功统一处理
     *
     * @param authData 登录成功返回的认证数据
     * @author Joker.X
     */
    fun loginSuccess(authData: Auth) {
        viewModelScope.launch {
            ToastUtils.showSuccess(R.string.login_success)
            appState.updateAuth(authData)
            appState.refreshUserInfo()
            navigateBack()
        }
    }

    /**
     * 微信登录点击
     *
     * @author Joker.X
     */
    fun onWechatLoginClick() {
        onWechatAndAlipayLoginTipClick()
    }

    /**
     * 支付宝登录点击
     *
     * @author Joker.X
     */
    fun onAlipayLoginClick() {
        onWechatAndAlipayLoginTipClick()
    }

    /**
     * 微信和支付宝登录提示
     *
     * @author Joker.X
     */
    fun onWechatAndAlipayLoginTipClick() {
        ToastUtils.showWarning(R.string.third_party_login_only_qq)
    }
}
