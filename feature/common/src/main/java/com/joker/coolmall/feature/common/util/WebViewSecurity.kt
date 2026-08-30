package com.joker.coolmall.feature.common.util

import android.content.Context
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.joker.coolmall.core.util.toast.ToastUtils
import com.joker.coolmall.feature.common.R

/**
 * 为 WebView 应用最小权限设置。
 */
internal fun WebSettings.applySecureDefaults(javaScriptAllowed: Boolean = false) {
    javaScriptEnabled = javaScriptAllowed
    domStorageEnabled = javaScriptAllowed
    javaScriptCanOpenWindowsAutomatically = false
    setSupportMultipleWindows(false)
    allowFileAccess = false
    allowContentAccess = false
    @Suppress("DEPRECATION")
    allowFileAccessFromFileURLs = false
    @Suppress("DEPRECATION")
    allowUniversalAccessFromFileURLs = false
    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    mediaPlaybackRequiresUserGesture = true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        safeBrowsingEnabled = true
    }
}

/**
 * 对主框架导航使用与初始 URL 相同的安全策略。
 */
internal class SecureWebViewClient(private val context: Context) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        handleNavigation(view = view, url = request.url.toString())

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
        handleNavigation(view = view, url = url)

    private fun handleNavigation(view: WebView, url: String): Boolean = when (val target = WebUrlPolicy.classify(url)) {
        is WebUrlDestination.InApp -> {
            view.settings.applySecureDefaults(
                javaScriptAllowed = target.javaScriptEnabled,
            )
            false
        }

        WebUrlDestination.External -> {
            openExternalUrl(context = context, url = url)
            true
        }

        WebUrlDestination.Blocked -> {
            ToastUtils.showWarning(R.string.web_url_blocked)
            true
        }
    }
}

/**
 * 永久离开组合时释放 WebView 持有的页面和 Client。
 */
internal fun WebView.releaseFromComposition() {
    stopLoading()
    webChromeClient = null
    webViewClient = WebViewClient()
    removeAllViews()
    destroy()
}
