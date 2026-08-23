package com.joker.coolmall.core.common.base.viewmodel

import com.joker.coolmall.core.model.response.NetworkPageData
import com.joker.coolmall.core.model.response.NetworkPageMeta
import com.joker.coolmall.core.model.response.NetworkResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BaseNetWorkListViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh response wins over cancelled initial response`() = runTest(dispatcher) {
        val viewModel = ControlledListViewModel()

        viewModel.startInitialLoad()
        val initialRequest = viewModel.requests.single()

        viewModel.onRefresh()
        val refreshRequest = viewModel.requests.last()
        refreshRequest.response.complete(pageResponse(page = 1, ids = listOf(20, 21)))
        advanceUntilIdle()

        initialRequest.response.complete(pageResponse(page = 1, ids = listOf(10, 11)))
        advanceUntilIdle()

        assertEquals(listOf(20, 21), viewModel.listData.value)
    }

    @Test
    fun `cancelled load more cannot append or advance page`() = runTest(dispatcher) {
        val viewModel = ControlledListViewModel()

        viewModel.startInitialLoad()
        viewModel.requests.single().response.complete(
            pageResponse(page = 1, ids = listOf(10), total = 30),
        )
        advanceUntilIdle()

        viewModel.onLoadMore()
        val cancelledPageTwo = viewModel.requests.last()
        assertEquals(2, cancelledPageTwo.page)

        viewModel.onRefresh()
        val refreshRequest = viewModel.requests.last()
        refreshRequest.response.complete(
            pageResponse(page = 1, ids = listOf(100), total = 30),
        )
        advanceUntilIdle()

        cancelledPageTwo.response.complete(
            pageResponse(page = 2, ids = listOf(20), total = 30),
        )
        advanceUntilIdle()

        assertEquals(listOf(100), viewModel.listData.value)

        viewModel.onLoadMore()
        assertEquals(2, viewModel.requests.last().page)
    }

    private fun pageResponse(page: Int, ids: List<Int>, total: Int = ids.size): NetworkResponse<NetworkPageData<Int>> =
        NetworkResponse(
            data = NetworkPageData(
                list = ids,
                pagination = NetworkPageMeta(
                    total = total,
                    size = 10,
                    page = page,
                ),
            ),
        )

    private class ControlledListViewModel : BaseNetWorkListViewModel<Int>() {
        val requests = mutableListOf<PendingRequest>()

        fun startInitialLoad() {
            initLoad()
        }

        override fun requestListData(page: Int, pageSize: Int): Flow<NetworkResponse<NetworkPageData<Int>>> {
            val request = PendingRequest(page)
            requests += request
            return flow { emit(request.response.await()) }
        }
    }

    private data class PendingRequest(
        val page: Int,
        val response: CompletableDeferred<NetworkResponse<NetworkPageData<Int>>> =
            CompletableDeferred(),
    )
}
