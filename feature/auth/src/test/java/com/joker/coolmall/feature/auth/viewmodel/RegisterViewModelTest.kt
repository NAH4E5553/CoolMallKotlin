package com.joker.coolmall.feature.auth.viewmodel

import android.content.Context
import com.joker.coolmall.core.data.repository.AuthRepository
import com.joker.coolmall.core.data.state.AppState
import com.joker.coolmall.core.model.entity.Captcha
import com.joker.coolmall.core.model.response.NetworkResponse
import com.joker.coolmall.core.util.notification.NotificationUtil
import com.joker.coolmall.core.util.toast.ToastUtils
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var appState: AppState
    private lateinit var authRepository: AuthRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        mockkObject(ToastUtils)
        every { ToastUtils.showError(any<CharSequence>()) } just runs
        every { ToastUtils.showError(any<Int>()) } just runs

        mockkObject(NotificationUtil)
        every { NotificationUtil.sendVerificationCodeNotification(any(), any(), any()) } returns 1

        appState = mockk(relaxed = true)
        authRepository = mockk()
        context = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `captcha failure keeps registration popup closed`() = runTest(mainDispatcherRule.dispatcher) {
        every { authRepository.getCaptcha() } returns
            flowOf(NetworkResponse(code = 400, message = "captcha failed"))
        val viewModel = createViewModel()
        viewModel.updatePhone(VALID_PHONE)

        viewModel.onSendCodeButtonClick()
        advanceUntilIdle()

        assertFalse(viewModel.showImageCodePopup.value)
        assertFalse(viewModel.isLoadingCaptcha.value)
    }

    @Test
    fun `registration ignores duplicate SMS submission`() = runTest(mainDispatcherRule.dispatcher) {
        every { authRepository.getCaptcha() } returns flowOf(NetworkResponse(data = CAPTCHA))
        val smsResponse = CompletableDeferred<NetworkResponse<String>>()
        every { authRepository.getSmsCode(any()) } returns flow { emit(smsResponse.await()) }
        val viewModel = createViewModel()
        viewModel.updatePhone(VALID_PHONE)
        viewModel.onSendCodeButtonClick()
        advanceUntilIdle()

        viewModel.onImageCodeConfirm("A1B2")
        runCurrent()
        viewModel.onImageCodeConfirm("A1B2")
        runCurrent()

        verify(exactly = 1) { authRepository.getSmsCode(any()) }
        assertTrue(viewModel.isSendingCode.value)

        smsResponse.complete(NetworkResponse(data = "1234"))
        advanceUntilIdle()

        assertFalse(viewModel.isSendingCode.value)
        assertFalse(viewModel.showImageCodePopup.value)
    }

    private fun createViewModel() = RegisterViewModel(
        appState = appState,
        authRepository = authRepository,
        context = context,
    )

    private companion object {
        const val VALID_PHONE = "13800138000"
        val CAPTCHA = Captcha(data = "image", captchaId = "captcha-1")
    }
}
