package com.joker.coolmall.core.flutter

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.idlefish.flutterboost.FlutterBoost
import com.idlefish.flutterboost.containers.FlutterBoostFragment
import io.flutter.embedding.android.FlutterTextureView
import io.flutter.plugin.common.BinaryMessenger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope

/** Transport and coroutine lifetime for a single Flutter container view. */
class FlutterContainerConnection internal constructor(
    val id: String,
    val messenger: BinaryMessenger,
    val scope: CoroutineScope,
    val close: () -> Unit,
)

/** Mounts a FlutterBoost container in the current native navigation entry. */
@Suppress("ktlint:standard:function-naming")
@Composable
fun FlutterPage(
    route: String,
    arguments: Map<String, Any>,
    onClose: () -> Unit,
    onFailure: () -> Unit,
    connect: (FlutterContainerConnection) -> AutoCloseable,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.background
    val dark = background.luminance() < 0.5
    val bundle = remember(route, arguments, dark) {
        PageArguments().apply {
            url(route)
            urlParams(arguments + ("dark" to dark))
        }.bundle()
    }
    val application = LocalContext.current.applicationContext as Application
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val close by rememberUpdatedState(onClose)
    val fail by rememberUpdatedState(onFailure)
    var mountFlutter by remember(route, arguments) { mutableStateOf(false) }
    var hasFrame by remember(route, arguments) { mutableStateOf(false) }
    var closing by remember(route, arguments) { mutableStateOf(false) }
    fun requestClose() {
        if (!closing) {
            closing = true
            close()
        }
    }
    LaunchedEffect(application, lifecycle, route, arguments) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!mountFlutter) {
                try {
                    // Navigation3 resumes the entry after the host transition finishes.
                    awaitFlutterLoader(application)
                    awaitMainQueueIdle()
                    startFlutterEngine(application)
                    mountFlutter = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w("FlutterPage", "Could not start the shared Flutter engine", error)
                    fail()
                }
            }
        }
    }
    Box(
        modifier = modifier.fillMaxSize().background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (!hasFrame) CircularProgressIndicator()
        if (mountFlutter) {
            AndroidFragment<FlutterContainerFragment>(
                modifier = Modifier.fillMaxSize(),
                arguments = bundle,
            ) { fragment ->
                fragment.bind(connect, onFirstFrame = { hasFrame = true }, close = ::requestClose)
            }
        }
    }

    BackHandler {
        if (hasFrame) FlutterBoost.instance().plugin.onBackPressed() else requestClose()
    }
}

private class PageArguments : FlutterBoostFragment.CachedEngineFragmentBuilder() {
    fun bundle(): Bundle = createArgs()
}

/** Fragment Manager recreates the container when the Android host is restored. */
class FlutterContainerFragment : FlutterBoostFragment() {
    private var bridge: AutoCloseable? = null
    private var hasFrame = false
    private var onFirstFrame: (() -> Unit)? = null
    private var close: (() -> Unit)? = null

    override fun onAttach(context: Context) {
        startFlutterEngine(requireActivity().application)
        super.onAttach(context)
    }

    // FlutterBoost attaches the shared engine to the active container's Activity.
    override fun shouldAttachEngineToActivity(): Boolean = false

    internal fun bind(
        connect: (FlutterContainerConnection) -> AutoCloseable,
        onFirstFrame: () -> Unit,
        close: () -> Unit,
    ) {
        this.close = close
        if (bridge == null) {
            bridge = connect(
                FlutterContainerConnection(
                    id = uniqueId,
                    messenger = FlutterBoost.instance().engine.dartExecutor.binaryMessenger,
                    scope = viewLifecycleOwner.lifecycleScope,
                    close = { this.close?.invoke() },
                ),
            )
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

    override fun onFlutterTextureViewCreated(flutterTextureView: FlutterTextureView) {
        super.onFlutterTextureViewCreated(flutterTextureView)
        flutterTextureView.isOpaque = false
    }

    override fun onDestroyView() {
        bridge?.close()
        bridge = null
        onFirstFrame = null
        close = null
        hasFrame = false
        super.onDestroyView()
    }

    override fun onFinishContainer() {
        close?.invoke()
    }
}
