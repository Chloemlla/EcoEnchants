# 配置运营手册

本章按服主常见目标整理 `config.yml` 的配置方法。完整默认配置可参考原文档中的 `Plugin Config`，这里重点解释上线运营时该怎么取舍。

## 授权与后端

| 配置段 | 默认状态 | 建议 |
| --- | --- | --- |
| `license` | 启用，必须通过后端验证 | 生产服必须填写 key，并确认后端地址稳定。 |
| `remote-operations.enabled` | true | 如果后端未部署或不使用远程运维，建议改为 false。 |
| `remote-operations.file-ops.enabled` | false | 保持 false，只有需要后端维护文件时启用。 |
| `remote-operations.backups.enabled` | false | 保持 false，除非要用后端触发备份/回滚。 |
| `runtime-telemetry.enabled` | true | 根据隐私政策决定是否开启。 |
| `runtime-telemetry.remote-reporting.enabled` | true | 若只想本地审计，改为 false。 |

最低风险的后端配置：

```yaml
remote-operations:
  enabled: false

runtime-telemetry:
  enabled: true
  remote-reporting:
    enabled: false
```

这样保留本地审计和运行观察能力，但不建立远程运维连接，也不上报遥测。

## 附魔获取渠道

### 偏原版生存

```yaml
enchanting-table:
  enabled: true
  book-multiplier: 0.5
  cap: 5
  reduction: 2.2

villager:
  enabled: true
  pass-through-chance: 25

loot:
  enabled: true
```

适合普通生存服。玩家能从常规玩法获取附魔，但高稀有度仍受稀有度表控制。

### 偏硬核经济

```yaml
enchanting-table:
  cap: 3
  reduction: 3.0

villager:
  pass-through-chance: 45
  book-multiplier: 0.08

loot:
  reduction: 9.0
```

适合担心附魔泛滥的服务器。降低村民和连带附魔产出，保留稀有战利品价值。

### 偏活动赛季

```yaml
enchanting-table:
  cap: 6
  reduction: 1.8

anvil:
  max-repair-cost: 60
  clamp-repair-cost: true
```

适合短赛季、RPG 或活动服，装备成型更快，但要注意 PvP 和 Boss 平衡。

## 铁砧成本

想减少玩家抱怨“太贵”，优先调：

```yaml
anvil:
  cost-exponent: 0.85
  max-repair-cost: 60
  clamp-repair-cost: true
```

想限制毕业装备，优先调：

```yaml
anvil:
  enchant-limit: 8
  clamp-repair-cost: false
```

`clamp-repair-cost: false` 时，超过 `max-repair-cost` 的结果会被阻止，更适合竞技服。

## 物品 lore 展示

大附魔池推荐：

```yaml
display:
  collapse:
    enabled: true
    threshold: 9
    per-line: 2
  descriptions:
    enabled: true
    threshold: 5
    word-wrap: 27
```

这样少量附魔时能看描述，多附魔毕业装备则折叠显示，避免物品说明过长。

如果你有其他插件接管物品展示，才考虑关闭：

```yaml
display:
  enabled: false
```

此项需要重启，不适合热改。

## GUI 与提示

要让玩家更容易上手，保持这些功能开启：

```yaml
player-experience:
  auto-hints:
    enabled: true
    on-first-join: true
    on-browser-open: true
    on-empty-results: true
    on-filter-change: true
```

如果聊天太吵：

```yaml
player-experience:
  auto-hints:
    cooldown-seconds: 180
    on-filter-change: false
```

如果玩家更喜欢分类浏览：

```yaml
enchant-gui:
  grouped: true
  group-by: type
```

`group-by` 可选 `type`、`rarity`、`target`。修改后要检查 `group-gui.groups` 的 `id` 是否来自对应文件。

## 变更发布流程

推荐生产服配置变更流程：

1. 在测试服修改配置。
2. 用 `/ecoenchants reload` 验证常规变更。
3. 用 `/ecoenchants gui` 检查浏览、筛选、空结果、冲突查看。
4. 用 `/enchantinfo` 检查关键附魔的目标、冲突和来源。
5. 如果新增附魔或改注册相关内容，安排重启窗口。
6. 上线后用 `/ecoenchants services` 和 `/ecoenchants experience` 查看运行状态。
