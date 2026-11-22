package com.joker.coolmall.feature.goods.view

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.compose.AndroidFragment
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.idlefish.flutterboost.FlutterBoost
import com.idlefish.flutterboost.FlutterBoostDelegate
import com.idlefish.flutterboost.FlutterBoostRouteOptions
import com.idlefish.flutterboost.containers.FlutterBoostFragment
import com.joker.coolmall.feature.goods.viewmodel.GoodsDetailViewModel
import com.joker.coolmall.navigation.goods.GoodsNavigator
import com.joker.coolmall.navigation.goods.GoodsRoutes
import com.joker.coolmall.navigation.navigateBack
import io.flutter.embedding.android.RenderMode

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun FlutterGoodsDetailRoute(navKey: GoodsRoutes.Detail) {
    val model = hiltViewModel<GoodsDetailViewModel, GoodsDetailViewModel.Factory>(
        creationCallback = { it.create(navKey) },
    )
    var native by rememberSaveable(navKey.goodsId) { mutableStateOf(false) }
    if (native) {
        GoodsDetailRoute(navKey, model)
        return
    }
    val background = MaterialTheme.colorScheme.background
    val dark = background.luminance() < 0.5
    val args = remember(navKey.goodsId, dark) {
        GoodsFragmentArguments().apply {
            url("/goods/detail")
            urlParams(mapOf("goodsId" to navKey.goodsId, "dark" to dark))
        }.bundle()
    }
    val application = LocalContext.current.applicationContext as Application
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var mountFlutter by remember(navKey) { mutableStateOf(false) }
    var hasFrame by remember(navKey) { mutableStateOf(false) }
    LaunchedEffect(application, lifecycle, navKey) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!mountFlutter) {
                // Navigation3 resumes the destination after the host transition finishes.
                awaitGoodsFlutterLoader(application)
                awaitMainQueueIdle()
                startGoodsFlutterEngine(application)
                mountFlutter = true
            }
        }
    }
    Box(
        modifier = Modifier.fillMaxSize().background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (!hasFrame) CircularProgressIndicator()
        if (mountFlutter) {
            AndroidFragment<GoodsFlutterFragment>(modifier = Modifier.fillMaxSize(), arguments = args) {
                it.bind(model, onFirstFrame = { hasFrame = true }, openNative = { native = true })
            }
        }
    }
    BackHandler {
        if (hasFrame) FlutterBoost.instance().plugin.onBackPressed() else navigateBack()
    }
}

private class GoodsFragmentArguments : FlutterBoostFragment.CachedEngineFragmentBuilder() {
    fun bundle(): Bundle = createArgs()
}

/** Flutter 容器由商品的 Navigation3 Entry 持有，关闭容器时仅退出该 Entry。 */
class GoodsFlutterFragment : FlutterBoostFragment() {
    private var bridge: GoodsDetailChannel? = null
    private var hasFrame = false
    private var onFirstFrame: (() -> Unit)? = null

    override fun onAttach(context: Context) {
        startGoodsFlutterEngine(requireActivity().application)
        super.onAttach(context)
    }

    internal fun bind(model: GoodsDetailViewModel, onFirstFrame: () -> Unit, openNative: () -> Unit) {
        if (bridge == null) {
            bridge = GoodsDetailChannel(this, model, openNative)
        }
        if (hasFrame) onFirstFrame() else this.onFirstFrame = onFirstFrame
    }

    override fun onFlutterUiDisplayed() {
        super.onFlutterUiDisplayed()
        hasFrame = true
        val callback = onFirstFrame
        onFirstFrame = null
        callback?.invoke()
    }

    // The full-screen view is mounted after the entry animation. Use a directly
    // composited surface so its first buffer does not go through the host texture.
    override fun getRenderMode(): RenderMode = RenderMode.surface

    override fun onDestroyView() {
        bridge?.close()
        bridge = null
        onFirstFrame = null
        hasFrame = false
        super.onDestroyView()
    }

    override fun onFinishContainer() = navigateBack()
}

internal fun startGoodsFlutterEngine(application: Application) {
    val boost = FlutterBoost.instance()
    if (boost.engine == null) {
        boost.setup(application, GoodsBoostDelegate, null)
    }
}

private object GoodsBoostDelegate : FlutterBoostDelegate {
    override fun pushFlutterRoute(options: FlutterBoostRouteOptions) {
        if (options.pageName() == "/goods/detail") {
            (options.arguments()["goodsId"] as? Number)?.toLong()?.let(GoodsNavigator::toDetail)
        }
    }

    override fun pushNativeRoute(options: FlutterBoostRouteOptions) {
        // Native destinations are dispatched through the current detail Entry's channel.
    }
}
