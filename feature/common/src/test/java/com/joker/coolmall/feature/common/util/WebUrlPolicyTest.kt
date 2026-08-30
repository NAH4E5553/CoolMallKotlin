package com.joker.coolmall.feature.common.util

import org.junit.Assert.assertEquals
import org.junit.Test

class WebUrlPolicyTest {
    @Test
    fun `known https hosts open in app without javascript`() {
        val urls = listOf(
            "https://airbnb.io/lottie/",
            "https://coil-kt.github.io/coil/compose/",
            "https://coolmall.apifox.cn",
            "https://developer.android.com/jetpack/compose",
            "https://github.com/Joker-x-dev/CoolMallKotlin",
            "https://opendocs.alipay.com/open/54/104509",
            "https://square.github.io/okhttp/",
            "https://www.pgyer.com/CoolMallKotlinProdRelease",
        )

        urls.forEach { url ->
            assertEquals(
                WebUrlDestination.InApp(javaScriptEnabled = false),
                WebUrlPolicy.classify(url),
            )
        }
    }

    @Test
    fun `gitee opens in app with javascript for webview verification`() {
        assertEquals(
            WebUrlDestination.InApp(javaScriptEnabled = true),
            WebUrlPolicy.classify("https://gitee.com/Joker-x-dev/CoolMallKotlin"),
        )
    }

    @Test
    fun `unknown valid https host opens externally`() {
        assertEquals(
            WebUrlDestination.External,
            WebUrlPolicy.classify("https://example.com/profile"),
        )
    }

    @Test
    fun `host suffix and prefix impersonation open externally`() {
        val urls = listOf(
            "https://github.com.evil.example/path",
            "https://evilgithub.com/path",
        )

        urls.forEach { url ->
            assertEquals(WebUrlDestination.External, WebUrlPolicy.classify(url))
        }
    }

    @Test
    fun `userinfo impersonation is blocked`() {
        assertEquals(
            WebUrlDestination.Blocked,
            WebUrlPolicy.classify("https://github.com@evil.example/path"),
        )
    }

    @Test
    fun `explicit standard https port is allowed`() {
        assertEquals(
            WebUrlDestination.InApp(javaScriptEnabled = false),
            WebUrlPolicy.classify("https://github.com:443/Joker-x-dev"),
        )
    }

    @Test
    fun `nonstandard https port is blocked`() {
        assertEquals(
            WebUrlDestination.Blocked,
            WebUrlPolicy.classify("https://github.com:8443/Joker-x-dev"),
        )
    }

    @Test
    fun `telephone and email schemes open externally`() {
        val urls = listOf(
            "tel:+1234567890",
            "mailto:support@example.com",
        )

        urls.forEach { url ->
            assertEquals(WebUrlDestination.External, WebUrlPolicy.classify(url))
        }
    }

    @Test
    fun `dangerous and unsupported schemes are blocked`() {
        val urls = listOf(
            "http://github.com/Joker-x-dev",
            "javascript:alert(1)",
            "file:///data/data/app/private.txt",
            "content://com.example.provider/private",
            "data:text/html,hello",
            "intent://scan/#Intent;scheme=zxing;end",
            "geo:25.0,121.5",
        )

        urls.forEach { url ->
            assertEquals(WebUrlDestination.Blocked, WebUrlPolicy.classify(url))
        }
    }

    @Test
    fun `blank malformed and hostless urls are blocked`() {
        val urls = listOf(
            "",
            "   ",
            "not a url",
            "https:///missing-host",
            "https://",
        )

        urls.forEach { url ->
            assertEquals(WebUrlDestination.Blocked, WebUrlPolicy.classify(url))
        }
    }
}
