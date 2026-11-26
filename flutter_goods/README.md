# Flutter 商品详情与订单

首页、分类和订单等入口继续使用 `GoodsRoutes.Detail(goodsId)`。Android 使用 Navigation3 管理返回栈，详情 Entry 通过 FlutterBoost Fragment 展示 Flutter 页面。页面右上角的“原生版”使用同一个 ViewModel 切回 Compose。规格、库存、购物车、领券和订单确认均使用现有 Android 业务层。

「我的 → 我的订单 → 查看全部」和状态快捷入口使用共享 Flutter 订单列表。选中订单后，详情在同一个容器内打开。列表、筛选和详情使用原生 `OrderRepository`。支付、售后、物流、确认收货、取消、评价和再次购买交给现有的原生操作。商品与订单页面共用 `core:flutter` 中的 FlutterBoost 引擎和容器。

Dart 源码和图标存于 [CoolMall-Flutter](https://github.com/NAH4E5553/CoolMall-Flutter)，本目录的 `source.lock` 固定所使用的提交。构建脚本通过 Git 获取锁定提交，并将其解包到 `.module`。本地也可通过 `COOLMALL_FLUTTER_MODULE` 指定已包含该提交的 Git 仓库。

## 本地构建

准备 JDK 17、Android SDK 36、Python 3 及 Bash（macOS 或 Linux），并确认 Git 可以读取共享私有仓库。如果 Android SDK 安装在非默认位置，请将 `ANDROID_HOME` 指向 SDK 目录。在仓库根目录运行：

```sh
./flutter_goods/build_android.sh --app
```

脚本读取 `sdk.lock`，首次下载并校验 Flutter 3.47.4，随后复用 `flutter_goods/.flutter-sdks` 下的 SDK。它生成 AAR，再调用仓库的 Gradle Wrapper 编译 DevDebug APK。只准备 AAR 时省略 `--app`，之后可以在 Android Studio 同步并运行 `devDebug`。APK 输出在 `app/build/outputs/apk/dev/debug`，选择与设备匹配的 ARM ABI。开发调试可使用 Android 默认签名。

可设置 `FLUTTER_SDK` 或 `FLUTTER_BOOST_SOURCE` 复用相应的固定版本 Git 检出。脚本提取 FlutterBoost 4.6.5 的固定提交 `6df80cc1ea425cde1bac7d0265407977221061df`，在生成目录应用仓库内的 `experiments/flutter-boost-4.6.5/boost-android.patch`。上游源码采用 MIT 许可。Flutter SDK 采用 BSD-3-Clause 许可。

修改 Dart 后重新执行构建脚本再打宿主包。Debug 和 Release AAR 发布到模块自己的 `build/host/outputs/repo`（Maven 坐标版本 1.0），包含生成的校验文件。两种模式分别使用匹配的 Flutter 引擎，目标 ABI 为 armeabi-v7a 和 arm64-v8a。Release 的反射保留规则在 `app/proguard-rules.pro`。本地仓库、SDK 包装工程和第三方检出均为生成文件。

CI 通过 `FLUTTER_GOODS_READ_TOKEN` 仓库 Secret 读取锁定版本。该凭据需要对共享私库的 Contents 只读权限。

## 验证

```sh
(cd flutter_goods/.module && ../.flutter-sdks/3.47.4/bin/flutter analyze --no-pub lib test)
(cd flutter_goods/.module && ../.flutter-sdks/3.47.4/bin/flutter test --no-pub)
./gradlew :app:assembleDevDebug :app:assembleProdRelease testDevDebugUnitTest lintDevDebug spotlessCheck
```

Dart 页面通过每个 Boost 容器专属的 MethodChannel 读取宿主数据并传回规格 ID、优惠券 ID 和数量。iOS 和鸿蒙宿主已接入同一契约。组件测试使用本地商品和库存，可验证购买路径而无需提交真实订单。
