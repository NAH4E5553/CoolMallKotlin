# core/navigation/AGENTS.md

## 适用范围

本文件适用于 `core/navigation/**`。本模块定义 Navigation3 的共享 Route、导航命令、回退栈控制、登录拦截和类型安全页面结果。

## 现有结构

```text
Feature/ViewModel -> *Navigator -> NavigationService -> AppNavigator
                                                     -> NavigationController
                                                     -> NavBackStack
```

- `*Routes` 保存实现 `NavKey` 的 `@Serializable` 路由。
- `*Navigator` 提供按业务命名的跳转入口。
- `AppNavigator` 缓存控制器未绑定期间的命令，并在导航前执行登录拦截。
- `BackStackNavigationController` 是唯一直接修改 `NavBackStack` 的实现。
- `NavigationResultKey<T>` 将结果 key 与返回类型绑定。

## Route 与 Navigator 规则

- 新页面按现有领域目录同时新增 Route 和语义化 Navigator 方法。
- Route 必须 `@Serializable` 并实现 `NavKey`；参数优先使用稳定 ID、小型字符串、布尔值等不可变数据。
- 不传递 Context、Activity、ViewModel、Repository、Flow 或大型可变对象。
- Navigator 只组装 Route 并调用公共导航函数，不实现业务请求或页面状态。
- 新 Route 仍需在对应 Feature Graph 注册；本模块不包含 `entryProvider` 或页面 Composable。
- 需要登录的 Route 必须同步加入 `RouteInterceptor.loginRequiredRouteTypes`，并验证已登录/未登录两条路径。
- 修改 Route 类型或参数是跨模块 API 变更，先搜索 Navigator、Graph 和调用方。

## Back Stack 与生命周期

- `NavigationService.bind/unbind` 负责全局 `AppNavigator`，`AppNavigator.attachController/detachController` 负责当前 BackStack 控制器；这是两层不同生命周期，两组调用都必须分别成对。
- 不绕过 `NavigationController` 在 ViewModel 或 Feature 中直接修改根 BackStack。
- 修改 `inclusive`、`allowPopToEmpty` 或 `popUpTo` 行为时验证栈底、目标不存在、连续跳转和返回键。
- 控制器未绑定时命令会排队；新增命令必须保持顺序并可安全延迟执行。
- `AppNavigator` 必须把控制器和 `NavBackStack` 操作切到主线程；不要从后台线程直接执行命令或绕过该入口。
- `NavigationService.requireNavigator()` 未绑定会抛错；不要在应用导航宿主完成绑定前启动无条件导航。

## 页面结果

- 新结果定义唯一的 `NavigationResultKey<T>`，发送和接收使用同一个对象。
- 刷新结果按地址、反馈、订单、支付等业务域定义 Key；禁止重新引入会广播给所有页面的全局刷新 Key。
- 基础不可变类型可使用默认透传；复杂类型应像 `SelectAddressResultKey` 一样显式序列化/反序列化。
- key 默认依赖类全名，重命名 Key 会改变分发标识；修改前检查所有发送和监听位置。
- 结果流是事件，不应被当作长期页面状态；ViewModel 收到后更新自己的权威状态。

## 依赖边界与验证

本模块依赖 Navigation3、Serialization、`core:data`（登录状态）和 `core:model`（结果类型），不依赖 Feature UI 或 app。

## 测试重点

- BackStack 测试覆盖普通跳转、返回、目标不存在、`inclusive`、栈底保护和 `allowPopToEmpty`。
- 登录拦截同时验证已登录直达目标和未登录跳转登录页，不使用真实账号状态。
- 控制器未绑定时验证命令顺序与绑定后回放；解绑旧控制器不能影响新控制器。
- 页面结果测试覆盖不同 Key 隔离、复杂类型序列化以及返回后只消费对应事件。

```bash
./gradlew :core:navigation:compileDevDebugKotlin :core:navigation:testDevDebugUnitTest
```

导航变更还要手工验证目标页面、返回栈、登录拦截和结果回传。
