# 订单与客服聊天分页并发优化说明

## 1. 文档目的

本文记录 `OrderListViewModel` 与 `ChatViewModel` 自定义分页实现中仍存在的共享页码、旧响应提交和消息覆盖问题，并明确修改方案、影响范围与验证标准。

这两个 ViewModel 没有继承 `BaseNetWorkListViewModel`，因此未包含在上一轮 Core 分页基类优化中。本文只收敛它们自身的分页和消息合并逻辑，不重写订单 Tab 架构、聊天 WebSocket 协议或页面交互。

涉及文件：

- `feature/order/.../OrderListViewModel.kt`
- `feature/cs/.../ChatViewModel.kt`
- `feature/cs/src/test/.../ChatMessageMergeTest.kt`

## 2. 审计结论与优先级

| 优先级 | 问题 | 主要风险 | 本轮决策 |
| --- | --- | --- | --- |
| P1 | 订单请求与响应都读取共享 `pageIndices` | 刷新、加载更多或订单变更事件交错时，旧响应可能按新页码覆盖或追加 | 修改 |
| P1 | 订单每个 Tab 没有独立请求身份与取消任务 | 已失效请求仍可提交，非当前 Tab 的旧请求也可能污染缓存 | 修改 |
| P1 | 聊天响应阶段读取共享 `currentPage` | 页码判断、失败回退和数据追加依赖可变状态 | 修改 |
| P1 | 聊天首屏响应直接覆盖消息列表 | 历史请求期间到达的 WebSocket 消息可能丢失 | 修改 |
| P2 | WebSocket 回调直接执行消息列表的读改写 | 回调线程与 ViewModel 请求回调可能并发修改列表 | 修改为投递到 `viewModelScope` |
| P3 | `AppState` 非空参数仍使用安全调用 | 产生无意义编译警告 | 随本轮清理 |

## 3. 订单列表分页问题

### 3.1 当前逻辑

加载更多先修改共享页码，再发起请求：

```kotlin
_loadMoreStates[tabIndex].value = LoadMoreState.Loading
pageIndices[tabIndex]++
loadListData(tabIndex)
```

构造请求时读取共享页码：

```kotlin
OrderPageRequest(
    page = pageIndices[tabIndex],
    size = pageSize,
    status = getStatusFilter(tabIndex),
)
```

响应返回后再次读取同一个共享页码，判断本次响应应覆盖还是追加：

```kotlin
when {
    pageIndices[tabIndex] == 1 -> {
        _listDataMap[tabIndex].value = newList
    }
    else -> {
        _listDataMap[tabIndex].value =
            _listDataMap[tabIndex].value + newList
    }
}
```

错误时再回退共享页码：

```kotlin
pageIndices[tabIndex]--
```

### 3.2 隐患

页码描述的是“当前已提交的数据页”，但当前实现把它提前改成“正在请求的页”。如果请求期间发生下拉刷新、订单状态变化刷新或其他页码修改，旧响应会读取修改后的值：

```text
Tab A 发起第 2 页请求，pageIndices[A] = 2
  -> 订单变更事件将 Tab A 重置为第 1 页
  -> 第 2 页旧响应返回
  -> 响应读取 pageIndices[A] == 1
  -> 第 2 页数据被当作首屏覆盖
```

每个 Tab 当前也没有保存请求 `Job` 或请求序号。即使 UI 状态阻止了多数重复点击，它也不能阻止业务刷新事件、延迟状态任务和已在途响应交错。

此外，加载更多成功后通过独立协程延迟 400ms 再追加数据。如果延迟期间发生刷新，这个延迟任务仍可能把旧页追加到刷新后的列表。

### 3.3 优化方案

为每个订单 Tab 分别维护：

- 已成功提交的页码；
- 当前请求 `Job`；
- 延迟状态更新 `Job`；
- 单调递增的请求序号。

所有请求显式携带不可变快照：

```kotlin
startRequest(
    tabIndex = tabIndex,
    requestPage = pageIndices[tabIndex] + 1,
    loadType = LoadType.LoadMore,
)
```

回调只使用 `requestPage` 和 `loadType`，不再用共享页码判断本次响应：

```kotlin
onSuccess = { response ->
    if (requestId == requestSequences[tabIndex]) {
        handleSuccess(
            tabIndex = tabIndex,
            requestPage = requestPage,
            loadType = loadType,
            data = response.data,
        )
    }
}
```

页码只在响应通过身份检查、数据真正提交后更新。刷新和重试取消该 Tab 的旧请求以及旧的延迟任务；加载更多失败不需要回退页码。

订单变更事件对非当前 Tab 只标记为待重新加载，并取消旧请求；当前 Tab 使用静默刷新，保留现有列表直到新数据成功返回。

### 3.4 收益

- 七个 Tab 的请求互不干扰；
- 旧响应和旧延迟任务不能覆盖新状态；
- 已展示数据与已提交页码保持一致；
- 刷新、重试和加载更多使用明确的加载类型；
- 失败不再依赖容易出错的页码回滚。

## 4. 客服聊天分页与实时消息问题

### 4.1 当前分页逻辑

聊天加载更多同样先递增共享页码：

```kotlin
currentPage++
loadHistoryMessages()
```

请求参数在发起时读取 `currentPage`，但成功与失败回调又继续读取共享值：

```kotlin
if (currentPage == 1) {
    _messages.value = newMessages
} else {
    _messages.value = currentMessages + uniqueNewMessages
}

if (currentPage > 1) {
    currentPage--
}
```

这使响应语义取决于返回时的可变状态，而不是发起请求时的页码。

### 4.2 WebSocket 消息覆盖

建立会话后，历史请求和 WebSocket 会同时启动。历史首屏成功时执行：

```kotlin
_messages.value = newMessages
```

如果 WebSocket 消息在历史请求返回前已经插入 `_messages`，首屏赋值会覆盖整个列表，新消息会从界面消失。

WebSocket 的回调线程还直接调用 `addNewMessage`，而历史请求回调运行在 `viewModelScope`。两边都执行列表的“读取—修改—写回”，存在并发覆盖窗口。

### 4.3 优化方案

- 保存当前历史请求 `Job` 和请求序号；
- 请求显式接收 `requestPage` 与加载类型；
- `currentPage` 只表示已成功提交的页码；
- 刷新或首屏请求取消旧历史请求；
- WebSocket 消息回调先投递到 `viewModelScope`，让消息变更与历史响应在同一作用域串行处理；
- 发起首屏请求时记录已有消息 ID；响应时保留请求期间新到的消息，再与服务端首屏按 ID 去重合并；
- 加载更多把旧页追加到列表尾部，并按 ID 去重；
- 为两种纯消息合并规则增加单元测试。

首屏合并的目标逻辑：

```kotlin
val liveMessages = currentMessages.filter { message ->
    message.id !in messageIdsAtRequestStart
}

messages.value = (liveMessages + pageMessages).distinctBy(CsMsg::id)
```

加载更多目标逻辑：

```kotlin
messages.value = (currentMessages + pageMessages).distinctBy(CsMsg::id)
```

### 4.4 收益

- 聊天分页响应与请求页码一一对应；
- 取消的历史请求无法提交旧结果；
- WebSocket 实时消息不会被首屏历史响应覆盖；
- 重叠页数据不会产生重复消息；
- 消息列表的读改写集中到 ViewModel 作用域，降低并发覆盖风险。

## 5. 修改边界与牵连

本轮修改：

- `OrderListViewModel` 的分页任务、请求快照、按 Tab 取消与响应提交；
- `ChatViewModel` 的历史分页任务、请求快照、实时消息调度和去重合并；
- 聊天消息合并纯函数的单元测试；
- 清理 `appState?.auth` 的无效安全调用警告。

本轮不修改：

- 订单页面 Pager 与 Tab 选中状态架构；
- 订单筛选条件、每页数量和七个业务状态定义；
- 聊天消息排序协议、WebSocket 重连策略和已读协议；
- `SpecSelectModal` 重新打开时是否重置规格与数量；
- Navigation result token 化等长期架构调整。

调用层继续使用现有 `onRefresh`、`onLoadMore`、`retryRequest` 和状态 Flow，正常情况下不需要修改 Screen 接口。

## 6. 验证标准

- 订单加载更多不再提前改变已提交页码；
- 同一 Tab 刷新时会取消旧请求和旧延迟任务；
- 一个 Tab 的请求状态不会影响其他 Tab；
- 订单变更刷新后，旧响应不能覆盖新列表；
- 聊天加载更多失败后无需回退页码，再次重试仍请求同一下一页；
- 聊天首屏请求期间收到的 WebSocket 消息在响应后仍存在；
- 重叠历史页不会产生重复消息；
- ViewModel 清理时取消不会被当作普通错误提交；
- 聊天消息合并单元测试通过；
- `:feature:order:compileDevDebugKotlin`、`:feature:cs:compileDevDebugKotlin`、相关单元测试和 `:app:compileDevDebugKotlin` 通过；
- `git diff --check` 通过。

## 7. 实施顺序

1. 重构订单分页为按 Tab 隔离的请求快照与请求身份。
2. 重构聊天历史分页为单请求快照与可取消任务。
3. 将 WebSocket 回调投递到 `viewModelScope`。
4. 增加首屏与加载更多消息去重合并函数及单元测试。
5. 编译两个 feature 与 app，执行测试和静态检查。

## 8. 当前状态

- [x] 完成问题复审与修改边界确认。
- [x] 完成技术优化说明文档。
- [x] 完成订单分页修改。
- [x] 完成聊天分页与消息合并修改。
- [x] 完成测试与编译验证。

## 9. 实施结果（2026-08-24）

实际完成内容：

- 订单七个 Tab 分别维护请求 `Job`、延迟状态 `Job` 和请求序号；
- 订单请求显式携带 `requestPage` 和 `LoadType`，响应不再读取共享页码判断覆盖或追加；
- 订单页码只在响应身份有效且数据真正提交后推进，加载更多失败不再回退页码；
- 订单刷新、重试和业务结果刷新会取消对应 Tab 的旧请求与旧延迟任务；
- 聊天历史请求保存 `Job` 和请求序号，`currentPage` 只表示已成功提交的历史页；
- WebSocket 消息与连接状态回调统一投递到 `viewModelScope`；
- 聊天首屏历史响应保留请求期间新到的实时消息，加载更多按 ID 去重追加；
- 新增三个消息合并单元测试，覆盖实时消息保留、陈旧首屏清理和重叠历史页去重；
- `markMessagesAsRead` 保留协程取消语义，并清理 `AppState` 的无效安全调用。

验证命令：

```text
./gradlew :feature:order:compileDevDebugKotlin \
  :feature:cs:compileDevDebugKotlin \
  :feature:cs:testDevDebugUnitTest

./gradlew :feature:order:testDevDebugUnitTest \
  :feature:cs:testDevDebugUnitTest \
  :app:compileDevDebugKotlin
```

两组编译与测试均通过；Kotlin 编译不再报告 `ChatViewModel` 的无效安全调用警告。
