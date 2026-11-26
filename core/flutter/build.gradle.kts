plugins {
    alias(libs.plugins.coolmall.android.library.compose)
}

android {
    namespace = "com.joker.coolmall.core.flutter"
}

dependencies {
    implementation(projects.core.navigation)
    implementation(libs.androidx.fragment.compose)

    // Container connections expose the engine's BinaryMessenger to feature bridges.
    debugApi(libs.goods.flutter.debug)
    releaseApi(libs.goods.flutter.release)
}
