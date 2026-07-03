# Chloemlla advanced 新功能总览

本章是 Chloemlla `advanced` 分支的功能入口。它先解释新增能力的定位，再把具体使用步骤拆到独立页面，避免把授权、远程运维、遥测和 GUI 说明塞进一个超长文件。

## 新增能力总览

`advanced` 分支新增能力主要分为 6 类：

| 类别 | 功能 | 服主价值 |
| --- | --- | --- |
| 商业授权 | 启动时在线 license 验证 | 统一控制商业构建授权，阻止未授权运行。 |
| 后端接入 | `/api/ecoenchants/v1` URL 规范化、状态查询、activation token | 为授权、遥测和远程运维提供统一后端入口。 |
| 远程运维 | WebSocket RPC、HMAC、mTLS、审计、文件操作、备份、回滚 | 支持集中维护多实例，但需要严格审批和审计。 |
| 运行遥测 | 本地 JSONL 审计、远程批量上报、身份/移动/状态/文本风险事件 | 帮助服主分析异常行为和运行风险。 |
| 玩家体验 | 双语提示、指南命令、指南书、空结果提示、经验统计 | 降低玩家学习成本，减少“GUI 坏了”的误解。 |
| GUI 管理 | 筛选按钮、兼容过滤、冲突查看、分组、管理员工具 | 让玩家更快找到可用附魔，让管理员更快测试配置。 |

## 先读哪一页

| 你要做什么 | 阅读 |
| --- | --- |
| 让插件通过商业授权并启动 | [授权与服务状态](./advanced-license-services) |
| 查看 `/ecoenchants services` 每行是什么意思 | [授权与服务状态](./advanced-license-services) |
| 接入后端控制台、RPC、文件维护、备份回滚 | [远程运维与备份](./advanced-remote-operations) |
| 只想本地审计，不想上报数据 | [遥测与环境探针](./advanced-telemetry-probe) |
| 向玩家开放新版 GUI 和自动提示 | [GUI 与玩家体验](./advanced-gui-experience) |
| 上线前检查或遇到启动/连接失败 | [排障与上线清单](./advanced-troubleshooting) |

## 默认状态

advanced 分支默认更偏“商业版 + 后端可接入”的形态：

| 配置 | 默认 | 影响 |
| --- | --- | --- |
| `license.key` | 空 | 不填 key 时授权校验失败，核心运行时不会启用。 |
| `license.api-url` | `https://tts.chloemlla.com/api/ecoenchants/v1` | 授权、远程运维和遥测默认后端。 |
| `remote-operations.enabled` | true | 授权成功且后端返回 token 后尝试注册远程运维实例。 |
| `remote-operations.file-ops.enabled` | false | 默认不允许远程读写删文件。 |
| `remote-operations.backups.enabled` | false | 默认不允许远程备份/恢复。 |
| `runtime-telemetry.enabled` | true | 本地记录运行审计事件。 |
| `runtime-telemetry.remote-reporting.enabled` | true | 有 activation token 时尝试远程批量上报。 |
| `runtime-telemetry.environment-probe.redline-action` | `disable-plugin` | 命中红线时禁用插件。 |
| `player-experience.auto-hints.enabled` | true | 向玩家发送低频引导提示。 |
| `enchant-gui.filters.*.enabled` | true | GUI 启用类型、稀有度、目标和兼容筛选。 |
| `admin-gui.enabled` | true | 有权限的管理员可从 GUI 打开工具页。 |

## 最小可用配置

如果你只想让插件启动并暂时不接入高风险运维能力，可以使用这个思路：

```yaml
license:
  key: "你的授权 key"

remote-operations:
  enabled: false

runtime-telemetry:
  enabled: true
  remote-reporting:
    enabled: false
```

这样做的结果是：

- 插件仍执行启动授权。
- 不建立远程运维 WebSocket。
- 本地保留遥测审计日志。
- 不向后端发送遥测事件。
- 玩家 GUI、提示、指南和管理员工具仍可使用。

## 集中运维配置

```yaml
license:
  key: "你的授权 key"

remote-operations:
  enabled: true
  security:
    require-secure-transport: true
    hmac:
      enabled: true
      require-signed-rpc: true
  file-ops:
    enabled: false
  backups:
    enabled: true
```

此配置允许后端注册实例、建立 RPC、触发备份和恢复。文件读写删仍然关闭，适合先验证后端控制台、状态查询、诊断快照和受控 reload。

## 必须告知管理团队的风险

- 授权后端不可用会影响启动，应准备维护窗口和沟通方案。
- 远程文件操作一旦启用，必须按高风险权限管理。
- 运行时遥测可能涉及玩家行为数据，应配合服务器隐私政策。
- 环境探针默认可能禁用插件，调试服要改成 `log-only`。
- GUI 管理员工具只应给可信管理员，因为可触发重载和随机书生成。

## 下一步

- 先完成 [授权与服务状态](./advanced-license-services)，确保插件能启动。
- 再按需要启用 [远程运维与备份](./advanced-remote-operations)。
- 同步确认 [遥测与环境探针](./advanced-telemetry-probe) 是否符合你的隐私公告。
- 面向玩家开放前阅读 [GUI 与玩家体验](./advanced-gui-experience)。
