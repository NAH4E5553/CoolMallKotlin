package com.joker.coolmall.core.flutter

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.MessageQueue
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.idlefish.flutterboost.FlutterBoost
import com.idlefish.flutterboost.FlutterBoostDelegate
import com.idlefish.flutterboost.FlutterBoostRouteOptions
import com.joker.coolmall.navigation.goods.GoodsNavigator
import io.flutter.FlutterInjector
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Prepares the shared engine after the home destination has settled. */
@Suppress("ktlint:standard:function-naming")
@Composable
fun FlutterWarmup() {
    val application = LocalContext.current.applicationContext as Application
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(application, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (FlutterBoost.instance().engine == null) {
                try {
                    awaitFlutterLoader(application)
                    awaitMainQueueIdle()
                    startFlutterEngine(application)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w("FlutterWarmup", "Flutter warmup failed", error)
                }
            }
        }
    }
}

internal suspend fun awaitFlutterLoader(application: Application) {
    if (FlutterBoost.instance().engine != null) return
    val loader = FlutterInjector.instance().flutterLoader()
    loader.startInitialization(application)
    suspendCancellableCoroutine { continuation ->
        loader.ensureInitializationCompleteAsync(application, null, Handler(Looper.getMainLooper())) {
            continuation.resume(Unit)
        }
    }
}

internal suspend fun awaitMainQueueIdle() = suspendCancellableCoroutine { continuation ->
    val queue = Looper.myQueue()
    val callback = MessageQueue.IdleHandler {
        continuation.resume(Unit)
        false
    }
    queue.addIdleHandler(callback)
    continuation.invokeOnCancellation { queue.removeIdleHandler(callback) }
}

internal fun startFlutterEngine(application: Application) {
    val boost = FlutterBoost.instance()
    if (boost.engine == null) {
        boost.setup(application, CoolMallBoostDelegate, null)
    }
}

private object CoolMallBoostDelegate : FlutterBoostDelegate {
    override fun pushFlutterRoute(options: FlutterBoostRouteOptions) {
        if (options.pageName() == "/goods/detail") {
            (options.arguments()["goodsId"] as? Number)?.toLong()?.let(GoodsNavigator::toDetail)
        }
    }

    override fun pushNativeRoute(options: FlutterBoostRouteOptions) {
        // Native destinations are dispatched through the owning container’s channel.
    }
}
