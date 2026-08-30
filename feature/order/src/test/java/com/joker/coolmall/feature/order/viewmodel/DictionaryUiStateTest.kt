package com.joker.coolmall.feature.order.viewmodel

import com.joker.coolmall.core.common.base.state.BaseNetWorkUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DictionaryUiStateTest {
    @Test
    fun `missing required dictionary is an error`() {
        val result = requiredDictionaryUiState<String>(null)

        assertEquals(BaseNetWorkUiState.Error(), result)
    }

    @Test
    fun `empty required dictionary is an empty state`() {
        val result = requiredDictionaryUiState(emptyList<String>())

        assertSame(BaseNetWorkUiState.Empty, result)
    }

    @Test
    fun `required dictionary with options is successful`() {
        val options = listOf("reason")

        val result = requiredDictionaryUiState(options)

        assertEquals(BaseNetWorkUiState.Success(options), result)
    }
}
