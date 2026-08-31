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
- OCR 找到问题时任务通常仍会成功；模型超时、认证失败或工具异常才会使非必需的 OpenCodeReview 检查失败。此时查看任务日志并重试，不影响现有四项强制 CI 的判定。

试用 5～10 个代码 PR 后，复盘有效问题、误报、耗时和模型成本，再决定保留、调整或移除。
