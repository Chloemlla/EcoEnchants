# advanced：远程运维与备份

本页说明 advanced 分支的后端远程运维能力。它适合多实例集中维护，但也属于高权限能力：文件操作和备份恢复开启前，必须有明确的权限、审批和审计流程。

## 能做什么

当前源码支持的 RPC 方法分三层：

| 层级 | 方法 | 默认可用条件 |
| --- | --- | --- |
| 诊断 | `ops.diagnostics.snapshot` | `remote-operations.enabled: true` 且注册成功 |
| 受控命令 | `ops.command.runManaged` | 同上 |
| 文件操作 | `ops.file.read`、`ops.file.write`、`ops.file.delete` | `remote-operations.file-ops.enabled: true` |
| 备份恢复 | `ops.backup.create`、`ops.backup.restore` | `remote-operations.backups.enabled: true` |

受控命令不是任意控制台命令。当前只允许：

| commandId | 行为 |
| --- | --- |
| `ecoenchants.reload` | 在服务器线程调用插件重载，并返回耗时与附魔数量。 |
| `ecoenchants.services.status` | 返回授权和远程运维状态行。 |

## 连接流程

1. 授权通过，后端返回 `activationToken`。
2. 插件向 `POST /ops/instances/register` 注册实例。
3. 后端返回 `instanceId`、`sessionToken`、`rpcUrl`、`policyVersion`。
4. 插件连接 WebSocket，默认路径为 `/api/ecoenchants/v1/rpc/connect`。
5. 插件发送 `rpc.hello`，列出当前支持的方法。
6. 后端通过 RPC 下发任务，插件回 `rpc.ack` 和最终 `rpc.result`。

## 安全开关

推荐保持：

```yaml
remote-operations:
  enabled: true
  security:
    require-secure-transport: true
    hmac:
      enabled: true
      require-signed-rpc: true
      max-clock-skew-seconds: 300
    mtls:
      enabled: false
```

含义：

| 配置 | 说明 |
| --- | --- |
| `require-secure-transport` | 拒绝普通 `http` / `ws`，要求 HTTPS/WSS。 |
| `hmac.enabled` | 给注册、握手和 RPC 做签名。 |
| `hmac.require-signed-rpc` | RPC 消息必须带签名。 |
| `hmac.key-id` | 后端需要区分签名密钥时填写。 |
| `hmac.secret` | 留空时使用 activation/session token 作为共享密钥。 |
| `mtls.enabled` | 企业或高权限环境可启用客户端证书。 |

## 三种启用档位

### 只允许状态与 reload

```yaml
remote-operations:
  enabled: true
  file-ops:
    enabled: false
  backups:
    enabled: false
```

适合先上线验证。后端只能拿诊断快照、查询状态、触发 EcoEnchants reload。

### 允许备份，不允许改文件

```yaml
remote-operations:
  enabled: true
  file-ops:
    enabled: false
  backups:
    enabled: true
    max-total-size-mb: 256
```

适合需要远程触发灾备归档，但不希望控制台读写服务器文件的服群。

### 允许文件维护

```yaml
remote-operations:
  enabled: true
  file-ops:
    enabled: true
    server-root: ""
    max-read-bytes: 1048576
    max-write-bytes: 10485760
    allow-permanent-delete: false
  backups:
    enabled: true
```

只有后端 RBAC、审批、审计、备份恢复演练都完成后再用。`allow-permanent-delete` 建议长期保持 false。

## 文件操作边界

远程文件能力支持受控 mount：

| mount | 说明 |
| --- | --- |
| `server-root` | Minecraft 服务器根目录，默认从 `plugins/EcoEnchants` 推断。 |
| `plugin-data` | EcoEnchants 插件数据目录。 |
| `config` | 配置维护目录。 |
| `logs` | 日志读取目录。 |
| `backups` | 备份归档目录。 |

路径必须是相对路径。插件会拒绝绝对路径、`..`、NUL、控制字符、Windows 盘符、UNC 路径和符号链接逃逸。

## 文件读取

读取会返回 base64 内容，并带上文件大小、offset、limit、sha256 和是否截断。

服主侧关注：

- 默认单次最多读 `max-read-bytes`。
- 大文件需要分页读。
- 带 `redactionPolicy` 时会做简单脱敏。
- 每次读取都会写入远程运维审计日志。

适合读取：

- `logs/latest.log` 的片段。
- `plugins/EcoEnchants/config.yml`。
- 单个附魔配置文件。

不适合读取：

- 世界文件。
- 大型数据库。
- 含大量玩家隐私的完整日志。

## 文件写入

写入要求：

- 请求必须带 `contentBase64`。
- 请求必须带 `contentSha256`。
- 插件会校验内容 hash，不匹配直接拒绝。
- 单文件不能超过 `max-write-bytes`。
- 写入先落临时文件，再原子替换。
- `mode` 只支持 `create` 和 `overwrite`。

插件会拒绝写入高风险文件类型和启动脚本，例如 `.jar`、`.class`、`.exe`、`.dll`、`.so`、脚本文件、启动参数文件等。远程维护应该只用于配置和文本文件。

## 删除策略

默认删除模式应使用 `quarantine`。文件会移动到隔离目录，而不是直接消失。

永久删除只有在同时满足下面条件时才可能执行：

- RPC 请求使用 `mode: permanent`。
- `remote-operations.file-ops.allow-permanent-delete: true`。
- 目标不是受保护目录。

插件会保护 `plugins`、`world`、`world_nether`、`world_the_end`、`backups` 等顶层目录，避免远程误删核心数据。

## 备份与恢复

备份归档写入：

```text
plugins/EcoEnchants/backups
```

恢复相关临时目录：

```text
plugins/EcoEnchants/ops-restore-staging
```

建议流程：

1. 开启 `remote-operations.backups.enabled`。
2. 先从后端触发小范围备份，例如 `plugin-data/config.yml`。
3. 下载或读取备份 manifest，确认 sha256、路径和条目数。
4. 在测试服验证 restore。
5. 生产服恢复前，先创建恢复前备份。
6. 恢复后执行受控 `ecoenchants.reload` 或安排重启。

## 审计日志

远程运维审计默认开启：

```yaml
remote-operations:
  audit-log:
    enabled: true
    file: security-audit.log
```

审计会记录 RPC 请求、文件读写删、备份创建和恢复。服主应把该文件纳入运维留存，并限制普通玩家和低权限管理员读取。

## 后端需要实现的接口

最低需要：

- `POST /api/ecoenchants/v1/licenses/verify`
- `POST /api/ecoenchants/v1/ops/instances/register`
- `GET /api/ecoenchants/v1/rpc/connect` WebSocket

如果启用遥测，还需要：

- `POST /api/ecoenchants/v1/telemetry/events`

## 上线建议

- 第一阶段只开 `remote-operations.enabled`，关闭 file ops 和 backups。
- 第二阶段开 backups，只做备份，不做恢复。
- 第三阶段在测试服演练 restore。
- 第四阶段才考虑打开 file ops。
- 永久删除保持关闭，除非后端有双人审批和可追溯工单。
