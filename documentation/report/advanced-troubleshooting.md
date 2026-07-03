# advanced：排障与上线清单

本页把 advanced 分支常见问题按症状整理。排障时先执行 `/ecoenchants services`，再按对应区块处理。

## 上线前总清单

| 项目 | 必须确认 |
| --- | --- |
| 授权 | `license.key` 已填写，后端能返回 `valid` 或 `trial`。 |
| 后端地址 | `Contract URL` 指向正确 `/api/ecoenchants/v1`。 |
| 远程运维 | 不需要就关闭 `remote-operations.enabled`。 |
| 文件操作 | 默认保持 `file-ops.enabled: false`。 |
| 备份 | 开启前先在测试服验证创建和恢复。 |
| 遥测 | 明确本地审计和远程上报是否符合隐私政策。 |
| 环境探针 | 调试服使用 `log-only`，生产服可保持 `disable-plugin`。 |
| GUI | 检查按钮槽位没有重叠。 |
| 权限 | 普通玩家只给 GUI、guide、enchantinfo、toggledescriptions。 |
| 回滚 | 修改 advanced 后端配置前保留 `config.yml` 备份。 |

## 插件启动失败

| 现象 | 可能原因 | 处理 |
| --- | --- | --- |
| 日志提示没有 license key | `license.key` 为空 | 填写 key 后重启。 |
| 授权请求超时 | 后端不可达、DNS/TLS、防火墙、超时太短 | 检查网络，`timeout-ms` 调到 5000。 |
| 授权返回 invalid | key 错误、过期或后端绑定不匹配 | 检查后端授权记录。 |
| 命中环境红线后禁用 | `environment-probe.redline-action: disable-plugin` | 调试服改 `log-only`，生产服移除调试 JVM 参数。 |
| registry / proxy 相关错误 | 服务端版本或 NMS 代理不匹配 | 换支持版本或等待对应代理模块。 |

## 远程运维不连接

看 `/ecoenchants services` 的 `Remote operations` 区块。

| 状态 | 含义 | 处理 |
| --- | --- | --- |
| `disabled by config` | 配置关闭 | 需要远程运维时改 `remote-operations.enabled: true` 并重载/重启。 |
| `waiting for activation token from license verification` | 授权响应没有 token | 后端 `licenses/verify` 成功响应需要返回 `activationToken`。 |
| `register failed: HTTP ...` | `/ops/instances/register` 失败 | 查后端注册接口、token 权限、实例额度。 |
| `websocket failed` | RPC URL、TLS、HMAC 或网络失败 | 查 `rpcUrl`、证书、WSS、防火墙。 |
| `reconnecting in ...` | 退避重连中 | 看括号里的原因，不要反复重启。 |
| `Instance ID: unregistered` | 尚未完成注册 | 先解决注册或 token 问题。 |

## 远程文件操作被拒绝

| 错误码 | 含义 | 处理 |
| --- | --- | --- |
| `file_ops_disabled` | 文件操作未启用 | 确认是否真的要开 `remote-operations.file-ops.enabled`。 |
| `missing_mount` / `missing_path` | 后端请求缺字段 | 修后端 RPC 参数。 |
| `path_outside_allowed_root` | 路径越界 | 使用相对路径，不要 `..`、绝对路径、跨盘符。 |
| `not_a_regular_file` | 读取目标不是普通文件 | 不要读目录或特殊文件。 |
| `write_limit_exceeded` | 超过写入大小限制 | 提高 `max-write-bytes` 或拆分。 |
| `sha256_mismatch` | 内容 hash 不匹配 | 后端重算 `contentSha256`。 |
| `file_type_blocked` | 文件类型被禁止写入 | 不要远程写 jar、脚本、启动参数等高风险文件。 |
| `unsupported_delete_mode` | 删除模式不允许 | 默认用 `quarantine`；永久删除需显式开启。 |

## 备份或恢复失败

| 错误码 | 含义 | 处理 |
| --- | --- | --- |
| `backups_disabled` | 备份恢复未启用 | 开启 `remote-operations.backups.enabled`。 |
| `backup_limit_exceeded` | 超过备份大小上限 | 调高 `max-total-size-mb` 或缩小 scope。 |
| `backup_not_found` | 找不到归档 | 检查 backupId 和 backups 目录。 |
| `backup_integrity_failed` | manifest 或 zip 条目不可信 | 不要恢复该归档，重新创建备份。 |

恢复生产配置前建议：

1. 先在测试服恢复同一个归档。
2. 生产服先创建恢复前备份。
3. 只恢复必要路径。
4. 恢复后执行受控 reload 或安排重启。

## 遥测队列增长

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `Queued events` 持续增长 | 后端不可达或非 2xx | 查 `/telemetry/events`。 |
| `Dropped events` 增长 | 队列超过 `max-queued-events` | 修后端，临时增大队列或关闭远程上报。 |
| `Last result` 是 401/403 | token 无效或后端鉴权失败 | 检查 activation token 验证。 |
| 没有远程上报 | `require-activation-token` 且无 token | 修授权响应或关闭该要求。 |
| 事件量过大 | 开了 movement samples 或 raw text | 关闭 `movement.log-samples`，减少文本采集。 |

## 玩家 GUI 问题

| 现象 | 可能原因 | 处理 |
| --- | --- | --- |
| 普通玩家打不开 GUI | 缺 `ecoenchants.command.gui` | 给普通组权限。 |
| 放入物品无结果 | 物品没有可用目标、已有冲突、兼容过滤开启 | 关闭仅兼容、查看冲突、检查 `targets.yml`。 |
| 分组页没有某类 | `group-gui.groups[].id` 和 `group-by` 不匹配 | 按 `type`、`rarity` 或 `target` 的真实 ID 修改。 |
| 管理员工具不显示 | 缺 reload/random-book 权限或配置关闭 | 检查权限和 `admin-gui.enabled`。 |
| 按钮重叠 | 多个控件同 row/column | 调整 `config.yml` 槽位。 |
| 提示太吵 | 冷却太短或筛选提示开启 | 提高 `cooldown-seconds` 或关闭 `on-filter-change`。 |

## 推荐的排障顺序

1. 先看服务器启动日志，确认授权和环境探针结果。
2. 执行 `/ecoenchants services`，记录 license、remote、telemetry、probe 状态。
3. 如果是玩家体验问题，执行 `/ecoenchants experience`。
4. 对配置做最小改动，不要同时改授权、远程运维和遥测。
5. 每次改完先 `/ecoenchants reload`，涉及启动授权、注册表、NMS 或显示总开关时安排重启。
6. 问题解决后恢复最小权限：关闭不需要的 file ops、raw text、raw IP 和 permanent delete。
