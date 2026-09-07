package com.joker.coolmall.feature.auth.notification

import android.content.Context
import com.joker.coolmall.core.util.notification.NotificationUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** 封装验证码通知的平台调用，避免 ViewModel 持有 Context。 */
class VerificationCodeNotifier @Inject constructor(@param:ApplicationContext private val context: Context) {
    /** 使用现有通知渠道展示验证码，不记录或持久化内容。 */
    fun notify(code: String) {
        NotificationUtil.sendVerificationCodeNotification(context = context, code = code)
    }
}
