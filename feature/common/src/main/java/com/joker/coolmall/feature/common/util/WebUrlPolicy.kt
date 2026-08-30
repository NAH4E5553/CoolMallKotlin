package com.joker.coolmall.feature.common.util

import java.net.URI
import java.util.Locale

/**
 * Web URL 的受信任目标。
 */
internal sealed interface WebUrlDestination {
    /**
     * 可在应用内 WebView 加载的 HTTPS 页面。
     */
    data class InApp(val javaScriptEnabled: Boolean) : WebUrlDestination

    /**
     * 仅交给系统应用处理的 URL。
     */
    data object External : WebUrlDestination

    /**
     * 不允许加载或交给系统处理的 URL。
     */
    data object Blocked : WebUrlDestination
}

/**
 * 统一校验 WebView 和系统 Intent 使用的 URL。
 */
internal object WebUrlPolicy {
    private val inAppHosts = setOf(
        "airbnb.io",
        "coil-kt.github.io",
        "coolmall.apifox.cn",
        "developer.android.com",
        "gitee.com",
        "github.com",
        "opendocs.alipay.com",
        "square.github.io",
        "www.pgyer.com",
    )

    // 默认不向网页授予脚本执行能力。后续只能在验证确有业务需要后添加精确 Host。
    private val javaScriptHosts = emptySet<String>()

    /**
     * 根据 Scheme、Host、用户信息和端口决定 URL 的处理方式。
     */
    fun classify(rawUrl: String): WebUrlDestination {
        val uri = parse(rawUrl) ?: return WebUrlDestination.Blocked
        return when (uri.scheme?.lowercase(Locale.ROOT)) {
            HTTPS_SCHEME -> classifyHttps(uri)

            TEL_SCHEME,
            MAILTO_SCHEME,
            -> if (uri.rawSchemeSpecificPart.isNullOrBlank()) {
                WebUrlDestination.Blocked
            } else {
                WebUrlDestination.External
            }

            else -> WebUrlDestination.Blocked
        }
    }

    private fun classifyHttps(uri: URI): WebUrlDestination {
        val host = uri.host?.lowercase(Locale.ROOT) ?: return WebUrlDestination.Blocked
        if (uri.rawUserInfo != null || uri.port !in setOf(NO_PORT, HTTPS_PORT)) {
            return WebUrlDestination.Blocked
        }
        return if (host in inAppHosts) {
            WebUrlDestination.InApp(javaScriptEnabled = host in javaScriptHosts)
        } else {
            WebUrlDestination.External
        }
    }

    private fun parse(rawUrl: String): URI? {
        val value = rawUrl.trim()
        if (value.isEmpty()) return null
        return runCatching { URI(value) }.getOrNull()
    }

    private const val HTTPS_SCHEME = "https"
    private const val TEL_SCHEME = "tel"
    private const val MAILTO_SCHEME = "mailto"
    private const val NO_PORT = -1
    private const val HTTPS_PORT = 443
}
