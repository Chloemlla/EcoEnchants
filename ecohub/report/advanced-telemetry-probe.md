# advanced：遥测与环境探针

advanced 分支新增运行时遥测和环境探针。它们用于审计、风控和排障，但也涉及玩家行为数据，服主必须根据服务器隐私政策决定开启范围。

## 两条数据路径

| 路径 | 配置 | 说明 |
| --- | --- | --- |
| 本地审计 | `runtime-telemetry.audit-log` | 写入服务器本地 JSONL 文件。 |
| 远程上报 | `runtime-telemetry.remote-reporting` | 批量 POST 到后端 `/telemetry/events`。 |

如果你只想本地留痕：

```yaml
runtime-telemetry:
  enabled: true
  audit-log:
    enabled: true
  remote-reporting:
    enabled: false
```

如果你连本地遥测也不想要：

```yaml
runtime-telemetry:
  enabled: false
```

## 本地审计日志

默认：

```yaml
runtime-telemetry:
  audit-log:
    enabled: true
    file: telemetry/audit.jsonl
    max-file-size-mb: 10
```

日志是 JSONL，每行一个事件。达到大小上限后会按实现策略轮转或限制写入。建议把该目录从公开下载、网页日志和低权限面板中排除。

## 远程上报

默认：

```yaml
runtime-telemetry:
  remote-reporting:
    enabled: true
    api-url: "https://tts.chloemlla.com/api/ecoenchants/v1"
    endpoint: "/telemetry/events"
    interval-ticks: 1200
    batch-size: 100
    max-queued-events: 5000
    timeout-ms: 3000
    require-activation-token: true
```

工作方式：

1. 事件先进入本地统一结构。
2. 本地审计按配置写入。
3. 如果远程上报开启且有 activation token，事件进入队列。
4. 队列有内容时才启动周期上报。
5. `2xx` 响应视为成功。
6. 非 `2xx`、超时或网络失败会把批次放回队列等待重试。
7. 超过 `max-queued-events` 会丢弃超限事件，并在 `/ecoenchants services` 显示 dropped 计数。

## 事件类别

| 类别 | 记录内容 |
| --- | --- |
| `telemetry_lifecycle` | 插件遥测启动、重载、停止。 |
| `environment_probe` | JVM 参数、Java agent、环境变量和系统属性探针结果。 |
| `identity_anchor` | 玩家 UUID、名称、online-mode、网络路由哈希。 |
| `client_context` | 协议版本、客户端品牌、语言、视距、ping。 |
| `session_end` | 玩家离线和会话结束。 |
| `trajectory_sample` | 可选移动采样，默认 `log-samples: false`。 |
| `trajectory_anomaly` | 超距离或超速度移动异常。 |
| `trajectory_transition` | 传送、跨世界等空间切换。 |
| `state_transition` | 飞行状态等玩家状态变化。 |
| `state_baseline` | 背包状态基线 hash。 |
| `state_delta` | 背包状态 hash 变化。 |
| `economy_delta` | 经验、等级、附魔消耗等变化。 |
| `behavioral_text` | 聊天/命令文本风险元数据。 |

## 隐私边界

默认不会写入或上传：

- 明文玩家 IP。
- 完整聊天文本。
- 完整背包内容。

默认会记录：

- 玩家 UUID 与名称。
- 网络地址、hostname、virtual host 的 hash。
- 坐标、世界 UUID、世界名 hash。
- 背包整体 hash 和按材料汇总的数量。
- 聊天/命令文本长度、hash、命中风险词。

会扩大数据面的配置：

```yaml
runtime-telemetry:
  privacy:
    include-raw-network-addresses: true
  text:
    capture-raw: true
```

除非你已经在规则、隐私政策和管理制度里明确说明，否则不要开启。

## 常用配置方案

### 保守生产服

```yaml
runtime-telemetry:
  enabled: true
  remote-reporting:
    enabled: false
  privacy:
    include-raw-network-addresses: false
  text:
    capture-raw: false
    log-all-metadata: false
```

### 风控服群

```yaml
runtime-telemetry:
  enabled: true
  remote-reporting:
    enabled: true
    require-activation-token: true
  movement:
    enabled: true
    log-samples: false
  text:
    enabled: true
    log-command-root: true
    log-matched-terms: true
```

### 排查移动异常

```yaml
runtime-telemetry:
  movement:
    enabled: true
    sample-interval-ms: 1000
    max-distance-per-sample: 24.0
    max-blocks-per-second: 30.0
    log-samples: false
```

不要长期打开 `log-samples: true`，否则事件量会明显增加。

## 环境探针

默认：

```yaml
runtime-telemetry:
  environment-probe:
    enabled: true
    interval-ticks: 1200
    redline-action: disable-plugin
    denied-jvm-args:
      - "-agentlib:jdwp"
      - "-Xdebug"
    block-java-agents: false
    denied-env-vars: []
    denied-system-properties: []
```

它会检查：

- JVM 启动参数。
- Java agent。
- 指定环境变量。
- 指定系统属性。

`redline-action` 可选：

| 值 | 行为 |
| --- | --- |
| `disable-plugin` | 命中红线时禁用 EcoEnchants。生产服默认推荐。 |
| `log-only` | 只写日志和审计，不禁用。测试服、调试服推荐。 |

## `/ecoenchants services` 中怎么看遥测

会出现 `Runtime telemetry` 和 `Telemetry remote reporting` 两段：

| 行 | 含义 |
| --- | --- |
| `Enabled` | 总开关。 |
| `Audit log enabled` | 是否写本地 JSONL。 |
| `Remote reporting enabled` | 是否远程上报。 |
| `Remote reporting URL` | 实际上报地址。 |
| `Queued events` | 等待发送事件数。 |
| `Dropped events` | 队列溢出被丢弃事件数。 |
| `Last result` | 最近一次发送结果。 |
| `Environment redline action` | 环境探针命中后的动作。 |

## 后端接收建议

后端应：

- 按 `productId + installationId + eventId` 去重。
- 对重复事件返回 `2xx`。
- 支持默认批量 100 条。
- 对 raw IP、raw text 设置更短保留周期。
- 不要用 `4xx` 拒绝单条坏事件导致插件整批重试。

## 服主验收

- 本地 `plugins/EcoEnchants/telemetry/audit.jsonl` 能看到生命周期事件。
- `/ecoenchants services` 中 queued 不长期增长。
- dropped 长期为 0。
- 隐私扩大项保持关闭，除非已经完成告知。
- 调试服已把 `redline-action` 改成 `log-only`。
