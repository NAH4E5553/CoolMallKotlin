# MainScreen 页面状态与渲染逻辑优化说明

## 1. 文档目的

本文说明 `MainScreen` 当前实现中页面状态管理、页面映射和生命周期状态收集存在的问题与潜在风险，并给出后续修改方案、关键代码示例、预期收益及影响范围。

实施状态：已于 2026-08-23 按本文最终建议完成代码修改，并通过 `:feature:main:compileDevDebugKotlin` 编译验证。文档中“修改前”章节保留原始代码和问题证据，“优化后”章节对应当前实现。

本文中的问题并非都代表当前已经稳定复现的线上故障。其中，双状态源属于当前调用路径下能够维持运行、但扩展后容易失效的脆弱设计；数字索引映射属于明确的可维护性隐患；生命周期收集属于规范一致性和资源使用优化。实施时应根据优先级分开处理，避免在一次修改中同时改变状态架构、页面映射和交互效果。

涉及的主要文件：

- `view/MainScreen.kt`
- `viewmodel/MainViewModel.kt`
- `model/TopLevelDestination.kt`
- `component/BottomNavigationBar.kt`
- `navigation/MainGraph.kt`
- `view/HomeScreen.kt`
- `view/CategoryScreen.kt`
- `view/CartScreen.kt`
- `view/MeScreen.kt`

## 2. 修改前结构概览

主页面由一个 `HorizontalPager` 和底部导航栏组成，包含首页、分类、购物车和个人中心四个页面。

修改前，当前页面索引同时保存在两个位置：

1. `MainViewModel.currentPageIndex`
2. `PagerState.currentPage`

数据流如下：

```text
点击底部导航项
  ├─ updateDestination(index) 更新 MainViewModel
  └─ scrollToPage(index) 更新 PagerState

左右滑动页面
  └─ PagerState.currentPage 变化
       └─ updatePageIndex(index) 回写 MainViewModel

底部导航栏选中状态
  └─ 读取 MainViewModel.currentPageIndex

实际显示页面
  └─ 读取 PagerState.currentPage
```

这意味着底栏选中状态和实际页面分别由不同对象驱动，需要依赖额外的同步逻辑维持一致。

按照当前代码已有的两条切页路径，两个状态通常能够保持一致：底栏点击会同时更新 ViewModel 和 Pager，手势滑动后也会从 Pager 回写 ViewModel。因此，该问题当前更准确地说是“存在两个需要人工同步的状态源”，而不是“现有功能必然显示错误”。风险主要会在增加新的状态入口、异步切页或恢复逻辑后放大。

## 3. 问题一：修改前页面存在两个状态源

### 3.1 修改前处理逻辑

`MainRoute` 收集 ViewModel 中的当前页面索引，并向下传递两个功能相同的更新回调：

```kotlin
val currentPageIndex by viewModel.currentPageIndex.collectAsState()

MainScreen(
    currentPageIndex = currentPageIndex,
    onPageChanged = viewModel::updatePageIndex,
    onNavigationItemSelected = viewModel::updateDestination,
)
```

`MainScreen` 又根据该索引创建自己的 `PagerState`：

```kotlin
val pageState = rememberPagerState(
    initialPage = currentPageIndex,
) {
    TopLevelDestination.entries.size
}
```

Pager 改页后，将结果回写 ViewModel：

```kotlin
LaunchedEffect(pageState.currentPage) {
    onPageChanged(pageState.currentPage)
}
```

点击底部导航项时，则分别修改 ViewModel 和 PagerState：

```kotlin
onNavigateToDestination = { index ->
    onNavigationItemSelected(index)
    scope.launch {
        pageState.scrollToPage(index)
    }
}
```

底部导航栏读取的是 ViewModel 状态：

```kotlin
currentPageIndex = currentPageIndex
```

而 `HorizontalPager` 实际显示的页面由 `pageState` 决定：

```kotlin
HorizontalPager(state = pageState) { page ->
    // 根据 page 显示页面
}
```

### 3.2 劣势与潜在问题

#### 3.2.1 `initialPage` 不是持续同步机制

`rememberPagerState(initialPage = currentPageIndex)` 中的 `initialPage` 只用于 PagerState 首次创建。组合完成后，即使 `currentPageIndex` 再次变化，也不会自动将 Pager 切换到对应页面。

因此，如果未来出现以下入口，底栏和页面可能不一致：

- 恢复业务状态后主动指定页面；
- 登录完成后切换到“我的”；
- 收到购物车事件后切换到购物车页；
- 测试代码直接更新 `currentPageIndex`；
- 以后为 `MainRoute` 增加初始 Tab 参数。

可能出现的状态是：

```text
MainViewModel.currentPageIndex = 2  // 底栏选中购物车
PagerState.currentPage = 0         // 实际仍显示首页
```

#### 3.2.2 一次点击产生重复状态更新

点击底栏时先执行 `updateDestination(index)`，Pager 切换后 `LaunchedEffect` 又执行一次 `updatePageIndex(index)`。

目前两个方法都只是执行同一条赋值：

```kotlin
fun updateDestination(index: Int) {
    _currentPageIndex.value = index
}

fun updatePageIndex(index: Int) {
    _currentPageIndex.value = index
}
```

虽然 `StateFlow` 对相同值通常不会再次通知订阅者，但这仍然造成了：

- 重复的调用路径；
- 不必要的状态同步代码；
- 后续在任一方法加入统计、校验或副作用时容易出现行为差异；
- 测试必须同时覆盖点击、滑动和双向同步。

#### 3.2.3 更新不是原子操作

点击底栏时，ViewModel 状态先更新，Pager 在协程中随后更新。两者之间存在一个短暂窗口：底栏已经显示新选中项，但页面仍是旧页面。

如果滚动协程被取消、索引无效，或者后续改成带动画的滚动并发生手势竞争，这个临时不一致可能持续更久。

#### 3.2.4 ViewModel 没有承载实际业务状态

`MainViewModel` 当前只保存一个纯 UI 组件状态，没有数据请求、业务规则或跨页面业务协作。PagerState 本身已经能够表示当前页；`rememberPagerState` 使用可保存状态机制保存 Pager 的页码和偏移，可以覆盖这里所需的 Pager 状态恢复职责。

这里不应进一步推导为“Pager 会永久保存页面内的全部普通 `remember` 状态”。各页面内部状态是否保存，仍取决于 `rememberSaveable`、ViewModel、页面 key 和具体组合生命周期。本次删除 `MainViewModel` 只移除主 Tab 索引，不改变 Home、Category、Cart、Me 各自业务 ViewModel 的获取方式和 owner。

在这种情况下额外使用 ViewModel，不仅没有增加明确能力，反而引入第二个状态源。

### 3.3 优化方案

让 `PagerState` 成为当前页面的唯一状态源：

- 实际显示页面读取 `pageState`；
- 底栏选中状态也读取 `pageState.currentPage`；
- 点击底栏只驱动 `pageState`；
- 用户滑动时，PagerState 自然更新；
- 删除 Pager 与 ViewModel 之间的双向同步；
- 删除不再承担职责的 `MainViewModel`。

优化后的数据流：

```text
点击底部导航项
  └─ scrollToPage(index)
       └─ PagerState.currentPage 更新
            ├─ HorizontalPager 显示对应页面
            └─ BottomNavigationBar 显示对应选中项

左右滑动页面
  └─ PagerState.currentPage 更新
       ├─ HorizontalPager 显示对应页面
       └─ BottomNavigationBar 显示对应选中项
```

### 3.4 优化后的关键代码示例

`MainRoute` 不再注入 `MainViewModel`：

```kotlin
@Composable
internal fun MainRoute(
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null,
) {
    MainScreen(
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
    )
}
```

`MainScreen` 内只维护 PagerState：

```kotlin
@Composable
internal fun MainScreen(
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedContentScope: AnimatedContentScope? = null,
) {
    val scope = rememberCoroutineScope()
    val destinations = TopLevelDestination.entries
    val pageState = rememberPagerState(
        initialPage = 0,
    ) {
        destinations.size
    }

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                destinations = destinations,
                currentPageIndex = pageState.currentPage,
                onNavigateToDestination = { index ->
                    if (index in destinations.indices && index != pageState.currentPage) {
                        scope.launch {
                            pageState.scrollToPage(index)
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        MainScreenContentView(
            pageState = pageState,
            paddingValues = paddingValues,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = animatedContentScope,
        )
    }
}
```

说明：

- `destinations.indices` 防止非法索引传给 Pager；
- 当前页被再次点击时不重复启动滚动协程；
- 示例保留当前已有的 `scrollToPage` 立即切换行为，确保状态架构重构不同时改变交互体验；
- 如果产品明确需要页面切换动画，可以另行评估 `animateScrollToPage`，不应把它视为本次状态优化的必要组成；
- 底栏与页面都读取 `pageState`，不存在同步先后顺序问题。

### 3.5 为什么不建议增加双向 `LaunchedEffect`

另一种看似直接的方案，是同时增加：

```kotlin
LaunchedEffect(currentPageIndex) {
    pageState.scrollToPage(currentPageIndex)
}

LaunchedEffect(pageState.currentPage) {
    onPageChanged(pageState.currentPage)
}
```

这虽然补上了 ViewModel 到 Pager 的同步，却保留了两个状态源，并需要继续处理：

- 首次恢复时谁覆盖谁；
- 两个 Effect 的启动顺序；
- 动画滚动中途的页面变化；
- 非法索引；
- 重复更新和潜在循环。

因此，除非当前页面索引确实是跨界面的业务状态，否则单一 PagerState 更简单、更可靠。

### 3.6 优化收益

- 从两个页面状态源减少为一个；
- 在所有切页操作统一通过 PagerState 驱动的前提下，消除两个长期状态源之间的同步不一致；
- 删除双向同步和重复更新；
- 减少 MainScreen 参数和回调数量；
- 删除无实际业务职责的 ViewModel；
- 降低单元测试和 UI 测试复杂度；
- 后续新增埋点时可以围绕 Pager 的稳定状态统一处理。

## 4. 问题二：修改前 Pager 数量与页面渲染使用不同依据

### 4.1 修改前处理逻辑

Pager 页数来自枚举数量：

```kotlin
val pageState = rememberPagerState {
    TopLevelDestination.entries.size
}
```

但页面内容通过数字索引硬编码：

```kotlin
when (page) {
    0 -> HomeRoute(...)
    1 -> CategoryRoute()
    2 -> CartRoute(navKey = MainRoutes.Cart())
    3 -> MeRoute(...)
}
```

底部导航栏同样使用 `TopLevelDestination.entries`：

```kotlin
BottomNavigationBar(
    destinations = TopLevelDestination.entries,
    // ...
)
```

因此，Pager 数量与底栏来自枚举，但页面内容来自另一份人工维护的索引映射。

### 4.2 劣势与潜在问题

#### 4.2.1 新增枚举时可能产生空白页

如果以后新增一个枚举项，Pager 页数和底栏会自动增加，但 `when (page)` 没有对应分支，新页面将显示空白内容。

由于 `page` 是 `Int`，当前 `when` 不是枚举穷尽检查，编译器不会要求补充分支。

#### 4.2.2 调整枚举顺序可能显示错误页面

例如将购物车调整到第二项后，底栏顺序会变化，但硬编码的 `1 -> CategoryRoute()` 不会变化，最终会出现“点击购物车却显示分类页”的问题。

#### 4.2.3 数字缺少业务语义

`0`、`1`、`2`、`3` 无法直接表达业务含义，代码评审时需要同时对照枚举顺序，增加理解和维护成本。

### 4.3 优化方案

先根据页面索引取得 `TopLevelDestination`，再对枚举做穷尽匹配：

```kotlin
HorizontalPager(
    state = pageState,
    modifier = Modifier.padding(paddingValues),
) { page ->
    when (TopLevelDestination.entries[page]) {
        TopLevelDestination.HOME -> HomeRoute(
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = animatedContentScope,
        )

        TopLevelDestination.CATEGORY -> CategoryRoute()

        TopLevelDestination.CART -> CartRoute(
            navKey = MainRoutes.Cart(),
        )

        TopLevelDestination.ME -> MeRoute(
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = animatedContentScope,
        )
    }
}
```

### 4.4 为什么保留 `CartRoute(MainRoutes.Cart())`

购物车既可以作为主页面中的 Tab 展示，也可以通过 Navigation3 作为独立页面进入。

`MainRoutes.Cart` 包含：

```kotlin
data class Cart(val showBackIcon: Boolean = false) : NavKey
```

`CartViewModel` 通过 assisted injection 读取该参数，以决定是否显示返回按钮。因此主页面中的购物车仍需传入默认的 `MainRoutes.Cart()`，不能简单删除这个参数。

### 4.5 为什么不直接使用 `TopLevelDestination.route` 导航

当前枚举中的 `route` 类型是 `Any`：

```kotlin
enum class TopLevelDestination(
    @StringRes val titleTextId: Int,
    @RawRes val animationResId: Int,
    val route: Any,
)
```

而 `MainGraph` 目前只注册了：

```kotlin
entry<MainRoutes.Main> { /* ... */ }
entry<MainRoutes.Cart> { /* ... */ }
```

`Home`、`Category` 和 `Mine` 并没有作为独立 Navigation3 Entry 注册。直接使用 `route` 会把当前 Pager 内部切页改造成导航栈切页，这属于另一项架构调整，会影响返回栈、ViewModel 作用域和共享转场，不在本次优化范围内。

### 4.6 优化收益

- 页面数量、底栏项目和页面类型使用同一份枚举定义；
- 新增枚举项时由编译器提醒补充页面实现；
- 调整枚举顺序不会导致页面错配；
- 去除无语义的数字分支；
- 不改变现有 Navigation3 返回栈和各页面 ViewModel 作用域。

## 5. 问题三：修改前 StateFlow 收集未统一感知生命周期

### 5.1 修改前处理逻辑

`MainRoute` 当前使用：

```kotlin
val currentPageIndex by viewModel.currentPageIndex.collectAsState()
```

关联页面中也存在相同写法：

- `HomeRoute`：收集首页状态、列表、刷新状态等；
- `CategoryRoute`：收集分类状态和选中索引；
- `CartRoute`：收集购物车列表、编辑状态、选中状态和金额等；
- `MeRoute`：已经使用 `collectAsStateWithLifecycle()`。

`MeRoute` 当前写法可作为模块内统一方式：

```kotlin
val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
val userInfo by viewModel.userInfo.collectAsStateWithLifecycle()
```

### 5.2 劣势与潜在问题

`collectAsState()` 的收集与 Composable 是否仍在组合中相关，但不会自动根据 Android Lifecycle 的 `STARTED/STOPPED` 状态暂停。

应用进入后台、页面被其他界面覆盖但仍保留在组合中时，Flow 可能继续被收集，从而带来：

- 后台仍处理不需要展示的 UI 状态；
- 上游使用 `WhileSubscribed` 时无法按预期停止；
- 高频列表或数据库流产生不必要的工作；
- 同一模块内生命周期策略不一致。

需要注意，“Lifecycle 感知”不等于“Pager 页面滑出屏幕后一定停止收集”。Pager 内的几个 Tab 通常共享 Main Navigation Entry 的 `LifecycleOwner`；当应用仍处于前台时，非当前 Tab 的 Lifecycle 仍可能处于 `STARTED`。因此，`collectAsStateWithLifecycle()` 可以可靠覆盖应用进入后台或对应 Navigation Entry Lifecycle 降级的场景，但不能单独作为 Pager 离屏页面暂停工作的机制。

这项优化的实际收益也取决于上游 Flow：

- Home、Category 中不少状态来自 `MutableStateFlow`，直接性能收益通常有限，主要收益是生命周期语义和模块写法统一；
- Cart 中存在多个 `SharingStarted.WhileSubscribed(5000)`，停止 UI 订阅后能够进一步影响上游共享 Flow，收益更明确；
- 如果以后确实需要非当前 Tab 停止工作，应额外根据 Pager 选中状态控制，而不能只依赖 Lifecycle。

对于当前 `MainRoute`，如果实施 PagerState 单一状态源方案，相关 Flow 和 MainViewModel 会被删除，因此该问题会自然消失。

但 `HomeRoute`、`CategoryRoute` 和 `CartRoute` 的收集仍然存在，建议作为独立的模块一致性优化处理。

### 5.3 优化方案

Route 层统一使用：

```kotlin
import androidx.lifecycle.compose.collectAsStateWithLifecycle

val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

例如 `CategoryRoute` 可调整为：

```kotlin
val uiState by viewModel.categoryUiState.collectAsStateWithLifecycle()
val selectedIndex by viewModel.selectedCategoryIndex.collectAsStateWithLifecycle()
```

该修改只改变 Flow 的收集时机，不改变 ViewModel 实例、状态内容或 Screen 参数。

### 5.4 依赖情况

文档编写时，`feature:main` 的运行时依赖解析结果中已经存在：

```text
androidx.lifecycle:lifecycle-runtime-compose:2.10.0
```

这里的 `2.10.0` 是文档编写时的解析版本，不应被视为长期固定版本。`MeScreen.kt` 已能正常引用 `collectAsStateWithLifecycle`，不过该库当前主要通过 Navigation3、Activity Compose 等依赖传递引入，公共 Compose 约定只显式声明了 `lifecycle-runtime-ktx`。

为了避免未来上游依赖关系变化后编译失败，建议在版本目录中增加明确别名，并在 Compose 约定插件中显式声明：

```kotlin
implementation(libs.androidx.lifecycle.runtime.compose)
```

如果暂时只希望控制本次修改范围，也可以先在 `feature/main/build.gradle.kts` 中声明；长期更推荐放入公共 Compose 约定，因为其他 Compose 模块也可能使用同一 API。

### 5.5 优化收益

- 应用进入后台或对应 Lifecycle 低于 `STARTED` 时停止不必要的 UI 状态收集；
- 更好地配合 `stateIn`、`shareIn` 和 `WhileSubscribed`；
- 统一模块内 Route 层写法；
- 减少后台资源消耗；
- 不改变现有 ViewModel 和 Screen 接口。

## 6. 三项优化之间的关系

这三项不应完全独立地机械修改：

```text
PagerState 成为唯一状态源
  ├─ 删除 MainViewModel.currentPageIndex
  ├─ 删除 MainRoute 中的 collectAsState
  ├─ 删除 Pager 与 ViewModel 双向同步
  └─ 底栏和页面统一读取 PagerState

枚举驱动页面渲染
  ├─ Pager 页数继续来自 destinations
  ├─ 页面内容也来自 destinations
  └─ 新增页面时获得编译期检查

生命周期感知收集
  ├─ MainRoute 不再需要处理
  └─ Home/Category/Cart Route 另行统一
```

因此建议先完成 MainScreen 状态与页面映射重构，再单独批量调整其他 Route 的生命周期收集。

## 7. 影响范围

### 7.1 本次已修改：MainScreen 状态与页面映射

- `MainScreen.kt`
  - 删除 ViewModel 状态参数和同步回调；
  - 底栏读取 `pageState.currentPage`；
  - 点击底栏只更新 PagerState；
  - 页面内容改为枚举匹配。
- `MainViewModel.kt`
  - 确认没有其他调用方后已删除。

### 7.2 本次已修改：生命周期收集与显式依赖

- `HomeScreen.kt`
- `CategoryScreen.kt`
- `CartScreen.kt`
  - 将 Route 层的 `collectAsState` 统一替换为 `collectAsStateWithLifecycle`。
- `gradle/libs.versions.toml`
  - 增加 `lifecycle-runtime-compose` 版本目录别名。
- Compose 公共约定
  - 显式声明 `lifecycle-runtime-compose`，使 Compose 模块不再依赖该 API 的传递依赖。

### 7.3 不需要修改

- `BottomNavigationBar.kt`
  - 参数契约仍然可用，仅调用方传入的数据源改变。
- `MainGraph.kt`
  - `MainRoute` 的共享转场参数不变。
- `TopLevelDestination.kt`
  - 本次可以保持现状；页面渲染改为匹配枚举即可。
- 各业务 ViewModel
  - Home、Category、Cart 和 Me 的作用域及业务行为不变。

## 8. 风险与注意事项

### 8.1 再次点击当前 Tab

建议忽略重复点击，避免无意义滚动。如果产品希望“再次点击首页回到列表顶部”，应增加独立的重复点击事件，而不是依靠重复调用 `scrollToPage`。

### 8.2 快速连续点击多个 Tab

本次建议继续使用 `scrollToPage`，保持现有的立即切换行为。如果以后改用 `animateScrollToPage`，新的滚动调用可能取消前一次动画，这是 Pager 的正常互斥滚动行为。只要最终页面和底栏仍由同一个 PagerState 驱动，就不会因为双状态源同步失败而永久不一致。

### 8.3 页面状态是否会丢失

`rememberPagerState` 负责保存 Pager 的页码和偏移；Home、Category、Cart、Me 的 Hilt ViewModel 获取方式和 owner 没有改变。删除 MainViewModel 只移除当前 Tab 索引，不会删除各业务页面的 ViewModel 状态。页面内部普通 `remember` 状态是否保存不属于这项修改的保证范围。

### 8.4 后续需要外部指定 Tab

如果未来确实需要从 MainScreen 外部切换 Tab，建议将其建模为一次性导航意图或明确的 `initialDestination`，由 MainScreen 消费后驱动 PagerState；不要重新引入一个长期与 PagerState 双向同步的页面索引。

例如：

```kotlin
LaunchedEffect(targetDestination) {
    val index = destinations.indexOf(targetDestination)
    if (index >= 0 && index != pageState.currentPage) {
        pageState.scrollToPage(index)
    }
}
```

这里 `targetDestination` 表示外部指令，PagerState 仍然是当前页面事实状态。

## 9. 实施顺序

1. 将页面数字分支改为 TopLevelDestination 枚举分支，先完成低风险且收益明确的映射优化。
2. 在 `MainScreen.kt` 中改用单一 PagerState，并继续使用现有 `scrollToPage` 行为。
3. 删除 `MainRoute` 对 MainViewModel 的依赖。
4. 确认没有新增调用方后删除 `MainViewModel.kt`。
5. 编译 `feature:main`，执行底栏、滑动、旋转恢复和购物车入口回归测试。
6. 另开独立修改，统一 Home、Category、Cart Route 的生命周期状态收集。
7. 在同一生命周期修改中显式声明 `lifecycle-runtime-compose` 依赖。
8. 如果产品需要切页动画，再单独评估 `animateScrollToPage`，不要混入状态重构。

## 10. 验证清单

修改完成后至少验证以下场景：

- 首次进入主页面默认显示首页，首页 Tab 选中；
- 依次点击四个底栏项目，页面与选中项始终一致；
- 左右滑动页面，底栏选中项跟随变化；
- 快速连续点击不同 Tab，最终页面和底栏一致；
- 再次点击当前 Tab 不产生异常；
- 横竖屏切换后仍停留在原页面；
- 应用进入后台再恢复后页面状态合理；
- 主页面内购物车不显示返回按钮；
- 通过独立 Navigation3 入口进入购物车时，返回按钮行为不变；
- Home 和 Me 的共享转场仍正常；
- 新增一个 TopLevelDestination 但未实现页面时，编译器能够提示未覆盖的枚举分支。

## 11. 预期结果

完成优化后，MainScreen 的页面状态关系将从“两个状态双向同步”简化为“一个状态驱动多个 UI 消费者”。页面数量、底栏和页面内容也将围绕同一份 `TopLevelDestination` 定义组织。

最终预期效果：

- 页面切换逻辑更直接；
- 在切页入口统一经过 PagerState 的前提下，页面和底栏不会因两个长期状态同步失败而错位；
- 新增或调整 Tab 时更容易获得编译期保护；
- 无效 ViewModel 和重复回调被移除；
- 生命周期收集策略更统一；
- 后续维护、测试和问题定位成本降低。

## 12. 最终建议与优先级

### 12.1 优先级结论

| 优化项 | 判断 | 优先级 | 说明 |
| --- | --- | --- | --- |
| 使用枚举驱动页面渲染 | 建议优先实施 | 高 | 修改成本低，能够直接消除数字索引和枚举定义失配风险，并获得编译期穷尽检查。 |
| PagerState 成为唯一页面状态源 | 建议实施 | 高 | 当前逻辑大多数时候可以同步，但设计脆弱；收敛状态源可以删除重复更新和潜在不同步路径。 |
| 删除 MainViewModel | 随单一状态源方案实施 | 中高 | 当前 ViewModel 只保存 Tab 索引，没有独立业务职责；删除前需再次确认没有新增外部调用方。 |
| 使用生命周期感知的 Flow 收集 | 建议独立实施 | 中 | 符合 Android Compose 生命周期语义，对 Cart 的 `WhileSubscribed` Flow 收益更明确，但不会自动停止非当前 Pager Tab 的收集。 |
| 显式声明 `lifecycle-runtime-compose` | 与生命周期修改一起实施 | 中 | 避免长期依赖 Navigation3 或 Activity Compose 的传递依赖。 |
| 使用 `animateScrollToPage` | 不属于必要优化 | 低 | 会改变交互表现，应由产品体验决定，不能作为状态重构的默认行为。 |

### 12.2 推荐修改边界

第一阶段只处理 MainScreen 的状态和页面映射：

1. 让 PagerState 同时驱动实际页面和底栏选中状态；
2. 底栏点击只调用 PagerState；
3. 保留 `scrollToPage`，避免混入交互变化；
4. 页面内容改为 `when (TopLevelDestination.entries[page])`；
5. 删除双向同步回调以及无业务职责的 MainViewModel；
6. 保持 MainGraph、共享转场、Cart Assisted ViewModel 和各业务页面 ViewModel owner 不变。

第二阶段单独处理生命周期一致性：

1. Home、Category、Cart Route 改用 `collectAsStateWithLifecycle()`；
2. 显式声明 `lifecycle-runtime-compose`；
3. 重点验证应用前后台切换和 Cart 的 `WhileSubscribed` 上游行为；
4. 不把该 API 描述为非当前 Pager Tab 自动暂停机制。

### 12.3 最终判断

上述优化值得实施，并已按建议范围完成：枚举映射和单一 PagerState 是 MainScreen 本身的高价值重构；生命周期感知收集属于模块级规范与资源优化；页面切换动画不是必要优化，本次继续保留 `scrollToPage`。

修改前的双状态源在已有点击和滑动路径下通常可以正常工作，因此实施理由主要是降低未来扩展风险、减少同步代码并提升可测试性，而不是修复一个必然发生的当前故障。通过控制修改边界，在获得维护收益的同时避免了切页动画、导航栈和业务 ViewModel 作用域等无关行为变化。

### 12.4 实际实施结果

本次已经完成：

- PagerState 同时驱动 HorizontalPager 和 BottomNavigationBar；
- 底栏点击只调用 `pageState.scrollToPage(index)`；
- 页面渲染改为 `when (TopLevelDestination.entries[page])`；
- 删除 MainRoute 与 Pager 之间的双向同步回调；
- 删除无其他调用方的 MainViewModel；
- Home、Category、Cart Route 改用 `collectAsStateWithLifecycle()`；
- 在版本目录和 Compose 公共约定中显式声明 `lifecycle-runtime-compose`；
- 保留 MainGraph、共享转场、购物车 Assisted ViewModel 和各业务页面 ViewModel 的原有结构；
- `:feature:main:compileDevDebugKotlin` 编译成功。
