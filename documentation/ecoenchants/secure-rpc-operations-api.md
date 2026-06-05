---
title: "安全 RPC 运维与灾备 API"
sidebar_position: 10
---

# `/api/ecoenchants/v1` 安全 RPC 运维与灾备技术需求

本文定义 EcoEnchants 后端与已授权 Minecraft 服务器实例之间的远程运维、受控文件管理、敏感数据脱敏导出、灾备归档与版本回滚能力。目标是在传输安全、身份可信、权限最小化、操作可审计的前提下，为多实例服务器提供集中化调试和应急恢复能力。

本设计不提供裸露的远程代码执行能力。所有远程操作必须映射为后端登记的受控任务，经过 RBAC、实例授权、参数校验、风险确认和审计后才能下发。

## 1. 范围与边界

### 1.1 系统目标

- 支持多实例服务器集中接入后端控制台。
- 支持安全下发受控管理任务，并异步回传执行结果。
- 支持在指定 Minecraft 服务器根目录内进行文件读取、上传、覆盖和安全删除。
- 支持敏感配置、日志片段、玩家相关调试数据的脱敏导出。
- 支持持久化数据和核心配置文件的自动压缩归档、远程触发备份和受控回滚。
- 支持本地安全审计日志与后端审计日志双写。

### 1.2 明确禁止

- 禁止通过 API 暴露任意系统 shell、任意 JVM 代码执行、动态加载外部 JAR 或隐式 RCE。
- 禁止后端接受匿名指令或未签名指令。
- 禁止文件接口访问服务器根目录以外的路径。
- 禁止通过路径穿越、符号链接、Windows 盘符、UNC 路径或绝对路径绕过受控目录。
- 禁止在日志、审计事件、任务结果中回传明文密钥、完整授权码、玩家 IP、聊天内容等非必要敏感数据。

## 2. 基础约定

- Base URL: `https://api.example.com/api/ecoenchants`
- API version: `/v1`
- WebSocket RPC: `wss://api.example.com/api/ecoenchants/v1/rpc/connect`
- Content-Type: `application/json; charset=utf-8`
- 所有时间使用 ISO-8601 UTC，例如 `2026-06-05T08:00:00Z`。
- 所有写入接口必须支持 `Idempotency-Key`。
- 所有响应必须包含 `requestId`。
- 插件必须主动向后端建立出站连接，后端不得要求客户暴露公网入站端口。

## 3. 通信与鉴权

### 3.1 传输安全

- 所有 HTTP 和 WebSocket 连接必须使用 TLS 1.3 或更高安全等级配置。
- 企业版或高权限运维能力必须支持 mTLS，插件实例使用短期客户端证书接入。
- 非 mTLS 场景必须使用短期访问令牌加 HMAC-SHA256 请求签名。
- 证书、令牌和签名密钥必须支持轮换、吊销和过期。

### 3.2 请求签名

使用 HMAC-SHA256 时，请求必须包含：

| Header | 必填 | 说明 |
| --- | --- | --- |
| `Authorization` | 是 | `Bearer <activationToken>` 或运维会话令牌 |
| `X-Eco-Key-Id` | 是 | 签名密钥 ID |
| `X-Eco-Timestamp` | 是 | UTC 秒级时间戳 |
| `X-Eco-Nonce` | 是 | 至少 128 bit 随机值 |
| `X-Eco-Signature` | 是 | HMAC-SHA256 签名 |
| `X-Request-Id` | 否 | 客户端生成或后端生成 |

签名串必须包含 HTTP method、path、query、timestamp、nonce、body SHA-256。后端必须拒绝：

- 时间偏差超过 300 秒的请求。
- 已使用过的 nonce。
- 签名不匹配的请求。
- token scope 与接口不匹配的请求。

### 3.3 权限模型

后端必须实现 RBAC 和实例级授权：

| 能力 | 推荐权限 |
| --- | --- |
| 查看实例状态 | `ops.instance.read` |
| 下发诊断任务 | `ops.job.diagnostics` |
| 读取文件 | `ops.file.read` |
| 上传或覆盖文件 | `ops.file.write` |
| 删除文件 | `ops.file.delete` |
| 创建备份 | `ops.backup.create` |
| 执行回滚 | `ops.backup.restore` |
| 修改任务白名单 | `ops.policy.write` |

高风险操作，例如文件删除、配置覆盖、备份回滚，必须支持二次确认。生产实例建议要求双人审批。

## 4. RPC 连接模型

### 4.1 实例注册

实例必须先完成授权激活，再申请运维 RPC 能力。后端只向具备运维权益和启用配置的实例开放 RPC。

`POST /v1/ops/instances/register`

请求：

```json
{
  "productId": "ecoenchants",
  "activationId": "act_01JZ0000000000000000000000",
  "installationId": "7f29607c-3e30-4b6b-8476-b0b286d9fb10",
  "server": {
    "name": "Survival Network",
    "platform": "Paper",
    "minecraftVersion": "1.21.11",
    "javaVersion": "21.0.7"
  },
  "capabilities": {
    "mtls": true,
    "fileOps": true,
    "backupArchive": true,
    "redactedExport": true
  }
}
```

响应：

```json
{
  "requestId": "req_01JZ0000000000000000000000",
  "instanceId": "ins_01JZ0000000000000000000000",
  "rpcUrl": "wss://api.example.com/api/ecoenchants/v1/rpc/connect",
  "sessionToken": "short-lived-token",
  "sessionExpiresAt": "2026-06-05T09:00:00Z",
  "policyVersion": "pol_2026_06_05"
}
```

### 4.2 WebSocket 握手

插件连接 `GET /v1/rpc/connect` 时必须携带：

- `Authorization: Bearer <sessionToken>`
- `X-Eco-Timestamp`
- `X-Eco-Nonce`
- `X-Eco-Signature`
- 可选 mTLS 客户端证书

连接建立后，插件必须发送 `rpc.hello`：

```json
{
  "type": "rpc.hello",
  "requestId": "req_01JZ0000000000000000000000",
  "instanceId": "ins_01JZ0000000000000000000000",
  "policyVersion": "pol_2026_06_05",
  "supportedMethods": [
    "ops.diagnostics.snapshot",
    "ops.command.runManaged",
    "ops.file.read",
    "ops.file.write",
    "ops.file.delete",
    "ops.backup.create",
    "ops.backup.restore"
  ]
}
```

### 4.3 RPC 消息信封

所有 RPC 消息使用统一信封：

```json
{
  "type": "rpc.request",
  "requestId": "req_01JZ0000000000000000000000",
  "jobId": "job_01JZ0000000000000000000000",
  "method": "ops.file.read",
  "issuedAt": "2026-06-05T08:00:00Z",
  "expiresAt": "2026-06-05T08:05:00Z",
  "params": {}
}
```

插件必须返回接收确认、进度事件和最终结果：

```json
{
  "type": "rpc.result",
  "requestId": "req_01JZ0000000000000000000000",
  "jobId": "job_01JZ0000000000000000000000",
  "status": "succeeded",
  "result": {},
  "completedAt": "2026-06-05T08:00:10Z"
}
```

## 5. 受控指令下发

### 5.1 任务创建接口

控制台通过后端创建任务，后端再通过 RPC 下发给目标实例。

`POST /v1/ops/instances/{instanceId}/jobs`

请求：

```json
{
  "method": "ops.command.runManaged",
  "reason": "Reload EcoEnchants configuration after approved change.",
  "riskLevel": "medium",
  "params": {
    "commandId": "ecoenchants.reload",
    "arguments": {
      "scope": "plugin-config"
    }
  }
}
```

响应：

```json
{
  "requestId": "req_01JZ0000000000000000000000",
  "jobId": "job_01JZ0000000000000000000000",
  "status": "queued",
  "createdAt": "2026-06-05T08:00:00Z"
}
```

### 5.2 指令白名单

`ops.command.runManaged` 只能执行后端策略中登记的 `commandId`。每个指令必须声明：

- `commandId`
- `description`
- `riskLevel`
- `allowedRoles`
- `argumentSchema`
- `timeoutSeconds`
- `maxOutputBytes`
- `requiresApproval`
- `minecraftConsoleTemplate`

示例策略：

```json
{
  "commandId": "ecoenchants.reload",
  "riskLevel": "medium",
  "allowedRoles": ["ops-admin"],
  "argumentSchema": {
    "type": "object",
    "properties": {
      "scope": {
        "type": "string",
        "enum": ["plugin-config"]
      }
    },
    "required": ["scope"]
  },
  "timeoutSeconds": 10,
  "maxOutputBytes": 65536,
  "requiresApproval": false,
  "minecraftConsoleTemplate": "ecoenchants reload"
}
```

插件不得拼接任意 shell 命令。若确需执行 Minecraft 控制台命令，也只能由固定模板生成，并且参数必须通过 schema 校验和字符集限制。

### 5.3 任务状态查询

`GET /v1/ops/jobs/{jobId}`

响应：

```json
{
  "requestId": "req_01JZ0000000000000000000000",
  "jobId": "job_01JZ0000000000000000000000",
  "instanceId": "ins_01JZ0000000000000000000000",
  "method": "ops.command.runManaged",
  "status": "succeeded",
  "createdAt": "2026-06-05T08:00:00Z",
  "startedAt": "2026-06-05T08:00:02Z",
  "completedAt": "2026-06-05T08:00:05Z",
  "output": {
    "truncated": false,
    "text": "Reload complete."
  }
}
```

## 6. 受控文件管理

### 6.1 目录边界

插件必须维护可访问目录清单：

| mount | 说明 |
| --- | --- |
| `server-root` | 指定 Minecraft 服务器运行根目录 |
| `plugin-data` | EcoEnchants 插件数据目录 |
| `config` | 允许维护的配置目录 |
| `logs` | 允许读取的日志目录 |
| `backups` | 备份归档目录 |

所有文件路径必须是相对路径。插件处理路径时必须：

- URL decode 后再规范化。
- 拒绝空路径、绝对路径、`..`、NUL 字符、控制字符。
- 拒绝 Windows 盘符、UNC 路径和跨盘符访问。
- 使用受控根目录解析真实路径，拒绝符号链接逃逸。
- 在写入前再次校验父目录真实路径仍位于受控根目录内。

### 6.2 文件读取

`POST /v1/ops/instances/{instanceId}/files/read`

请求：

```json
{
  "mount": "logs",
  "path": "latest.log",
  "offset": 0,
  "limitBytes": 131072,
  "redactionPolicy": "logs-default"
}
```

响应：

```json
{
  "requestId": "req_01JZ0000000000000000000000",
  "jobId": "job_01JZ0000000000000000000000",
  "status": "queued"
}
```

读取结果必须通过 RPC 异步回传。超过大小限制的文件必须分页或拒绝。

### 6.3 文件上传与覆盖

`POST /v1/ops/instances/{instanceId}/files/write`

请求：

```json
{
  "mount": "config",
  "path": "enchants/custom.yml",
  "mode": "overwrite",
  "contentSha256": "hex-sha256",
  "contentBase64": "base64-content",
  "reason": "Approved configuration update."
}
```

写入要求：

- 默认禁止覆盖 `.jar`、`.class`、`.exe`、`.dll`、`.so`、脚本文件和启动参数文件。
- 写入必须先落到同目录临时文件，校验 hash 后原子替换。
- 覆盖前必须保存变更前 hash，必要时生成本地回滚副本。
- 配置文件写入后可触发受控 reload 任务，但不得自动执行任意命令。

### 6.4 安全删除

`POST /v1/ops/instances/{instanceId}/files/delete`

请求：

```json
{
  "mount": "server-root",
  "path": "cache/tmp-12345.bin",
  "mode": "quarantine",
  "reason": "Clean temporary cache after incident."
}
```

删除要求：

- 默认使用 quarantine 或 recycle 模式，直接永久删除必须是高风险操作。
- 禁止删除受保护目录，例如 `world` 根、`plugins` 根、`backups` 根。
- 批量删除必须限制最大文件数和总大小。
- 每次删除必须记录审计事件和删除前 hash。

## 7. 数据脱敏导出

### 7.1 脱敏策略

后端必须支持命名脱敏策略：

| 策略 | 用途 |
| --- | --- |
| `logs-default` | 日志导出，遮蔽 IP、token、授权码、邮箱 |
| `config-default` | 配置导出，遮蔽 secret、password、key、token |
| `players-debug` | 玩家调试数据，哈希玩家标识并删除非必要字段 |

脱敏方式包括：

- 固定格式遮蔽，例如 `192.168.1.10` 变为 `192.168.x.x`。
- 不可逆哈希，例如 `sha256:<digest>`。
- 稳定伪匿名化，例如使用实例级 salt 的 HMAC-SHA256。
- 字段级删除，例如移除聊天内容、完整 IP、会话 token。

### 7.2 导出接口

`POST /v1/ops/instances/{instanceId}/exports`

请求：

```json
{
  "source": {
    "mount": "plugin-data",
    "paths": [
      "config.yml",
      "enchants/"
    ]
  },
  "redactionPolicy": "config-default",
  "archiveFormat": "tar.gz",
  "reason": "Support debugging without exposing secrets."
}
```

响应：

```json
{
  "requestId": "req_01JZ0000000000000000000000",
  "jobId": "job_01JZ0000000000000000000000",
  "status": "queued"
}
```

导出产物必须记录：

- 源路径清单。
- 每个源文件 hash。
- 使用的脱敏策略版本。
- 导出包 hash。
- 生成时间、操作者、审批记录。

## 8. 灾备归档与回滚

### 8.1 创建备份

`POST /v1/ops/instances/{instanceId}/backups`

请求：

```json
{
  "scope": {
    "mounts": ["plugin-data", "config"],
    "paths": ["config.yml", "enchants/", "types.yml"]
  },
  "format": "tar.gz",
  "retentionDays": 30,
  "reason": "Backup before configuration migration."
}
```

备份要求：

- 只允许备份受控根目录下的文件。
- 归档必须包含 manifest，包括文件路径、大小、mtime、sha256、插件版本、服务器版本。
- 归档文件必须写入受控 backups 目录或上传到后端对象存储。
- 归档 hash 必须回传后端。
- 大型备份必须分片上传并支持续传。

### 8.2 查询备份

`GET /v1/ops/instances/{instanceId}/backups`

响应：

```json
{
  "requestId": "req_01JZ0000000000000000000000",
  "backups": [
    {
      "backupId": "bak_01JZ0000000000000000000000",
      "createdAt": "2026-06-05T08:00:00Z",
      "format": "tar.gz",
      "sizeBytes": 1048576,
      "sha256": "hex-sha256",
      "scope": ["plugin-data", "config"],
      "status": "available"
    }
  ]
}
```

### 8.3 版本回滚

`POST /v1/ops/instances/{instanceId}/backups/{backupId}/restore`

请求：

```json
{
  "mode": "staged",
  "restorePaths": ["config.yml", "enchants/"],
  "preRestoreBackup": true,
  "reason": "Rollback after failed configuration migration."
}
```

回滚要求：

- 必须验证归档 hash 和 manifest。
- 必须先创建回滚前备份，除非实例不可写且审批明确豁免。
- 默认使用 staged 模式，先解压到临时目录并比对目标变更。
- 高风险回滚必须要求二次确认。
- 回滚完成后必须回传变更摘要和审计事件。

## 9. 审计日志

### 9.1 本地 Security Audit Log

插件必须在本地写入 append-only JSONL 审计日志。建议路径：

`plugins/EcoEnchants/security-audit.log`

事件示例：

```json
{
  "auditId": "aud_01JZ0000000000000000000000",
  "requestId": "req_01JZ0000000000000000000000",
  "jobId": "job_01JZ0000000000000000000000",
  "createdAt": "2026-06-05T08:00:00Z",
  "actor": {
    "type": "admin",
    "id": "usr_01JZ0000000000000000000000"
  },
  "action": "ops.file.write",
  "resource": {
    "mount": "config",
    "path": "enchants/custom.yml"
  },
  "decision": "allowed",
  "beforeSha256": "hex-before",
  "afterSha256": "hex-after",
  "policyVersion": "pol_2026_06_05",
  "previousEntryHash": "hex-previous",
  "entryHash": "hex-current"
}
```

审计要求：

- 本地和后端都必须记录关键操作。
- 审计日志不得包含完整 secret、token、license key、玩家 IP 或聊天正文。
- 本地日志建议使用 hash chain 降低篡改风险。
- 后端审计日志必须可按实例、操作者、任务、时间范围查询。

### 9.2 审计查询接口

`GET /v1/ops/audit-logs?instanceId={instanceId}&from={time}&to={time}`

响应必须分页，并支持按 `action`、`actorId`、`jobId` 过滤。

## 10. 错误模型

通用错误响应：

```json
{
  "requestId": "req_01JZ0000000000000000000000",
  "error": {
    "code": "path_outside_allowed_root",
    "message": "The requested path is outside the allowed root.",
    "retryAfterSeconds": null
  }
}
```

常见错误码：

| code | HTTP | 说明 |
| --- | --- | --- |
| `unauthorized` | 401 | token 缺失或无效 |
| `signature_invalid` | 401 | 签名校验失败 |
| `permission_denied` | 403 | 权限不足 |
| `approval_required` | 403 | 需要审批 |
| `instance_offline` | 409 | 实例未连接 RPC |
| `policy_rejected` | 422 | 任务不符合策略 |
| `path_outside_allowed_root` | 422 | 路径越界 |
| `file_type_blocked` | 422 | 文件类型禁止写入 |
| `backup_integrity_failed` | 422 | 备份 hash 或 manifest 校验失败 |
| `rate_limited` | 429 | 请求过于频繁 |

## 11. 运行限制

- 单实例并发任务数默认不超过 2。
- 文件读取默认单次不超过 1 MiB，必须支持分页或分片。
- 文件写入默认单文件不超过 10 MiB，超过需走分片上传。
- 任务输出默认最多保留 64 KiB，超出必须截断并标记。
- WebSocket 断线必须指数退避重连。
- 后端不得因为 RPC 不可用影响插件核心游戏逻辑，除非客户明确启用严格运维模式。

## 12. 最低验收清单

- [ ] 所有控制链路使用 TLS，企业高权限能力支持 mTLS。
- [ ] 所有写操作通过 HMAC 时间戳签名或 mTLS 身份校验。
- [ ] 后端拒绝匿名、过期、重放和 scope 不匹配的请求。
- [ ] 远程指令只能执行白名单受控任务，不能执行任意 shell。
- [ ] 文件路径规范化和真实路径校验覆盖 Linux 与 Windows。
- [ ] 文件写入使用原子替换，删除默认进入隔离区。
- [ ] 脱敏导出覆盖 license key、token、password、IP、email 等敏感模式。
- [ ] 备份归档包含 manifest 和 sha256，回滚前默认创建预备份。
- [ ] 本地 Security Audit Log 与后端审计日志都能追踪关键操作。
- [ ] 高风险任务支持审批、二次确认、限流和可取消。
- [ ] 断网或后端故障不会触发未授权操作，也不会破坏服务器数据。
