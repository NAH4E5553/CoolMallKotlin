# app/AGENTS.md

## 适用范围

本文件适用于 `app/**`。它补充根目录 `AGENTS.md`；发生冲突时，本文件中更具体的 App 模块规则优先。

## 模块职责

`app` 是应用组装层，负责：

- Android Application 入口；
- Single Activity 宿主；
- Navigation3 根级 Back Stack；
- Feature Graph 聚合；
- 应用主题与 Splash Screen；
- Manifest 和应用级权限；
- BuildType、ProductFlavor、ABI 与签名配置；
- 必需的应用级初始化；
- App Name、Launcher、Splash 和 Theme 等应用级资源。

`app` 不负责：

- Feature 页面与业务状态；
- Repository 业务逻辑；
- Retrofit Service 或 DAO；
- DTO、Entity 和领域模型转换；
- Feature 专属 UI 组件与资源。

主要依赖方向：

```text
app -> feature -> core
```

- Feature 和 Core 不得反向依赖 `app`。
- 不把 `app` 当作共享工具模块。
- 不在 `app` 中新增 Feature 业务实现。
- App 对 Feature 的直接引用应主要用于依赖组装和 Graph 注册。

## Application 入口

应用入口：

```text
app/src/main/java/com/joker/coolmall/Application.kt
```

规则：

- Application 保持轻量，只执行必需的全局初始化。
- 不在 `Application.onCreate()` 中执行阻塞 I/O、网络请求或数据库查询。
- 不在 Application 中保存 Activity、View 或 Composable 引用。
- 新增 SDK 初始化前，先判断能否延迟初始化。
- 初始化失败不能无条件阻止应用启动，除非它是运行应用的必要条件。
- 修改初始化顺序前，必须检查初始化依赖。
- 不手动创建 Hilt 管理的依赖。
- 不在 Application 中实现页面导航。

当前初始化顺序：

```text
Toast
-> Log
-> MMKV
-> QQ SDK
-> AppState
```

`AppState.initialize()` 依赖 MMKV 已完成初始化。修改时必须保持该依赖关系，或显式重构初始化契约。

Hilt Application 必须保留：

```kotlin
@HiltAndroidApp
class Application : Application()
```

## MainActivity

应用采用 Single Activity 架构：

```text
MainActivity
└── AppTheme
    └── AppNavHost
```

规则：

- `MainActivity` 只负责应用宿主职责。
- 不在 Activity 中实现具体 Feature 页面。
- 不将 Feature ViewModel 注入 `MainActivity`。
- 不在 Activity 中直接请求业务数据。
- 应用主题状态由统一 Theme Manager 提供。
- 新增 Flow 收集使用 `collectAsStateWithLifecycle()`。
- 修改现有主题 Flow 收集时，应迁移为生命周期感知收集。
- 保持 `enableEdgeToEdge()`，除非任务明确改变 Window Insets 策略。
- Splash Screen 在 `super.onCreate()` 前安装。
- Splash 保留条件由明确启动状态驱动，不使用固定延迟模拟启动。
- 不在 Activity 中直接维护 Navigation Back Stack。

推荐：

```kotlin
setContent {
    val themeMode by ThemePreferenceManager.themeMode
        .collectAsStateWithLifecycle()

    AppTheme {
        AppNavHost(navigator = navigator)
    }
}
```

## 第三方登录回调

`MainActivity.onActivityResult()` 当前用于 QQ 登录回调。

- 未完整验证 QQ 登录流程前，不删除该回调。
- 修改 QQ SDK、RequestCode 或回调方式时，同时检查 `QQLoginManager`、Manifest、`TENCENT_APPID`、登录 ViewModel 和登录页面。
- 如果迁移到 Activity Result API，必须确认 SDK 支持并完成端到端验证。
- 不记录 AccessToken、OpenId 或完整授权响应。
- QQ 登录修改属于高风险变更，需要真机验证。

## MainActivityViewModel

只有 Activity 确实拥有应用级 UI 状态时才保留 `MainActivityViewModel`。

允许的职责：

- 应用级启动状态；
- 应用更新状态；
- 全局会话状态；
- 顶层 UI 状态。

不允许的职责：

- Feature 页面状态；
- 商品、订单、购物车等业务状态；
- 导航目标页面内部状态。

不要创建或保留没有职责的空 ViewModel。删除前必须确认没有调用方。

## Navigation3 根宿主

应用级 Navigation Host：

```text
app/src/main/java/com/joker/coolmall/navigation/AppNavHost.kt
```

规则：

- 根级起始 Route 必须明确、类型安全且可序列化。
- 修改起始 Route 时验证冷启动、登录状态和引导流程。
- `AppNavHost` 只聚合 Graph，不实现 Feature 页面。
- Feature Entry 在对应 Feature Graph 中注册。
- 添加 Feature Graph 时，在 `appEntryProvider` 中集中注册。
- 不把所有 Feature Entry 展开到 `AppNavHost`。
- Navigation Controller 的 attach/detach 必须对称。
- `NavigationService.bind()` 与 `NavigationService.unbind()` 必须对称。
- 不让全局服务持有已销毁的 Navigation Controller。
- 修改 `DisposableEffect` Key 时验证重建和解绑行为。
- 不移除 Saveable State 或 ViewModel Store Decorator，除非明确接受状态保存和 ViewModel 生命周期变化。
- 修改 Decorator 顺序前必须确认 Navigation3 生命周期语义。
- 修改转场时同时验证前进、返回和 Predictive Back。
- 不在没有迁移方案时替换 Navigation3。

当前 Decorator：

```kotlin
entryDecorators = listOf(
    rememberSaveableStateHolderNavEntryDecorator(),
    rememberViewModelStoreNavEntryDecorator(),
)
```

当前根 Route：

```kotlin
rememberNavBackStack(LaunchRoutes.Splash)
```

新增或删除 Feature 时必须同步维护：

```text
settings.gradle.kts
app/build.gradle.kts
AppNavHost.kt
Feature Graph
```

## Shared Transition

- `SharedTransitionLayout` 只在根 Navigation Host 创建一次。
- 需要共享转场的 Feature Graph 显式接收 `SharedTransitionScope`。
- 不把 `SharedTransitionScope` 保存到 ViewModel 或全局单例。
- 不跨不相关页面复用相同 Shared Element Key。
- 修改共享转场时验证进入、返回和快速连续导航。

## Manifest

Manifest：

```text
app/src/main/AndroidManifest.xml
```

- 新增权限前说明使用场景。
- 不添加与功能无关的权限。
- 敏感权限必须配套运行时授权。
- `android:exported="true"` 只能用于确实需要外部启动的组件。
- 新增导出组件时检查所有 Intent 输入。
- 不在 Manifest 中写入 Secret。
- 修改 Application 或 Activity 类名时同步修改包名和 Manifest。
- 修改 App Theme 时同步检查普通主题和 Android 12+ Splash Theme。
- 修改 Launcher Icon 时同步替换 Adaptive Icon 和各密度资源。
- 修改备份策略时检查 Token、账号状态、MMKV、数据库和隐私数据。

以下配置属于安全敏感项：

```xml
android:allowBackup="true"
android:usesCleartextTraffic="true"
```

- 不默认复制到新的生产应用。
- 新应用优先使用 HTTPS。
- 仅开发环境需要 HTTP 时，应使用 Variant 或 Network Security Config 限制范围。
- 登录凭据、Token、支付数据和设备绑定数据不得进入云备份。
- 修改备份策略时同时检查 `backup_rules.xml` 和 `data_extraction_rules.xml`。

## App 资源

`app/src/main/res` 只存放应用级资源，例如 App Name、Launcher、Splash、Application Theme 和 Backup Rules。Feature 专属资源保存在对应 Feature。

修改品牌时检查：

```text
app/src/main/res/values/strings.xml
app/src/main/res/values/themes.xml
app/src/main/res/values-v31/themes.xml
app/src/main/res/values-night-v31/themes.xml
app/src/main/res/mipmap-*
app/src/main/res/drawable/ic_launcher_*
app/src/main/ic_launcher-playstore.png
```

Theme 名称、Manifest 引用和资源声明必须同步。

## app/build.gradle.kts

`app/build.gradle.kts` 只负责应用级构建和模块组装。

允许配置：

- App Plugin；
- Android Application 特有配置；
- Locale Filter；
- ABI Split；
- Signing Config；
- BuildType；
- App 入口直接需要的依赖；
- Feature 模块聚合；
- App 测试依赖。

不应配置：

- Feature 业务实现；
- Repository 实现；
- 可以统一放入 Convention Plugin 的重复配置；
- 与 App 入口无关的第三方依赖。

以下配置由 `AndroidApplicationConventionPlugin` 集中提供：

- `namespace`
- `applicationId`
- `targetSdk`
- `versionCode`
- `versionName`
- Test Runner
- `dev` / `prod` ProductFlavor

共享配置放入 Convention Plugin；仅 App 使用的特例留在 `app/build.gradle.kts`。不要在两个位置重复定义同一配置。

## Dependencies 基本规则

核心原则：依赖声明在真正使用该 API 的最低层模块，`app` 只保留应用入口、根导航和 Feature 组装直接需要的依赖。

- Feature 专属依赖放入对应 Feature。
- 网络依赖放入 `core:network`。
- 数据库依赖放入 `core:database`。
- Repository 依赖放入 `core:data`。
- 通用 UI 依赖放入 `core:ui` 或 `core:designsystem`。
- 不把 `app` 当作依赖集中仓库。
- 不依赖其他模块偶然暴露的传递依赖。
- App 源码直接引用某个第三方 API 时，应显式声明对应依赖。
- 删除代码后同步移除不再使用的依赖。
- 新增生产依赖前说明用途、所属模块和现有能力不足的原因。

## Version Catalog

第三方依赖统一定义在：

```text
gradle/libs.versions.toml
```

模块通过 Alias 引用：

```kotlin
implementation(libs.androidx.navigation3.runtime)
```

不要硬编码坐标：

```kotlin
implementation("androidx.navigation3:navigation3-runtime:1.0.0")
```

- 不使用 `1.+`、`latest.release` 等动态版本。
- Compose Library 使用 BOM 时，不单独指定版本。
- Plugin 版本统一放在 Version Catalog 或 Build Logic。
- 升级版本前检查 Release Notes、最低 SDK 和迁移要求。

## Dependency Configuration

默认使用：

```kotlin
implementation(...)
```

`app` 是 Application 模块，通常不使用 `api(...)`。

- `implementation`：生产运行时需要。
- `debugImplementation`：仅 Debug 工具。
- `releaseImplementation`：仅 Release 实现。
- `testImplementation`：JVM 单元测试。
- `androidTestImplementation`：设备测试。
- `ksp`：生产源码注解处理器。
- `kspAndroidTest`：Android Test 注解处理器。
- `compileOnly`：仅编译期需要且运行时由环境提供。

规则：

- 不使用 `compileOnly` 隐藏实际运行时依赖。
- Debug 工具不得进入 Release。
- 测试 Library 不得使用生产 `implementation`。
- Annotation Processor 与 Runtime Library 使用正确的配置。

## 模块依赖

App 可以聚合 Feature：

```kotlin
implementation(projects.feature.main)
implementation(projects.feature.auth)
implementation(projects.feature.goods)
```

只有 App 源码直接使用对应 Core API 时，才声明 Core 依赖：

```kotlin
implementation(projects.core.designsystem)
implementation(projects.core.util)
implementation(projects.core.data)
implementation(projects.core.common)
implementation(projects.core.navigation)
```

新增 Core 依赖前确认：

1. App 源码是否直接引用该模块类型；
2. 该逻辑是否应下沉到 Feature 或 Core；
3. 是否只是在弥补传递依赖不可见；
4. 是否产生不合理模块耦合。

## 第三方依赖归属

默认归属：

```text
Retrofit / OkHttp -> core:network
Room             -> core:database
DataStore        -> core:datastore
Lottie           -> owning UI module
Alipay SDK       -> owning payment module
QQ SDK           -> owning authentication/common module
LeakCanary       -> app debugImplementation
```

如果 App 只负责组装，不应因为其他模块需要某个 Library 就在 App 重复声明。

当前 App 中的 Retrofit 与 OkHttp 没有被 `app/src/main` 直接引用。修改依赖时应优先验证它们能否从 App 删除，而不是继续在 App 中添加网络逻辑。

`compileOnly(libs.ksp.gradlePlugin)` 属于需要复查的构建依赖。KSP Plugin 应通过 Plugin 或 Convention Plugin 应用，Processor 应使用 `ksp(...)` 或 `kspAndroidTest(...)`。没有明确用途时不要复制到新项目。

## 本地 JAR/AAR

不推荐新增本地二进制依赖：

```kotlin
implementation(files("libs/example.jar"))
```

必须使用时记录来源、版本、License、校验值、更新方式、所属模块、ProGuard、ABI 和 Native Library 情况。

QQ SDK 当前通过本地 JAR 引入，并被 `MainActivity` 直接引用。调整其归属前必须同时验证编译、Manifest、QQ 登录、ProGuard 和 Release 构建。

## 新增依赖审查

新增生产依赖时必须说明：

1. 解决什么问题；
2. 为什么现有能力无法解决；
3. 为什么属于当前模块；
4. 是否影响包体积；
5. 是否包含 Native Library；
6. 是否要求新增权限；
7. 是否采集或上传用户数据；
8. 是否影响最低 SDK；
9. 是否需要 ProGuard；
10. 是否存在维护和 License 风险。

未经明确授权，不新增 Analytics、Advertising、Push、Payment、Social Login SDK 或来源不明的 JAR/AAR。

## Build Variant 与 ABI

当前 Flavor：

```text
dev
prod
```

当前 BuildType：

```text
debug
release
```

- 开发验证默认使用 `devDebug`。
- Release 配置修改后验证 `prodRelease`。
- 环境地址、App ID 和第三方配置应按 Flavor 区分。
- 不通过注释代码手动切换开发和生产环境。
- Debug 可以启用调试工具；Release 不包含 LeakCanary 或调试日志。
- 修改 Minify、Shrink Resources 或 ProGuard 后执行 Release 构建验证。

当前 ABI：

```text
armeabi-v7a
arm64-v8a
```

- 修改 ABI 前确认第三方 Native Library 支持情况。
- 测试模拟器时单独确认是否需要 `x86_64`。
- 修改 ABI Split 后检查产物数量和安装目标。

## 签名与密钥

签名配置属于最高敏感级别。

禁止：

- 将 Keystore 提交到 Git；
- 将 Key Password 或 Store Password 写入仓库；
- 在日志、文档或 AI 输出中展示密码；
- 未经授权替换正式签名；
- 将本地签名修改与普通功能提交混合；
- 把现有签名复制到新项目。

必须忽略：

```gitignore
*.jks
*.keystore
keystore.properties
secrets.properties
```

签名信息从环境变量或未提交的属性文件读取。当前工作树中的签名文件与硬编码签名信息属于本地配置，不自动暂存、不自动提交、不在回复中输出具体内容。

## 验证

修改 App Kotlin、Manifest、Navigation 或依赖后至少运行：

```bash
./gradlew :app:assembleDevDebug
```

修改 App 单元测试逻辑时运行：

```bash
./gradlew :app:testDevDebugUnitTest
```

修改签名、Minify、ProGuard、资源压缩或 Native 依赖时，在具备安全签名配置的环境中运行：

```bash
./gradlew :app:assembleProdRelease
```

修改导航后人工验证：

- 冷启动与 Splash；
- 登录流程；
- 前进与返回导航；
- Predictive Back；
- Shared Transition；
- 配置变更；
- 进程重建后的状态恢复。

第三方登录修改必须真机验证。

## Code Review

审查 `app` 时重点报告：

- App 中新增 Feature 业务实现；
- Application 中出现阻塞 I/O；
- Activity 中出现业务请求；
- 新增 Flow 使用非生命周期感知收集；
- Navigation Controller 绑定和解绑不对称；
- Feature 依赖与 Graph 注册不一致；
- 新增导出组件或敏感权限；
- 明文网络范围扩大；
- Token、账号或支付数据进入备份；
- Keystore 或密码进入 Git；
- Release 包含调试工具；
- Navigation Decorator 修改导致状态或 ViewModel 生命周期变化；
- ProductFlavor 环境配置串用；
- 品牌修改遗漏 Launcher、Splash、Theme 或 App Name；
- App 中出现应归属 Feature/Core 的第三方依赖；
- 新增依赖但缺少用途、安全、License 和验证说明。
