# core/flutter

本模块持有 Android 的共享 FlutterBoost 引擎、预热和 Compose 容器生命周期。商品与订单通过各自的通道绑定业务能力。通道随容器销毁注销，原生导航通过类型安全的 Navigation3 契约处理。

引擎及 FlutterBoost 版本由 `flutter_goods` 固定。平台适配留在本模块，订单状态、商品状态、网络和持久化留在所属模块。修改共享宿主后验证 App 构建、两类 Flutter 页面、原生跳转和系统返回。
