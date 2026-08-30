package com.joker.coolmall.feature.auth.viewmodel

import android.content.Context
import com.joker.coolmall.core.data.repository.AuthRepository
import com.joker.coolmall.core.data.state.AppState
import com.joker.coolmall.core.model.entity.Captcha
import com.joker.coolmall.core.model.response.NetworkResponse
import com.joker.coolmall.core.util.notification.NotificationUtil
import com.joker.coolmall.core.util.storage.MMKVUtils
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SmsLoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var appState: AppState
    private lateinit var authRepository: AuthRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        mockkObject(MMKVUtils)
        every { MMKVUtils.getString(any(), any()) } returns ""
        every { MMKVUtils.putString(any(), any()) } just runs

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
    fun `captcha popup opens only after successful response`() = runTest(mainDispatcherRule.dispatcher) {
        every { authRepository.getCaptcha() } returns
            flowOf(NetworkResponse(data = CAPTCHA))
        val viewModel = createViewModel()
        viewModel.updatePhone(VALID_PHONE)

        viewModel.onSendCodeButtonClick()
        assertTrue(viewModel.isLoadingCaptcha.value)
        assertFalse(viewModel.showImageCodePopup.value)

        advanceUntilIdle()

        assertEquals(CAPTCHA, viewModel.captcha.value)
        assertTrue(viewModel.showImageCodePopup.value)
        assertFalse(viewModel.isLoadingCaptcha.value)
    }

    @Test
    fun `captcha failure keeps popup closed and restores loading state`() = runTest(mainDispatcherRule.dispatcher) {
        every { authRepository.getCaptcha() } returns
            flowOf(NetworkResponse(code = 400, message = "captcha failed"))
        val viewModel = createViewModel()
        viewModel.updatePhone(VALID_PHONE)

        viewModel.onSendCodeButtonClick()
        advanceUntilIdle()

        assertFalse(viewModel.showImageCodePopup.value)
        assertFalse(viewModel.isLoadingCaptcha.value)
        verify(exactly = 1) { ToastUtils.showError("captcha failed") }
    }

    @Test
    fun `latest captcha request wins when an older request completes later`() = runTest(mainDispatcherRule.dispatcher) {
        val firstResponse = CompletableDeferred<NetworkResponse<Captcha>>()
        val secondResponse = CompletableDeferred<NetworkResponse<Captcha>>()
        every { authRepository.getCaptcha() } returnsMany listOf(
            flow { emit(firstResponse.await()) },
            flow { emit(secondResponse.await()) },
        )
        val viewModel = createViewModel()
        viewModel.updatePhone(VALID_PHONE)

        viewModel.onSendCodeButtonClick()
        runCurrent()
        viewModel.onSendCodeButtonClick()
        runCurrent()

        secondResponse.complete(NetworkResponse(data = SECOND_CAPTCHA))
        advanceUntilIdle()
        firstResponse.complete(NetworkResponse(data = CAPTCHA))
        advanceUntilIdle()

        assertEquals(SECOND_CAPTCHA, viewModel.captcha.value)
        assertTrue(viewModel.showImageCodePopup.value)
        assertFalse(viewModel.isLoadingCaptcha.value)
    }

    @Test
    fun `duplicate SMS submission is ignored until active request finishes`() = runTest(mainDispatcherRule.dispatcher) {
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
        verify(exactly = 1) {
            NotificationUtil.sendVerificationCodeNotification(context, "1234", any())
        }
    }

    private fun createViewModel() = SmsLoginViewModel(
        appState = appState,
        authRepository = authRepository,
        context = context,
    )

    private companion object {
        const val VALID_PHONE = "13800138000"
        val CAPTCHA = Captcha(data = "image", captchaId = "captcha-1")
        val SECOND_CAPTCHA = Captcha(data = "image-2", captchaId = "captcha-2")
    }
}
