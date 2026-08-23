# core/model/AGENTS.md

## 适用范围

本文件适用于 `core/model/**`。本模块保存跨层共享的数据契约：通用 ID、领域实体、请求参数、网络响应与 Preview 数据。

## 目录职责

- `common`：`Id`、`Ids` 等基础参数。
- `entity`：商品、订单、用户、地址、购物车等共享模型。
- `request`：明确的接口请求体和分页参数。
- `response`：`NetworkResponse<T>`、分页元数据及组合响应。
- `preview`：仅供 Compose Preview 使用的稳定样例数据。

## 模型规则

- 模型保持数据化，不加入 Android Context、View、Repository、Service、DAO、导航或 UI 状态。
- 网络/持久化模型继续使用 Kotlin Serialization；服务端字段名不一致时用 `@SerialName` 明确映射。
- 字段名、类型、可空性、默认值、枚举值与自定义 Serializer 都是序列化契约，修改前检查 network、database、datastore 和全部 Feature 调用方。
- 对服务端可缺失或历史数据可能没有的字段提供符合接口事实的默认值/可空值，不用随意非空断言。
- 金额单位必须沿用调用链现有约定；不要在同一模型中无说明地混用元、分、`Int` 与 `Double`。
- 请求模型只包含接口需要的字段；不要把 ViewModel、Context 或可变 UI 对象作为参数。
- 能用明确模型表达时不要新增 `Any` 或 `Map<String, Any>`；保留既有宽泛接口时，不继续扩大其使用范围。
- 自定义 Serializer 必须覆盖服务端当前实际返回形态，并为未知/错误输入提供可预期行为。
- Preview fixtures 不应被生产数据流引用，也不得访问网络、数据库或 Hilt。

## NetworkResponse 与分页

- `NetworkResponse<T>` 的成功判定、code、message、data 由 `core:result` 和所有 Repository 共同依赖，属于高影响公共 API。
- `NetworkPageData<T>` 与 `NetworkPageMeta` 的 `list/total/size/page` 语义必须和 `BaseNetWorkListViewModel` 分页算法一致。
- 调整响应包装前同步修改 Service、DataSource、Repository、ResultHandler 和页面状态处理。

## 构建边界

本模块只依赖 Kotlin Serialization，不应依赖其他业务 Core、Compose、Hilt、Retrofit、Room、Feature 或 app。`ExperimentalSerializationApi` 已在所有 source set 开启；新增用法仍应控制在确有必要的序列化代码内。

最小验证：

```bash
./gradlew :core:model:compileDevDebugKotlin
```
