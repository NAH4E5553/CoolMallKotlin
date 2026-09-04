plugins {
    alias(libs.plugins.coolmall.android.feature)
}

android {
    namespace = "com.joker.coolmall.feature.cs"
}
dependencies {
    // 网络请求
    implementation(libs.okhttp3)
    // kotlin序列化
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
