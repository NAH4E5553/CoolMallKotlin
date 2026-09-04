plugins {
    alias(libs.plugins.coolmall.android.feature)
}

android {
    namespace = "com.joker.coolmall.feature.user"
}
dependencies {
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
