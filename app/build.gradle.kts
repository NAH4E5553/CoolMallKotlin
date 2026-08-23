import java.util.Properties

plugins {
    alias(libs.plugins.coolmall.android.application.compose)
    alias(libs.plugins.coolmall.hilt)
}

val keystorePropertiesFile = file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}
val signingProperty: (String) -> String = { name ->
    keystoreProperties.getProperty(name)?.takeIf(String::isNotBlank)
        ?: error("Missing signing property '$name' in ${keystorePropertiesFile.path}")
}

android {
    defaultConfig {
        // 仅包括中文和英文必要的语言资源
        androidResources {
            localeFilters += listOf("zh", "en")
        }
    }

    // ABI 分包配置 - 一次性打包多个架构版本
    splits {
        abi {
            // 启用 ABI 分包
            isEnable = true
            // 重置默认列表
            reset()
            // 包含的架构：32位和64位 ARM
            include("armeabi-v7a", "arm64-v8a")
            // 是否生成通用 APK（包含所有架构）
            // 设置为 true 会额外生成一个包含所有架构的 APK
            isUniversalApk = false
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.isFile) {
            create("common") {
                storeFile = file(signingProperty("storeFile"))
                keyAlias = signingProperty("keyAlias")
                keyPassword = signingProperty("keyPassword")
                storePassword = signingProperty("storePassword")

                // 启用所有签名方案以确保最大兼容性
                enableV1Signing = true  // JAR 签名 (Android 1.0+)
                enableV2Signing = true  // APK 签名 v2 (Android 7.0+)
                enableV3Signing = true  // APK 签名 v3 (Android 9.0+)
                enableV4Signing = true  // APK 签名 v4 (Android 11.0+)
            }
        }
    }

    // 构建类型配置
    buildTypes {
        debug {
            // 本地提供签名配置时复用正式签名，便于调试支付与第三方登录；否则使用默认 debug 签名
            signingConfigs.findByName("common")?.let { signingConfig = it }
            // debug 模式下包名后缀
            applicationIdSuffix = ".debug"
        }

        release {
            // 未提供本地签名配置时生成未签名产物，避免将密钥或密码写入版本库
            signingConfigs.findByName("common")?.let { signingConfig = it }
            // 是否启用代码压缩
            isMinifyEnabled = true
            // 资源压缩
            isShrinkResources = true
            // 配置ProGuard规则文件
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.util)
    implementation(projects.core.data)
    implementation(projects.core.common)

    // 导航
    implementation(projects.core.navigation)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // 网络相关依赖
    implementation(libs.okhttp3)
    implementation(libs.retrofit)
    // 首页模块
    implementation(projects.feature.main)
    // 商品模块
    implementation(projects.feature.goods)
    // 登录(认证)模块
    implementation(projects.feature.auth)
    // 用户模块
    implementation(projects.feature.user)
    // 订单模块
    implementation(projects.feature.order)
    // 客服模块
    implementation(projects.feature.cs)
    // 通用模块
    implementation(projects.feature.common)
    // 营销模块
    implementation(projects.feature.market)
    // 反馈模块
    implementation(projects.feature.feedback)
    // 启动流程模块
    implementation(projects.feature.launch)

    // 依赖注入
    // https://developer.android.google.cn/training/dependency-injection/hilt-android?hl=zh-cn
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.hilt.android.testing)

    compileOnly(libs.ksp.gradlePlugin)

    // 启动页
    implementation(libs.androidx.core.splashscreen)

    // LeakCanary - 内存泄漏检测工具（仅在debug构建中使用）
    // https://github.com/square/leakcanary
    debugImplementation(libs.leakcanary.android)

    // 测试依赖
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // QQ SDK 依赖
    implementation(files("../core/common/libs/open_sdk_lite.jar"))
}
