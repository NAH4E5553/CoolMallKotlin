package com.joker.coolmall.feature.user.viewmodel

import com.joker.coolmall.core.data.repository.AddressRepository
import com.joker.coolmall.core.model.common.Id
import com.joker.coolmall.core.model.entity.Address
import com.joker.coolmall.core.model.response.NetworkResponse
import com.joker.coolmall.core.navigation.user.AddressChangedResultKey
import com.joker.coolmall.core.navigation.user.UserRoutes
import com.joker.coolmall.core.util.log.LogUtils
import com.joker.coolmall.core.util.toast.ToastUtils
import com.joker.coolmall.navigation.NavigationService
import com.joker.coolmall.navigation.RefreshResult
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class AddressDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var addressRepository: AddressRepository

    @Before
    fun setUp() {
        addressRepository = mockk()

        mockkObject(LogUtils)
        every { LogUtils.e(any<String>()) } just runs

        mockkObject(ToastUtils)
        every { ToastUtils.showError(any<CharSequence>()) } just runs

        mockkObject(NavigationService)
        every {
            NavigationService.popBackStackWithResult(
                AddressChangedResultKey,
                any<RefreshResult>(),
            )
        } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `form is valid only when every required field is valid`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createAddViewModel()
        val collectJob = observeFormValidity(viewModel)

        fillValidForm(viewModel)
        runCurrent()
        assertTrue(viewModel.isFormValid.value)

        viewModel.updateContactName("   ")
        runCurrent()
        assertFalse(viewModel.isFormValid.value)
        viewModel.updateContactName(CONTACT)

        viewModel.updatePhone("12345")
        runCurrent()
        assertFalse(viewModel.isFormValid.value)
        viewModel.updatePhone(PHONE)

        viewModel.updateRegion("", CITY, DISTRICT)
        runCurrent()
        assertFalse(viewModel.isFormValid.value)
        viewModel.updateRegion(PROVINCE, "", DISTRICT)
        runCurrent()
        assertFalse(viewModel.isFormValid.value)
        viewModel.updateRegion(PROVINCE, CITY, "")
        runCurrent()
        assertFalse(viewModel.isFormValid.value)
        viewModel.updateRegion(PROVINCE, CITY, DISTRICT)

        viewModel.updateDetailAddress("  ")
        runCurrent()
        assertFalse(viewModel.isFormValid.value)
        viewModel.updateDetailAddress(DETAIL_ADDRESS)
        runCurrent()
        assertTrue(viewModel.isFormValid.value)

        collectJob.cancel()
    }

    @Test
    fun `save revalidates form when UI guard is bypassed`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createAddViewModel()
        fillValidForm(viewModel)
        viewModel.updatePhone("not-a-phone")

        viewModel.saveAddress()
        runCurrent()

        verify(exactly = 0) { addressRepository.addAddress(any()) }
        assertFalse(viewModel.isSaving.value)
        verify(exactly = 0) {
            NavigationService.popBackStackWithResult(
                AddressChangedResultKey,
                any<RefreshResult>(),
            )
        }
    }

    @Test
    fun `duplicate add saves only once and submits normalized fields`() = runTest(mainDispatcherRule.dispatcher) {
        val response = CompletableDeferred<NetworkResponse<Id>>()
        val addressSlot = slot<Address>()
        every { addressRepository.addAddress(capture(addressSlot)) } returns
            flow { emit(response.await()) }
        val viewModel = createAddViewModel()
        fillValidForm(viewModel, withSurroundingWhitespace = true)
        viewModel.updateIsDefaultAddress(true)

        viewModel.saveAddress()
        viewModel.saveAddress()
        runCurrent()

        assertTrue(viewModel.isSaving.value)
        verify(exactly = 1) { addressRepository.addAddress(any()) }
        assertEquals(
            Address(
                contact = CONTACT,
                phone = PHONE,
                province = PROVINCE,
                city = CITY,
                district = DISTRICT,
                address = DETAIL_ADDRESS,
                isDefault = true,
            ),
            addressSlot.captured,
        )

        response.complete(NetworkResponse(data = Id(id = NEW_ADDRESS_ID)))
        advanceUntilIdle()

        assertFalse(viewModel.isSaving.value)
        verify(exactly = 1) {
            NavigationService.popBackStackWithResult(
                AddressChangedResultKey,
                RefreshResult(refresh = true),
            )
        }
    }

    @Test
    fun `edit loads existing address and updates the same id`() = runTest(mainDispatcherRule.dispatcher) {
        val existingAddress = validAddress(id = ADDRESS_ID)
        every { addressRepository.getAddressInfo(ADDRESS_ID) } returns
            flowOf(NetworkResponse(data = existingAddress))
        every { addressRepository.updateAddress(any()) } returns flowOf(NetworkResponse())
        val addressSlot = slot<Address>()
        val viewModel = createEditViewModel()
        val collectJob = observeFormValidity(viewModel)

        advanceUntilIdle()

        assertEquals(CONTACT, viewModel.contactName.value)
        assertEquals(PHONE, viewModel.phone.value)
        assertTrue(viewModel.isFormValid.value)

        viewModel.saveAddress()
        viewModel.saveAddress()
        advanceUntilIdle()

        verify(exactly = 1) { addressRepository.updateAddress(capture(addressSlot)) }
        verify(exactly = 0) { addressRepository.addAddress(any()) }
        assertEquals(existingAddress, addressSlot.captured)
        assertFalse(viewModel.isSaving.value)
        verify(exactly = 1) {
            NavigationService.popBackStackWithResult(
                AddressChangedResultKey,
                RefreshResult(refresh = true),
            )
        }

        collectJob.cancel()
    }

    @Test
    fun `failed save restores state and allows retry without navigating early`() =
        runTest(mainDispatcherRule.dispatcher) {
            every { addressRepository.addAddress(any()) } returns
                flowOf(NetworkResponse(code = 400, message = "save failed")) andThen
                flowOf(NetworkResponse(data = Id(id = NEW_ADDRESS_ID)))
            val viewModel = createAddViewModel()
            fillValidForm(viewModel)

            viewModel.saveAddress()
            assertTrue(viewModel.isSaving.value)
            advanceUntilIdle()

            assertFalse(viewModel.isSaving.value)
            verify(exactly = 1) { ToastUtils.showError("save failed") }
            verify(exactly = 0) {
                NavigationService.popBackStackWithResult(
                    AddressChangedResultKey,
                    any<RefreshResult>(),
                )
            }

            viewModel.saveAddress()
            advanceUntilIdle()

            verify(exactly = 2) { addressRepository.addAddress(any()) }
            assertFalse(viewModel.isSaving.value)
            verify(exactly = 1) {
                NavigationService.popBackStackWithResult(
                    AddressChangedResultKey,
                    RefreshResult(refresh = true),
                )
            }
        }

    private fun createAddViewModel() = AddressDetailViewModel(
        navKey = UserRoutes.AddressDetail(),
        addressRepository = addressRepository,
    )

    private fun createEditViewModel() = AddressDetailViewModel(
        navKey = UserRoutes.AddressDetail(isEditMode = true, addressId = ADDRESS_ID),
        addressRepository = addressRepository,
    )

    private fun fillValidForm(viewModel: AddressDetailViewModel, withSurroundingWhitespace: Boolean = false) {
        val padding = if (withSurroundingWhitespace) "  " else ""
        viewModel.updateContactName("$padding$CONTACT$padding")
        viewModel.updatePhone("$padding$PHONE$padding")
        viewModel.updateRegion(
            province = "$padding$PROVINCE$padding",
            city = "$padding$CITY$padding",
            district = "$padding$DISTRICT$padding",
        )
        viewModel.updateDetailAddress("$padding$DETAIL_ADDRESS$padding")
    }

    private fun TestScope.observeFormValidity(viewModel: AddressDetailViewModel): Job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.isFormValid.collect()
        }

    private fun validAddress(id: Long) = Address(
        id = id,
        contact = CONTACT,
        phone = PHONE,
        province = PROVINCE,
        city = CITY,
        district = DISTRICT,
        address = DETAIL_ADDRESS,
        isDefault = true,
    )

    private companion object {
        const val ADDRESS_ID = 11L
        const val NEW_ADDRESS_ID = 12L
        const val CONTACT = "张三"
        const val PHONE = "13800138000"
        const val PROVINCE = "广东省"
        const val CITY = "深圳市"
        const val DISTRICT = "南山区"
        const val DETAIL_ADDRESS = "科技园 1 号"
    }
}
