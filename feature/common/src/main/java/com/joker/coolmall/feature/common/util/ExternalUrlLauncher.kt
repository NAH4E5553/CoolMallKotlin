package com.joker.coolmall.feature.common.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.joker.coolmall.core.util.log.LogUtils
import com.joker.coolmall.core.util.toast.ToastUtils
import com.joker.coolmall.feature.common.R

/**
 * 使用系统应用打开经过校验的 URL。
 */
internal fun openExternalUrl(context: Context, url: String): Boolean {
    if (WebUrlPolicy.classify(url) is WebUrlDestination.Blocked) {
        ToastUtils.showWarning(R.string.web_url_blocked)
        return false
    }

    return try {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
        true
    } catch (exception: ActivityNotFoundException) {
        LogUtils.e(TAG, "No application can open the requested URI", exception)
        ToastUtils.showError(R.string.web_open_failed)
        false
    } catch (exception: SecurityException) {
        LogUtils.e(TAG, "The requested URI was rejected by the system", exception)
        ToastUtils.showError(R.string.web_open_failed)
        false
    }
}

private const val TAG = "ExternalUrlLauncher"
