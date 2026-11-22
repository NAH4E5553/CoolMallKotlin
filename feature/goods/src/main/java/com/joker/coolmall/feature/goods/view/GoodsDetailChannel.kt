package com.joker.coolmall.feature.goods.view

import androidx.lifecycle.lifecycleScope
import com.idlefish.flutterboost.FlutterBoost
import com.joker.coolmall.core.common.base.state.BaseNetWorkUiState
import com.joker.coolmall.core.model.entity.GoodsSpec
import com.joker.coolmall.core.model.entity.SelectedGoods
import com.joker.coolmall.feature.goods.model.presentation
import com.joker.coolmall.feature.goods.viewmodel.GoodsDetailViewModel
import com.joker.coolmall.navigation.cs.CsNavigator
import com.joker.coolmall.navigation.goods.GoodsNavigator
import com.joker.coolmall.navigation.main.MainNavigator
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Each container has a separate messenger address, including two visits to the same product. */
internal class GoodsDetailChannel(
    fragment: GoodsFlutterFragment,
    private val model: GoodsDetailViewModel,
    private val openNative: () -> Unit,
) {
    private val scope = fragment.viewLifecycleOwner.lifecycleScope
    private val channel = MethodChannel(
        FlutterBoost.instance().engine.dartExecutor.binaryMessenger,
        "coolmall/goods/${fragment.uniqueId}",
    )

    init {
        channel.setMethodCallHandler { call, result ->
            scope.launch {
                try {
                    val response = when (call.method) {
                        "detail", "retry" -> {
                            if (call.method == "retry") model.retryRequest()
                            model.uiState.loaded()?.presentation(model.selectedSpec.value)
                        }

                        "specs" -> {
                            model.loadGoodsSpecs()
                            model.specsModalUiState.loaded().orEmpty().map(GoodsSpec::presentation)
                        }

                        "native" -> openNative()

                        "cart" -> MainNavigator.toCart(showBackIcon = true)

                        "support" -> CsNavigator.toChat()

                        "comments" -> GoodsNavigator.toComment(model.getSuccessData().goodsInfo.id)

                        "coupon" -> model.receiveCoupon(model.getSuccessData().coupon.first { it.id == call.id() })

                        "select", "add", "buy" -> selectGoods(call)

                        else -> {
                            result.notImplemented()
                            return@launch
                        }
                    }
                    result.success(response.takeUnless { it == Unit })
                } catch (cancelled: CancellationException) {
                    result.error("closed", null, null)
                    throw cancelled
                } catch (_: Exception) {
                    result.error("unavailable", null, null)
                }
            }
        }
    }

    fun close() = channel.setMethodCallHandler(null)

    private fun selectGoods(call: MethodCall) {
        val goods = model.getSuccessData().goodsInfo
        val specs = (model.specsModalUiState.value as? BaseNetWorkUiState.Success)?.data.orEmpty()
        val spec = specs.first { it.id == call.id() && it.goodsId == goods.id }
        val count = requireNotNull(call.argument<Int>("count"))
        require(count in 1..spec.stock)
        if (model.selectedSpec.value?.id != spec.id) model.selectSpec(spec)
        if (call.method == "select") return
        val selection = SelectedGoods(goodsId = goods.id, goodsInfo = goods, spec = spec, count = count)
        if (call.method == "add") model.addToCart(selection) else model.buyNow(selection)
    }

    private fun MethodCall.id() = requireNotNull(argument<Number>("id")).toLong()

    private suspend fun <T> StateFlow<BaseNetWorkUiState<T>>.loaded(): T? =
        when (val state = first { it !is BaseNetWorkUiState.Loading }) {
            is BaseNetWorkUiState.Success -> state.data
            BaseNetWorkUiState.Empty -> null
            else -> error("Request failed")
        }
}
