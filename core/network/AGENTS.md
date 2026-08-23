# core/network/AGENTS.md

## 适用范围

本文件适用于 `core/network/**`。本模块维护 Retrofit Service、网络 DataSource、OkHttp/Serialization 配置、认证拦截器和文件上传实现。

## 网络调用链

```text
Repository -> *NetworkDataSource interface -> implementation -> Retrofit Service
```

- Service 只声明 HTTP 方法、路径、参数和返回类型。
- DataSource 接口是 `core:data` 使用的边界；实现委托对应 Service。
- `ServiceModule` 创建 Service，`DataSourceModule` 提供 DataSource。
- 普通请求和文件上传使用带 `@FileUploadQualifier` 的不同 OkHttpClient。

## 接口开发规则

- 新接口按 Service、DataSource 接口、DataSource 实现、Hilt Provider、Repository 的顺序成套补齐。
- Retrofit 路径、HTTP 动词、Body/Query/Path、字段类型和 `NetworkResponse<T>` 泛型必须与后端契约一致。
- 优先在 `core:model` 新增明确 request/response 类型，不继续扩散 `Any` 或未定义结构的 Map。
- DataSource 不处理 Compose、Toast、导航和页面状态，也不把业务失败改造成成功结果。
- 不在多个 DataSource 复制相同 Retrofit 配置或自行创建未经 Hilt 管理的客户端。

## 网络配置与安全

- BASE_URL 来自 `BuildConfig.BASE_URL`；修改地址需同步检查 convention/build variant，且必须获得明确授权。
- JSON 当前启用 `ignoreUnknownKeys`、`coerceInputValues`、`isLenient`；调整会影响所有接口解析。
- 普通客户端超时为 10 秒，上传客户端为 30 秒。修改需基于具体接口事实，不为单一接口全局放大。
- `AuthInterceptor` 从 `AuthStoreDataSource` 读取 token 并设置 `Authorization`；不要记录、拼接到 URL 或暴露 token。
- 普通客户端在调试构建启用 BODY 日志和 Chucker，并遮蔽 `Authorization`；Release 为 NONE/no-op。
- 上传客户端不得接入 BODY 日志或 Chucker，因为 Multipart 包含临时凭据与文件内容。
- 新拦截器要明确顺序，因为认证、日志和监控的先后会改变可见请求内容。
- 不把 Secret、临时凭据或签名 key 写进源码、BuildConfig 常量或日志。

## 文件上传

- 上传流程先获取临时凭据，再通过专用 Service/Client 上传；不要持久化临时密钥。
- 保持单图与多图返回顺序、失败语义和 MIME/文件名处理。
- 读取 `Uri` 使用 Application Context，不持有 Activity；流与响应体必须正确关闭。
- 文件请求体按流写入 OkHttp sink，禁止使用 `readBytes()` 将完整文件载入内存。
- 捕获上传异常时必须重新抛出 `CancellationException`，日志不得输出上传 URL、临时凭据或文件内容。
- 上传主机不应默认携带业务 API 的 Authorization 拦截器，除非服务端明确要求。

## 依赖边界与验证

本模块依赖 `core:model` 与 `core:datastore`，不依赖 `core:data`、Feature、Compose 或 app。

```bash
./gradlew :core:network:compileDevDebugKotlin
```

接口变更还需验证真实请求路径、序列化、认证头、错误响应和对应 Repository。
