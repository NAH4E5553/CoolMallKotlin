package com.joker.coolmall.feature.auth.viewmodel

import androidx.lifecycle.viewModelScope
import com.joker.coolmall.core.common.base.viewmodel.BaseViewModel
import com.joker.coolmall.core.data.repository.AuthRepository
import com.joker.coolmall.core.model.entity.Captcha
import com.joker.coolmall.core.util.toast.ToastUtils
import com.joker.coolmall.core.util.validation.ValidationUtil
import com.joker.coolmall.feature.auth.R
import com.joker.coolmall.feature.auth.notification.VerificationCodeNotifier
import com.joker.coolmall.result.ResultHandler
import com.joker.coolmall.result.asResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 找回密码页面的图形验证码及短信请求状态。 */
data class ResetPasswordCodeState(
    val captcha: Captcha = Captcha(),
    val showImageCodePopup: Boolean = false,
    val isLoadingCaptcha: Boolean = false,
    val isSendingCode: Boolean = false,
)

/**
 * 找回密码ViewModel
 *
 * @author Joker.X
 */
@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val verificationCodeNotifier: VerificationCodeNotifier,
) : BaseViewModel() {

    private var captchaRequestJob: Job? = null
    private var smsRequestJob: Job? = null
    private var codeRequestId = 0L

    private val _codeState = MutableStateFlow(ResetPasswordCodeState())
    val codeState: StateFlow<ResetPasswordCodeState> = _codeState.asStateFlow()

    /**
     * 手机号输入
     */
    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone

    val isPhoneValid = _phone.map(ValidationUtil::isValidPhone)

    /**
     * 新密码输入
     */
    private val _newPassword = MutableStateFlow("")
    val newPassword: StateFlow<String> = _newPassword

    /**
     * 确认密码输入
     */
    private val _confirmPassword = MutableStateFlow("")
    val confirmPassword: StateFlow<String> = _confirmPassword

    /**
     * 验证码输入
     */
    private val _verificationCode = MutableStateFlow("")
    val verificationCode: StateFlow<String> = _verificationCode

    /**
     * 更新手机号输入
     *
     * @param value 手机号值
     * @author Joker.X
     */
    fun updatePhone(value: String) {
        if (_phone.value == value) return
        cancelCodeRequests()
        _verificationCode.value = ""
        _phone.value = value
    }

    /**
     * 更新新密码输入
     *
     * @param value 新密码值
     * @author Joker.X
     */
    fun updateNewPassword(value: String) {
        _newPassword.value = value
    }

    /**
     * 更新确认密码输入
     *
     * @param value 确认密码值
     * @author Joker.X
     */
    fun updateConfirmPassword(value: String) {
        _confirmPassword.value = value
    }

    /**
     * 更新验证码输入
     *
     * @param value 验证码值
     * @author Joker.X
     */
    fun updateVerificationCode(value: String) {
        _verificationCode.value = value
    }

    /**
     * 校验手机号后获取图形验证码，成功时打开安全验证弹窗。
     *
     * @author Joker.X
     */
    fun sendVerificationCode() {
        if (!ValidationUtil.isValidPhone(_phone.value)) {
            ToastUtils.showError(R.string.invalid_phone_number)
            return
        }
        if (_codeState.value.isLoadingCaptcha || _codeState.value.isSendingCode) return

        fetchCaptcha(showPopupOnSuccess = true)
    }

    /** 刷新弹窗中的图形验证码，旧请求不能覆盖新结果。 */
    fun getCaptcha() {
        if (!_codeState.value.showImageCodePopup || _codeState.value.isSendingCode) return
        fetchCaptcha(showPopupOnSuccess = false)
    }

    /** 关闭弹窗并取消图片请求；已提交的短信仍等待结果，不把关闭视为服务端撤销。 */
    fun onHideImageCodePopup() {
        if (_codeState.value.isSendingCode) {
            _codeState.update { it.copy(showImageCodePopup = false) }
            return
        }
        cancelCodeRequests()
    }

    /** 提交图形验证码，沿用短信登录的验证码接口与通知展示方式。 */
    fun onImageCodeConfirm(imageCode: String) {
        val state = _codeState.value
        if (state.isSendingCode || state.isLoadingCaptcha || !state.showImageCodePopup) return
        if (!ValidationUtil.isValidPhone(_phone.value)) {
            ToastUtils.showError(R.string.invalid_phone_number)
            return
        }
        if (state.captcha.captchaId.isBlank() || !ValidationUtil.isValidImageCode(imageCode)) {
            ToastUtils.showError(R.string.invalid_verification_code)
            return
        }

        val requestId = ++codeRequestId
        val params = mapOf(
            "phone" to _phone.value,
            "captchaId" to state.captcha.captchaId,
            "code" to imageCode,
        )
        _codeState.update { it.copy(isSendingCode = true) }
        smsRequestJob = ResultHandler.handleResultWithData(
            scope = viewModelScope,
            flow = authRepository.getSmsCode(params).asResult(),
            onData = { smsCode ->
                if (requestId == codeRequestId) {
                    verificationCodeNotifier.notify(smsCode)
                    _codeState.update { it.copy(showImageCodePopup = false, captcha = Captcha()) }
                }
            },
            onFinally = {
                if (requestId == codeRequestId) {
                    _codeState.update { it.copy(isSendingCode = false) }
                    smsRequestJob = null
                }
            },
        )
    }

    private fun fetchCaptcha(showPopupOnSuccess: Boolean) {
        val requestId = ++codeRequestId
        captchaRequestJob?.cancel()
        _codeState.update {
            it.copy(
                captcha = Captcha(),
                isLoadingCaptcha = true,
                showImageCodePopup = !showPopupOnSuccess && it.showImageCodePopup,
            )
        }
        captchaRequestJob = ResultHandler.handleResultWithData(
            scope = viewModelScope,
            flow = authRepository.getCaptcha().asResult(),
            onData = { captcha ->
                if (requestId == codeRequestId) {
                    _codeState.update {
                        it.copy(captcha = captcha, showImageCodePopup = true)
                    }
                }
            },
            onFinally = {
                if (requestId == codeRequestId) {
                    _codeState.update { it.copy(isLoadingCaptcha = false) }
                    captchaRequestJob = null
                }
            },
        )
    }

    private fun cancelCodeRequests() {
        codeRequestId++
        captchaRequestJob?.cancel()
        smsRequestJob?.cancel()
        captchaRequestJob = null
        smsRequestJob = null
        _codeState.value = ResetPasswordCodeState()
    }

    /**
     * 执行重置密码操作
     *
     * @author Joker.X
     */
    fun resetPassword() {
        // 此处仅为空实现，实际项目中需要调用重置密码API
        viewModelScope.launch {
            // TODO: 实现实际重置密码逻辑
        }
    }
}
