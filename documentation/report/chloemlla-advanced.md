# Chloemlla advanced 新功能

本章专门说明 Chloemlla `advanced` 分支相对上游新增或强化的功能，帮助服主知道“新增了什么、默认怎么运行、如何启用或关闭、风险在哪里”。

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

## 在线授权门禁

启动流程会调用后端 license 校验。只有返回 `valid` 或 `trial` 才会继续启用核心运行时。授权结果还会为遥测和远程运维提供 activation token。

服主需要做：

1. 在 `license.key` 填入授权 key。
2. 保持 `license.api-url` 指向正确后端。
3. 用 `/ecoenchants services` 查看最近授权状态。
4. 如果服务器名属于隐私信息，保持 `send-server-name: false`。

风险边界：

- 后端不可用或授权失败会影响插件启动。
- `timeout-ms` 太短会让弱网络实例误判失败。
- 多实例共用 key 时要明确安装 ID 策略，避免后端侧难以区分实例。

## 远程运维客户端

默认配置中 `remote-operations.enabled: true`，但高风险的文件操作和备份是关闭的：

```yaml
remote-operations:
  enabled: true
  file-ops:
    enabled: false
  backups:
    enabled: false
```

远程运维不会暴露任意 shell。源码和文档都把操作限定为受控 RPC 方法，包含诊断、受控命令、文件读取/写入/删除、备份创建、备份恢复等。安全层支持：

- 强制安全传输，拒绝普通 `http/ws`。
- HMAC 签名，防重放和未签名 RPC。
- 可选 mTLS 客户端证书。
- 本地 `security-audit.log` 审计。
- 路径规范化和真实路径校验，防止 `..`、绝对路径、Windows 盘符、UNC、符号链接逃逸。
- 写入大小限制、读取大小限制、永久删除默认禁止。

推荐生产配置：

```yaml
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
    enabled: false
```

只有当你已经有后端控制台、RBAC、审批、审计和回滚流程时，才打开：

```yaml
remote-operations:
  file-ops:
    enabled: true
  backups:
    enabled: true
```

## 运行时遥测

`runtime-telemetry` 默认开启，本地写入审计事件，也可远程上报。事件类别包括：

- `telemetry_lifecycle`
- `environment_probe`
- `identity_anchor`
- `client_context`
- `session_end`
- `trajectory_sample`
- `trajectory_anomaly`
- `trajectory_transition`
- `state_transition`
- `state_baseline`
- `state_delta`
- `economy_delta`
- `behavioral_text`

默认隐私边界：

- 不上传明文玩家 IP。
- 不上传完整背包内容。
- 不上传完整聊天文本。
- 网络地址、hostname、virtual host 使用本地盐哈希。
- 背包以整体 hash 和材料汇总形式记录。
- 文本默认记录长度、hash、命中风险词等元数据。

会扩大数据面的配置：

```yaml
runtime-telemetry:
  privacy:
    include-raw-network-addresses: true
  text:
    capture-raw: true
```

服主如果没有明确玩家告知和数据处理制度，不建议开启这两个选项。

## 环境探针

环境探针会检查 JVM 参数、Java agent、环境变量和系统属性等信号。默认配置包含：

```yaml
runtime-telemetry:
  environment-probe:
    enabled: true
    redline-action: disable-plugin
    denied-jvm-args:
      - "-agentlib:jdwp"
      - "-Xdebug"
    block-java-agents: false
```

如果命中 redline，默认会禁用 EcoEnchants。生产服建议保留；开发服或排查服如果需要调试器，可以改为：

```yaml
runtime-telemetry:
  environment-probe:
    redline-action: log-only
```

## 玩家体验增强

新增玩家体验模块提供：

- 首次进服提示。
- 首次打开浏览器提示。
- 空结果原因提示。
- 筛选变化提示。
- `/ecoenchants guide` 聊天指南。
- `/ecoenchants guide book` 指南书。
- `/ecoenchants experience` 员工查看提示设置和空结果统计。
- 权限感知帮助，只显示玩家有权限使用的命令。
- GUI 无效点击音效。

服主可以把自动提示作为新手教程的一部分。若服务器已有完整教程系统，可以关闭部分触发：

```yaml
player-experience:
  auto-hints:
    on-first-join: false
    on-filter-change: false
```

## GUI 新增能力

advanced 分支的 GUI 已从“附魔列表”扩展成“浏览、筛选、诊断、管理”入口：

| 功能 | 使用方式 | 适用场景 |
| --- | --- | --- |
| 类型筛选 | 点击 compass | 找普通、法术、特殊、诅咒。 |
| 稀有度筛选 | 点击 emerald | 找指定价值层级。 |
| 目标筛选 | 点击 anvil | 找某种装备可用附魔。 |
| 仅兼容 | 点击 lime dye | 放入装备后只看能添加的附魔。 |
| 冲突查看 | 点击 knowledge book | 看已有附魔阻止了哪些附魔。 |
| 管理员工具 | 点击 command block | 重载配置或给自己随机书。 |
| 分组浏览 | `enchant-gui.grouped: true` | 大附魔池更适合先分类再浏览。 |

## 新命令与状态排查

`/ecoenchants services` 是 advanced 分支最重要的排查命令。它会输出：

- license 校验状态。
- 后端 API URL 和 channel。
- 远程运维是否启用。
- HMAC、mTLS、文件操作、备份状态。
- 远程客户端连接状态。
- 遥测策略和上报状态。
- 环境探针最近发现。

`/ecoenchants experience` 用于排查玩家引导是否过度或失效：

- 自动提示是否启用。
- 首次加入/浏览器/空结果/筛选提示是否启用。
- 空结果原因计数。

## 服主启用建议

保守生产服：

```yaml
remote-operations:
  enabled: false

runtime-telemetry:
  enabled: true
  remote-reporting:
    enabled: false
```

集中运维服群：

```yaml
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

只有在后端审批、审计、备份恢复测试都完成后，再启用 `file-ops.enabled`。

## 必须告知管理团队的风险

- 授权后端不可用会影响启动，应准备维护窗口和沟通方案。
- 远程文件操作一旦启用，必须按高风险权限管理。
- 运行时遥测可能涉及玩家行为数据，应配合服务器隐私政策。
- 环境探针默认可能禁用插件，调试服要改成 `log-only`。
- GUI 管理员工具只应给可信管理员，因为可触发重载和随机书生成。
