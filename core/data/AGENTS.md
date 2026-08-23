# core/data/AGENTS.md

## 适用范围

本文件适用于 `core/data/**`。本模块是 Repository 与应用级状态层，位于 Feature/ViewModel 和 network、database、datastore 之间。

## 当前数据流

```text
Feature ViewModel -> Repository -> Network/DataBase/Store DataSource
                              -> model.NetworkResponse / domain model
```

- 网络 Repository 注入对应的 `*NetworkDataSource`，当前单次请求统一以 `flow { emit(...) }.flowOn(Dispatchers.IO)` 暴露。
- Room Repository 委托 `CartDataSource`、`FootprintDataSource`、`SearchHistoryDataSource`，查询保留响应式 `Flow`，写入使用 `suspend`。
- 本地认证和用户信息通过 `AuthStoreRepository`、`UserInfoStoreRepository` 访问。
- 文件上传 Repository 直接转交 DataSource 已提供的 Flow，不重复包裹。

## Repository 规则

- Feature 和 ViewModel 只依赖 Repository，不绕过本模块直接调用 Service、DAO 或 Store DataSource。
- 一个后端领域对应现有 Repository 和 DataSource；新增接口时同步补齐 network 的 Service、DataSource 接口与实现。
- 保持请求类型、响应泛型和空值语义与 Service 契约一致，不用 `Any` 替代已有明确模型。
- 不在 Repository 中处理 Compose 状态、Toast、导航或 Android View。
- 调度策略集中在数据层；不要让调用方为每个数据库或网络请求重复切换到 IO。
- 不无故吞掉异常或把失败伪装为成功；网络结果继续交由 `NetworkResponse`/`core:result` 处理。
- 本地数据的领域模型转换属于 database DataSource；Repository 不复制同一套 Entity 映射。
- `@Singleton` 只用于确实需要共享实例或状态的 Repository，保持 Hilt 构造注入风格。

## AppState

- `AppState` 是登录状态、用户 ID、认证信息和用户信息的应用级统一状态源。
- `initialize()` 使用 `@ApplicationScope` 加载持久化状态；必须在 MMKV 初始化后调用。
- 登录、资料更新和退出必须同时维护内存 StateFlow 与对应 Store，避免两个状态源分叉。
- 对外继续暴露不可变 `StateFlow`，状态修改集中在 `AppState` 方法中。
- 修改退出流程时同时检查认证信息、用户信息及所有派生状态是否被清理。
- 应用级 Scope 使用 `SupervisorJob + Dispatchers.Default`；不要在其中启动没有结束条件的 Feature 任务。
- 不把 Activity、Context、NavController 或 ViewModel 保存到 `AppState`。

## 依赖边界

本模块依赖 `core:model`、`core:network`、`core:datastore`、`core:database`、`core:result` 和内部实现依赖 `core:util`。不要通过 `api` 暴露内部依赖；上层直接使用某模块能力时必须声明自己的依赖。新增依赖前先确认能力是否应放在已有下层模块，禁止依赖 Feature、UI 或 app。

修改公共 Repository 方法或 `AppState` 状态时，先搜索所有调用方；它们是跨 Feature API。

最小验证：

```bash
./gradlew :core:data:compileDevDebugKotlin
```
