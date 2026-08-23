# core/datastore/AGENTS.md

## 适用范围

本文件适用于 `core/datastore/**`。名称虽为 datastore，当前实际实现是基于 `MMKVUtils` 的认证信息和用户信息键值存储。

## 模块结构

- `AuthStoreDataSource` / `Impl`：保存、读取、清除 `Auth`，提供 token 与登录判断。
- `UserInfoStoreDataSource` / `Impl`：保存、读取、局部更新和清除 `User`。
- `DataStoreModule`：将接口与实现以 Hilt Singleton 绑定。

## 持久化规则

- 上层只依赖 DataSource 接口；实现继续通过 Hilt `@Binds` 提供。
- MMKV key（当前包括 `auth_info`、`user_info`）与序列化 JSON 都是跨版本契约，重命名或改格式必须兼容旧数据。
- `Json { ignoreUnknownKeys = true }` 用于前向兼容；新增字段优先提供默认值或可空值。
- 认证 token 和用户资料不得写入日志、异常消息或分析事件。
- `Auth.shouldRefresh()` 是刷新判断的模型语义，不在多个层重复实现另一套算法。
- 局部更新用户信息时只接受可序列化的受支持类型；新增复杂类型前改成明确模型更新，不继续扩大 `Map<String, Any?>` 的隐式协议。
- 读取损坏数据时保持可恢复行为；若增加清理或上报，不能泄露原始认证 JSON。
- `AuthStoreDataSource` 是同步 MMKV API，并缓存已解析的 Auth；保存和清除时必须同步更新缓存。
- `UserInfoStoreDataSource` 目前保留 `suspend` API。不要为了调用同步认证读取而使用 `runBlocking`。

## 初始化与边界

- 使用本模块前必须由 app 完成 `MMKVUtils.init(application)`。
- 不在本模块保存 Context、Activity 或 UI 对象。
- 不放置网络刷新、Repository 协调、页面状态或 Compose 逻辑。
- 本模块依赖 `core:util`、`core:model` 和 Kotlin Serialization；禁止依赖 Feature、UI、network 或 app。

新增一类持久化数据时，按“接口、实现、稳定 key、序列化策略、Hilt 绑定、清除策略”成套实现，并检查退出登录是否需要清除它。

## 测试重点

- Auth 存储测试覆盖首次读取、缓存命中、保存后缓存更新、清除后缓存失效和损坏 JSON 回退。
- User 存储测试覆盖完整保存、局部更新、未知字段兼容、非法字段类型和清除行为。
- 序列化测试使用虚构 token 和用户资料，禁止复制真实账号数据到 fixture、日志或失败消息。
- 修改 key 或 JSON 结构时，加入旧格式兼容或迁移测试；不能只验证新安装场景。

最小验证：

```bash
./gradlew :core:datastore:compileDevDebugKotlin :core:datastore:testDevDebugUnitTest
```
