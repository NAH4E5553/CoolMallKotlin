# OpenCodeReview 试用说明

## 定位

OpenCodeReview 是现有 Android CI 之外的自动二次审查，只提供 PR 行级评论和汇总，不替代 Build、Unit Tests、Android Lint、Code Format、人工审查或设备回归，也不加入 `main` 的必需状态检查。

试用固定使用 OpenCodeReview `v1.11.1` 对应提交 `8d023aafcec05f8ba5628fca3eaba88078e5d201`，同时固定 CLI 版本 `1.11.1`。升级时先核对 Release、Action 输入、权限和变更记录，再在独立 PR 中同时更新两处版本。

## 触发范围

满足以下条件时自动运行：

- PR 的目标分支为 `main`；
- PR 不是 Draft；
- PR 分支来自当前仓库，不是外部 Fork；
- PR 被创建、更新、重新打开或转为 Ready for review；
- Diff 包含 Kotlin、Gradle Kotlin DSL、Manifest、values/xml 安全配置、OCR 规则或本工作流。

同一 PR 推送新提交时会取消旧审查并重新运行。试用阶段不监听 PR 评论命令，也不在合并后的 `main` Push 上运行。

## 历史 PR 手动审查

`.github/workflows/open-code-review-history.yml` 提供仅限仓库写权限成员使用的手动入口，不监听评论命令，也不会自动遍历历史 PR。它从 GitHub API 获取目标 PR 当时的 base/head SHA，只读取该精确 Git 范围，不执行历史分支中的 Gradle、脚本或其他代码。

在 GitHub `Actions -> OpenCodeReview History -> Run workflow` 中填写：

- `pr_number`：需要复审的 PR 编号；
- `publish_summary`：是否在目标 PR 发布或更新一条汇总评论，默认开启。

也可以使用 GitHub CLI：

```bash
gh workflow run open-code-review-history.yml \
  --ref main \
  -f pr_number=28 \
  -f publish_summary=true
```

原始 JSON 和变更文件清单作为 Artifact 保留 14 天；为避免上游错误输出意外包含凭据片段，stderr 不进入 Artifact。读取历史范围和运行 OCR 的 Job 只有仓库内容只读权限，PR 写权限仅授予独立的汇总发布 Job。历史审查在仓库内全局串行，避免同时消耗多份模型额度；对同一 PR 重跑时会更新已有的历史审查汇总，避免重复评论；已合并 PR 不发布行内意见。手动工作流必须先合并到默认分支，GitHub 才允许通过 `workflow_dispatch` 运行。

## 启用配置

工作流默认关闭。按以下顺序在 GitHub `Settings -> Secrets and variables -> Actions` 配置，API Token 不得写入仓库、PR、日志或文档：

| 类型 | 名称 | 含义 |
| --- | --- | --- |
| Secret | `OCR_LLM_URL` | OpenAI 兼容或 Anthropic 模型端点 |
| Secret | `OCR_LLM_AUTH_TOKEN` | 模型访问 Token |
| Variable | `OCR_LLM_MODEL` | 模型名称 |
| Variable | `OCR_LLM_USE_ANTHROPIC` | Anthropic 协议填 `true`，OpenAI 兼容协议填 `false` |
| Variable | `OCR_ENABLED` | 前四项确认无误后设为 `true` |

缺少任一模型配置时保持 `OCR_ENABLED=false`。停用时只需将它改回 `false`，不需要删除 Secret 或修改分支保护。

## 结果处理

- Critical、High、Medium 且证据充分的问题在对应代码行确认；Low、Style 和 Documentation 主要进入固定汇总，减少评论噪声。
- 属于本 PR 的真实缺陷在同一 PR 修复并补充匹配测试；新提交会自动重新审查。
- 与本 PR 无关但真实的问题记录为独立后续任务；影响本次安全性或正确性时不能延后。
- 误报需要用调用链、状态约束、接口契约或测试证据说明；同类误报重复出现时调整 `.opencodereview/rule.json`。
- 审查结果不自动修改代码，不因 AI 建议直接扩大修改范围。
- 历史 PR 的真实问题不得回写已经合并的分支，应建立新的独立修复批次；误报仍需给出代码或测试证据。
- OCR 找到问题时任务通常仍会成功；模型超时、认证失败或工具异常才会使非必需的 OpenCodeReview 检查失败。此时查看任务日志并重试，不影响现有四项强制 CI 的判定。

试用 5～10 个代码 PR 后，复盘有效问题、误报、耗时和模型成本，再决定保留、调整或移除。
