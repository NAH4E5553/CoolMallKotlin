plugins {
    alias(libs.plugins.coolmall.android.feature)
}

android {
    namespace = "com.joker.coolmall.feature.order"
}

dependencies {
    // 支付宝支付
    implementation(libs.alipaysdk.android)

    // 测试中用于序列化订单缓存数据
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
