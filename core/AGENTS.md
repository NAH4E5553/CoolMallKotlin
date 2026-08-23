# core/AGENTS.md

## 适用范围

本文件适用于 `core/**`，补充仓库根目录 `AGENTS.md`。进入具体 Core 模块时继续读取该模块的 `AGENTS.md`；模块文件只记录该模块特有的职责、风险和测试重点。

## Core 公共边界

- Core 提供跨 Feature 复用且职责稳定的能力，不接收 Feature 或 `app` 的反向依赖。
- 新能力优先放入现有职责匹配的模块；不要为了复用少量代码创建依赖或把业务逻辑提升到 Core。
- 模块只能依赖自身 `build.gradle.kts` 已声明或架构明确允许的更底层 Core；新增依赖前检查完整调用链和循环依赖。
- `core:model` 保持纯数据契约；Data、Network、Database 和 DataStore 不依赖 Compose/UI；UI/DesignSystem 不访问 Repository、DataSource 或持久化实现。
- Feature 只能通过公开契约使用 Core。新增或修改 Core 公共 API 前搜索所有调用方，评估二进制、序列化、持久化和用户行为影响。
- 通用接口优先保持最小可见性；只在模块边界确实需要时公开，不为了测试扩大生产 API。

## 实现与依赖

- 接口、实现与 Hilt 绑定保持在所属模块；默认构造注入，不从上层模块手动组装内部实现。
- 跨层模型转换放在拥有边界的 DataSource/Repository，不在多个 Core 模块复制同一套映射。
- Android Context 使用 Application Context；不持有 Activity、View、NavController、ViewModel 或 Composable。
- 长生命周期状态必须有明确所有者和初始化顺序；持久化 key、数据库 schema、序列化字段和导航 key 都属于兼容性契约。
- 第三方版本使用 Version Catalog，测试库只进入测试配置，Debug 工具不进入 Release。
- 模块依赖清单以对应 `build.gradle.kts` 为事实来源；模块 `AGENTS.md` 负责约束允许的依赖方向，不复制完整依赖版本。

## 测试规则

- 模块文档不记录“当前有多少测试”这类易过期状态；测试有效性的判断遵循根目录规则。
- 纯 Kotlin 状态、映射、序列化和计算优先使用 JVM 单元测试；Android 平台、Room Migration、权限、通知和 Compose 交互使用匹配的 Android 测试或设备验证。
- Repository/DataSource 测试使用 Fake 或可控测试实现，不连接真实后端、不读取用户数据库/MMKV，也不使用真实凭据。
- 修改公共状态机或异步流程时覆盖 Success、Failure、空数据、边界值、取消、并发与旧结果提交。
- 修改持久化或序列化契约时使用固定旧格式 fixture 验证兼容性；敏感 fixture 必须是虚构数据。
- 不为了让测试容易编写而改变生产行为或扩大可见性；必要的纯逻辑提取应由生产调用方和测试共同使用。

## 验证

Core 模块的快速验证模板：

```bash
./gradlew :core:<module>:compileDevDebugKotlin :core:<module>:testDevDebugUnitTest
```

具体模块文档保留可直接复制的命令。以下情况还需要额外验证：

- 公共 API、模块依赖或 Convention Plugin：构建 `:app:assembleDevDebug`。
- Room Schema：比较生成 schema，并执行 Migration 测试。
- Compose 视觉与交互：检查 Preview、目标页面、主题、字体缩放和系统 Insets。
- 权限、通知、SDK 回调和平台存储：在对应 Android 版本的设备或模拟器验证。
- 准备 PR：执行根 `AGENTS.md` 规定的完整 CI 等效命令。

## 文档维护

- AGENTS 记录长期有效的规则，不记录短期任务状态、当前测试数量或临时待办。
- 模块职责、依赖或测试基础设施变化时同步更新最近一级的 `AGENTS.md`，不在多个层级复制同一条通用规则。
- 当前实现细节以源码、`build.gradle.kts` 和 CI Workflow 为准；AGENTS 与实现不一致时先确认事实，再修正文档或代码。
