# core/result/AGENTS.md

## 适用范围

本文件适用于 `core/result/**`。本模块统一将 Flow 转换为 Loading/Success/Error，并解释 `NetworkResponse<T>` 的业务成功与失败。

## 当前职责

- `Result<T>`：加载、成功和异常三态。
- `Flow<T>.asResult()`：在开始时发出 Loading，将值包装为 Success，将上游异常包装为 Error。
- `ResultHandler`：在给定 `CoroutineScope` 中收集结果，检查 `NetworkResponse.isSucceeded`，回调数据/错误，并按配置记录日志和显示 Toast。

## 使用规则

- 页面级统一请求可使用 `BaseNetWorkViewModel`；页面内独立操作可直接使用 `ResultHandler`，不要在每个 ViewModel 复制同一套解析。
- 调用方必须传入有明确生命周期的 Scope，通常是 `viewModelScope`；禁止使用无归属的全局 Scope。
- `showToast` 只控制通用错误提示；需要页面内联错误时关闭 Toast 并通过 `onError` 更新 UI 状态。
- `handleResult` 的 `onSuccess` 只在业务成功后接收完整响应，允许 `data == null`。
- `handleResultWithData<T : Any>` 只用于协议保证返回非空数据的接口；业务成功但 data 为空会进入 `onError`。
- 删除、更新等 `NetworkResponse<Unit>` 使用 `handleResultWithoutData`，不要借用 `handleResultWithData` 等待不存在的响应体。
- 需要取消旧请求时保存 `ResultHandler` 返回的 `Job`；不要在外层再套一个仅用于启动收集的 `launch`。
- `onFinally` 必须适合成功、失败和取消后的收尾，不能在其中无条件写入“成功”。
- 不在本模块加入 Feature 文案、页面导航或具体业务状态。

## 异常与兼容性

- 修改 `Result` 三态、`asResult()` 发射顺序或 `NetworkResponse.isSucceeded` 处理会影响所有网络 ViewModel，必须先搜索调用方。
- 协程取消必须保持可传播；新增 catch/try-catch 时不要把 `CancellationException` 当普通网络错误消费。
- 日志不得包含 token、密码、验证码、完整认证响应或其他敏感请求体。
- 用户可见错误优先使用后端 message 或稳定的通用文案，不直接展示完整堆栈。
- ToastUtils、LogUtils 依赖 app 已完成初始化；不要让本模块持有 Context。
- README 示例变更时必须与实际函数签名和默认参数一致。

## 依赖与验证

本模块依赖 `core:model`、`core:util` 和 Serialization，不依赖 Feature、Compose、Repository 或 app。

```bash
./gradlew :core:result:compileDevDebugKotlin
```
