package com.joker.coolmall.feature.feedback.viewmodel

import com.joker.coolmall.core.common.base.state.BaseNetWorkUiState
import com.joker.coolmall.core.model.response.DictDataResponse

internal fun feedbackTypeUiState(data: DictDataResponse): BaseNetWorkUiState<DictDataResponse> {
    val feedbackTypes = data.feedbackType
    return when {
        feedbackTypes == null -> BaseNetWorkUiState.Error()
        feedbackTypes.isEmpty() -> BaseNetWorkUiState.Empty
        else -> BaseNetWorkUiState.Success(data)
    }
}
