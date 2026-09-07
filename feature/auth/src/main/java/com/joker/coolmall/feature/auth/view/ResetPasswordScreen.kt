@file:Suppress("ktlint:standard:function-naming")

package com.joker.coolmall.feature.auth.view

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joker.coolmall.core.designsystem.component.StartRow
import com.joker.coolmall.core.designsystem.theme.AppTheme
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalMedium
import com.joker.coolmall.core.designsystem.theme.SpaceVerticalXLarge
import com.joker.coolmall.core.model.entity.Captcha
import com.joker.coolmall.core.ui.component.button.AppButton
import com.joker.coolmall.core.util.permission.PermissionUtils
import com.joker.coolmall.core.util.toast.ToastUtils
import com.joker.coolmall.feature.auth.R
import com.joker.coolmall.feature.auth.component.AnimatedAuthPage
import com.joker.coolmall.feature.auth.component.ImageCaptchaDialog
import com.joker.coolmall.feature.auth.component.PasswordInputField
import com.joker.coolmall.feature.auth.component.PhoneInputField
import com.joker.coolmall.feature.auth.component.VerificationCodeField
import com.joker.coolmall.feature.auth.viewmodel.ResetPasswordViewModel
import com.joker.coolmall.navigation.navigateBack

/**
 * 找回密码路由
 *
 * @param viewModel 重置密码ViewModel
 * @author Joker.X
 */
@Composable
internal fun ResetPasswordRoute(viewModel: ResetPasswordViewModel = hiltViewModel()) {
    val context = LocalContext.current
    // 收集手机号输入
    val phone by viewModel.phone.collectAsStateWithLifecycle()
    // 收集新密码输入
    val newPassword by viewModel.newPassword.collectAsStateWithLifecycle()
    // 收集确认密码输入
    val confirmPassword by viewModel.confirmPassword.collectAsStateWithLifecycle()
    // 收集验证码输入
    val verificationCode by viewModel.verificationCode.collectAsStateWithLifecycle()
    val codeState by viewModel.codeState.collectAsStateWithLifecycle()
    val isPhoneValid by viewModel.isPhoneValid.collectAsStateWithLifecycle(initialValue = false)

    // 与短信登录保持一致：验证码通过通知展示，先获取通知权限。
    val onSendVerificationCodeWithPermission = {
        PermissionUtils.requestNotificationPermission(context) { granted ->
            if (granted) {
                viewModel.sendVerificationCode()
            } else {
                ToastUtils.showError(R.string.notification_permission_required)
            }
        }
    }

    ResetPasswordScreen(
        phone = phone,
        newPassword = newPassword,
        confirmPassword = confirmPassword,
        verificationCode = verificationCode,
        showImageCodePopup = codeState.showImageCodePopup,
        captcha = codeState.captcha,
        isLoadingCaptcha = codeState.isLoadingCaptcha,
        isSendingCode = codeState.isSendingCode,
        isPhoneValid = isPhoneValid,
        onPhoneChange = viewModel::updatePhone,
        onNewPasswordChange = viewModel::updateNewPassword,
        onConfirmPasswordChange = viewModel::updateConfirmPassword,
        onVerificationCodeChange = viewModel::updateVerificationCode,
        onSendVerificationCode = onSendVerificationCodeWithPermission,
        onHideImageCodePopup = viewModel::onHideImageCodePopup,
        onImageCodeConfirm = viewModel::onImageCodeConfirm,
        onRefreshCaptcha = viewModel::getCaptcha,
        onResetPasswordClick = viewModel::resetPassword,
    )
}

/**
 * 找回密码界面
 *
 * @param phone 手机号
 * @param newPassword 新密码
 * @param confirmPassword 确认密码
 * @param verificationCode 验证码
 * @param showImageCodePopup 是否显示图形验证码弹窗
 * @param captcha 图形验证码数据
 * @param isLoadingCaptcha 是否正在获取图形验证码
 * @param isSendingCode 是否正在发送短信验证码
 * @param isPhoneValid 手机号是否合法
 * @param onPhoneChange 手机号变更回调
 * @param onNewPasswordChange 新密码变更回调
 * @param onConfirmPasswordChange 确认密码变更回调
 * @param onVerificationCodeChange 验证码变更回调
 * @param onSendVerificationCode 发送验证码回调
 * @param onHideImageCodePopup 关闭图形验证码弹窗
 * @param onImageCodeConfirm 提交图形验证码
 * @param onRefreshCaptcha 刷新图形验证码
 * @param onResetPasswordClick 重置密码按钮点击回调
 * @author Joker.X
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResetPasswordScreen(
    phone: String = "",
    newPassword: String = "",
    confirmPassword: String = "",
    verificationCode: String = "",
    showImageCodePopup: Boolean = false,
    captcha: Captcha = Captcha(),
    isLoadingCaptcha: Boolean = false,
    isSendingCode: Boolean = false,
    isPhoneValid: Boolean = false,
    onPhoneChange: (String) -> Unit = {},
    onNewPasswordChange: (String) -> Unit = {},
    onConfirmPasswordChange: (String) -> Unit = {},
    onVerificationCodeChange: (String) -> Unit = {},
    onSendVerificationCode: () -> Unit = {},
    onHideImageCodePopup: () -> Unit = {},
    onImageCodeConfirm: (String) -> Unit = {},
    onRefreshCaptcha: () -> Unit = {},
    onResetPasswordClick: () -> Unit = {},
) {
    AnimatedAuthPage(
        title = stringResource(id = R.string.reset_password),
        withFadeIn = true,
        onBackClick = { navigateBack() },
    ) {
        ResetPasswordContentView(
            phone = phone,
            newPassword = newPassword,
            confirmPassword = confirmPassword,
            verificationCode = verificationCode,
            canSendCode = isPhoneValid && !isLoadingCaptcha && !isSendingCode,
            onPhoneChange = onPhoneChange,
            onNewPasswordChange = onNewPasswordChange,
            onConfirmPasswordChange = onConfirmPasswordChange,
            onVerificationCodeChange = onVerificationCodeChange,
            onSendVerificationCode = onSendVerificationCode,
            onResetPasswordClick = onResetPasswordClick,
        )
    }

    // 更换图片时清空组件内部的图形验证码输入，避免沿用旧图片的答案。
    key(captcha.captchaId) {
        ImageCaptchaDialog(
            visible = showImageCodePopup,
            captcha = captcha,
            onDismiss = onHideImageCodePopup,
            onConfirm = onImageCodeConfirm,
            onRefreshCaptcha = onRefreshCaptcha,
            isSubmitting = isSendingCode || isLoadingCaptcha,
        )
    }
}

/**
 * 找回密码内容视图
 *
 * @param phone 手机号
 * @param newPassword 新密码
 * @param confirmPassword 确认密码
 * @param verificationCode 验证码
 * @param canSendCode 是否允许获取验证码
 * @param onPhoneChange 手机号变更回调
 * @param onNewPasswordChange 新密码变更回调
 * @param onConfirmPasswordChange 确认密码变更回调
 * @param onVerificationCodeChange 验证码变更回调
 * @param onSendVerificationCode 发送验证码回调
 * @param onResetPasswordClick 重置密码按钮点击回调
 * @author Joker.X
 */
@Composable
private fun ResetPasswordContentView(
    phone: String,
    newPassword: String,
    confirmPassword: String,
    verificationCode: String,
    canSendCode: Boolean,
    onPhoneChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onVerificationCodeChange: (String) -> Unit,
    onSendVerificationCode: () -> Unit,
    onResetPasswordClick: () -> Unit,
) {
    // 记录输入框焦点状态
    val phoneFieldFocused = remember { mutableStateOf(false) }
    val codeFieldFocused = remember { mutableStateOf(false) }
    val newPasswordFieldFocused = remember { mutableStateOf(false) }
    val confirmPasswordFieldFocused = remember { mutableStateOf(false) }

    // 使用封装的手机号输入组件
    PhoneInputField(
        phone = phone,
        onPhoneChange = onPhoneChange,
        phoneFieldFocused = phoneFieldFocused,
        placeholder = stringResource(id = R.string.phone_hint),
        nextAction = ImeAction.Next,
    )

    Spacer(modifier = Modifier.height(30.dp))

    // 使用封装的验证码输入组件
    VerificationCodeField(
        verificationCode = verificationCode,
        onVerificationCodeChange = onVerificationCodeChange,
        codeFieldFocused = codeFieldFocused,
        onSendVerificationCode = onSendVerificationCode,
        placeholder = stringResource(id = R.string.verification_code),
        nextAction = ImeAction.Next,
        isPhoneValid = canSendCode,
    )

    Spacer(modifier = Modifier.height(30.dp))

    // 使用封装的密码输入组件 - 新密码
    PasswordInputField(
        password = newPassword,
        onPasswordChange = onNewPasswordChange,
        passwordFieldFocused = newPasswordFieldFocused,
        placeholder = stringResource(id = R.string.set_new_password),
        nextAction = ImeAction.Next,
    )

    Spacer(modifier = Modifier.height(30.dp))

    // 使用封装的密码输入组件 - 确认新密码
    PasswordInputField(
        password = confirmPassword,
        onPasswordChange = onConfirmPasswordChange,
        passwordFieldFocused = confirmPasswordFieldFocused,
        placeholder = stringResource(id = R.string.confirm_new_password),
        nextAction = ImeAction.Done,
    )

    SpaceVerticalMedium()

    // 提示信息
    StartRow {
        Text(
            text = stringResource(id = R.string.reset_password_tip),
            fontSize = 12.sp,
            color = Color.Gray,
        )
    }

    // 密码不匹配提示
    if (newPassword.isNotEmpty() && confirmPassword.isNotEmpty() && newPassword != confirmPassword) {
        SpaceVerticalMedium()
        Text(
            text = stringResource(id = R.string.password_mismatch),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    SpaceVerticalXLarge()

    AppButton(
        text = stringResource(id = R.string.reset_password_button),
        onClick = onResetPasswordClick,
        enabled = false,
    )
}

/**
 * 找回密码界面浅色主题预览
 *
 * @author Joker.X
 */
@Preview(showBackground = true)
@Composable
fun ResetPasswordScreenPreview() {
    AppTheme {
        ResetPasswordScreen()
    }
}
