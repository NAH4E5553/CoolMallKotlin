package com.joker.coolmall.feature.goods.view

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
import io.flutter.FlutterInjector
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Prepares the shared goods engine after the home destination has settled. */
@Composable
fun GoodsDetailWarmup() {
    val application = LocalContext.current.applicationContext as Application
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(application, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (FlutterBoost.instance().engine == null) {
                try {
                    awaitGoodsFlutterLoader(application)
                    awaitMainQueueIdle()
                    startGoodsFlutterEngine(application)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w("GoodsDetailWarmup", "Flutter warmup failed", error)
                }
            }
        }
    }
}

internal suspend fun awaitGoodsFlutterLoader(application: Application) {
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
