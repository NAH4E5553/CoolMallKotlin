package com.joker.coolmall.feature.auth.viewmodel

import com.joker.coolmall.core.data.repository.AuthRepository
import com.joker.coolmall.core.data.state.AppState
import com.joker.coolmall.core.model.entity.Auth
import com.joker.coolmall.core.model.response.NetworkResponse
import com.joker.coolmall.core.util.storage.MMKVUtils
import com.joker.coolmall.core.util.toast.ToastUtils
import com.joker.coolmall.navigation.NavigationService
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountLoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var appState: AppState
    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
        mockkObject(MMKVUtils)
        every { MMKVUtils.getString(any(), any()) } returns ""
        every { MMKVUtils.remove(any()) } just runs
        every { MMKVUtils.putString(any(), any()) } just runs

        mockkObject(ToastUtils)
        every { ToastUtils.showSuccess(any<Int>()) } just runs

        mockkObject(NavigationService)
        every { NavigationService.navigateBack() } just runs

        appState = mockk(relaxed = true)
        authRepository = mockk()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `initialization restores phone and deletes legacy plaintext password`() {
        every { MMKVUtils.getString("saved_phone", "") } returns SAVED_PHONE

        val viewModel = createViewModel()

        assertEquals(SAVED_PHONE, viewModel.account.value)
        verify(exactly = 1) { MMKVUtils.remove("saved_password") }
    }

    @Test
    fun `successful login saves phone without persisting password`() = runTest(mainDispatcherRule.dispatcher) {
        val auth = Auth(token = "token", refreshToken = "refresh")
        val params = slot<Map<String, String>>()
        every { authRepository.loginByPassword(capture(params)) } returns
            flowOf(NetworkResponse(data = auth))
        val viewModel = createViewModel()
        viewModel.updateAccount(VALID_PHONE)
        viewModel.updatePassword(VALID_PASSWORD)

        viewModel.login()
        advanceUntilIdle()

        assertEquals(VALID_PHONE, params.captured["phone"])
        assertEquals(VALID_PASSWORD, params.captured["password"])
        verify(exactly = 1) { MMKVUtils.putString("saved_phone", VALID_PHONE) }
        verify(exactly = 0) { MMKVUtils.putString("saved_password", any()) }
        coVerify(exactly = 1) { appState.updateAuth(auth) }
        verify(exactly = 1) { appState.refreshUserInfo() }
        verify(exactly = 2) { NavigationService.navigateBack() }
    }

    private fun createViewModel() = AccountLoginViewModel(
        appState = appState,
        authRepository = authRepository,
    )

    private companion object {
        const val SAVED_PHONE = "13900000000"
        const val VALID_PHONE = "13800138000"
        const val VALID_PASSWORD = "password123"
    }
}
