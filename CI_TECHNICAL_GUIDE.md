# CoolMallKotlin CI 技术与使用指南

## 1. 文档目的

本文记录 CoolMallKotlin 本次持续集成（Continuous Integration，CI）建设的实际方案、设计原因、日常使用方式、故障排查方法和后续扩展原则。

文档面向项目维护者和后续参与开发的 AI Coding Agent。涉及的命令和配置均以当前仓库为准，示例代码保持英文。

## 2. 当前建设结果

项目已经建立 Android CI，并通过 GitHub `main` 分支保护将检查结果接入合并流程。

当前每个面向 `main` 的 Pull Request 必须通过以下四项检查：

| GitHub Check | Gradle 命令 | 主要目标 |
| --- | --- | --- |
| `Build` | `./gradlew :app:assembleDevDebug` | 验证开发调试变体可以完整编译和打包 |
| `Unit Tests` | `./gradlew testDevDebugUnitTest` | 运行所有模块的 `DevDebug` JVM 单元测试 |
| `Android Lint` | `./gradlew lintDevDebug` | 检查 Android API、资源、清单和常见代码问题 |
| `Code Format` | `./gradlew spotlessCheck` | 检查本次改动涉及的 Kotlin 和 Gradle Kotlin DSL 文件格式 |

对应的建设记录：

- [PR #8：建立 Android CI](https://github.com/NAH4E5553/CoolMallKotlin/pull/8)
- [PR #9：接入 Spotless、ktlint 和格式检查](https://github.com/NAH4E5553/CoolMallKotlin/pull/9)

## 3. CI、CD 与本项目当前边界

CI 是代码合并前后的自动验证过程，重点是尽早发现编译、测试、静态检查和格式问题。

CD 通常包括两种含义：

- Continuous Delivery：持续交付，自动生成可发布产物，但最终发布可能需要人工确认。
- Continuous Deployment：持续部署，检查通过后自动发布到目标环境或应用商店。

当前项目只完成了 CI，还没有实现以下 CD 能力：

- Release 变体构建和签名。
- APK/AAB 正式制品发布。
- GitHub Release 创建。
- Google Play 上传。
- 发布环境、发布密钥和人工审批流程。

因此，当前 CI 通过只能说明代码满足现有四项质量门禁，不能等同于已经具备生产发布能力。

## 4. 整体工作流

日常流程如下：

```text
Update local main
        |
Create feature branch
        |
Develop and run local checks
        |
Push branch and create PR
        |
GitHub runs 4 CI jobs in parallel
        |
Update branch if main changed
        |
All required checks pass
        |
Merge PR into main
        |
GitHub verifies the push to main again
```

分支保护负责“是否允许合并”，GitHub Actions 负责“执行哪些检查”。两者职责不同，必须同时存在才能形成完整门禁。

## 5. 关键配置文件

| 文件 | 作用 |
| --- | --- |
| `.github/workflows/android-ci.yml` | 定义 CI 触发条件、运行环境和四个检查任务 |
| `build.gradle.kts` | 在根项目统一配置 Spotless 和 ktlint |
| `gradle/libs.versions.toml` | 集中管理 Spotless、ktlint 等版本 |
| `.editorconfig` | 定义项目文本和 Kotlin 格式规则 |
| `gradle/wrapper/gradle-wrapper.properties` | 固定 Gradle 版本、下载地址和发行包校验值 |

## 6. GitHub Actions 配置解析

### 6.1 触发条件

当前工作流的触发配置为：

```yaml
on:
  pull_request:
    branches: [main]
  push:
    branches: [main]
  workflow_dispatch:
```

含义如下：

- `pull_request`：创建或更新面向 `main` 的 PR 时执行，是合并前门禁。
- `push`：提交进入 `main` 后再次执行，用于验证主分支最终状态。
- `workflow_dispatch`：允许在 GitHub Actions 页面手动触发。

只修改本地代码不会触发 GitHub CI，必须推送到 GitHub。

### 6.2 最小权限

```yaml
permissions:
  contents: read
```

工作流只读取仓库内容，不具备写入代码、创建发布或修改 PR 的权限。这遵循最小权限原则，也降低了第三方 Action 或构建脚本被滥用时的风险。

### 6.3 并发控制

```yaml
concurrency:
  group: android-ci-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

同一分支或 PR 连续推送多个版本时，旧版本的未完成任务会被取消，只保留最新提交的检查。这样可以节省 GitHub Actions 时间，也避免开发者误看旧提交结果。

任务显示 `Cancelled` 并不一定代表代码失败，应先确认该任务是否被更新的提交替代。

### 6.4 统一 Java 环境

```yaml
env:
  JAVA_VERSION: "17"
```

四个任务均使用 Temurin JDK 17，与当前 Android Gradle Plugin 和项目构建要求保持一致。开发机也应使用 JDK 17，避免本地与 CI 运行环境不一致。

### 6.5 Action 版本与 Gradle 缓存

每个任务使用：

```yaml
- uses: actions/checkout@v6
- uses: actions/setup-java@v5
  with:
    distribution: temurin
    java-version: ${{ env.JAVA_VERSION }}
    cache: gradle
```

`setup-java` 的 Gradle 缓存可以复用已下载的依赖，提高后续构建速度。缓存只影响速度，不应成为构建正确性的前提。

第三方 Action 应固定在明确的主版本或提交 SHA，升级时通过独立 PR 验证，不使用不可预测的动态版本。

### 6.6 超时、无守护进程和错误栈

每个任务配置了 `timeout-minutes`，防止构建异常挂起并持续消耗资源。

CI 命令统一使用：

```shell
--no-daemon --stacktrace
```

- `--no-daemon`：CI 是一次性环境，无需保留 Gradle Daemon。
- `--stacktrace`：失败时输出调用栈，便于定位问题。

## 7. 四项检查分别保证什么

### 7.1 Build

```shell
./gradlew :app:assembleDevDebug --no-daemon --stacktrace
```

该任务验证 `app` 的 `DevDebug` 变体可以完成依赖解析、资源处理、Kotlin/Java 编译、Manifest 合并和 APK 打包。

它能发现：

- 编译错误。
- 资源引用错误。
- 模块依赖或插件配置问题。
- Manifest 合并问题。
- 开发调试变体打包问题。

它不能证明：

- App 的交互和业务逻辑全部正确。
- Release 变体可构建、可签名或可发布。
- 真机和不同 Android 系统版本上的行为正确。

### 7.2 Unit Tests

```shell
./gradlew testDevDebugUnitTest --no-daemon --stacktrace
```

该命令运行各模块 `DevDebug` 变体的本地 JVM 单元测试。失败时，CI 会继续上传测试报告，便于下载分析：

```yaml
name: unit-test-reports
retention-days: 7
```

单元测试不等于设备测试。依赖 Android Framework、Compose UI、数据库真实行为或系统交互的场景，后续仍需 instrumentation test 或其他测试方案覆盖。

### 7.3 Android Lint

```shell
./gradlew lintDevDebug --no-daemon --stacktrace
```

Android Lint 会检查 Android 特有问题，例如：

- API Level 使用不安全。
- 权限和 Manifest 配置问题。
- 资源缺失、无效或国际化问题。
- Compose 和 AndroidX 的部分静态规则。
- 潜在性能、可访问性和生命周期风险。

Lint 报告会以 `android-lint-reports` Artifact 保留 7 天。不要为了让 CI 通过而直接全局关闭规则；应先判断代码是否确有问题，只有经过说明的误报才考虑局部抑制。

### 7.4 Code Format

```shell
./gradlew spotlessCheck --no-daemon --stacktrace
```

该任务只检查格式，不修改文件。格式不符合规则时任务失败，开发者需要在本地执行修复并提交修复结果。

格式任务使用完整 Git 历史：

```yaml
- uses: actions/checkout@v6
  with:
    fetch-depth: 0
```

这是因为 Spotless 的 `ratchetFrom("origin/main")` 需要访问基准分支历史，浅克隆可能找不到共同基线。

## 8. Spotless、ktlint 与 `.editorconfig`

### 8.1 三者的职责

- Spotless：Gradle 格式检查入口，负责选择文件、调用格式引擎、提供 `spotlessCheck` 和 `spotlessApply` 任务。
- ktlint：Kotlin 格式规则引擎，真正解析并检查 `.kt` 和 `.gradle.kts` 文件。
- `.editorconfig`：跨 IDE 和工具共享的格式规则来源。

它们不是互相替代的关系。当前方案是 Spotless 负责组织，ktlint 负责 Kotlin 格式规则，`.editorconfig` 负责项目约定。

### 8.2 当前版本和目标文件

当前版本集中定义在 `gradle/libs.versions.toml`：

```toml
spotless = "8.10.0"
ktlint = "1.8.0"
```

根项目配置覆盖：

```kotlin
spotless {
    ratchetFrom("origin/main")

    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/generated/**")
        ktlint(libs.versions.ktlint.get())
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "**/.gradle/**")
        ktlint(libs.versions.ktlint.get())
    }
}
```

统一放在根项目的收益是所有模块执行同一套规则，不需要在每个模块重复声明插件和版本。

`build`、`generated` 和 `.gradle` 等自动生成目录被排除，避免修改构建产物。

### 8.3 当前 `.editorconfig` 规则

```editorconfig
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true

[*.{kt,kts}]
indent_style = space
indent_size = 4
max_line_length = 120
ktlint_code_style = android_studio
ij_kotlin_allow_trailing_comma = true
ij_kotlin_allow_trailing_comma_on_call_site = true
```

主要约定：

- 文件使用 UTF-8 和 LF 换行。
- 文件末尾保留一个换行。
- 删除行尾空格。
- Kotlin 使用 4 个空格缩进。
- Kotlin 行宽目标为 120。
- 使用 Android Studio 风格，并允许尾随逗号。

Android Studio 应启用 `.editorconfig` 支持。IDE 自动格式化与 CI 仍有差异时，以仓库固定版本的 `./gradlew spotlessCheck` 结果为准。

## 9. Ratchet 增量格式治理

当前配置：

```kotlin
ratchetFrom("origin/main")
```

Ratchet 的目的不是放宽新代码标准，而是避免一次性格式化整个历史仓库，造成大规模无业务意义的 diff、合并冲突和 Git blame 污染。

它的工作方式是：以 `origin/main` 为基线，只把当前分支相对基线发生变化的目标文件纳入 Spotless 检查。需要注意，它通常是“按文件”纳入，而不是只格式化被修改的几行。因此，一旦修改某个旧文件，该文件中原有的格式问题也可能需要一并修复。

收益包括：

- 新增和修改代码立即遵守统一规范。
- 历史技术债随日常修改逐步收敛。
- 避免单次全仓格式化带来的审查噪声。

限制包括：

- 本地必须能解析 `origin/main`。
- CI 需要完整 Git 历史，因此 `Code Format` 使用 `fetch-depth: 0`。
- 长时间未更新的功能分支可能因为 `main` 变化而需要先同步基线。
- 在 `main` 的 push 工作流中，远端基线可能已指向当前提交；主要格式门禁仍发生在 PR 阶段。

如果本地提示找不到 `origin/main`，先执行：

```shell
git fetch origin main
```

不要为了绕过问题随意删除 Ratchet。是否改成全仓格式检查需要单独评估，并通过专门 PR 处理全仓格式化。

## 10. 日常开发标准流程

### 10.1 开始开发

先更新本地 `main`：

```shell
git switch main
git fetch origin
git merge --ff-only origin/main
```

再创建功能分支：

```shell
git switch -c codex/short-feature-name
```

`--ff-only` 可以避免在本地 `main` 意外制造额外合并提交。如果无法快进，应先检查本地 `main` 是否存在未推送提交，而不是强制覆盖。

### 10.2 开发过程中

优先运行与修改范围直接相关的编译或测试。例如只修改某个模块时，先运行该模块的测试或编译任务，缩短反馈时间。

准备提交前执行：

```shell
./gradlew spotlessApply
./gradlew spotlessCheck
```

然后检查格式化产生的变化：

```shell
git status --short
git diff
```

`spotlessApply` 会修改文件，不能在未查看 diff 的情况下直接提交。

### 10.3 创建 PR 前的完整本地检查

建议执行与 CI 等价的命令：

```shell
./gradlew :app:assembleDevDebug testDevDebugUnitTest lintDevDebug spotlessCheck --no-daemon --stacktrace
```

这条命令可以一次启动四类 Gradle 任务，但本地资源有限时也可以分开执行，便于判断是哪一项失败。

通过后再提交和推送：

```shell
git add <files>
git commit -m "type(scope): summary"
git push -u origin codex/short-feature-name
```

### 10.4 PR 阶段

创建面向 `main` 的 PR 后：

1. 等待 `Build`、`Unit Tests`、`Android Lint`、`Code Format` 全部完成。
2. 任一任务失败时打开对应 Job，定位第一处实际错误。
3. 本地修复并推送到同一分支，PR 会自动重新运行。
4. 如果 GitHub 提示分支落后于 `main`，先更新分支，再等待新一轮检查。
5. 四项检查全部通过后合并。

不要关闭失败的检查、直接改分支保护或重新运行多次来掩盖可复现问题。

### 10.5 合并后更新本地 `main`

```shell
git switch main
git fetch origin
git merge --ff-only origin/main
```

已经合并的功能分支可在确认无继续开发需求后删除。删除分支不影响已经进入 `main` 的提交。

## 11. 分支保护规则

当前 `main` 保护策略为：

- 所有修改必须通过 Pull Request 合并。
- 合并前分支必须与最新 `main` 保持同步。
- 必须通过四项 Required Status Checks：
  - `Build`
  - `Unit Tests`
  - `Android Lint`
  - `Code Format`
- 当前不要求 Approval，适合现阶段单人维护。
- 管理员也不绕过以上规则。

CI Job 的 `name` 是分支保护引用的身份标识。不要随意重命名，否则 GitHub 可能一直等待一个不会再上报的旧检查名称。

新增 Required Check 时，推荐顺序是：

1. 先把新 Job 加入 workflow。
2. 在 PR 中让新 Job 至少成功运行一次。
3. 确认检查稳定且名称正确。
4. 再到 Branch protection 中把它设为 Required。

如果先要求一个从未产生过的 Check，所有 PR 都可能被阻塞。

删除或重命名 Required Check 时则应反向处理：先调整分支保护，再删除或重命名 workflow Job，避免保护规则等待不存在的检查。

## 12. 如何查看 GitHub CI 结果

在 PR 页面可以直接查看 Checks：

- 绿色：任务成功。
- 红色：任务失败，需要打开日志修复。
- 黄色：正在运行或等待 Runner。
- 灰色：跳过或取消，需要确认触发条件或是否被新提交替代。

定位失败的一般顺序：

1. 打开失败的 Check。
2. 展开失败 Step。
3. 从日志中找到第一处明确的 Gradle、编译器、测试或 Lint 错误。
4. 忽略由第一处错误引发的大量后续异常。
5. 使用同一 Gradle 命令在本地复现。

`Unit Tests` 和 `Android Lint` 失败后，可以从该 Workflow Run 的 Artifacts 区域下载报告。Artifact 只保留 7 天，不应作为长期文档存储。

## 13. 常见问题与排查

### 13.1 四个任务都在很短时间内失败

多个独立任务同时快速失败，通常意味着公共初始化环节有问题，而不是四套业务代码同时出错。优先检查：

- `actions/checkout` 是否成功。
- JDK 是否成功安装。
- Gradle Wrapper 是否可下载。
- Wrapper SHA-256 是否正确。
- 依赖仓库是否暂时不可用。

### 13.2 Gradle Wrapper 校验失败

当前 Wrapper 固定 Gradle 9.3.1，并配置了官方 SHA-256：

```properties
distributionSha256Sum=b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06
```

本次 CI 建设中曾出现过校验值错误：本地已有缓存，所以构建没有重新下载发行包，也就没有暴露问题；GitHub Runner 是干净环境，首次下载时立即校验失败。这个案例说明 CI 的价值之一是验证“全新环境能否从零构建”。

升级 Gradle 时必须同时处理：

1. 修改 `distributionUrl`。
2. 从 Gradle 官方发布校验页面取得对应二进制包 SHA-256。
3. 更新 `distributionSha256Sum`。
4. 在干净环境或 CI 中验证。

不要删除校验值来规避失败。校验用于防止下载内容被篡改或损坏。

### 13.3 `Code Format` 失败

本地执行：

```shell
git fetch origin main
./gradlew spotlessApply
./gradlew spotlessCheck
git diff
```

确认变化只涉及合理格式调整后提交。如果格式化结果异常，应检查 `.editorconfig`、ktlint 版本和 Spotless 目标文件范围，而不是手工与工具反复对抗。

### 13.4 找不到 `origin/main`

通常是本地没有获取远端分支，或 CI 使用了浅克隆：

```shell
git fetch origin main
```

CI 的 `Code Format` 必须保留 `fetch-depth: 0`。

### 13.5 Build 仅在 CI 失败

重点检查本地环境是否隐式提供了 CI 没有的内容：

- 未提交但被本地引用的文件。
- 大小写不一致的路径。Linux Runner 区分大小写。
- 本机缓存掩盖的依赖或 Wrapper 问题。
- 只存在于本地的环境变量、SDK 配置或密钥。
- 生成文件是否错误地被源码依赖。

CI 不应依赖开发者机器上的私有状态。

### 13.6 Android Lint 失败

先阅读具体 Issue ID、文件和行号。涉及 Android 版本兼容时，应优先使用正确的 API Guard、兼容实现或提高调用边界，而不是全局 `disable`。

如果确认是误报，使用范围最小的抑制方式，并在代码或 PR 中说明原因。

### 13.7 Unit Tests 失败

本地运行相同测试任务，并根据失败测试进一步缩小范围。报告位于各模块的：

```text
build/reports/tests/testDevDebugUnitTest/
build/test-results/testDevDebugUnitTest/
```

若测试只在 CI 失败，应检查时区、Locale、执行顺序、共享状态、文件路径和时间相关逻辑。

### 13.8 Required Check 一直等待

常见原因：

- Workflow Job 被重命名，但分支保护仍引用旧名称。
- Workflow 触发分支不包含当前 PR 的目标分支。
- YAML 语法错误导致工作流根本没有创建。
- 新增 Check 尚未成功运行过就被设为 Required。

应先核对 Actions 页面是否产生了对应 Workflow Run，再核对 Job 名称和分支保护名称是否完全一致。

## 14. 版本升级原则

### 14.1 Spotless 和 ktlint

升级步骤：

1. 在 `gradle/libs.versions.toml` 修改固定版本。
2. 阅读官方 Release Notes，关注规则变化和最低 Gradle/JDK 要求。
3. 执行 `./gradlew spotlessCheck`。
4. 必要时在独立分支执行 `./gradlew spotlessApply` 并审查全部 diff。
5. 通过 PR 让四项 CI 验证后再合并。

格式引擎升级可能改变大量输出，应避免和业务功能放在同一个 PR 中。

### 14.2 GitHub Actions

升级 `checkout`、`setup-java` 或 `upload-artifact` 时：

- 查看官方迁移说明和运行时要求。
- 使用独立 PR。
- 保持 Job 名称不变，除非同步调整分支保护。
- 验证报告 Artifact 仍能正确上传。

### 14.3 Gradle 和 Android Gradle Plugin

Gradle、AGP、Kotlin 和 JDK 存在兼容矩阵，不能只升级其中一个版本后假设其他组件必然兼容。升级应同时检查：

- Gradle Wrapper 版本和官方 SHA-256。
- AGP 支持的 Gradle 与 JDK 范围。
- Kotlin 和 Compose Compiler/插件兼容性。
- 本地四项任务和 GitHub CI。

## 15. 安全与签名

当前 CI 构建 `DevDebug`，不需要上传正式签名密钥。

必须遵守：

- 不把 keystore、密码、Token 或服务账号文件提交到仓库。
- 不在 workflow 日志中打印 Secret。
- 新增需要写权限的 Job 时单独声明最小权限。
- 第三方 Action 使用可信来源和明确版本。
- 正式发布将来应使用 GitHub Environments、Secrets 和人工审批保护。

如果以后实现 Release/CD，需要单独设计密钥注入、签名、制品留存、发布权限、回滚和审计流程，不能直接在现有 DevDebug Job 中附加明文凭据。

## 16. 如何增加新的 CI 检查

新增检查前先回答：

- 它要防止什么具体问题？
- 本地是否有等价命令？
- 失败信息是否足够清晰？
- 执行时间和资源成本是否合理？
- 它应该是提醒，还是阻止合并的 Required Check？

推荐实施步骤：

1. 在本地建立可重复执行的 Gradle 任务或脚本。
2. 在 workflow 中增加独立 Job，并设置稳定的 `name` 和合理超时。
3. 失败时需要报告的内容使用 `if: always()` 上传 Artifact。
4. 创建 PR，观察成功率、运行时间和错误质量。
5. 稳定后再加入分支保护 Required Checks。
6. 更新本文档中的检查列表和日常命令。

保持独立 Job 的优点是并行执行、失败归因清晰。只有多个步骤强依赖同一昂贵构建产物时，才值得评估合并 Job 或传递 Artifact。

## 17. 当前尚未覆盖的质量能力

后续可以按风险和收益逐步建设：

1. 测试覆盖率报告与最低阈值，例如 Kover。
2. Android instrumentation test 或 Compose UI test。
3. 依赖漏洞、Dependency Review 和 Secret 扫描。
4. Release 构建验证，但暂不发布。
5. APK/AAB Artifact 留存。
6. GitHub Release 或应用商店发布流程。
7. 发布环境保护、人工审批和回滚策略。

建议先提高有效单元测试覆盖率，再增加覆盖率门禁。没有足够测试基础时，只增加百分比阈值容易产生低价值测试。

CD 应在签名和发布权限方案明确后单独建设，不与日常 CI 扩展混在同一个变更中。

## 18. 配置变更与回滚注意事项

CI 配置本身也是生产流程代码，必须通过 PR 审查和现有检查验证。

尤其注意：

- 不要先删除 workflow Job，再处理 Required Check。
- 不要随意修改 Job 的 `name`。
- 不要因一次网络波动永久降低质量门禁。
- 不要将 `spotlessApply` 放进检查任务并自动提交代码；CI 应验证开发者提交的内容。
- 不要通过忽略真实错误来追求全绿。

如果某项检查长期不稳定，应先确认是代码、工具、网络还是规则问题。临时取消 Required 状态属于管理操作，需要记录原因和恢复条件。

## 19. 常用命令速查

更新本地 `main`：

```shell
git switch main
git fetch origin
git merge --ff-only origin/main
```

创建开发分支：

```shell
git switch -c codex/short-feature-name
```

自动修复格式并检查：

```shell
./gradlew spotlessApply
./gradlew spotlessCheck
```

运行完整 CI 等价检查：

```shell
./gradlew :app:assembleDevDebug testDevDebugUnitTest lintDevDebug spotlessCheck --no-daemon --stacktrace
```

只运行某一项：

```shell
./gradlew :app:assembleDevDebug --no-daemon --stacktrace
./gradlew testDevDebugUnitTest --no-daemon --stacktrace
./gradlew lintDevDebug --no-daemon --stacktrace
./gradlew spotlessCheck --no-daemon --stacktrace
```

查看修改：

```shell
git status --short
git diff
```

获取 Ratchet 基线：

```shell
git fetch origin main
```

## 20. 官方参考资料

- [GitHub Actions 文档](https://docs.github.com/actions)
- [GitHub 受保护分支文档](https://docs.github.com/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)
- [actions/checkout](https://github.com/actions/checkout)
- [actions/setup-java](https://github.com/actions/setup-java)
- [actions/upload-artifact](https://github.com/actions/upload-artifact)
- [Spotless Gradle Plugin](https://github.com/diffplug/spotless/tree/main/plugin-gradle)
- [ktlint](https://github.com/pinterest/ktlint)
- [Gradle Release Checksums](https://gradle.org/release-checksums/)
- [Android Lint 文档](https://developer.android.com/studio/write/lint)

## 21. 维护约定

当以下内容发生变化时，应同步更新本文档：

- CI Job 名称、命令或触发条件。
- Required Checks 和分支保护策略。
- JDK、Gradle、AGP、Spotless 或 ktlint 版本。
- `.editorconfig` 规则。
- 新增测试、扫描、制品或发布能力。

本文描述的是当前仓库的实际行为。后续方案应以已合并配置为准，不能只更新文档而不更新代码，也不能只修改 CI 而让使用文档长期失真。
