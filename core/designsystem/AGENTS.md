# core/designsystem/AGENTS.md

## 适用范围

本文件适用于 `core/designsystem/**`。本模块提供最底层 Compose 设计 Token、主题和无业务语义的布局/图标组件。

## 当前职责

- `theme/Color.kt`：亮色、暗色、语义色和主色派生值。
- `theme/Type.kt`、`Shape.kt`、`Size.kt`：排版、圆角和间距 Token。
- `theme/Theme.kt`：`AppTheme` 与 Material 3 ColorScheme。
- `theme/Icon.kt`：基础图标封装。
- `component`：Box、Row、Column、LazyList 和 Scroll 等布局快捷组件。

## 设计系统规则

- 颜色、字号、形状和间距优先复用现有 Token，避免在上层组件散落重复魔法值。
- 新 Token 必须具有跨页面复用价值，并同时考虑亮色、暗色及主色切换。
- `AppTheme` 是主题入口；修改 ColorScheme 时检查所有 `MaterialTheme.colorScheme.*` 使用方。
- 动态色当前默认关闭。不要在未评估品牌主色一致性的情况下改为默认开启。
- 公共 Composable 保持 `modifier: Modifier = Modifier`，调用方传入的 Modifier 应位于合理的外层位置。
- 布局封装只提供稳定、可理解的默认值；参数组合变复杂时优先直接使用 Compose 原生组件。
- 图标必须提供合理的 `contentDescription` 入口；纯装饰图标可以明确传 `null`。
- 新增资源时使用语义化名称，不把 Feature 专属图标或文案放入本模块。
- 修改现有公开组件默认参数视为跨模块行为变更，先搜索调用方并验证 Preview/页面。

## 模块边界

- 不依赖 `core:model`、Repository、Navigation、Feature 或 app。
- 不读取登录状态、网络状态或业务数据。
- 具有应用业务语义的复用组件放 `core:ui`；Feature 专属组件留在对应 Feature。
- 不在设计 Token 中直接访问 MMKV；外部将解析后的 `themeColor` 和 `darkTheme` 传给 `AppTheme`。

最小验证：

```bash
./gradlew :core:designsystem:compileDevDebugKotlin
```

视觉改动还需至少检查亮色、暗色和一种非默认主题色。
