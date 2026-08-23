# app/AGENTS.md

## 适用范围与职责

本文件适用于 `app/**`，补充根目录 `AGENTS.md`。`app` 是应用组装层，负责 Application、Single Activity 宿主、Navigation3 根 BackStack、Feature Graph 聚合、应用主题、Splash、Manifest、构建变体、签名配置和应用级资源。

`app` 不实现 Feature 页面、业务状态、Repository、Service、DAO、模型转换或 Feature 专属组件。Feature/Core 不得反向依赖 `app`，App 对 Feature 的引用主要用于依赖组装和 Graph 注册。

## Application 入口

入口位于 `app/src/main/java/com/joker/coolmall/Application.kt`。

- 保留 `@HiltAndroidApp`，Application 保持轻量，只执行必需的全局初始化。
- 不在 `onCreate()` 中执行阻塞 I/O、网络请求、数据库查询、页面导航或 Feature 业务。
- 不保存 Activity、View 或 Composable，不手动创建 Hilt 管理对象；新增 SDK 前优先考虑延迟初始化。
- 初始化失败不应无条件阻止启动，除非该能力是应用运行的必要条件。

当前初始化顺序：

```text
Toast -> Log -> MMKV -> QQ SDK -> AppState
```

`AppState.initialize()` 依赖 MMKV。修改顺序必须保持该契约，或同步重构调用方和测试。

## MainActivity 与第三方回调

应用采用 `MainActivity -> AppTheme -> AppNavHost` 的 Single Activity 结构。

- Activity 只承担宿主职责，不实现 Feature 页面、业务请求或 Feature ViewModel 状态。
- 保持 `enableEdgeToEdge()`；修改时同步评估全局 Window Insets 策略。
- Splash 在 `super.onCreate()` 前安装，保留条件由明确启动状态驱动，不使用固定延迟。
- 新增 Flow 使用 `collectAsStateWithLifecycle()`；修改现有主题 Flow 时同步迁移为生命周期感知收集。
- 只有 Activity 确实拥有启动、更新、会话等应用级 UI 状态时才使用 `MainActivityViewModel`，不保留空 ViewModel。

`MainActivity.onActivityResult()` 当前承接 QQ SDK 回调：

- 未完成端到端验证前不删除或改写回调。
- 修改 SDK、RequestCode、Activity Result API 或 QQ App ID 时，同时检查 `QQLoginManager`、Manifest、`TENCENT_APPID`、登录 ViewModel 和登录页面。
- 不记录 AccessToken、OpenId 或完整授权响应；QQ 登录必须真机验证成功、取消、失败和 Activity 重建。

## Navigation3 根宿主

根宿主位于 `app/src/main/java/com/joker/coolmall/navigation/AppNavHost.kt`。

- 根起始 Route 必须明确、可序列化且类型安全；当前为 `LaunchRoutes.Splash`。
- `AppNavHost` 只聚合 Feature Graph，不实现页面；新增或删除 Feature 时同步维护 `settings.gradle.kts`、`app/build.gradle.kts`、Feature Graph 和 `appEntryProvider`。
- `AppNavigator.attachController/detachController` 与 `NavigationService.bind/unbind` 是两组不同生命周期，必须分别成对。
- 不让全局服务持有失效 Controller；修改 `DisposableEffect` Key 后验证重建与解绑。
- 保留 Saveable State 与 ViewModel Store Decorator，改变顺序或删除前必须说明状态保存和 ViewModel 生命周期影响。
- 修改起始 Route、转场或 BackStack 行为时验证冷启动、登录流程、前进、返回、Predictive Back、配置变化和进程重建。

当前 Decorator：

```kotlin
rememberSaveableStateHolderNavEntryDecorator()
rememberViewModelStoreNavEntryDecorator()
```

`SharedTransitionLayout` 只在根宿主创建一次。Feature Graph 显式接收 `SharedTransitionScope`；不把 Scope 保存到 ViewModel/单例，也不跨无关页面复用 Shared Element Key。

## Manifest 与应用级资源

Manifest 位于 `app/src/main/AndroidManifest.xml`。

- 新增权限前说明用途，不申请无关权限；敏感权限必须配套运行时授权。
- `android:exported="true"` 只用于确需外部启动的组件，并校验所有 Intent 输入。
- 不在 Manifest 写 Secret；修改 Application、Activity、包名、主题或第三方回调时同步检查引用。
- `allowBackup`、`usesCleartextTraffic`、Backup Rules 和 Network Security Config 属于安全敏感配置，不默认复制到新项目。
- Token、账号、支付、MMKV 和数据库敏感数据不得进入云备份；生产环境优先 HTTPS。

`app/src/main/res` 只保存 App Name、Launcher、Splash、Application Theme 和 Backup Rules 等应用级资源。品牌修改同步检查：

```text
values/strings.xml
values/themes.xml
values-v31/themes.xml
values-night-v31/themes.xml
mipmap-*
drawable/ic_launcher_*
ic_launcher-playstore.png
```

Theme 名称、Manifest 引用、普通主题与 Android 12+ Splash Theme 必须一致。

## App 构建与依赖

`app/build.gradle.kts` 只配置 Application 特例和模块组装。namespace、applicationId、SDK、版本、Test Runner 及 `dev`/`prod` Flavor 等共享配置由 `AndroidApplicationConventionPlugin` 提供，不能在两处重复定义。

- `app` 可以聚合 Feature，只在 App 源码直接引用 Core/第三方 API 时声明对应依赖。
- Feature、网络、数据库、Repository 和通用 UI 依赖分别归属 owning Feature、`core:network`、`core:database`、`core:data`、`core:ui/designsystem`。
- 不依赖其他模块偶然暴露的传递依赖；删除代码后同步清理依赖。
- App 通常使用 `implementation`，测试和 Debug 工具使用根规则指定的配置；Release 不包含 LeakCanary、BODY 日志或其他调试实现。
- 当前 App 中 Retrofit/OkHttp 没有被 `app/src/main` 直接使用，修改依赖时优先验证能否移除，不在 App 增加网络逻辑。
- `compileOnly(libs.ksp.gradlePlugin)` 属于待复查构建依赖；没有明确用途时不复制到新项目。
- QQ SDK 当前由 App 与 `core:common` 共同涉及；调整本地 JAR 归属时必须验证编译、Manifest、登录回调、ProGuard 和 Release。
- 新增本地 JAR/AAR 时记录来源、版本、License、校验值、更新方式、ProGuard、ABI 和 Native Library 情况。

新增生产依赖除根规则外，还要检查包体积、Native Library、权限、数据采集、最低 SDK、ProGuard 和 License。未经授权不新增 Analytics、Advertising、Push、Payment、Social Login SDK 或来源不明的二进制依赖。

## Build Variant、ABI 与签名

当前 Flavor 为 `dev/prod`，BuildType 为 `debug/release`，开发验证默认使用 `devDebug`。

- 环境地址、App ID 和第三方配置按 Flavor 区分，不通过注释代码切换环境。
- Release 的 Minify、Shrink Resources、ProGuard 或 Native 依赖变化后验证 `prodRelease`。
- 当前 ABI 为 `armeabi-v7a`、`arm64-v8a` 且不生成 Universal APK；修改前检查第三方 Native Library、模拟器 `x86_64` 和产物数量。
- Keystore、密码和 `keystore.properties` 不提交；签名读取环境变量或被忽略的本地配置。
- 未经授权不替换正式签名，不把签名修改与普通功能混合，不在日志、文档或回复中输出签名内容。
- 本地缺少签名时保持可生成未签名 Release 的安全行为；只有在具备受控签名配置时验证正式签名产物。

## 验证与审查

修改 App Kotlin、Manifest、Navigation、依赖或资源后至少运行：

```bash
./gradlew :app:assembleDevDebug :app:testDevDebugUnitTest
```

提交前执行根文档规定的完整 CI 等效检查。修改签名、Minify、ProGuard、资源压缩或 Native 依赖时，在安全环境中额外运行：

```bash
./gradlew :app:assembleProdRelease
```

人工验证重点：

- 冷启动、Splash、主题和 Insets；
- 根导航、前进/返回、Predictive Back、Shared Transition 和状态恢复；
- Graph 注册与 Feature 依赖一致；
- 权限、导出组件、备份与明文网络范围；
- Flavor、ABI、Launcher、Splash、Theme 和 App Name；
- Release 不含调试工具，Git 不含密钥或密码。
