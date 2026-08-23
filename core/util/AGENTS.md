# core/util/AGENTS.md

## 适用范围

本文件适用于 `core/util/**`。本模块集中提供 Android 平台级工具封装和其配套资源、说明文档。

## 当前工具范围

- `LogUtils`：Timber 初始化和各等级日志。
- `ToastUtils`：Toaster 初始化、主题样式和成功/错误/警告 Toast。
- `MMKVUtils`：默认、多进程、加密实例及基础/序列化类型读写。
- `PermissionUtils`：基于 XXPermissions 的相机、相册、通知、音频、定位和自定义授权。
- `NotificationUtil`：通知渠道、普通通知与验证码通知。
- `PackageUtils`、`TimeUtils`、`ValidationUtil`：包信息、聊天时间和输入校验。

## 通用规则

- 工具只封装稳定、通用的平台能力，不承载商品、订单、登录等业务流程。
- API 优先返回结果或通过明确回调报告，避免静默改变全局状态。
- 需要 Context 时优先使用传入的 Application Context；不得在 object 中长期持有 Activity、View 或短生命周期 Context。
- 不记录 token、密码、验证码、个人资料、完整请求/响应或文件路径中的敏感信息。
- 时间格式化必须明确输入格式、Locale、时区和失败回退；不要在函数之间混用秒和毫秒。
- Validation 规则改变会影响多个表单，修改前搜索全部调用方并保持边界值一致。
- 与实际 API 行为不一致时同步更新同目录 README，不保留失效示例。

## 初始化型工具

- app 当前负责初始化 Toast、Log 和 MMKV；任何调用都必须发生在对应 `init()` 之后。
- 初始化保持幂等、轻量，不在 Application 启动路径执行阻塞 I/O。
- `MMKVUtils` 的默认实例、实例名、加密 key 和存储 key 都属于持久化契约；不得无迁移改名。
- `clearAll()`、按前缀删除和通知全量取消属于高影响操作，只能在明确业务授权下调用。
- Toast 主题切换应继续通过统一样式方法完成，不让 Feature 直接配置全局第三方库。

## 权限与通知

- 新权限必须先在 app Manifest 声明，并通过 `PermissionUtils` 做运行时请求；不要请求与当前功能无关的权限。
- Context 无法解析为 Activity 时要安全失败，不能强转崩溃。
- 拒绝、永久拒绝和授权结果必须分别回调，设置页跳转由明确用户操作触发。
- Notification Channel ID 一经发布应保持稳定；渠道名称和说明使用资源。
- Android 13+ 发送通知前检查通知权限；PendingIntent flags 必须符合当前系统安全要求。
- 验证码属于敏感信息，新增日志或持久化时不得保存其内容。

## 依赖边界与验证

本模块依赖 `core:designsystem`、`core:model`、Serialization、Toaster、XXPermissions、MMKV 和 Timber。不要依赖 Feature、Repository、network、database 或 app。

```bash
./gradlew :core:util:compileDevDebugKotlin
```

权限、通知和存储改动还需要对应 Android 版本的真机/模拟器验证。
