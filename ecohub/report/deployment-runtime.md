# 部署与运行边界

本章用于服主上线前确认 EcoEnchants 的硬性依赖、启动流程和运维边界。

## 插件依赖

`plugin.yml` 中声明的运行关系如下：

| 类型 | 插件 | 说明 |
| --- | --- | --- |
| 必需依赖 | `eco` | EcoEnchants 基础运行库，缺失时插件不能正常启动。 |
| 软依赖 | `libreforge` | 当前构建会嵌入 libreforge shadow jar，同时仍声明软依赖以兼容生态。 |
| 软依赖 | `CMI` | 用于附魔注册和显示相关兼容。 |
| 软依赖 | `EssentialsX` | 用于附魔注册和显示相关兼容。 |

插件 `load: STARTUP`，会在服务端启动早期注册附魔。`folia-supported: true` 已声明，但生产环境仍建议先在测试服检查与其他插件的交互。

## 支持的服务端版本形态

源码包含多个 NMS 模块，当前分支覆盖 `v1_21_8`、`v1_21_10`、`v1_21_11`、`v26_1_1`、`v26_1_2`、`v26_2` 等代理模块。服主不需要手动选择模块，插件会通过代理层加载对应实现。

如果代理加载失败，插件会记录服务端版本、Bukkit 版本和异常类型，并阻止继续运行，避免半注册状态污染附魔注册表。

## advanced 分支授权门禁

当前 `advanced` 分支包含在线授权校验。默认配置位于 `license`：

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

启动时后端返回 `valid` 或 `trial` 才会继续启用核心运行时。授权请求默认发送授权 key、安装 ID、插件/服务端版本、Java 版本、online-mode、频道，并可选发送服务器名和构建指纹。源码注释明确不收集玩家 UUID、玩家 IP、聊天、经济、背包、坐标、权限或世界文件指纹。

服主上线前应确认：

1. 已填写合法 `license.key`。
2. 服务器能访问 `license.api-url`。
3. `timeout-ms` 符合本机到后端的网络质量。
4. 多实例网络应固定或明确区分 `installation-id`。

## 重载与重启

常规配置可以通过 `/ecoenchants reload` 重载。命令反馈会提示重载耗时和附魔数量。

以下情况建议重启：

| 情况 | 原因 |
| --- | --- |
| 新增或删除附魔文件 | 附魔注册和客户端/插件缓存可能需要完整生命周期刷新。 |
| 修改 `display.enabled` | 配置注释明确此项需要服务器重启。 |
| 调整 NMS、依赖或构建产物 | 代理和注册表在启动阶段确定。 |
| 授权/远程运维安全策略大改 | 确保会话 token、HMAC、mTLS、审计状态全部重新初始化。 |

## 文件位置

常用配置文件在插件数据目录中生成，仓库默认资源来源如下：

| 文件 | 用途 |
| --- | --- |
| `config.yml` | 总配置，包含授权、远程运维、遥测、获取来源、显示、GUI、玩家体验。 |
| `lang.yml` | 消息与 GUI 文案，当前默认包含中英双语。 |
| `enchants/*.yml` | 默认附魔与自定义附魔配置。 |
| `types.yml` | 附魔类型、类型限制、高等级偏置、砂轮规则。 |
| `rarity.yml` | 稀有度、附魔台概率、最低等级、村民概率、战利品概率。 |
| `targets.yml` | 目标装备、槽位、额外可附魔物品。 |
| `vanillaenchants.yml` | 原版附魔相关配置。 |

## 上线前检查清单

- 确认 `eco` 已安装且版本与构建匹配。
- 确认服务端版本在当前 NMS 模块覆盖范围内。
- 填写授权 key，并在测试服验证启动结果。
- 保留默认 `remote-operations.file-ops.enabled: false`，除非已经有后端审批与审计流程。
- 检查 `runtime-telemetry` 是否符合你的隐私公告与玩家告知要求。
- 修改 GUI 行列位置后，确认所有按钮没有占用同一格。
- 大型生存服先降低高稀有度获取概率，再逐步观察经济流通。
