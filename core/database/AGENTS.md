# core/database/AGENTS.md

## 适用范围

本文件适用于 `core/database/**`。本模块维护 Room 数据库、DAO、持久化 Entity、类型转换器以及面向 Repository 的数据库 DataSource。

## 结构约定

```text
AppDatabase -> DAO -> Entity
                   -> DataSource -> core:model 领域模型
```

- `AppDatabase` 集中注册 Entity、DAO 和 `CartSpecConverter`。
- DAO 使用 `suspend` 完成一次性写入/查询，持续观察的数据返回 `Flow`。
- DataSource 负责 Entity 与 `core:model` 模型之间的双向转换。
- Hilt 依赖在 `DatabaseModule` 中以 Singleton 提供数据库和 DAO。

## Room 规则

- 表名、列名、主键、类型转换结果和数据库名都是持久化契约，不得当作普通重命名处理。
- 新增 Entity 后必须同步加入 `@Database(entities = [...])`，新增 DAO 后同步增加抽象方法和 Hilt Provider。
- 修改表结构必须提升数据库 version，并提供可验证的 Migration；未经明确授权不要使用 destructive migration。
- `exportSchema = true`，schema 输出到 `core/database/schemas` 并纳入版本管理；结构或版本变化时必须同步提交生成的 JSON。
- SQL 查询的排序、Limit、冲突策略和删除粒度属于业务行为，修改前检查 Repository 和页面预期。
- 保持 Flow 查询响应式，不在 DataSource 中把它们转换为一次性快照。
- 不在主线程执行 Room 阻塞操作，不引入 `allowMainThreadQueries()`。

## Entity 与模型映射

- Entity 只描述本地结构，Feature 不直接依赖 Entity。
- DataSource 返回 `core:model` 类型；新增字段时双向映射必须同时更新。
- `CartSpecConverter` 的 JSON 是已落盘格式。调整 `CartGoodsSpec` 或序列化配置时保证旧数据仍能解码。
- 解码失败当前回退为空列表；若改变失败策略，需要评估现有用户数据和购物车行为。
- 时间字段继续使用明确的毫秒时间戳语义，不混入格式化字符串。
- 对购物车规格的计数、删除最后一项等现有规则属于 DataSource 行为，修改时验证边界值和并发写入。

## 边界

本模块可依赖 `core:model` 与 Room/Serialization，不依赖 network、Feature、Compose 或 app。不在这里实现远端同步、Toast、导航和页面状态。

## 测试重点

- DAO 行为测试覆盖插入、更新、冲突策略、排序、删除粒度和持续观察 Flow，不只验证 SQL 能执行。
- `CartSpecConverter` 变更必须使用已发布格式样例验证旧 JSON 可解码，并覆盖损坏数据的当前回退行为。
- 修改 Schema 时保留旧版本 schema，使用 Room Migration 测试从受支持旧版本升级到最新版本，并校验关键数据仍然存在。
- Migration 测试需要设备或模拟器时使用 `androidTestImplementation` 和 `connectedDevDebugAndroidTest`；不要把 Room 测试库放入生产依赖。

最小验证：

```bash
./gradlew :core:database:compileDevDebugKotlin :core:database:testDevDebugUnitTest
```

涉及数据库结构时还必须比较 schema、执行 Migration 测试，并在可用设备上验证旧版本数据升级。
