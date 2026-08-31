# Android CI 路径感知说明

`Android CI` 的 Build、Unit Tests、Android Lint 和 Code Format 是 `main` 的必需检查，因此工作流本身不能通过 `paths` 或 `paths-ignore` 跳过，否则 GitHub 会让这些检查保持 Pending 并阻止合并。

工作流先通过 `Classify Changes` 判断本次范围是否会影响 Android 工程。四个必需 Job 始终创建；仅包含安全文档或 OpenCodeReview 配置时，它们执行轻量跳过步骤并成功结束，不启动 JDK 或 Gradle。分类失败、输出异常、手动触发、Android CI 自身变化或出现未知路径时一律执行完整检查或明确失败，不允许静默放行。

当前可跳过完整 Android 检查的路径：

- 任意 Markdown 文件；
- `.opencodereview/**`；
- `.github/OPEN_CODE_REVIEW.md`；
- `.github/workflows/open-code-review.yml`；
- `.github/workflows/open-code-review-history.yml`。

源码、资源、Manifest、Gradle 配置、版本目录、Android CI 工作流、分类脚本及其他未知文件仍执行完整检查。扩展安全路径前必须确认其不会改变 Android 构建、测试、Lint、格式规则或必需检查本身。
