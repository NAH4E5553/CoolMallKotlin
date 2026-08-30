package com.joker.coolmall.feature.auth.viewmodel

import androidx.lifecycle.viewModelScope
import com.joker.coolmall.core.common.base.viewmodel.BaseViewModel
import com.joker.coolmall.core.data.repository.AuthRepository
import com.joker.coolmall.core.data.state.AppState
import com.joker.coolmall.core.model.entity.Auth
import com.joker.coolmall.core.util.storage.MMKVUtils
import com.joker.coolmall.core.util.toast.ToastUtils
import com.joker.coolmall.core.util.validation.ValidationUtil
import com.joker.coolmall.feature.auth.R
import com.joker.coolmall.navigation.navigateBack
import com.joker.coolmall.result.ResultHandler
import com.joker.coolmall.result.asResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 账号密码登录ViewModel
 *
 * @author Joker.X
 */
@HiltViewModel
class AccountLoginViewModel @Inject constructor(
    private val appState: AppState,
    private val authRepository: AuthRepository,
) : BaseViewModel() {

    companion object {
        private const val KEY_SAVED_PHONE = "saved_phone"
        private const val KEY_LEGACY_SAVED_PASSWORD = "saved_password"
    }

    /**
     * 账号输入
     */
    private val _account = MutableStateFlow("")
    val account: StateFlow<String> = _account

    /**
     * 密码输入
     */
    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password

    init {
        // 只加载已保存的手机号，并清理历史版本保存的明文密码
        loadSavedPhone()
        MMKVUtils.remove(KEY_LEGACY_SAVED_PASSWORD)
    }

    /**
     * 登录按钮是否可用
     */
    val isLoginEnabled = _account.combine(_password) { account, password ->
        ValidationUtil.isValidPhone(account) && ValidationUtil.isValidPassword(password)
    }

    /**
     * 更新账号输入
     *
     * @param value 账号值
     * @author Joker.X
     */
    fun updateAccount(value: String) {
        _account.value = value
    }

    /**
     * 更新密码输入
     *
     * @param value 密码值
     * @author Joker.X
     */
    fun updatePassword(value: String) {
        _password.value = value
    }

    /**
     * 执行登录操作
     *
     * @author Joker.X
     */
    fun login() {
        // 验证手机号
        if (!ValidationUtil.isValidPhone(_account.value)) {
            ToastUtils.showError(R.string.invalid_phone_number)
            return
        }

        // 验证密码
        if (!ValidationUtil.isValidPassword(_password.value)) {
            ToastUtils.showError(R.string.invalid_password)
            return
        }

        val params = mapOf(
            "phone" to account.value,
            "password" to password.value,
        )

        ResultHandler.handleResultWithData(
            scope = viewModelScope,
            flow = authRepository.loginByPassword(params).asResult(),
            onData = { authData -> loginSuccess(authData) },
        )
    }

    /**
     * 登录成功
     *
     * @param authData 认证数据
     * @author Joker.X
     */
    private fun loginSuccess(authData: Auth) {
        viewModelScope.launch {
            // 只保存手机号，不持久化用户原始密码
            savePhone(_account.value)

            ToastUtils.showSuccess(R.string.login_success)
            appState.updateAuth(authData)
            appState.refreshUserInfo()
            navigateBack()
            navigateBack()
        }
    }

    /**
     * 加载已保存的手机号
     *
     * @author Joker.X
     */
    private fun loadSavedPhone() {
        val savedPhone = MMKVUtils.getString(KEY_SAVED_PHONE, "")

        if (savedPhone.isNotEmpty()) {
            _account.value = savedPhone
        }
    }

    /**
     * 保存手机号
     *
     * @param phone 手机号
     * @author Joker.X
     */
    private fun savePhone(phone: String) {
        MMKVUtils.putString(KEY_SAVED_PHONE, phone)
    }
}
