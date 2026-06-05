---
title: "商业化与授权 API 方案"
sidebar_position: 9
---

# EcoEnchants 商业化与授权 API 方案

本文档面向 EcoEnchants 这类 Minecraft Paper/Spigot 插件的商业化落地，重点说明：

- 当前仓库许可证与商业化边界。
- 合理的收费模式、产品分层与运营策略。
- 插件侧接入授权系统时应遵守的工程要求。
- 后端授权 API 需要实现的接口、数据模型、安全要求与验收标准。

> 说明：当前仓库 `LICENSE.md` 是 GPLv3。GPLv3 允许收费分发，但不能禁止接收者复制、修改、再分发 GPL 代码；如果你不是全部版权持有人，也不能把现有 GPL 代码直接改成闭源商业授权。商业化设计必须围绕这一事实展开。

## 1. 总体结论

EcoEnchants 可以商业化，但不建议把“强 DRM 防破解”作为核心价值。

更合理的商业化核心应是：

1. 官方构建与持续兼容更新。
2. 高质量默认配置、平衡性调优与扩展附魔包。
3. 私有支持、故障排查、迁移服务、服务器定制。
4. 官方下载、版本更新、变更日志、兼容性矩阵。
5. 可选云服务，例如授权管理、配置同步、附魔市场、远程备份。
6. 品牌、商标、官方渠道可信度。

授权 API 的定位应是：

- 管理官方购买权益、下载资格、支持资格和云服务权益。
- 给官方构建提供温和的激活、租约和状态校验。
- 在网络异常时提供离线宽限，避免影响服务器正常开服。
- 记录必要审计信息，辅助客服处理盗用、退款、超额激活。

授权 API 不应承诺：

- 永久阻止绕过。GPLv3 代码接收者有权修改源码，包括移除授权检查。
- 通过隐藏算法或硬编码密钥保证安全。插件客户端内的密钥都应视为可被读取。
- 采集玩家数据或服务器隐私信息来做强绑定。

## 2. 法务与许可证边界

### 2.1 GPLv3 下可以做的事

- 可以收费出售插件二进制包。
- 可以只向购买者提供官方下载入口。
- 可以向购买者提供源码或源码获取方式。
- 可以出售技术支持、更新服务、安装服务、配置服务。
- 可以出售独立的配置包、素材包、文档、云服务，但需要注意它们和 GPL 代码是否构成派生作品。
- 可以使用商标、官方渠道、签名构建区分“官方版本”和第三方再分发版本。

### 2.2 GPLv3 下不能依赖的事

- 不能阻止购买者按 GPLv3 再分发他们收到的 GPL 版本。
- 不能在 GPL 版本中加入额外条款禁止修改或绕过授权检查。
- 不能只发二进制而拒绝提供对应源码。
- 如果有外部贡献者或上游版权，不能未经所有版权人同意改成闭源。

### 2.3 推荐许可证策略

如果你拥有全部版权：

- 可采用“双许可证”：
  - 社区版继续 GPLv3。
  - 商业版使用单独商业许可证，包含官方支持、额外功能、私有模块。
- 新增商业模块尽量保持明确边界，避免和 GPL 核心强耦合到难以区分。

如果你基于他人 GPL 项目开发：

- 保持代码 GPLv3 合规。
- 重点销售官方服务、构建、支持、配置内容、云端能力。
- 不把授权系统描述成“禁止使用 GPL 程序”，而是描述成“验证官方权益和云服务资格”。

## 3. 产品与收费建议

### 3.1 产品分层

建议分为 3 个层级：

| 层级 | 适合用户 | 权益 |
| --- | --- | --- |
| Community | 小型服、开发者 | GPL 源码、自行构建、社区支持、基础文档 |
| Pro | 商业服务器 | 官方构建、稳定更新、授权下载、优先问题修复、基础支持、附魔配置包 |
| Network / Enterprise | 多服网络、托管商 | 多实例授权、SLA、迁移支持、定制附魔、私有兼容修复、批量部署 |

### 3.2 计费方式

推荐组合：

- 一次性购买 + 12 个月更新资格。
- 订阅制支持服务。
- 多服务器席位包，例如 1、3、10、无限网络席位。
- 定制开发按工时报价。
- 配置包、平衡包、赛季包作为可选内容。

不建议：

- 对服务器玩家数量做强制实时计费。玩家峰值波动大，争议多，隐私风险高。
- 每次启动都强依赖授权服务器。授权服务故障会直接变成客户事故。
- 把正常 bugfix 全部锁在高价套餐里。会损害插件口碑和安全性。

### 3.3 可售卖能力

EcoEnchants 这类插件适合售卖：

- 版本兼容：Paper/Folia、Minecraft 新版本、NMS 变更适配。
- 附魔平衡：PVP、RPG、生存、空岛、监狱服等预设包。
- 迁移工具：从其他附魔插件迁移 lore、物品和配置。
- 托管服务：在线配置编辑器、配置校验、附魔库导入导出。
- 开发者服务：稳定 Maven API、示例插件、私有集成支持。
- 运维服务：性能分析、异常附魔定位、配置审计。

## 4. 授权系统设计原则

### 4.1 授权对象

建议授权对象为“安装实例”，而不是硬件指纹。

插件首次启动时生成并持久化：

```yaml
license:
  key: ""
  installation-id: "uuid-generated-on-first-run"
  api-url: "https://license.example.com"
  channel: "stable"
  timeout-ms: 3000
  offline-grace-hours: 72
```

绑定字段建议：

- `productId`: 产品 ID，例如 `ecoenchants`。
- `licenseKey`: 用户输入的授权码。
- `installationId`: 插件本地生成的随机 UUID。
- `serverName`: 可选，仅用于客户后台识别。
- `serverVersion`: Paper/Spigot/Folia 版本。
- `pluginVersion`: 插件版本。
- `javaVersion`: Java 版本。

不建议强绑定：

- 机器硬件序列号。
- 全量 IP 地址历史。
- 玩家 UUID、玩家 IP、聊天或经济数据。
- world 文件散列。容器化、迁服、备份恢复都会造成误封。

### 4.2 授权状态

后端应统一返回以下状态：

| 状态 | 含义 | 插件建议行为 |
| --- | --- | --- |
| `valid` | 授权有效 | 正常启用 |
| `trial` | 试用有效 | 正常启用，日志显示试用到期时间 |
| `expired` | 更新或订阅过期 | 允许已安装版本运行，禁止下载新版本或云服务；如果合同要求也可关闭高级功能 |
| `suspended` | 风控暂停 | 进入宽限或限制云服务，提示联系支持 |
| `revoked` | 退款、欺诈、手动吊销 | 禁用商业权益，保留清晰日志 |
| `activation_limit_exceeded` | 激活数量超额 | 本实例不激活，提示到客户后台释放旧实例 |
| `invalid` | 授权码不存在或格式错误 | 首次安装时不启用商业构建，提示配置授权码 |
| `server_error` | 后端异常 | 使用本地缓存租约和离线宽限 |

### 4.3 离线宽限

必须支持离线宽限：

- 最近一次有效校验后，默认允许离线运行 72 小时。
- Enterprise 可配置 7-30 天。
- 宽限期只依赖后端签名过的本地租约，不依赖本地时间完全可信。
- 宽限期内日志降噪，例如每 6 小时警告一次。
- 宽限过期后不要崩溃服务器，应禁用插件或禁用商业功能，并输出明确原因。

### 4.4 签名租约

后端每次激活或校验返回一个签名租约：

- 格式建议使用 JWS/JWT。
- 算法建议 Ed25519。
- 插件内只内置公钥，用于验证响应签名。
- 私钥只保存在后端 KMS 或密钥管理服务。
- 租约包含授权状态、权益、过期时间、离线宽限时间。

示例租约载荷：

```json
{
  "iss": "EcoEnchants License Service",
  "aud": "ecoenchants",
  "productId": "ecoenchants",
  "licenseId": "lic_01JZ0000000000000000000000",
  "activationId": "act_01JZ0000000000000000000000",
  "installationIdHash": "sha256:...",
  "status": "valid",
  "entitlements": ["official-build", "updates", "support", "config-pack-pro"],
  "maxActivations": 3,
  "issuedAt": "2026-06-05T08:00:00Z",
  "expiresAt": "2026-06-08T08:00:00Z",
  "offlineGraceUntil": "2026-06-11T08:00:00Z",
  "latestVersion": "13.0.0",
  "minimumSupportedVersion": "12.5.0"
}
```

## 5. 插件侧接入要求

虽然本文主要定义后端接口，但后端设计必须假设插件会这样接入。

### 5.1 生命周期建议

- 插件启动时先读取本地签名租约。
- 本地租约有效时立即启用，避免阻塞服务器启动。
- 远程授权校验异步执行，不在 Bukkit 主线程阻塞 HTTP 请求。
- 首次安装且没有本地租约时，可进入“未授权限制状态”，提供控制台提示和授权命令。
- 远程返回 `revoked`、`invalid` 且不在宽限时，按策略禁用插件或禁用商业功能。

### 5.2 建议命令与权限

新增管理命令：

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/ecoenchants license status` | `ecoenchants.command.license` | 查看授权状态、到期时间、激活 ID |
| `/ecoenchants license activate <key>` | `ecoenchants.command.license` | 激活授权并写入配置 |
| `/ecoenchants license refresh` | `ecoenchants.command.license` | 手动刷新授权租约 |
| `/ecoenchants license deactivate` | `ecoenchants.command.license` | 释放当前实例激活 |

注意：

- 命令输出不要打印完整 license key。
- 日志中只展示授权码后 4 位，例如 `****-****-ABCD`。
- HTTP 错误只输出可操作信息，不泄露内部堆栈或后端密钥。

### 5.3 配置要求

建议新增：

```yaml
license:
  enabled: true
  key: ""
  api-url: "https://license.example.com"
  channel: "stable"
  timeout-ms: 3000
  offline-grace-hours: 72
  strict-mode: false
```

字段说明：

| 字段 | 要求 |
| --- | --- |
| `enabled` | 官方商业构建默认启用；社区构建可关闭 |
| `key` | 用户授权码，支持命令写入 |
| `api-url` | 默认官方地址；测试构建可改 |
| `channel` | `stable`、`beta`、`dev` |
| `timeout-ms` | 默认 3000，不建议超过 5000 |
| `offline-grace-hours` | 本地偏好值，最终以签名租约为准 |
| `strict-mode` | true 时无有效授权直接禁用；默认 false 更利于客户运维 |

## 6. 后端 API 总览

### 6.1 基础约定

- Base URL: `https://license.example.com/api`
- API version: `/v1`
- Content-Type: `application/json; charset=utf-8`
- 所有接口必须使用 HTTPS。
- 所有时间使用 ISO-8601 UTC，例如 `2026-06-05T08:00:00Z`。
- 所有写接口支持 `Idempotency-Key` 请求头。
- 所有响应包含 `requestId`，便于排查。

通用请求头：

| Header | 必填 | 说明 |
| --- | --- | --- |
| `User-Agent` | 是 | 例如 `EcoEnchants/13.0.0 Paper/1.21.11 Java/21` |
| `X-Request-Id` | 否 | 客户端生成，后端也可生成 |
| `Idempotency-Key` | 写接口必填 | 避免重试导致重复激活 |
| `Authorization` | 部分接口必填 | 激活后使用 `Bearer <activationToken>` |
| `X-Signature` | Webhook 必填 | 第三方平台或内部服务签名 |

通用错误响应：

```json
{
  "requestId": "req_01JZ0000000000000000000000",
  "error": {
    "code": "activation_limit_exceeded",
    "message": "Activation limit exceeded for this license.",
    "docsUrl": "https://docs.example.com/license/errors#activation_limit_exceeded",
    "retryAfterSeconds": null
  }
}
```

HTTP 状态建议：

| 状态码 | 场景 |
| --- | --- |
| `200` | 查询或校验成功 |
| `201` | 激活创建成功 |
| `202` | Webhook 已接收，异步处理 |
| `400` | 请求字段错误 |
| `401` | 未认证或 token 无效 |
| `403` | 授权无权限、被吊销、超额 |
| `404` | 对象不存在 |
| `409` | 幂等冲突或重复激活冲突 |
| `422` | 授权状态不允许当前操作 |
| `429` | 频率限制 |
| `500` | 后端未知错误 |
| `503` | 依赖服务不可用 |

## 7. 插件授权接口

### 7.1 获取服务状态

`GET /v1/health`

用途：

- 监控授权服务是否可用。
- 不参与授权决策。

响应：

```json
{
  "requestId": "req_01JZ0000000000000000000000",
  "status": "ok",
  "time": "2026-06-05T08:00:00Z"
}
```

### 7.2 获取产品策略

`GET /v1/products/{productId}/policy`

用途：

- 获取产品当前支持版本、最新版本、默认校验间隔、公告。
- 可以公开，但不要返回客户敏感信息。

响应：

```json
{
  "requestId": "req_01JZ0000000000000000000000",
  "productId": "ecoenchants",
  "latestVersion": "13.0.0",
  "minimumSupportedVersion": "12.5.0",
  "recommendedJava": 21,
  "supportedPlatforms": ["Paper", "Folia"],
  "defaultCheckIntervalSeconds": 21600,
  "defaultLeaseSeconds": 259200,
  "defaultOfflineGraceSeconds": 259200,
  "notices": [
    {
      "level": "warning",
      "message": "Minecraft 1.21.7 is no longer supported.",
      "startsAt": "2026-06-01T00:00:00Z",
      "endsAt": "2026-07-01T00:00:00Z"
    }
  ]
}
```

### 7.3 获取签名公钥

`GET /.well-known/license-public-keys`

用途：

- 支持公钥轮换。
- 插件仍应内置至少一个当前公钥，避免首次启动完全依赖网络。

响应：

```json
{
  "keys": [
    {
      "kid": "ed25519-2026-01",
      "alg": "EdDSA",
      "kty": "OKP",
      "crv": "Ed25519",
      "x": "base64url-public-key",
      "status": "active",
      "notBefore": "2026-01-01T00:00:00Z"
    }
  ]
}
```

### 7.4 激活授权

`POST /v1/licenses/activate`

用途：

- 授权码首次绑定到当前安装实例。
- 返回 `activationId`、短期 `activationToken` 和签名租约。

请求：

```json
{
  "productId": "ecoenchants",
  "licenseKey": "ECOE-XXXX-XXXX-XXXX",
  "installationId": "7f29607c-3e30-4b6b-8476-b0b286d9fb10",
  "server": {
    "name": "Survival Network",
    "platform": "Paper",
    "platformVersion": "1.21.11-R0.1-SNAPSHOT",
    "minecraftVersion": "1.21.11",
    "onlineMode": true,
    "javaVersion": "21.0.7",
    "timezone": "Asia/Shanghai"
  },
  "plugin": {
    "version": "13.0.0",
    "channel": "stable",
    "buildHash": "sha256:..."
  },
  "capabilities": {
    "foliaSupported": true,
    "offlineLeaseSupported": true,
    "signatureAlgorithms": ["EdDSA"]
  }
}
```

响应 `201`：

```json
{
  "requestId": "req_01JZ0000000000000000000000",
  "license": {
    "licenseId": "lic_01JZ0000000000000000000000",
    "status": "valid",
    "plan": "pro",
    "expiresAt": "2027-06-05T08:00:00Z",
    "supportUntil": "2027-06-05T08:00:00Z",
    "maxActivations": 3
  },
  "activation": {
    "activationId": "act_01JZ0000000000000000000000",
    "name": "Survival Network",
    "createdAt": "2026-06-05T08:00:00Z",
    "lastSeenAt": "2026-06-05T08:00:00Z"
  },
  "entitlements": [
    "official-build",
    "updates",
    "support",
    "config-pack-pro"
  ],
  "activationToken": "jwt-or-random-token",
  "activationTokenExpiresAt": "2026-07-05T08:00:00Z",
  "signedLease": "eyJhbGciOiJFZERTQSIsImtpZCI6ImVkMjU1MTktMjAyNi0wMSJ9..."
}
```

错误：

| code | HTTP | 说明 |
| --- | --- | --- |
| `invalid_license_key` | 401 | 授权码不存在或格式错误 |
| `product_mismatch` | 403 | 授权码不属于该产品 |
| `activation_limit_exceeded` | 403 | 超过激活数量 |
| `license_revoked` | 403 | 授权已吊销 |
| `license_expired` | 422 | 订阅已过期且策略不允许激活 |
| `rate_limited` | 429 | 尝试过于频繁 |

### 7.5 校验授权

`POST /v1/licenses/verify`

认证：

`Authorization: Bearer <activationToken>`

用途：

- 插件启动后异步校验。
- 定期刷新签名租约。
- 同步授权状态、版本策略、权益变化。

请求：

```json
{
  "productId": "ecoenchants",
  "activationId": "act_01JZ0000000000000000000000",
  "installationId": "7f29607c-3e30-4b6b-8476-b0b286d9fb10",
  "nonce": "base64url-random-32-bytes",
  "server": {
    "platform": "Paper",
    "platformVersion": "1.21.11-R0.1-SNAPSHOT",
    "minecraftVersion": "1.21.11",
    "onlineMode": true,
    "javaVersion": "21.0.7"
  },
  "plugin": {
    "version": "13.0.0",
    "channel": "stable",
    "buildHash": "sha256:..."
  },
  "lastLeaseId": "lease_01JZ0000000000000000000000"
}
```

响应 `200`：

```json
{
  "requestId": "req_01JZ0000000000000000000000",
  "status": "valid",
  "lease": {
    "leaseId": "lease_01JZ0000000000000000000001",
    "expiresAt": "2026-06-08T08:00:00Z",
    "offlineGraceUntil": "2026-06-11T08:00:00Z",
    "nextCheckAfter": "2026-06-05T14:00:00Z"
  },
  "license": {
    "licenseId": "lic_01JZ0000000000000000000000",
    "plan": "pro",
    "supportUntil": "2027-06-05T08:00:00Z",
    "maxActivations": 3
  },
  "entitlements": [
    "official-build",
    "updates",
    "support",
    "config-pack-pro"
  ],
  "policy": {
    "latestVersion": "13.0.0",
    "minimumSupportedVersion": "12.5.0",
    "message": null
  },
  "signedLease": "eyJhbGciOiJFZERTQSIsImtpZCI6ImVkMjU1MTktMjAyNi0wMSJ9..."
}
```

特殊响应：

- 授权被吊销时仍可返回 `200`，但 `status` 为 `revoked`，同时返回签名租约。这样插件可以验证“吊销状态”确实来自官方后端。
- 网络错误、`500`、`503` 不应立即导致插件禁用，应让插件使用本地租约和宽限策略。

### 7.6 心跳

`POST /v1/licenses/heartbeat`

认证：

`Authorization: Bearer <activationToken>`

用途：

- 轻量更新 `lastSeenAt`。
- 检测同一授权超额并发使用。
- 不一定每次都返回新租约。

请求：

```json
{
  "productId": "ecoenchants",
  "activationId": "act_01JZ0000000000000000000000",
  "installationId": "7f29607c-3e30-4b6b-8476-b0b286d9fb10",
  "uptimeSeconds": 86400,
  "pluginVersion": "13.0.0"
}
```

响应：

```json
{
  "requestId": "req_01JZ0000000000000000000000",
  "status": "valid",
  "serverTime": "2026-06-05T08:00:00Z",
  "nextHeartbeatAfter": "2026-06-05T09:00:00Z",
  "shouldVerify": false
}
```

频率建议：

- 默认每 1 小时。
- 后端可通过响应要求下次心跳时间。
- 失败后指数退避，避免服务故障时大量重试。

### 7.7 释放激活

`POST /v1/licenses/deactivate`

认证：

`Authorization: Bearer <activationToken>`

用途：

- 用户迁服或停用时释放当前安装实例。

请求：

```json
{
  "productId": "ecoenchants",
  "activationId": "act_01JZ0000000000000000000000",
  "installationId": "7f29607c-3e30-4b6b-8476-b0b286d9fb10",
  "reason": "server_migration"
}
```

响应：

```json
{
  "requestId": "req_01JZ0000000000000000000000",
  "deactivated": true,
  "deactivatedAt": "2026-06-05T08:00:00Z"
}
```

### 7.8 下载最新构建

`GET /v1/downloads/latest?productId=ecoenchants&channel=stable`

认证：

客户 Portal token 或短期下载 token，不建议直接使用插件内的 activation token 下载完整 JAR。

响应：

```json
{
  "requestId": "req_01JZ0000000000000000000000",
  "productId": "ecoenchants",
  "version": "13.0.0",
  "channel": "stable",
  "fileName": "EcoEnchants-13.0.0.jar",
  "sha256": "hex-sha256",
  "signature": "minisign-or-sigstore-signature",
  "downloadUrl": "https://cdn.example.com/signed-url",
  "expiresAt": "2026-06-05T08:15:00Z",
  "sourceArchiveUrl": "https://cdn.example.com/source/EcoEnchants-13.0.0-src.zip"
}
```

要求：

- 下载链接必须短期有效。
- 返回 JAR 校验和与签名。
- GPL 构建必须提供对应源码获取方式。
- 插件不应自动热加载远程 JAR，避免远程代码执行风险。

## 8. 客户后台接口

客户后台用于购买者管理授权、下载和支持资格。

### 8.1 查询我的授权

`GET /v1/me/licenses`

认证：

用户登录 token。

响应：

```json
{
  "requestId": "req_01JZ0000000000000000000000",
  "licenses": [
    {
      "licenseId": "lic_01JZ0000000000000000000000",
      "productId": "ecoenchants",
      "plan": "pro",
      "status": "valid",
      "licenseKeyLast4": "ABCD",
      "supportUntil": "2027-06-05T08:00:00Z",
      "maxActivations": 3,
      "activeActivations": 1
    }
  ]
}
```

### 8.2 查询授权详情

`GET /v1/me/licenses/{licenseId}`

响应应包含：

- 授权基础信息。
- 激活实例列表。
- 最近校验时间。
- 当前权益。
- 可下载版本。
- 发票或订单引用。

### 8.3 释放某个激活

`POST /v1/me/licenses/{licenseId}/activations/{activationId}/revoke`

用途：

- 客户在后台释放旧服务器。
- 后端应限制频率，避免频繁换绑滥用。

请求：

```json
{
  "reason": "customer_requested"
}
```

### 8.4 轮换授权码

`POST /v1/me/licenses/{licenseId}/key/rotate`

用途：

- 授权码泄露时重置。
- 旧 key 立即失效或进入短暂迁移期。

响应：

```json
{
  "requestId": "req_01JZ0000000000000000000000",
  "licenseKey": "ECOE-NEWX-XXXX-XXXX",
  "rotatedAt": "2026-06-05T08:00:00Z"
}
```

### 8.5 查询下载列表

`GET /v1/me/downloads?productId=ecoenchants`

响应应包含：

- 可下载版本。
- 支持的 Minecraft/Paper 版本。
- changelog。
- JAR sha256。
- 源码包地址。
- 是否需要续费才可下载。

## 9. 管理后台接口

管理后台仅限内部使用，必须有强认证、审计日志和 RBAC。

### 9.1 产品管理

| 接口 | 说明 |
| --- | --- |
| `POST /v1/admin/products` | 创建产品 |
| `GET /v1/admin/products` | 查询产品 |
| `PATCH /v1/admin/products/{productId}` | 修改产品策略 |
| `POST /v1/admin/products/{productId}/versions` | 发布版本元数据 |

产品字段：

- `productId`
- `name`
- `currentVersion`
- `minimumSupportedVersion`
- `channels`
- `defaultLeaseSeconds`
- `defaultOfflineGraceSeconds`
- `publicKeys`

### 9.2 套餐管理

| 接口 | 说明 |
| --- | --- |
| `POST /v1/admin/plans` | 创建套餐 |
| `PATCH /v1/admin/plans/{planId}` | 修改套餐 |
| `GET /v1/admin/plans` | 查询套餐 |

套餐字段：

- `planId`
- `productId`
- `name`
- `maxActivations`
- `entitlements`
- `supportDurationDays`
- `updateDurationDays`
- `price`
- `currency`

### 9.3 授权管理

| 接口 | 说明 |
| --- | --- |
| `POST /v1/admin/licenses` | 手动创建授权 |
| `GET /v1/admin/licenses` | 查询授权 |
| `GET /v1/admin/licenses/{licenseId}` | 查询详情 |
| `PATCH /v1/admin/licenses/{licenseId}` | 修改状态、席位、到期时间 |
| `POST /v1/admin/licenses/{licenseId}/revoke` | 吊销授权 |
| `POST /v1/admin/licenses/{licenseId}/extend` | 延长授权 |
| `POST /v1/admin/licenses/{licenseId}/notes` | 添加客服备注 |

### 9.4 激活管理

| 接口 | 说明 |
| --- | --- |
| `GET /v1/admin/activations` | 按授权、客户、IP、版本查询 |
| `POST /v1/admin/activations/{activationId}/revoke` | 吊销单个激活 |
| `POST /v1/admin/activations/{activationId}/rename` | 重命名实例 |

### 9.5 审计与事件

| 接口 | 说明 |
| --- | --- |
| `GET /v1/admin/audit-logs` | 查询管理员操作 |
| `GET /v1/admin/license-events` | 查询授权事件 |
| `GET /v1/admin/risk-events` | 查询风控事件 |

必须记录：

- 谁在什么时间修改了授权。
- 修改前后的关键字段。
- Webhook 来源与处理结果。
- 授权激活、校验、吊销、退款事件。

## 10. 支付与市场 Webhook

### 10.1 Polymart Webhook

`POST /v1/webhooks/polymart`

用途：

- 接收购买、退款、争议、用户信息变更。
- 自动创建或更新 license。

要求：

- 验证 Polymart 签名或 shared secret。
- 使用事件 ID 做幂等。
- 原始 payload 入库，便于重放和排查。

事件处理：

| 事件 | 后端动作 |
| --- | --- |
| purchase_created | 创建 license，发送邮件 |
| purchase_refunded | 设置 `revoked` 或 `suspended` |
| purchase_chargeback | 设置 `suspended`，标记风险 |
| user_email_changed | 更新客户资料 |

### 10.2 Stripe / PayPal Webhook

`POST /v1/webhooks/stripe`

`POST /v1/webhooks/paypal`

要求：

- 必须验证官方 webhook 签名。
- 订单状态和授权状态要有明确映射。
- 退款和拒付必须自动同步到授权状态。
- 重复事件不得重复创建 license。

## 11. 数据模型

建议核心表：

### 11.1 `products`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 产品 ID，例如 `ecoenchants` |
| `name` | string | 产品名 |
| `status` | enum | `active`、`archived` |
| `latest_version` | string | 最新版本 |
| `minimum_supported_version` | string | 最低支持版本 |
| `default_lease_seconds` | int | 默认租约时间 |
| `default_grace_seconds` | int | 默认离线宽限 |

### 11.2 `plans`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 套餐 ID |
| `product_id` | string | 产品 ID |
| `name` | string | 套餐名 |
| `max_activations` | int | 最大激活数 |
| `entitlements` | json | 权益列表 |
| `support_duration_days` | int | 支持期限 |
| `update_duration_days` | int | 更新期限 |

### 11.3 `customers`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 客户 ID |
| `email` | string | 邮箱 |
| `marketplace_user_id` | string | 市场账号 ID |
| `created_at` | timestamp | 创建时间 |

### 11.4 `licenses`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 授权 ID |
| `product_id` | string | 产品 ID |
| `customer_id` | string | 客户 ID |
| `plan_id` | string | 套餐 ID |
| `key_hash` | string | 授权码哈希 |
| `key_last4` | string | 授权码后 4 位 |
| `status` | enum | `valid`、`trial`、`expired`、`suspended`、`revoked` |
| `max_activations` | int | 最大激活数 |
| `expires_at` | timestamp | 授权到期 |
| `support_until` | timestamp | 支持到期 |
| `created_at` | timestamp | 创建时间 |

授权码存储要求：

- 不存明文授权码。
- 使用 HMAC-SHA256 或 Argon2id 哈希。
- pepper 存在 KMS/环境变量，不进数据库。

### 11.5 `activations`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 激活 ID |
| `license_id` | string | 授权 ID |
| `installation_id_hash` | string | 安装 ID 哈希 |
| `name` | string | 客户自定义服务器名 |
| `status` | enum | `active`、`deactivated`、`revoked` |
| `first_seen_at` | timestamp | 首次激活 |
| `last_seen_at` | timestamp | 最近心跳 |
| `last_ip_hash` | string | IP 哈希，可选 |
| `platform` | string | Paper/Folia/Spigot |
| `minecraft_version` | string | MC 版本 |
| `plugin_version` | string | 插件版本 |

### 11.6 `leases`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 租约 ID |
| `activation_id` | string | 激活 ID |
| `status` | enum | 返回给插件的授权状态 |
| `signed_payload` | text | 签名租约 |
| `expires_at` | timestamp | 租约到期 |
| `offline_grace_until` | timestamp | 离线宽限到期 |
| `created_at` | timestamp | 创建时间 |

### 11.7 `orders`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 内部订单 ID |
| `provider` | string | Polymart/Stripe/PayPal |
| `provider_order_id` | string | 平台订单 ID |
| `customer_id` | string | 客户 ID |
| `status` | enum | `paid`、`refunded`、`chargeback` |
| `raw_payload` | json | 原始事件 |

### 11.8 `audit_logs`

记录管理员操作、自动化任务、Webhook 处理和风控决策。

字段至少包括：

- `actor_type`: `admin`、`system`、`webhook`。
- `actor_id`
- `action`
- `resource_type`
- `resource_id`
- `before`
- `after`
- `request_id`
- `created_at`

## 12. 安全要求

### 12.1 服务端安全

- 全站 HTTPS，禁用明文 HTTP。
- 管理后台启用 MFA。
- 管理 API 使用 RBAC，不同角色权限隔离。
- 私钥放 KMS，不写入代码仓库。
- 授权码只存哈希，不可逆。
- Webhook 必须验签。
- 下载 URL 使用短期签名。
- 所有关键操作写审计日志。
- 管理员导出数据需要额外权限和审计。

### 12.2 插件通信安全

- 首次激活使用 license key。
- 激活后使用 activation token。
- activation token 可轮换、可吊销、有过期时间。
- 后端响应的授权结论必须带签名租约。
- 插件验证签名后才更新本地租约。
- 请求包含 nonce，防止简单重放。
- 客户端请求失败时指数退避。

### 12.3 频率限制

建议限制：

| 接口 | 限制 |
| --- | --- |
| activate | 同一 key 每 10 分钟 10 次 |
| verify | 同一 activation 每分钟 6 次 |
| heartbeat | 同一 activation 每分钟 2 次 |
| customer login | 按账号和 IP 限制 |
| admin API | 按账号、IP、权限限制 |

### 12.4 隐私要求

- 不采集玩家列表、玩家 IP、聊天内容、经济数据。
- 服务器 IP 如需风控，建议哈希或只保存最近一次。
- 客户后台必须提供隐私政策。
- 提供数据删除或匿名化流程。
- 日志保留期限明确，例如 180 天。

## 13. 运维要求

### 13.1 可用性

授权服务目标：

- 核心 verify/activate API 月可用性不低于 99.9%。
- API P95 响应时间小于 300ms。
- 单区域故障时，已激活客户可通过本地宽限继续运行。

### 13.2 监控

必须监控：

- API 错误率。
- verify 延迟。
- activate 失败原因分布。
- Webhook 积压。
- 数据库连接池。
- 签名租约生成失败。
- 下载 CDN 错误率。

### 13.3 告警

建议告警：

- `5xx` 超过 1% 持续 5 分钟。
- verify P95 超过 1 秒持续 10 分钟。
- Webhook 处理延迟超过 5 分钟。
- 签名密钥不可用。
- 数据库主从延迟异常。

### 13.4 备份

- 数据库每日全量备份。
- 关键表启用 PITR。
- Webhook 原始事件至少保留 90 天。
- 签名私钥有轮换和灾备方案。

## 14. 风控策略

建议只做低误伤风控：

- 同一授权短时间大量新激活，标记风险而不是立即永久封禁。
- 同一授权在多个国家或机房并发心跳，进入人工审核。
- 退款、拒付自动暂停。
- 客户后台允许自助释放旧激活，但设置每日或每周上限。
- Enterprise 可配置更宽松迁移策略。

不建议：

- 仅凭 IP 变化封禁。服务器迁移和动态 IP 很常见。
- 频繁强制联网校验。
- 插件内做复杂反调试或混淆作为主要防线。

## 15. 文档与客户体验要求

必须提供以下文档：

- 购买后如何下载。
- 如何填写授权码。
- 迁服时如何释放激活。
- 授权服务器不可达时插件如何处理。
- 常见错误码说明。
- 退款、续费、支持范围说明。
- GPL 源码获取方式。
- 隐私政策和数据收集说明。

错误提示应可操作：

| 错误 | 面向用户的提示 |
| --- | --- |
| `invalid_license_key` | 授权码无效，请检查是否复制完整 |
| `activation_limit_exceeded` | 激活数量已达上限，请在客户后台释放旧服务器 |
| `license_revoked` | 授权已被吊销，请联系支持 |
| `network_error` | 无法连接授权服务器，正在使用离线宽限 |
| `lease_expired` | 离线宽限已过期，请恢复网络或重新激活 |

## 16. 开发里程碑

### Phase 1: 最小可用授权服务

范围：

- license key 生成与哈希存储。
- `activate`、`verify`、`heartbeat`、`deactivate`。
- 签名租约。
- 本地离线宽限。
- 管理后台基础查询。

验收：

- 新授权可激活。
- 超出激活数会被拒绝。
- 后端断网时插件可使用本地有效租约启动。
- 吊销授权后，下一次 verify 能返回签名吊销状态。
- 日志不泄露完整授权码。

### Phase 2: 支付与客户后台

范围：

- Polymart/Stripe/PayPal webhook。
- 客户登录。
- 授权列表、激活管理、下载列表。
- 授权码轮换。

验收：

- 购买后自动生成授权。
- 退款后自动吊销或暂停。
- 客户可以自助释放旧激活。
- 重复 webhook 不会重复创建授权。

### Phase 3: 商业运营能力

范围：

- 版本发布管理。
- CDN 下载与文件签名。
- 支持工单关联授权。
- 风控事件与人工审核。
- 管理后台 RBAC 与审计。

验收：

- 管理员操作可追踪。
- 支持按授权快速定位客户环境。
- 发布新版本时客户后台能显示兼容性和 changelog。
- 风控误伤有可回滚流程。

## 17. 最低验收清单

上线前必须满足：

- [ ] GPL 源码提供方式明确。
- [ ] 商业条款不与 GPLv3 冲突。
- [ ] 授权 API 全部 HTTPS。
- [ ] 授权码不明文入库。
- [ ] 响应授权结论有后端签名。
- [ ] 插件 HTTP 请求不阻塞主线程。
- [ ] 支持离线宽限。
- [ ] 支持激活释放。
- [ ] 支持退款/拒付吊销。
- [ ] 客户后台能查看激活实例。
- [ ] 管理员操作有审计日志。
- [ ] 错误码文档完整。
- [ ] 隐私政策说明采集字段。
- [ ] 监控和告警已配置。

## 18. 不建议投入的方向

- 把大量时间投入不可维护的混淆和反篡改。
- 强制每次开服联网且无宽限。
- 采集大量服务器或玩家隐私来识别盗版。
- 用授权 API 控制开源 GPL 用户的基本运行权利。
- 将商业价值全部押在“别人不能复制 JAR”上。

最稳妥的商业化路线是：GPL 合规 + 官方构建 + 高质量配置内容 + 稳定更新 + 支持服务 + 可选云端权益。授权 API 只负责管理官方权益和服务资格，而不是作为唯一商业壁垒。
