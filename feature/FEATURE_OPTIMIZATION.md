# Feature 模块代码优化说明

## 1. 文档目的

本文档记录对 `feature/` 下各业务模块的代码审计结果、风险、修改方案和验收标准，作为后续实施与复查依据。

审计范围包括：

- `feature/auth`
- `feature/common`
- `feature/cs`
- `feature/feedback`
- `feature/goods`
- `feature/launch`
- `feature/main`
- `feature/market`
- `feature/order`
- `feature/user`

所有模块的 `build/` 目录均已排除。第一批中的 3.1～3.3、第二批中的 4.1～4.4 已实施，其余条目仍为待实施方案。

## 2. 总体处理原则

1. 优先修复安全、支付、订单一致性、崩溃和异步竞态问题。
2. 服务端模型中的可空字段不得使用 `!!` 假设必有数据。
3. 地址缺失时，页面显示明确的占位内容，不直接崩溃，也不伪造真实地址。
4. 字典数据缺失时，根据具体页面语义进入 `Error` 或 `Empty`：
   - 请求失败、响应结构异常，或当前业务必须依赖该字典才能继续时，进入 `Error`。
   - 请求成功且服务端明确返回空集合，页面允许“暂无可选项”时，进入 `Empty`。
   - 不允许通过 `orEmpty()` 把异常响应一律包装成 `Success(emptyList())`。
5. Route 层收集 ViewModel 的 `Flow`/`StateFlow` 时使用生命周期感知方式。
6. 每项业务修改同步补充测试，避免只修改实现而没有回归保护。

## 3. 第一批：认证模块

### 3.1 禁止持久化用户原始密码

实施状态：已完成。

涉及文件：

- `auth/.../viewmodel/AccountLoginViewModel.kt`

当前代码使用默认 MMKV 保存账号和原始密码：

```kotlin
private const val KEY_SAVED_PASSWORD = "saved_password"

MMKVUtils.putString(KEY_SAVED_PASSWORD, password)
```

默认 MMKV 未使用加密实例。设备备份、Root、调试环境或本地文件泄漏时，密码可能被直接读取。

修改方案：

- 删除密码的读取和写入逻辑。
- 只保留手机号记忆功能。
- 升级后主动删除历史 `saved_password` 数据。
- 后续如需免密登录，使用登录 Token、Credential Manager 或系统生物认证，不保存原始密码。

实际实现：

```kotlin
private const val KEY_LEGACY_SAVED_PASSWORD = "saved_password"

init {
    loadSavedPhone()
    MMKVUtils.remove(KEY_LEGACY_SAVED_PASSWORD)
}

private fun savePhone(phone: String) {
    MMKVUtils.putString(KEY_SAVED_PHONE, phone)
}
```

预期收益：

- 消除原始密码落盘风险。
- 使实现符合项目安全规则。

### 3.2 修复图形验证码请求时序

实施状态：已完成。

涉及文件：

- `auth/.../viewmodel/SmsLoginViewModel.kt`
- `auth/.../viewmodel/RegisterViewModel.kt`

当前外层协程调用 `fetchCaptcha()` 后立即结束加载并显示弹窗，但 `fetchCaptcha()` 内部又启动了独立请求任务：

```kotlin
_isLoadingCaptcha.value = true
fetchCaptcha()
_isLoadingCaptcha.value = false
_showImageCodePopup.value = true
```

因此弹窗可能在验证码尚未返回时显示，加载状态也不能反映真实请求状态。

修改方案：

- 让一次验证码请求只有一个受控协程任务。
- 在请求开始时设置加载状态。
- 仅在请求成功并写入验证码后显示弹窗。
- 在 `onFinally` 中结束加载状态。
- 保存并取消旧请求，避免连续点击产生并行刷新。

建议逻辑：

```kotlin
private fun fetchCaptcha(showPopupOnSuccess: Boolean) {
    val requestId = ++captchaRequestId
    captchaRequestJob?.cancel()
    _isLoadingCaptcha.value = true
    captchaRequestJob = ResultHandler.handleResultWithData(
        scope = viewModelScope,
        flow = authRepository.getCaptcha().asResult(),
        onData = { captcha ->
            if (requestId == captchaRequestId) {
                _captcha.value = captcha
                if (showPopupOnSuccess) _showImageCodePopup.value = true
            }
        },
        onFinally = {
            if (requestId == captchaRequestId) {
                _isLoadingCaptcha.value = false
                captchaRequestJob = null
            }
        },
    )
}
```

另外，短信验证码发送期间会禁止重复提交；只有发送成功才关闭图形验证码弹窗，失败时保留弹窗供用户修正后重试。

### 3.3 QQ 登录结果只能订阅一次

实施状态：已完成。

涉及文件：

- `auth/.../viewmodel/LoginViewModel.kt`

当前每次点击 QQ 登录都会启动一个长期 `collect`，多次点击后会存在多个订阅者，可能重复请求后端、重复提示和重复导航。

修改方案：

- 在 ViewModel 初始化阶段只启动一次结果订阅；或保存订阅 `Job`，启动前取消旧任务。
- 点击事件只负责调用 QQ SDK。
- 防止登录请求进行期间重复发起登录。

实际实现由 `init` 注册唯一订阅，点击时只负责启动 SDK：

```kotlin
init {
    observeQQLoginResult()
}

fun startQQLogin(activity: Activity) {
    if (_isQqLoginInProgress.value) return
    qqLoginManager.clearLoginResult()
    if (!_isQqLoginInProgress.compareAndSet(false, true)) return
    qqLoginManager.startQQLogin(activity)
}
```

### 3.4 处理未完成的找回密码页面

实施状态：暂缓。已确认当前没有未登录找回密码接口，本批次不开发该功能，不使用需要 `Authorization` 的更新密码接口替代。

涉及文件：

- `auth/.../viewmodel/ResetPasswordViewModel.kt`
- `auth/.../view/ResetPasswordScreen.kt`

当前发送验证码和重置密码都是空实现，提交按钮永久禁用，但页面入口仍然可见。

本次决定：

- `ResetPasswordViewModel.kt` 和 `ResetPasswordScreen.kt` 保持现状，本批次不修改。
- 后端提供未登录找回密码接口及明确请求契约后，再完整实现验证码、表单校验、提交状态和重置流程。
- 如果长期不提供接口，再单独确认是否隐藏找回密码入口。

## 4. 第二批：订单、商品和反馈

### 4.1 防止重复创建订单

涉及文件：

- `order/.../viewmodel/OrderConfirmViewModel.kt`
- `order/.../view/OrderConfirmScreen.kt`

当前提交按钮没有请求中状态，连续点击可能多次调用创建订单接口。

修改方案：

- 增加 `isSubmitting` 状态。
- 请求期间禁用按钮并展示加载效果。
- 请求结束后统一恢复状态。
- 提交前再次校验地址和商品列表。
- 如果服务端支持，为创建订单增加幂等键。

实施结果（2026-08-30）：

- 已增加同步生效的 `isSubmitting` 状态，首次点击后立即阻止后续提交。
- 提交按钮在完整流程结束前保持禁用和加载状态。
- 提交前会确认页面数据已成功加载，并校验地址与商品列表。
- 当前服务端契约未提供幂等键字段，因此本次未伪造客户端字段；仍建议服务端后续增加最终幂等保障。

### 4.2 保证下单后的购物车清理完成

当前订单创建成功后，购物车删除在新的 `viewModelScope` 协程中执行，同时立即关闭订单确认页面。ViewModel 销毁时，清理任务存在被取消的风险。

修改方案：

- 将“订单创建成功、清理购物车、跳转支付页”组织为一个可等待的顺序流程。
- 购物车清理完成后再关闭订单确认页面。
- 删除失败时记录可追踪错误，并明确是否允许继续支付；不能静默留下不一致状态。

实施结果（2026-08-30）：

- 创建订单、清理已购买购物车项、导航支付页已放入同一个顺序协程中。
- 清理完成前不会导航，避免页面销毁取消本地数据库操作。
- 某个购物车项清理失败时会记录错误并显示明确警告，但仍继续支付：服务端订单已经创建，阻止支付可能诱导用户再次下单。
- 清理继续采用按商品对比全部规格的策略，只删除本次购买的规格，不扩大删除范围。

预期流程：

```text
Validate request
    -> Create order
    -> Remove purchased cart items
    -> Navigate to payment
```

### 4.3 支付结果以服务端订单状态为准

涉及文件：

- `order/.../viewmodel/OrderPayViewModel.kt`
- `order/.../view/OrderPayScreen.kt`

当前仅根据支付宝 SDK 的 `resultStatus` 显示支付成功。客户端返回值不能作为最终支付凭据。

修改方案：

- SDK 返回成功只代表支付客户端流程完成。
- 随后查询 `getOrderInfo(orderId)`。
- 通过有限次数、带间隔的查询确认服务端订单已支付。
- 只有服务端状态确认后才显示支付成功。
- 查询超时显示“支付结果确认中”，允许用户进入订单详情继续查看状态。
- 支付请求期间禁用支付按钮，防止重复拉起 SDK。

实施结果（2026-08-30）：

- 获取支付参数、等待支付宝结果和服务端确认共用一个进行中状态，期间按钮禁用并显示加载效果。
- 支付宝 SDK 成功后最多查询服务端订单 3 次，间隔 1 秒；订单状态进入 `1..6` 才提示最终支付成功，服务端明确返回已关闭（`7`）则提示支付失败。
- 查询期内仍为待付款或请求异常时，不显示“支付成功”，而是提示“支付结果确认中”，并进入订单详情或返回列表刷新。
- 支付参数在处理 SDK 结果时清空，后续重试即使返回相同参数也能再次触发，不会被旧 `StateFlow` 值吞掉。

### 4.4 清理可空字段的强制非空调用

实施状态：已完成（2026-08-30）。

涉及位置主要包括：

- 商品轮播图 `pics!!`
- 商品详情图 `contentPics!!`
- 商品副标题 `subTitle!!`
- 优惠券条件 `condition!!`
- 订单地址 `address!!`
- 反馈类型 `feedbackType!!`
- 订单取消原因 `orderCancelReason!!`
- 订单退款原因 `orderRefundReason!!`

#### 商品可空内容

- `pics` 为空时使用商品主图作为回退；主图也为空时显示图片占位。
- `contentPics` 为空时隐藏图文详情列表，并显示“暂无图文详情”占位。
- `subTitle` 为空时不渲染副标题及其间距。
- 优惠券 `condition` 为空时不得强制展示满减文案，应根据优惠券类型显示可解释的备用文案或隐藏该优惠券入口。

建议形式：

```kotlin
val bannerImages = data.pics.orEmpty().ifEmpty {
    listOfNotNull(data.mainPic.takeIf(String::isNotBlank))
}

data.subTitle
    ?.takeIf(String::isNotBlank)
    ?.let { subTitle ->
        Text(text = subTitle)
    }
```

#### 订单地址缺失

地址缺失时显示占位内容，不直接调用 `data.address!!`：

```kotlin
val address = data.address
if (address == null) {
    MissingAddressPlaceholder()
} else {
    AddressCard(address = address)
}
```

占位内容应说明“该订单暂无收货地址信息”，并根据订单状态决定是否提供补充地址、刷新或联系客服入口。不得构造一个空 `Address()` 冒充真实地址。

#### 字典数据缺失

字典处理必须区分请求失败和成功空数据：

```kotlin
val reasons = data.orderCancelReason
_cancelReasonsModalUiState.value = when {
    reasons == null -> BaseNetWorkUiState.Error()
    reasons.isEmpty() -> BaseNetWorkUiState.Empty
    else -> BaseNetWorkUiState.Success(reasons)
}
```

具体规则：

- 字段为 `null`：视为响应结构不完整，进入 `Error`。
- 字段为非空集合：进入 `Success`。
- 字段为空集合：
  - 取消订单、退款等必须选择原因才能继续的场景，进入 `Empty`，禁用确认按钮并显示“暂无可选原因”。
  - 反馈类型为空时进入 `Empty`，禁止提交并提供重试入口。
- 网络请求本身失败：进入 `Error` 并提供重试。

注：如果当前 `BaseNetWorkUiState` 没有 `Empty` 类型，需要先补齐对应状态或为弹窗定义专用状态，不能用 `Success(emptyList())` 混淆语义。

#### 实施结果

公共页面状态已增加独立空态，`BaseNetWorkView` 和字典弹窗会分别渲染通用空数据与“暂无可选项”，不会再把空集合交给成功内容继续执行：

```kotlin
data object Empty : BaseNetWorkUiState<Nothing>()

when (state) {
    is BaseNetWorkUiState.Loading -> PageLoading()
    is BaseNetWorkUiState.Empty -> EmptyData(onRetryClick = onRetry)
    is BaseNetWorkUiState.Error -> EmptyNetwork(onRetryClick = onRetry)
    is BaseNetWorkUiState.Success -> content(state.data)
}
```

订单取消原因、退款原因统一使用明确映射，并在重新请求时清除旧选中项，避免异常或空响应后仍能提交旧原因：

```kotlin
internal fun <T> requiredDictionaryUiState(
    items: List<T>?,
): BaseNetWorkUiState<List<T>> = when {
    items == null -> BaseNetWorkUiState.Error()
    items.isEmpty() -> BaseNetWorkUiState.Empty
    else -> BaseNetWorkUiState.Success(items)
}
```

反馈类型采用相同规则：字段缺失进入 `Error`，成功空集合进入 `Empty`，两种状态都不会显示提交按钮；空态和错误态均提供重试入口。

商品详情的实际处理如下：

- 轮播图过滤空 URL；`pics` 缺失或为空时回退 `mainPic`；两者都不可用时展示图片占位。
- `contentPics` 缺失、为空或只有空 URL 时显示“暂无图文详情”。
- `subTitle` 为 `null`、空串或空白串时不渲染文本及额外间距。
- 优惠券 `condition` 缺失时显示“无门槛减 N 元”，不再强制解包；英文满减文案同时修正了金额参数顺序。

```kotlin
val bannerImages = pics
    .orEmpty()
    .filter(String::isNotBlank)
    .ifEmpty { listOfNotNull(mainPic.takeIf(String::isNotBlank)) }

data.subTitle
    ?.takeIf(String::isNotBlank)
    ?.let { subTitle -> Text(text = subTitle) }
```

订单详情和物流页不再使用 `address!!`。地址缺失时复用地址卡片展示“该订单暂无收货地址信息”，隐藏操作箭头并禁用点击；订单确认页仍保留“选择/添加地址”的原有交互语义，没有用空 `Address()` 伪造真实数据。

新增纯逻辑测试覆盖：商品图片回退与空 URL、优惠券有/无条件、订单字典 `null/empty/non-empty`、反馈类型 `null/empty/non-empty`。

## 5. 第三批：客服、WebView 和通用状态收集

### 5.1 重构客服 WebSocket 生命周期和协议构建

实施状态：已完成（2026-08-30）。

涉及文件：

- `cs/.../util/WebSocketManager.kt`
- `cs/.../viewmodel/ChatViewModel.kt`
- `cs/.../view/ChatScreen.kt`

当前问题：

- 重连方法只延时，没有真正重新建立连接。
- WebSocket 消息通过字符串拼接 JSON，特殊字符可能破坏协议。
- 历史消息尚未返回时就尝试标记已读。
- 页面传入了已读回调，但没有实际调用。
- 日志包含部分 Token 和完整聊天内容。
- 已连接状态下缺少充分的重复连接保护。

修改方案：

- 使用 `kotlinx.serialization` 构建发送消息。
- 实现有限次数、可取消、带退避的真实重连。
- `Connecting` 和 `Connected` 状态均阻止重复创建连接。
- 历史消息成功合并后再提交已读请求。
- 页面可见期间收到新消息时，根据业务规则及时标记已读。
- 删除 Token 和聊天正文日志，保留脱敏后的事件、消息 ID 和错误信息。
- 明确页面离开后是否保持连接；如果不需要后台客服消息，则在页面停止时断开。

实际处理：

- 新增 `WebSocketProtocol`，使用 `kotlinx.serialization` 构建认证和发送事件；聊天正文中的引号、反斜杠、换行等字符不再破坏协议帧，同时统一解析带命名空间和不带命名空间的消息事件。
- `WebSocketManager` 保存当前有效 Token 和作用域，异常失败或非主动关闭后按 1、2、3 秒执行最多三次真实重连；重连任务随作用域或主动断开取消，`Connecting`、`Connected` 均拒绝重复连接。
- 使用连接代次隔离旧 WebSocket 回调。页面停止、主动断连或新连接替代旧连接后，旧连接不能再提交连接状态或触发重连。
- `ChatRoute` 使用 `LifecycleStartEffect`：页面进入 `STARTED` 后连接，停止时断开；页面中的 ViewModel 状态同步改为 `collectAsStateWithLifecycle()`。
- 历史消息合并完成后才筛选未读客服消息；页面可见期间收到客服消息也会触发已读。已读请求只提交 `type == 1`、`status == 0` 且 ID 有效的消息，服务端成功后同步更新本地状态，并串行处理请求期间新到达的消息。
- 删除未使用的页面已读回调、部分 Token 和完整协议帧日志；只记录连接事件、HTTP 状态、消息 ID、类型、会话 ID、数量和异常。

新增单元测试覆盖协议特殊字符转义、两种消息帧解析、畸形事件容错、有限重连、主动断开取消重连、已连接去重、未读客服消息筛选及本地已读状态同步。

### 5.2 收紧 WebView 安全设置并释放资源

实施状态：已完成（2026-08-31）。

涉及文件：

- `common/.../view/WebScreen.kt`
- `common/.../viewmodel/WebViewModel.kt`
- `common/.../view/PrivacyPolicyScreen.kt`
- `common/.../view/UserAgreementScreen.kt`

修改方案：

- 默认只允许 HTTPS。
- JavaScript 默认关闭，只对明确可信的域名按需开启。
- 为站内、第三方和系统 Scheme 建立白名单策略。
- 非 HTTP(S) Scheme 不应不加区分地交给系统。
- 使用 `AndroidView` 的释放回调执行 `stopLoading()`、清理 client、移除引用并 `destroy()`。
- 打开外部浏览器失败时使用项目日志和用户可理解的提示，移除 `printStackTrace()`。

实施结果：

- 新增统一的 URL 策略，按解析后的 Scheme、Host、用户信息和端口分类；只有代码中明确列出的 HTTPS Host 可以在应用内加载，未列入许可名单的合法 HTTPS 链接交给系统浏览器。
- 应用内 WebView 默认关闭 JavaScript 和 DOM Storage，禁止文件、Content URI、混合内容、自动开窗和自动媒体播放；仅精确 Host `gitee.com` 因 WebView 入口依赖同域 WAF 校验而开启 JavaScript 和 DOM Storage。
- HTTP、`javascript:`、`file:`、`content:`、`data:`、`intent:`、异常端口、用户信息伪装和未知 Scheme 会被拦截；系统 Scheme 只允许 `tel:` 和 `mailto:`。
- 服务端返回的贡献者网址不再进入应用内 WebView，统一交给系统应用处理。
- 外部打开逻辑移到 UI 层，`WebViewModel` 不再持有 `Context`；打开失败使用项目日志和中英文用户提示。
- 通用网页、隐私政策和用户协议 WebView 的后续导航共用同一安全策略，并在 `AndroidView.onRelease` 中停止加载、清理 Client、移除子 View 并销毁。
- 新增纯逻辑测试覆盖许可 Host、伪装域名、用户信息、端口、危险 Scheme、合法外部 HTTPS、电话和邮件链接。

### 5.3 Route 层使用生命周期感知的状态收集

审计发现 `feature/` 中存在大量 Route 层 `collectAsState()`。修改时只处理 ViewModel 暴露的 `Flow`/`StateFlow`，不机械改动纯 Compose 局部状态。

```kotlin
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

优先涉及：

- `auth`
- `common`
- `cs`
- `feedback`
- `goods`
- `launch`
- `market`
- `order`
- `user`

`main` 的主要页面已经采用生命周期感知方式，修改时保持现状并复查新增代码。

预期收益：

- 页面停止或保留在返回栈时不再无意义地持续收集。
- 减少后台请求、重组和资源占用。
- 为 WebSocket 等有生命周期要求的功能提供一致基础。

### 5.4 补充地址表单校验

涉及文件：

- `user/.../viewmodel/AddressDetailViewModel.kt`
- `user/.../view/AddressDetailScreen.kt`

修改方案：

- 校验联系人、手机号、省市区和详细地址。
- ViewModel 暴露 `isFormValid`。
- 表单无效时禁用保存按钮。
- `saveAddress()` 内再次校验，避免绕过 UI 直接提交。
- 请求期间提供保存中状态并防止重复提交。

## 6. 低优先级整理项

以下问题不阻塞核心业务，可在高优先级修改完成后处理：

- Compose `Modifier` 参数顺序统一。
- `AboutScreen` 的滚动值通过 `derivedStateOf` 读取，减少高频重组。
- 统一用项目日志工具替代 `Log` 和 `printStackTrace()`。
- 英文数量文本改用 plurals。
- `...` 替换为排版省略号 `…`。
- 删除无效变量、重复日志和已失效注释。
- 评估过大的 Vector Drawable 是否需要栅格化资源。

## 7. 测试计划

每批修改至少补充以下测试：

### 认证

- 登录成功后不写入密码。
- 升级逻辑会删除历史密码。
- 验证码成功后才显示弹窗。
- 验证码失败时不显示弹窗并恢复加载状态。
- 连续点击 QQ 登录不会增加结果订阅或重复调用后端。

### 订单和商品

- 连续点击只创建一次订单。
- 下单成功后只删除已购买的购物车规格。
- 购物车清理完成后才发生导航。
- 支付 SDK 成功但服务端未支付时不提示最终成功。
- 商品图片、副标题、优惠条件、订单地址为 `null` 时页面不崩溃。
- 字典为 `null`、空集合和非空集合时分别进入预期状态。

### 客服和 WebView

- WebSocket 失败后按策略真正重连，达到上限后停止。
- 含引号、换行和反斜杠的聊天内容能正确序列化。
- 历史消息加载完成后正确标记未读消息。
- 不可信 URL 不启用 JavaScript。
- WebView 离开组合时释放资源。

### 用户地址

- 空联系人、非法手机号、未选地区和空详细地址不能保存。
- 合法表单只提交一次。

## 8. 验证命令

完成每批修改后执行：

```bash
./gradlew testDevDebugUnitTest lintDevDebug spotlessCheck --no-daemon
```

涉及页面布局、弹窗、支付、WebView 或系统导航栏时，还需要在模拟器或真机执行对应场景验证。

## 9. 推荐实施顺序

1. 认证安全和验证码时序。
2. 订单重复提交、购物车一致性和支付确认。
3. 商品、订单、反馈的空安全处理。
4. 客服 WebSocket。
5. WebView 安全与资源释放。
6. 生命周期感知收集和地址表单校验。
7. 低优先级 Lint 与代码整洁项。

每一批独立修改、独立测试和独立提交，减少问题之间的牵连并方便回滚。
