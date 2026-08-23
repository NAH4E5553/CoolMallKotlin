# core/ui/AGENTS.md

## 适用范围

本文件适用于 `core/ui/**`。本模块提供带应用视觉语义的可复用 Compose 组件，建立在 `core:designsystem` 之上。

## 当前组件范围

- 应用栏、按钮、卡片、列表项、标签、文本、图片和脚手架。
- 商品、订单、地址、优惠券等跨 Feature 展示组件。
- Empty、Loading、网络状态视图和加载更多。
- Dialog、BottomModal、图片选择器、Swiper、Rate、Stepper。
- 自定义下拉刷新 `RefreshLayout`、`RefreshState` 与列表/网格内容。

Feature 专属、只使用一次或包含页面业务流程的组件留在对应 Feature，不为追求复用强行放入本模块。

## Compose 组件规则

- 公共组件保持状态提升：数据和事件通过参数传入，不注入 ViewModel、Repository、Service 或 DAO。
- 参数默认包含 `modifier: Modifier = Modifier`；不要丢弃调用方 Modifier，也不要无条件覆盖尺寸和点击行为。
- 组件读取主题和资源，业务判断交由调用方；不在组合期间发起网络或持久化写入。
- 文案放入 `res/values/strings.xml`，用户可见文本不硬编码；新增文案同步维护已有语言资源。
- 图片继续使用 Coil 封装，动画使用 Lottie；新增库前确认现有能力不能满足。
- 可交互组件提供语义、contentDescription、禁用态和足够点击区域。
- 新增或显著修改公共组件时提供/更新 Preview，Preview 使用 `core:model/preview` 或本地假数据，不依赖 Hilt 和网络。
- 改动公共签名、默认颜色、间距或动画时先搜索所有调用方，避免全局视觉回归。

## 网络与刷新组件

- `BaseNetWorkView` 与 `BaseNetWorkListView` 只负责渲染 `core:common` 的状态和转发重试，不拥有请求逻辑。
- Loading、Error、Empty、Success 必须保持可区分；自定义 slot 不应改变状态机语义。
- `RefreshLayout` 的 `isRefreshing` 由外部权威状态驱动，组件内部只管理手势偏移和动画。
- 加载更多继续由 `LoadMoreState` 和 `shouldTriggerLoadMore` 控制；避免在重组中重复调用 `onLoadMore`。
- `LoadMore` 只渲染状态和转发重试，不得主动滚动调用方的 LazyList/Grid。
- 修改 NestedScroll、阈值或动画时验证列表、瀑布流、顶部 AppBar 联动、快速手势和刷新结束。
- 传入 LazyListState/GridState 时应复用调用方状态，不能额外创建第二个竞争状态源。

## Insets、弹窗与长内容

- 固定在窗口底部的操作栏必须处理系统导航栏 Insets，并验证经典三键导航和手势导航；不能只依赖设备默认留白。
- 同一层级只消费一次 Insets，避免 Scaffold、页面和底栏重复添加 padding。
- BottomModal 的标题、长内容和底部操作区要有明确约束；内容超出可用高度时必须可滚动，操作按钮不能被压缩成零高度或一条线。
- 弹窗内 LazyList/Grid 与 BottomSheet 拖动要明确 NestedScroll 所有权，验证顶部下拉、列表上下滑和快速连续手势不会弹跳。
- 涉及输入框时同时验证 IME、导航栏和 BottomModal 的组合 Insets。

## 测试重点

- 可独立的状态计算和阈值逻辑使用 JVM 单元测试；交互、语义和布局行为使用 Compose UI 测试或目标页面验证。
- 修改刷新、加载更多、弹窗或底部操作栏时，至少覆盖空数据、长内容、字体缩放、经典三键导航和手势导航。

## 模块边界与验证

本模块可依赖 `core:designsystem`、`core:model`、`core:common`、`core:util`、Coil 和 Lottie；不依赖 Feature、Repository、network、database 或 app。

```bash
./gradlew :core:ui:compileDevDebugKotlin :core:ui:testDevDebugUnitTest
```

视觉变更还需检查 Preview、亮/暗主题、字体缩放和目标页面。
