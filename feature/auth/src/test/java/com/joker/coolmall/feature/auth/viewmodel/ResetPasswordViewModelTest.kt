package com.joker.coolmall.feature.auth.viewmodel

import androidx.lifecycle.ViewModelStore
import com.joker.coolmall.core.data.repository.AuthRepository
import com.joker.coolmall.core.model.entity.Captcha
import com.joker.coolmall.core.model.response.NetworkResponse
import com.joker.coolmall.core.util.toast.ToastUtils
import com.joker.coolmall.feature.auth.R
import com.joker.coolmall.feature.auth.notification.VerificationCodeNotifier
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class ResetPasswordViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var authRepository: AuthRepository
    private lateinit var notifier: VerificationCodeNotifier

    @Before
    fun setUp() {
        mockkObject(ToastUtils)
        every { ToastUtils.showError(any<CharSequence>()) } just runs
        every { ToastUtils.showError(any<Int>()) } just runs
        authRepository = mockk()
        notifier = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `invalid phone does not request a captcha`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        listOf("", "123", "12800000000").forEach { phone ->
            viewModel.updatePhone(phone)
            assertFalse(viewModel.isPhoneValid.first())
            viewModel.sendVerificationCode()
        }
        advanceUntilIdle()

        verify(exactly = 0) { authRepository.getCaptcha() }
        verify(exactly = 3) { ToastUtils.showError(R.string.invalid_phone_number) }
        assertEquals(ResetPasswordCodeState(), viewModel.codeState.value)
    }

    @Test
    fun `captcha opens only after success and repeated button clicks are ignored`() =
        runTest(mainDispatcherRule.dispatcher) {
            val response = CompletableDeferred<NetworkResponse<Captcha>>()
            every { authRepository.getCaptcha() } returns flow { emit(response.await()) }
            val viewModel = createViewModel()
            viewModel.updatePhone(VALID_PHONE)
            assertTrue(viewModel.isPhoneValid.first())

            viewModel.sendVerificationCode()
            viewModel.sendVerificationCode()
            runCurrent()

            assertTrue(viewModel.codeState.value.isLoadingCaptcha)
            assertFalse(viewModel.codeState.value.showImageCodePopup)
            verify(exactly = 1) { authRepository.getCaptcha() }

            response.complete(NetworkResponse(data = CAPTCHA))
            advanceUntilIdle()

            assertEquals(CAPTCHA, viewModel.codeState.value.captcha)
            assertTrue(viewModel.codeState.value.showImageCodePopup)
            assertFalse(viewModel.codeState.value.isLoadingCaptcha)
        }

    @Test
    fun `captcha business failure allows retry without opening popup`() = runTest(mainDispatcherRule.dispatcher) {
        every { authRepository.getCaptcha() } returnsMany listOf(
            flowOf(NetworkResponse(code = 400, message = "captcha failed")),
            flowOf(NetworkResponse(data = CAPTCHA)),
        )
        val viewModel = createViewModel()
        viewModel.updatePhone(VALID_PHONE)
        viewModel.sendVerificationCode()
        advanceUntilIdle()

        assertEquals(ResetPasswordCodeState(), viewModel.codeState.value)
        verify { ToastUtils.showError("captcha failed") }

        viewModel.sendVerificationCode()
        advanceUntilIdle()
        assertTrue(viewModel.codeState.value.showImageCodePopup)
    }

    @Test
    fun `captcha exception restores loading state`() = runTest(mainDispatcherRule.dispatcher) {
        every { authRepository.getCaptcha() } returns flow { throw IOException("offline") }
        val viewModel = createViewModel()
        viewModel.updatePhone(VALID_PHONE)
        viewModel.sendVerificationCode()
        advanceUntilIdle()

        assertEquals(ResetPasswordCodeState(), viewModel.codeState.value)
        verify { ToastUtils.showError("offline") }
    }

    @Test
    fun `latest refresh wins and submission waits for the current captcha`() = runTest(mainDispatcherRule.dispatcher) {
        val oldResponse = CompletableDeferred<NetworkResponse<Captcha>>()
        val newResponse = CompletableDeferred<NetworkResponse<Captcha>>()
        every { authRepository.getCaptcha() } returnsMany listOf(
            flowOf(NetworkResponse(data = CAPTCHA)),
            flow { emit(oldResponse.await()) },
            flow { emit(newResponse.await()) },
        )
        val viewModel = createViewModel()
        viewModel.updatePhone(VALID_PHONE)
        viewModel.sendVerificationCode()
        advanceUntilIdle()

        viewModel.getCaptcha()
        runCurrent()
        viewModel.getCaptcha()
        runCurrent()
        viewModel.onImageCodeConfirm(IMAGE_CODE)
        verify(exactly = 0) { authRepository.getSmsCode(any()) }

        oldResponse.complete(NetworkResponse(data = CAPTCHA))
        runCurrent()
        assertTrue(viewModel.codeState.value.isLoadingCaptcha)

        newResponse.complete(NetworkResponse(data = SECOND_CAPTCHA))
        advanceUntilIdle()
        assertEquals(SECOND_CAPTCHA, viewModel.codeState.value.captcha)
        assertFalse(viewModel.codeState.value.isLoadingCaptcha)
    }

    @Test
    fun `dismiss cancels pending captcha and late response cannot reopen popup`() =
        runTest(mainDispatcherRule.dispatcher) {
            val response = CompletableDeferred<NetworkResponse<Captcha>>()
            var cancelled = false
            every { authRepository.getCaptcha() } returns flow {
                try {
                    emit(response.await())
                } finally {
                    cancelled = true
                }
            }
            val viewModel = createViewModel()
            viewModel.updatePhone(VALID_PHONE)
            viewModel.sendVerificationCode()
            runCurrent()
            viewModel.onHideImageCodePopup()
            response.complete(NetworkResponse(data = CAPTCHA))
            advanceUntilIdle()

            assertTrue(cancelled)
            assertEquals(ResetPasswordCodeState(), viewModel.codeState.value)
            verify(exactly = 0) { ToastUtils.showError(any<CharSequence>()) }
        }

    @Test
    fun `confirmed captcha sends correct parameters once and notifies on success`() =
        runTest(mainDispatcherRule.dispatcher) {
            val response = CompletableDeferred<NetworkResponse<String>>()
            every { authRepository.getSmsCode(any()) } returns flow { emit(response.await()) }
            val viewModel = createWithCaptcha()
            advanceUntilIdle()

            viewModel.onImageCodeConfirm(IMAGE_CODE)
            viewModel.onImageCodeConfirm(IMAGE_CODE)
            runCurrent()

            assertTrue(viewModel.codeState.value.isSendingCode)
            viewModel.getCaptcha()
            viewModel.onHideImageCodePopup()
            assertFalse(viewModel.codeState.value.showImageCodePopup)
            verify(exactly = 1) { authRepository.getCaptcha() }
            verify(exactly = 1) {
                authRepository.getSmsCode(
                    mapOf("phone" to VALID_PHONE, "captchaId" to CAPTCHA.captchaId, "code" to IMAGE_CODE),
                )
            }

            response.complete(NetworkResponse(data = SMS_CODE))
            advanceUntilIdle()

            verify(exactly = 1) { notifier.notify(SMS_CODE) }
            assertEquals(ResetPasswordCodeState(), viewModel.codeState.value)
        }

    @Test
    fun `SMS failure keeps popup available for correction and retry`() = runTest(mainDispatcherRule.dispatcher) {
        every { authRepository.getSmsCode(any()) } returnsMany listOf(
            flowOf(NetworkResponse(code = 400, message = "invalid captcha")),
            flowOf(NetworkResponse(data = SMS_CODE)),
        )
        val viewModel = createWithCaptcha()
        advanceUntilIdle()
        viewModel.onImageCodeConfirm(IMAGE_CODE)
        advanceUntilIdle()

        assertTrue(viewModel.codeState.value.showImageCodePopup)
        assertFalse(viewModel.codeState.value.isSendingCode)
        verify(exactly = 0) { notifier.notify(any()) }
        verify { ToastUtils.showError("invalid captcha") }

        viewModel.onImageCodeConfirm(IMAGE_CODE)
        advanceUntilIdle()
        verify(exactly = 1) { notifier.notify(SMS_CODE) }
    }

    @Test
    fun `empty SMS response does not send a notification`() = runTest(mainDispatcherRule.dispatcher) {
        every { authRepository.getSmsCode(any()) } returns flowOf(NetworkResponse<String>())
        val viewModel = createWithCaptcha()
        advanceUntilIdle()
        viewModel.onImageCodeConfirm(IMAGE_CODE)
        advanceUntilIdle()

        assertFalse(viewModel.codeState.value.isSendingCode)
        assertTrue(viewModel.codeState.value.showImageCodePopup)
        verify(exactly = 0) { notifier.notify(any()) }
    }

    @Test
    fun `invalid image code or missing popup cannot submit SMS`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createWithCaptcha()
        viewModel.onImageCodeConfirm(IMAGE_CODE)
        advanceUntilIdle()
        listOf("", "12", "12345", "12!4").forEach(viewModel::onImageCodeConfirm)
        advanceUntilIdle()

        verify(exactly = 0) { authRepository.getSmsCode(any()) }
        verify(exactly = 4) { ToastUtils.showError(R.string.invalid_verification_code) }
    }

    @Test
    fun `changing phone cancels SMS and clears previous phone verification`() = runTest(mainDispatcherRule.dispatcher) {
        val response = CompletableDeferred<NetworkResponse<String>>()
        var cancelled = false
        every { authRepository.getSmsCode(any()) } returns flow {
            try {
                emit(response.await())
            } finally {
                cancelled = true
            }
        }
        val viewModel = createWithCaptcha()
        advanceUntilIdle()
        viewModel.updateVerificationCode(SMS_CODE)
        viewModel.onImageCodeConfirm(IMAGE_CODE)
        runCurrent()
        viewModel.updatePhone(SECOND_PHONE)
        response.complete(NetworkResponse(data = SMS_CODE))
        advanceUntilIdle()

        assertTrue(cancelled)
        assertEquals(SECOND_PHONE, viewModel.phone.value)
        assertEquals("", viewModel.verificationCode.value)
        assertEquals(ResetPasswordCodeState(), viewModel.codeState.value)
        verify(exactly = 0) { notifier.notify(any()) }
    }

    @Test
    fun `clearing ViewModel cancels SMS without notification or error`() = runTest(mainDispatcherRule.dispatcher) {
        val response = CompletableDeferred<NetworkResponse<String>>()
        var cancelled = false
        every { authRepository.getSmsCode(any()) } returns flow {
            try {
                emit(response.await())
            } finally {
                cancelled = true
            }
        }
        val viewModel = createWithCaptcha()
        val store = ViewModelStore().apply { put("reset-password", viewModel) }
        advanceUntilIdle()
        viewModel.onImageCodeConfirm(IMAGE_CODE)
        runCurrent()
        store.clear()
        advanceUntilIdle()

        assertTrue(cancelled)
        assertFalse(viewModel.codeState.value.isSendingCode)
        verify(exactly = 0) { notifier.notify(any()) }
        verify(exactly = 0) { ToastUtils.showError(any<CharSequence>()) }
    }

    private fun createViewModel() = ResetPasswordViewModel(authRepository, notifier)

    private fun createWithCaptcha(): ResetPasswordViewModel {
        every { authRepository.getCaptcha() } returns flowOf(NetworkResponse(data = CAPTCHA))
        return createViewModel().apply {
            updatePhone(VALID_PHONE)
            sendVerificationCode()
        }
    }

    private companion object {
        // 仅用于测试，不请求真实后端或发送系统通知。
        const val VALID_PHONE = "13800000000"
        const val SECOND_PHONE = "13900000000"
        const val IMAGE_CODE = "1234"
        const val SMS_CODE = "5678"
        val CAPTCHA = Captcha(data = "image", captchaId = "test-captcha-1")
        val SECOND_CAPTCHA = Captcha(data = "image-2", captchaId = "test-captcha-2")
    }
}
