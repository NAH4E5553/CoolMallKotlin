# core/common/AGENTS.md

## 适用范围

本文件适用于 `core/common/**`，并补充仓库根目录与 `core/AGENTS.md`。本模块当前包含共享 ViewModel 基类、网络页面状态、主题偏好和 QQ 登录管理。

## 模块职责

- `base/state`：定义 `BaseNetWorkUiState`、`BaseNetWorkListUiState` 和 `LoadMoreState`。
- `base/viewmodel`：统一普通请求、分页、刷新、重试和导航结果监听。
- `config`：保存应用公共配置和可持久化的主题枚举。
- `manager`：管理主题偏好与 QQ SDK 登录结果。

不要在本模块加入 Feature 专属业务、页面 Composable、Retrofit Service、Room DAO 或 Repository 实现。通用能力只有被多个上层模块实际复用时才放入这里。

## ViewModel 与状态规则

- 当页面符合单请求或单列表状态机时，优先沿用 `BaseNetWorkViewModel<T>` 或 `BaseNetWorkListViewModel<T>`。多数据源、多 Tab 或组合状态页面不要为了继承而强行套用基类。
- 子类通过 `requestApiFlow()` 或 `requestListData(page, pageSize)` 提供 Repository Flow，不在基类中直接访问 Service 或 DAO。
- 对外只暴露 `StateFlow`；新增的 `MutableStateFlow` 必须保持 `private` 或 `protected`，不要扩大现有可变状态的可见性。
- 首次加载、刷新、加载更多和错误回退的语义必须保持独立。分页子类必须使用参数中的页码快照构造请求，不能在异步请求中再次读取或提前递增 `currentPage`。
- 初始化/刷新会取消旧请求，加载更多只允许一个在途请求；页码只在成功提交数据后推进。
- `enableMinLoadingTime` 是可选的视觉策略，不应用它掩盖慢请求，也不要在多个位置重复增加固定延迟。
- `observeRefreshState()` 依靠 `refreshObserveJob` 保证每个 ViewModel 只注册一次；新增类似监听也必须避免重复 collect。
- 导航结果使用 `NavigationResultKey<T>`，不要恢复为裸字符串或 `Any` 约定。
- 普通请求可覆盖 `onRequestSuccess`/`onRequestError`；分页状态提交由基类集中维护，不在子类复制成功、失败或页码回退逻辑。

## 主题与本地偏好

- `ThemePreferenceManager` 是主题模式和主题颜色的统一状态源；UI 订阅其只读 `StateFlow`。
- `settings_theme_mode`、`settings_theme_color` 以及枚举的 `storageValue` 是持久化契约，改名需要兼容旧值或提供迁移。
- 读取失败继续使用安全默认值；写入时先避免无意义的重复更新。
- `ThemeColorOption.colorHex` 与 `core:designsystem` 的可用主题色应保持一致。
- 本管理器依赖 `MMKVUtils` 已初始化；不要在静态初始化中加入阻塞 I/O。

## QQ 登录

- `QQLoginManager` 必须使用 Application Context 初始化，Activity 只用于发起授权。
- 登录结果继续通过只读 `StateFlow<QQLoginResult?>` 分发，消费完成后调用 `clearLoginResult()`。
- 不记录或持久化 access token、openId、完整授权响应。
- 修改 QQ App ID、SDK 回调或 Manifest 组件时，同步检查 `AppConfig.QQ_APP_ID`、`build.gradle.kts` 的 `TENCENT_APPID`、`core/common/src/main/AndroidManifest.xml`、app 初始化和登录 Feature；两处 ID 值必须一致，Manifest scheme 必须继续引用对应 placeholder。
- QQ 登录相关变更需要真机验证回调、取消、失败和 Activity 重建。

## 测试重点

- 修改网络基类时，必须覆盖首次加载、刷新、加载更多、失败重试和空列表状态。
- 必须验证新请求替换旧请求、取消后旧结果不能提交、页码只在成功后推进，以及最少加载时长的延迟任务不会覆盖新状态。
- `requestListData(page, pageSize)` 的测试必须证明使用传入页码快照，而不是异步读取共享 `currentPage`。
- 修改导航结果监听时，验证重复注册不会产生多个 collector。
- `BaseNetWorkListViewModelTest` 是当前分页回归基线；修改相关状态机时同步更新测试。

## 依赖与构建

本模块使用 `coolmall.android.library`、Hilt、Navigation3，并依赖 `core:model`、`core:navigation`、`core:data`、`core:result`、`core:util` 和本地 QQ SDK；协程测试库仅用于 `src/test`。不要仅为 Feature 便利新增向上的模块依赖。

最小验证：

```bash
./gradlew :core:common:compileDevDebugKotlin :core:common:testDevDebugUnitTest
```
