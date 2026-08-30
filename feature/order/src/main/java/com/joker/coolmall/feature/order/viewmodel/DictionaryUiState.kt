package com.joker.coolmall.feature.order.viewmodel

import com.joker.coolmall.core.common.base.state.BaseNetWorkUiState

internal fun <T> requiredDictionaryUiState(items: List<T>?): BaseNetWorkUiState<List<T>> = when {
    items == null -> BaseNetWorkUiState.Error()
    items.isEmpty() -> BaseNetWorkUiState.Empty
    else -> BaseNetWorkUiState.Success(items)
}
