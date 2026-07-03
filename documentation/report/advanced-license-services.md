# advanced：授权与服务状态

本页教服主完成 advanced 分支的启动授权，并读懂 `/ecoenchants services` 输出。授权是 advanced 分支的第一道门：授权失败时，核心运行时不会继续启用。

## 授权启动流程

启动时插件会读取：

```yaml
license:
  key: ""
  api-url: "https://tts.chloemlla.com/api/ecoenchants/v1"
  channel: stable
  timeout-ms: 3000
  installation-id: ""
  send-server-name: false
  send-build-fingerprint: true
```

然后向后端发送 `POST /licenses/verify`。后端返回 `valid` 或 `trial` 才算通过。成功响应中如果包含 `activationToken` 和 `activationId`，后续远程运维和遥测远程上报才有凭据。

## 第一次部署

1. 在 `license.key` 填入授权 key。
2. 保持 `api-url` 为后端给出的地址。默认地址已经包含 `/api/ecoenchants/v1`。
3. 多台服务器共用同一授权体系时，为每个实例设置不同 `installation-id`；留空时插件会生成并保存本地安装 ID。
4. 如果不希望后端看到服务器名，保持 `send-server-name: false`。
5. 启动服务器后，用管理员账号执行 `/ecoenchants services`。

## URL 容错规则

插件会规范化后端地址：

| 输入 | 实际用途 |
| --- | --- |
| `https://tts.chloemlla.com` | 补成 `https://tts.chloemlla.com/api/ecoenchants/v1` |
| `https://tts.chloemlla.com/api/ecoenchants` | 补成 versioned API |
| `https://tts.chloemlla.com/api/ecoenchants/v1` | 原样作为 versioned API |
| 重复粘贴的绝对 URL | 尝试折叠成最后一个有效绝对 URL |

仍然建议直接填写完整默认格式，减少排查成本。

## `/ecoenchants services` 怎么看

这个命令会输出多个区块。第一段来自授权策略：

| 行 | 含义 | 正常判断 |
| --- | --- | --- |
| `EcoEnchants license gate` | 授权门禁区块开始 | 只是标题。 |
| `Mode: required-online` | 当前为强制在线授权模式 | advanced 分支预期如此。 |
| `API URL` | 配置中填写的后端地址 | 应指向你的授权后端。 |
| `Contract URL` | 规范化后的契约根地址 | 应以 `/api/ecoenchants/v1` 结尾。 |
| `Product ID` | 固定 `ecoenchants` | 用于后端区分产品。 |
| `Channel` | `stable` 等发布频道 | 应与你购买或部署的频道一致。 |
| `Timeout` | 授权请求超时 | 代码会限制在 500-5000ms。 |
| `Send server name` | 是否发送服务器名 | 生产服通常保持 false。 |
| `Send build fingerprint` | 是否发送构建指纹 | 默认 true，便于后端判断构建。 |
| `Last check` | 最近授权结果 | 应显示 valid/trial 相关摘要。 |

如果 `Last check` 显示失败，先排查 key、网络、后端地址和后端响应。

## 与远程运维的关系

远程运维不直接使用 `license.key` 建立长期连接，而是依赖授权响应中的 `activationToken`。因此可能出现：

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 插件启动成功，但远程运维等待 token | 授权通过但后端未返回 `activationToken` | 修后端授权响应，或关闭 `remote-operations.enabled`。 |
| 遥测本地有日志，但不上报 | `require-activation-token: true` 且没有 token | 修后端响应，或关闭远程上报。 |
| `/ecoenchants services` 显示 remote enabled，但 Instance ID 是 `unregistered` | RPC 注册未成功 | 查看远程运维状态和后端 `/ops/instances/register`。 |

## 推荐配置模板

只启用授权和本地功能：

```yaml
license:
  key: "替换为授权 key"
  api-url: "https://tts.chloemlla.com/api/ecoenchants/v1"
  channel: stable
  timeout-ms: 3000
  send-server-name: false
  send-build-fingerprint: true

remote-operations:
  enabled: false

runtime-telemetry:
  remote-reporting:
    enabled: false
```

启用后端状态联动，但不允许远程改文件：

```yaml
license:
  key: "替换为授权 key"

remote-operations:
  enabled: true
  file-ops:
    enabled: false
  backups:
    enabled: false
```

## 常见失败

| 失败 | 判断 | 处理 |
| --- | --- | --- |
| `No license key is configured at license.key.` | 未填写 key | 填写 key 后重启。 |
| 授权请求超时 | 后端不可达或 `timeout-ms` 太短 | 检查防火墙、DNS、TLS，必要时调到 5000。 |
| HTTP 非 2xx | 后端拒绝或接口路径不对 | 检查 `api-url` 和 license 后端日志。 |
| 返回状态不是 `valid` / `trial` | 授权无效、过期或后端响应格式不符 | 修授权或后端响应。 |
| 插件启动后远程功能不可用 | 缺少 `activationToken` | 后端 `valid`/`trial` 响应需包含 token。 |

## 服主验收

- `/ecoenchants services` 能显示授权最近检查成功。
- `Contract URL` 与后端实际接口一致。
- 如果开启远程运维，`Remote operations` 不应长期停在 `waiting for activation token from license verification`。
- 如果只想本地运行，`remote-operations.enabled` 和 `runtime-telemetry.remote-reporting.enabled` 已按策略关闭。
