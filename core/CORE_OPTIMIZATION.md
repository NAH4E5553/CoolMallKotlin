# Core 模块高优先级优化说明

## 1. 文档目的

本文记录对 `core` 及其直接调用方再次审计后确认的问题、修改边界、实施顺序和验证标准。目标是先修复会导致错误业务状态、并发竞态、安全泄漏或跨页面串扰的问题，再让各模块的 `AGENTS.md` 以修正后的代码为准。

本轮会同步修改必要的 `feature` 调用方，但不做无关的架构重写和产品行为调整。

## 2. 优先级与实施范围

| 优先级 | 问题 | 主要影响 | 本轮决策 |
| --- | --- | --- | --- |
| P0 | `ResultHandler` 在业务失败时仍先执行成功回调 | 错误数据入库、错误页面返回、同一请求同时表现为成功和失败 | 必须修改 |
| P0 | 请求取消被当作普通异常处理 | 页面退出或刷新取消时产生错误提示、破坏协程取消语义 | 必须修改 |
| P0 | 上传请求记录 BODY、临时凭证和文件内容 | 临时密钥、Token、文件和上传地址可能进入日志 | 必须修改 |
| P1 | 分页基类使用共享 `currentPage` 判断异步响应 | 快速刷新/加载时发生页码错乱、旧响应覆盖新状态 | 必须修改，并迁移现有 7 个子类 |
| P1 | `AuthInterceptor` 每次请求使用 `runBlocking` 读取 Token | 阻塞 OkHttp 工作线程，认证读取路径复杂 | 必须修改 |
| P1 | 全局刷新结果 Key 广播给所有存活页面 | 地址、反馈、订单等无关页面可能被错误刷新 | 必须修改为业务域 Key |
| P1 | 导航栈操作没有代码级主线程保证 | 后台线程调用时可能并发修改 Compose 导航状态 | 必须修改 |
| P1 | `LoadMore` 自动滚动到越界索引 | 加载后抢夺用户滚动位置，并存在无效索引 | 必须修改 |
| P2 | `core:common` 通过传递依赖使用 `core:util` | 依赖关系隐式，调整 `core:data` 暴露方式后可能编译失败 | 必须修改 |
| P2 | Room 配置 schema 目录但关闭 schema 导出 | 数据库迁移缺少可审查、可测试的历史基线 | 必须修改 |

## 3. 问题与方案

### 3.1 统一网络结果语义

#### 当前逻辑与隐患

`ResultHandler.handleSuccess` 当前先调用成功回调，再判断服务端业务码：

```kotlin
onSuccess(response)

if (response.isSucceeded) {
    response.data?.let { onData(it) }
} else {
    val errorMsg = response.message ?: "未知错误"
    handleError(errorMsg, Exception(errorMsg), showToast, onError)
}
```

这意味着 HTTP 请求虽然成功、但 `code` 表示业务失败时，调用方仍会先进入成功分支。例如分页基类可能先提交响应内容再显示错误，地址编辑页也可能在更新失败时返回上一页。

另一个问题是 `catch (e: Exception)` 会捕获 `CancellationException`。协程被新请求取消或页面销毁时，取消信号会被误当成业务异常，导致错误 UI 或日志。

`NetworkResponse<Unit>` 的成功响应通常没有 `data`。如果删除操作使用“必须有数据”的回调，成功后的列表刷新可能永远不执行。

#### 优化方案

- 仅在 `response.isSucceeded == true` 后执行成功回调。
- `handleResult` 表达“成功不要求响应体”，适用于 `Unit`、更新等操作。
- `handleResultWithData<T : Any>` 表达“成功必须有非空数据”；空数据按协议异常处理。
- 三个入口都返回 `Job`，供分页和页面请求取消旧任务。
- 捕获异常时先重新抛出 `CancellationException`。
- `Flow.asResult()` 同样保留取消语义。

目标语义示例：

```kotlin
if (!response.isSucceeded) {
    val errorMsg = response.message ?: "未知错误"
    handleError(errorMsg, Exception(errorMsg), showToast, onError)
    return
}

onSuccess(response)
```

收益：一个请求只会进入一种终态；取消不会冒充失败；无响应体操作也能可靠触发成功逻辑。

### 3.2 消除分页竞态

#### 当前逻辑与隐患

分页基类在发起请求和处理响应时都读取共享的 `currentPage`。请求 A 尚未返回时，请求 B 可以改变页码；A 返回后会按 B 的页码判断“首屏还是追加”，造成覆盖、重复或回滚错误。快速下拉刷新、连续加载以及页面恢复时尤其容易触发。

当前抽象接口也没有把页码作为参数传给子类：

```kotlin
protected abstract fun requestListData(): Flow<NetworkResponse<NetworkPageData<T>>>
```

子类只能再次读取共享字段，从而扩大了竞态窗口。

#### 优化方案

- 抽象请求显式接收本次请求快照：

```kotlin
protected abstract fun requestListData(
    page: Int,
    pageSize: Int,
): Flow<NetworkResponse<NetworkPageData<T>>>
```

- 每次请求捕获 `requestPage` 和加载类型，响应只使用该快照。
- 保存当前请求 `Job`；初始化和刷新会取消旧请求，加载更多只允许一个在途请求。
- 页码只在成功提交数据后推进，不再使用“先加一、失败再回滚”。
- 将可变状态限制为 `protected`，外部只读取不可变 `StateFlow`。
- 迁移当前 7 个直接子类，保持各业务接口及 UI 行为不变。

收益：旧响应不会按照新页码提交；刷新与加载更多不会相互覆盖；页码和已展示数据始终同步。

### 3.3 上传链路安全与资源释放

#### 当前逻辑与隐患

上传专用客户端启用了 BODY 级别日志和 Chucker。Multipart 请求中包含临时密钥、会话 Token、文件内容及上传地址，因此调试日志可能直接保存敏感数据。

上传实现还使用 `readBytes()` 一次性读取文件，并未通过 `use` 统一关闭所有响应资源；大文件会显著增加峰值内存，异常路径也更容易遗漏释放。多层 `catch (Exception)` 同样会吞掉取消信号。

#### 优化方案

- 上传客户端禁用 BODY 日志和 Chucker，不记录 Multipart 内容。
- 普通 HTTP 日志显式遮蔽 `Authorization`。
- 文件请求体改为流式写入，输入流与 OkHttp `Response` 均使用 `use`。
- 各上传层重新抛出 `CancellationException`。
- 删除上传 URL、临时凭证和文件内容相关日志；错误仅保留必要的状态信息。
- 删除未使用的旧日志拦截器。

收益：降低凭证和用户文件泄漏风险；避免大文件整块进入内存；取消和资源释放行为可预测。

### 3.4 认证读取去阻塞

#### 当前逻辑与隐患

MMKV 本身是同步存储，但 `AuthStoreDataSource` 将简单读写声明为 `suspend`，导致 `AuthInterceptor` 在每个请求中使用 `runBlocking` 获取 Token。它会阻塞 OkHttp 拦截器线程，也掩盖了实际并不存在的异步 I/O。

#### 优化方案

- 将 `AuthStoreDataSource` 的 MMKV 读写改为同步函数。
- Repository 对 feature 维持现有 `suspend` 接口，减少上层牵连。
- `AuthInterceptor` 直接同步读取 Token，移除 `runBlocking`。
- 遮蔽认证请求头日志。

收益：减少线程阻塞和协程桥接；数据源 API 与真实存储模型一致；调用链更容易测试。

### 3.5 导航结果隔离与线程约束

#### 当前逻辑与隐患

地址、反馈和订单页面共用全局 `RefreshResultKey`。由于返回栈中的 ViewModel 仍可能订阅结果，一个业务页面产生的刷新事件会广播给其他存活页面，形成无关请求或状态串扰。

同时，`AppNavigator` 可从任意线程调用，但最终会直接修改 Compose `NavBackStack`，当前仅依赖“调用方通常在主线程”的约定，没有代码保证。

#### 优化方案

- 用业务域 Key 替换全局 Key：地址变更、反馈提交、订单变更、支付完成分别发布和订阅。
- 基础观察方法不再提供容易误用的默认全局 Key。
- 所有导航命令在主线程执行；后台调用先投递到主线程，再操作 back stack。
- 暂不使用 `SharedFlow(replay = 1)`，避免新订阅页面收到历史结果。

收益：返回结果只影响目标业务域；后台回调触发导航时不会并发修改 UI 状态。

长期如需同一业务域多实例并存，可再引入一次性的 `resultToken` 做请求级关联；本轮不扩大到该重构。

### 3.6 LoadMore 副作用

#### 当前逻辑与隐患

加载更多组件在数据变化后执行：

```kotlin
listState.scrollToItem(listState.layoutInfo.totalItemsCount)
```

`totalItemsCount` 是数量而不是有效索引，并且自动滚动会抢夺用户位置。组件的职责应当只是发出加载事件，不应改变列表滚动位置。

#### 优化方案

- 删除自动滚动和不再需要的 `listState` 参数。
- 用 `rememberUpdatedState` 持有最新回调，并为派生状态补齐 key。
- 列表新增数据后让 Compose 保持当前位置。

收益：避免越界索引和跳动；加载组件职责单一；回调更新不会引用旧闭包。

### 3.7 显式 Gradle 依赖

`core:common` 直接使用 `MMKVUtils`，却依赖 `core:data` 暴露的传递依赖。调整为：

```kotlin
// core/common
implementation(projects.core.util)

// core/data
implementation(projects.core.util)
```

收益：源码引用与模块依赖一致；`core:data` 不再无意暴露内部实现依赖。

### 3.8 Room schema 基线

当前已配置 `schemaDirectory`，但数据库声明 `exportSchema = false`，实际不会生成可版本管理的 schema。

本轮将开启 schema 导出并生成版本 1 的 JSON 基线；数据库结构和版本号不变。

收益：后续迁移可比较、可测试，避免升级时才发现历史 schema 缺失。

## 4. 牵连范围

本轮预计修改：

- `core/result`：结果语义和取消传播。
- `core/common`：分页基类、通用请求基类、显式依赖。
- `core/network`：认证拦截器、上传客户端和上传实现。
- `core/datastore`、`core/data`：认证数据源同步化及依赖收口。
- `core/navigation`：业务结果 Key 和主线程执行。
- `core/ui`：LoadMore 与刷新内容调用。
- `core/database`：schema 导出。
- `feature/common`、`feature/feedback`、`feature/goods`、`feature/main`、`feature/market`、`feature/user`、`feature/order`：必要的接口迁移与结果 Key 替换。

不修改：

- `SpecSelectModal` 再次打开时是否重置数量/规格。该行为需要产品定义，不能仅凭技术判断改变。
- `OrderListViewModel`、`ChatViewModel` 的自定义分页架构重写。本轮只承接结果语义变更；其并发模型作为后续独立审计项。
- 模块的大规模重新分层或 Navigation 结果 Token 化重构。
- 用户当前未提交的 `app/build.gradle.kts`、签名文件及其他无关改动。

## 5. 实施顺序

1. 修复结果语义和取消传播，并迁移无响应体操作。
2. 重构分页基类，迁移 7 个子类。
3. 修复认证与上传链路。
4. 隔离导航结果并保证主线程操作。
5. 修复 LoadMore、Gradle 依赖和 Room schema。
6. 编译受影响模块，执行静态检查，并同步更新相关 `AGENTS.md`。

## 6. 验证标准

- 业务失败不会执行成功回调；协程取消不会进入错误回调。
- 删除/更新等无响应体请求成功后仍执行预期动作。
- 快速刷新与加载更多不存在共享页码判断，旧请求会被取消或忽略。
- 上传请求日志不包含 BODY、Token、临时密钥、文件内容或完整上传 URL。
- `AuthInterceptor` 不再使用 `runBlocking`。
- 各业务返回结果只被对应页面消费；导航栈仅在主线程修改。
- 加载更多不主动滚动列表。
- Gradle 依赖显式，Room schema 文件能够生成并纳入版本管理。
- 受影响 Kotlin 编译任务通过，`git diff --check` 通过。

## 7. 实施记录

- [x] 完成高优先级问题复审并确定修改边界。
- [x] 完成结果语义与取消传播修复。
- [x] 完成分页基类及 7 个子类迁移。
- [x] 完成认证与上传安全修复。
- [x] 完成导航结果隔离与线程约束。
- [x] 完成 LoadMore、Gradle 与 Room 修复。
- [x] 完成编译验证并记录结果。

## 8. 实施结果（2026-08-24）

已完成以下变更：

- `ResultHandler` 的业务成功、非空数据成功和无响应体成功已拆分为明确入口，均返回可取消的 `Job`；`asResult` 和 Handler 都重新抛出取消异常。
- 分页基类改为使用请求级页码快照，初始化/刷新取消旧请求，加载更多成功后才提交页码；7 个子类及首页组合接口已迁移。
- 认证 MMKV 数据源改为同步读取并缓存解析结果，OkHttp 拦截器不再使用 `runBlocking`。
- 上传客户端移除 BODY 日志与 Chucker；文件改为流式 RequestBody，输入流和 Response 使用 `use`，并删除上传 URL 日志与未使用的旧日志拦截器。
- 全局刷新 Key 已删除，替换为地址、反馈、订单和支付结果 Key；导航命令统一投递到主线程。
- `LoadMore` 不再改变 LazyList 滚动位置；列表/网格加载回调使用最新闭包。
- `core:common` 显式依赖 `core:util`，`core:data` 不再通过 `api` 暴露 util。
- Room 已开启 schema 导出，并生成 `core/database/schemas/com.joker.coolmall.core.database.AppDatabase/1.json`。
- 相关 core 模块 `AGENTS.md` 已同步为修改后的约束。

验证结果：

```text
./gradlew :core:result:compileDevDebugKotlin \
  :core:datastore:compileDevDebugKotlin \
  :core:network:compileDevDebugKotlin \
  :core:navigation:compileDevDebugKotlin \
  :core:common:compileDevDebugKotlin \
  :core:ui:compileDevDebugKotlin \
  :core:database:compileDevDebugKotlin

./gradlew :core:common:compileDevDebugKotlin \
  :core:ui:compileDevDebugKotlin \
  :feature:common:compileDevDebugKotlin \
  :feature:feedback:compileDevDebugKotlin \
  :feature:goods:compileDevDebugKotlin \
  :feature:main:compileDevDebugKotlin \
  :feature:market:compileDevDebugKotlin \
  :feature:user:compileDevDebugKotlin \
  :feature:order:compileDevDebugKotlin

./gradlew :app:compileDevDebugKotlin
```

最终三组编译均通过，`git diff --check` 通过。整库编译仍报告一条原有警告：`ChatViewModel.kt:265` 对非空 `AppState` 使用了不必要的安全调用；该文件不在本轮修改范围。
