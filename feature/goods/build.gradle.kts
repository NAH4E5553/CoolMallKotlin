plugins {
    alias(libs.plugins.coolmall.android.feature)
}

android {
    namespace = "com.joker.coolmall.feature.goods"
}

dependencies {
    implementation(libs.androidx.fragment.compose)
    debugImplementation(libs.goods.flutter.debug)
    releaseImplementation(libs.goods.flutter.release)
}
