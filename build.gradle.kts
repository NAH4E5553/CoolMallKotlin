// 顶层构建文件，用于配置所有子项目/模块的通用选项

// 配置项目级插件
plugins {
    // Android应用程序插件，用于构建Android应用
    alias(libs.plugins.android.application) apply false
    // Kotlin Compose插件，用于Jetpack Compose UI开发
    alias(libs.plugins.kotlin.compose) apply false
    // Kotlin Serialization插件
    alias(libs.plugins.kotlin.serialization) apply false

    // 依赖注入相关插件
    // Hilt插件，用于依赖注入框架的支持
    alias(libs.plugins.hilt) apply false
    // KSP (Kotlin Symbol Processing)插件，用于注解处理
    alias(libs.plugins.ksp) apply false
    // Android库插件，用于构建Android库模块
    alias(libs.plugins.android.library) apply false
    // 统一检查 Kotlin 与 Gradle Kotlin DSL 代码格式
    alias(libs.plugins.spotless)
}

spotless {
    // 仅检查相对主分支新增或修改过的文件，逐步收敛存量格式问题
    ratchetFrom("origin/main")

    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/generated/**")
        ktlint(libs.versions.ktlint.get())
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "**/.gradle/**")
        ktlint(libs.versions.ktlint.get())
    }
}
