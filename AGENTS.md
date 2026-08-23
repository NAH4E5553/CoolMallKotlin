# AGENTS.md

## 适用范围与项目概述

本文件适用于整个仓库。进入 `app/**` 或 `core/**` 工作时，还必须读取路径中更具体的 `AGENTS.md`；发生冲突时，更具体的规则优先。

这是一个多模块 Android 应用，主要使用 Kotlin、Jetpack Compose、Material 3、Coroutines/Flow、Hilt、Navigation3、Retrofit/OkHttp、Room、Kotlin Serialization、Gradle Convention Plugins 和 JDK 17+。

当前基础包名为 `com.joker.coolmall`。复制为新应用前必须明确新项目名、应用名和基础包名，再统一修改构建配置、源码包、Manifest、第三方平台和签名配置。

## 通用工作规则

- 修改前先阅读目标目录及父目录的 `AGENTS.md`，检查目标实现、调用方、依赖关系和 Git 状态。
- 除非任务明确要求改变行为，否则保持现有行为不变，并采用能够完整解决问题的最小修改范围。
- 实现功能或修复缺陷时不顺便重构无关代码，不覆盖、回滚或丢弃用户已有修改。
- 低风险歧义遵循项目已有模式；会影响架构、持久化、公共 API、模块边界或用户行为的选择必须先说明。
- 新增生产依赖前说明用途、所属模块、替代方案和风险；未经授权不修改签名、发布配置、包名、后端地址、数据库结构或第三方凭据。
- 只报告实际执行过的验证；环境或权限阻止验证时，明确说明未验证范围。
- 审查任务只报告问题，不直接修改；修复任务定位并覆盖根因；功能任务同步考虑状态、导航、资源和测试。

## 模块架构

依赖方向：

```text
app -> feature -> core
```

主要职责：

- `app`：应用入口、根导航、主题、Manifest、构建变体和模块组装。
- `feature`：功能 UI、ViewModel、功能状态和 Feature Graph。
- `core:model`：跨层共享的数据契约。
- `core:data`：Repository 与应用级状态。
- `core:network`：Retrofit Service、网络 DataSource 和网络配置。
- `core:database`：Room Database、DAO、Entity 和本地 DataSource。
- `core:datastore`：认证与用户信息键值持久化。
- `core:navigation`：Navigation3 Route、导航命令和结果契约。
- `core:designsystem`：主题、设计 Token 和无业务语义组件。
- `core:ui`：具有应用视觉语义的跨 Feature 组件。
- `core:common`：共享 ViewModel 基类、页面状态和公共管理器。
- `core:result`：统一 Result 和错误处理。
- `core:util`：通用 Android 平台工具。

边界规则：

- Feature 和 Core 不得反向依赖 `app`，Feature 之间不得建立实现依赖。
- Core 只能依赖 Gradle 图中明确允许的更底层 Core 模块。
- Composable 不直接访问 Retrofit Service、DAO、DataSource、DataStore 或 Repository。
- UI 不持有 Entity/DTO，Data/Model 层不引入 Android UI 类型。
- Feature 间通信使用 Navigation Contract、共享 Repository 或合适的应用级状态。
- 不把 Feature 业务放入 `app`、`core:ui`、`core:designsystem` 或通用工具模块。
- 不为少量复用引入新的模块依赖，不绕过 Repository 直接访问 DataSource。

## Compose、状态与资源

- 页面采用 Route/Screen 分层：Route 获取 ViewModel、收集状态和处理导航；Screen 通过参数渲染状态并向上回调事件。
- Android UI 中的 Flow 使用 `collectAsStateWithLifecycle()`；ViewModel 对外暴露不可变 `StateFlow`，可变 Flow 保持私有或必要的 protected。
- 每个 UI 事实只有一个权威状态源。业务状态放 ViewModel，局部展示状态放 Compose；派生值使用 Flow 运算符或 `derivedStateOf`。
- 多个公开状态必须原子更新时，优先合并为不可变 `UiState`；Loading、Success、Empty 和 Error 要有明确语义。
- `stateIn` 明确指定 `SharingStarted`，面向 UI 默认优先 `SharingStarted.WhileSubscribed(5_000)`。
- `LaunchedEffect` 使用稳定且有状态语义的 Key，不在组合过程中发起网络请求、持久化写入或创建长期业务对象。
- 不把 `Context`、Activity、View、NavController 或 Composable 引用传入或保存在 ViewModel/Repository。
- 只有 Pager、LazyList、Snackbar、BottomSheet 等局部 UI 控制可以在 Composable 回调中启动局部协程。
- 优先复用 `core:designsystem` 和 `core:ui`；用户可见文本使用 String Resource，已有中英文资源时同步维护。
- 颜色、排版、Shape 和间距优先使用 Token；交互元素提供合理语义、触摸区域和无障碍描述，并验证 Dark Theme。
- Preview 不依赖 Hilt、导航、网络或数据库；只有被多个 Feature 稳定复用的组件才提升到 Core。

## 数据、导航与依赖注入

数据流保持：

```text
UI -> ViewModel -> Repository -> DataSource -> Network/Database/DataStore
```

- Repository 提供业务 API；一次性操作使用 `suspend`，可观察数据使用 `Flow`，调度策略由数据或执行层负责。
- 网络失败转换为统一 Result/Error；UI 不解析底层异常，也不使用 `null` 同时表达加载、空数据和错误。
- DAO Entity 不泄漏到 UI；数据库结构变化必须提升版本、提供 Migration 并验证历史数据升级。
- 共享 Navigation3 Key 定义在 `core:navigation`，必须可序列化且类型安全；参数只传稳定 ID 或小型不可变值。
- Feature Graph 注册对应 Navigation Entry；调整 Route、Back Stack、结果契约或 ViewModel Owner 后验证前进、返回和结果回传。
- 默认使用 Hilt 构造注入；接口绑定放在所属模块的 Hilt Module，不使用 Service Locator 或无约束全局可变单例。
- 生命周期敏感对象使用正确 Scope，不把 Activity Scope 对象注入 Singleton。

## Kotlin 与 Coroutines

- 优先不可变值、不可变数据类、明确类型、穷尽 `when` 和命名参数。
- 避免 `Any`、字符串类型协议、未检查转换和没有局部不变量保证的 `!!`。
- 函数保持单一职责，不为单一场景引入复杂泛型抽象；遵循现有命名、格式和目录结构。
- 不使用通配符导入；新增公共 API 补充必要文档。
- ViewModel 业务协程使用 `viewModelScope`，禁止 `GlobalScope` 和无法取消的长期协程。
- 不捕获或吞掉 `CancellationException`；新请求替代旧请求时取消旧工作并阻止旧结果提交状态。
- Dispatcher 由实际执行层选择，UI 不硬编码 `Dispatchers.IO`；同一请求不得由多个 Effect 重复触发。
- 使用 `flatMapLatest`、`distinctUntilChanged` 或额外状态包装前确认其真实语义，不机械套用。

## Gradle 依赖规则

- 第三方版本和别名统一维护在 `gradle/libs.versions.toml`，模块中不写死版本或使用动态版本。
- 新增模块依赖优先使用类型安全访问器；不为格式统一机械改写未涉及的旧声明。
- 默认使用 `implementation`；只有公共 API 确实暴露依赖类型时才考虑 `api`。
- JVM 测试使用 `testImplementation`，设备测试使用 `androidTestImplementation`，测试库不得进入生产 `implementation`。
- 调试工具使用 `debugImplementation`；Release 需要同一 API 时使用对应 no-op，不能携带调试实现。
- 插件优先复用 `build-logic` 的 Convention Plugin；依赖声明在真正使用 API 的最低层模块。
- 修改公共依赖、插件或模块图后，除模块验证外还要构建 `app`。

## 安全规则

- 不提交 Keystore、密码、API Key、Token、私有 URL、临时凭据或真实用户测试数据。
- 签名密码来自环境变量或被忽略的本地文件；未经授权不替换正式签名或修改发布配置。
- 不在日志、异常、测试 fixture 或文档中暴露 Token、验证码、支付参数、个人资料或完整请求体。
- 不关闭 TLS 校验，不擅自启用明文网络；第三方 App ID 和 Callback Scheme 按环境隔离。
- 登录、支付、权限、备份和导出组件属于高风险修改，需要专项验证。
- `.gitignore` 必须覆盖 `*.jks`、`*.keystore`、`keystore.properties`、`local.properties` 和 `secrets.properties`。

## 测试与 CI

修改业务逻辑时增加或更新测试，覆盖 Success、Failure、边界值和取消；修改状态同步时覆盖所有状态入口。

局部验证：

```bash
./gradlew :feature:<module>:compileDevDebugKotlin :feature:<module>:testDevDebugUnitTest
./gradlew :core:<module>:compileDevDebugKotlin :core:<module>:testDevDebugUnitTest
```

提交 PR 前执行与 GitHub Actions 一致的完整检查：

```bash
./gradlew :app:assembleDevDebug testDevDebugUnitTest lintDevDebug spotlessCheck --no-daemon --stacktrace
```

- `ExampleUnitTest` 只是模板，不代表业务逻辑已有回归保护。
- 导航修改验证参数、Back Stack 和结果；Room Schema 修改验证 Migration；UI 修改验证相关主题、字体缩放、系统 Insets 和交互边界。
- 原有失败与本次失败分开报告；不能用局部编译替代提交前完整检查。

## Git、审查与架构变更

- 不暂存、提交或回滚无关修改，不使用破坏性命令清理用户工作，不强制推送。
- 提交前检查 `git status`、`git diff --check` 和 `git diff --cached`；每个提交保持单一目的。
- 提交信息使用 `feat|fix|refactor|test|docs|chore(module): description`。
- Code Review 优先报告可复现缺陷；每个问题说明风险、路径、修复方式、优先级和是否属于本次范围。
- 重点检查重复状态源、生命周期外协程、取消被吞、旧请求覆盖、可变 Flow 暴露、硬编码文案、Feature 反向依赖、Secret、无 Migration 的 Schema 变化和缺少测试的行为变化。
- 新增/删除模块、改变依赖方向、导航框架、Repository 边界、数据库结构、统一错误处理、登录/支付流程、包名、构建变体或公共状态归属前，先说明当前实现、问题、方案、影响、兼容性、测试和回滚方式。

## AI 工作流程

1. 读取任务和适用的 `AGENTS.md`。
2. 检查目标实现、调用方、依赖和 Git 状态。
3. 明确最小修改范围及潜在影响。
4. 实现完整修改并增加与风险匹配的测试。
5. 执行模块验证、格式检查和必要的完整 CI 等效检查。
6. 检查最终 Diff，不混入无关内容。
7. 汇报行为变化、验证结果和剩余风险。
