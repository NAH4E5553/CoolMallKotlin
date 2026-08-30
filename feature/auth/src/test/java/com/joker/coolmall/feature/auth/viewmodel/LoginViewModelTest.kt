package com.joker.coolmall.feature.auth.viewmodel

import android.app.Activity
import com.joker.coolmall.core.common.manager.QQLoginManager
import com.joker.coolmall.core.common.manager.QQLoginResult
import com.joker.coolmall.core.data.repository.AuthRepository
import com.joker.coolmall.core.data.state.AppState
import com.joker.coolmall.core.model.entity.Auth
import com.joker.coolmall.core.model.response.NetworkResponse
import com.joker.coolmall.core.util.toast.ToastUtils
import com.joker.coolmall.navigation.NavigationService
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var appState: AppState
    private lateinit var authRepository: AuthRepository
    private lateinit var qqLoginManager: QQLoginManager
    private lateinit var loginResult: MutableStateFlow<QQLoginResult?>
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        loginResult = MutableStateFlow(null)
        qqLoginManager = mockk()
        every { qqLoginManager.loginResult } returns loginResult
        every { qqLoginManager.clearLoginResult() } answers { loginResult.value = null }
        every { qqLoginManager.startQQLogin(any()) } just runs

        mockkObject(QQLoginManager.Companion)
        every { QQLoginManager.getInstance() } returns qqLoginManager

        mockkObject(ToastUtils)
        every { ToastUtils.showSuccess(any<Int>()) } just runs
        every { ToastUtils.showError(any<Int>()) } just runs
        every { ToastUtils.showWarning(any<Int>()) } just runs

        mockkObject(NavigationService)
        every { NavigationService.navigateBack() } just runs

        appState = mockk(relaxed = true)
        authRepository = mockk()
        activity = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `repeated QQ login click starts SDK only once while login is active`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()
            runCurrent()

            viewModel.startQQLogin(activity)
            viewModel.startQQLogin(activity)

            verify(exactly = 1) { qqLoginManager.startQQLogin(activity) }
            assertTrue(viewModel.isQqLoginInProgress.value)
        }

    @Test
    fun `QQ success calls backend once and stale result is ignored`() = runTest(mainDispatcherRule.dispatcher) {
        val auth = Auth(token = "token", refreshToken = "refresh")
        every { authRepository.loginByQqApp(any()) } returns
            flowOf(NetworkResponse(data = auth))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.startQQLogin(activity)
        loginResult.value = QQLoginResult.Success("access", "open-id")
        advanceUntilIdle()

        verify(exactly = 1) { authRepository.loginByQqApp(any()) }
        coVerify(exactly = 1) { appState.updateAuth(auth) }
        assertFalse(viewModel.isQqLoginInProgress.value)

        loginResult.value = QQLoginResult.Success("stale-access", "stale-open-id")
        advanceUntilIdle()

        verify(exactly = 1) { authRepository.loginByQqApp(any()) }
    }

    private fun createViewModel() = LoginViewModel(
        appState = appState,
        authRepository = authRepository,
    )
}
