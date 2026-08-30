package com.joker.coolmall.feature.feedback.viewmodel

import com.joker.coolmall.core.common.base.state.BaseNetWorkUiState
import com.joker.coolmall.core.model.entity.DictItem
import com.joker.coolmall.core.model.response.DictDataResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FeedbackTypeUiStateTest {
    @Test
    fun `missing feedback type is an error`() {
        val result = feedbackTypeUiState(DictDataResponse(feedbackType = null))

        assertEquals(BaseNetWorkUiState.Error(), result)
    }

    @Test
    fun `empty feedback type is an empty state`() {
        val result = feedbackTypeUiState(DictDataResponse(feedbackType = emptyList()))

        assertSame(BaseNetWorkUiState.Empty, result)
    }

    @Test
    fun `available feedback types are successful`() {
        val response = DictDataResponse(feedbackType = listOf(DictItem(id = 1)))

        val result = feedbackTypeUiState(response)

        assertEquals(BaseNetWorkUiState.Success(response), result)
    }
}
