# 玩法与平衡模型

EcoEnchants 的平衡由“附魔定义、类型、稀有度、目标、获取来源、铁砧规则、展示规则”共同决定。服主调参时不要只改单个概率，否则很容易造成某类装备过强或市场价格失真。

## 附魔生命周期

一个附魔通常从 `enchants/*.yml` 加载，经过注册后成为真实服务端附魔。玩家可以通过以下路径接触它：

| 路径 | 相关配置 | 服主关注点 |
| --- | --- | --- |
| 附魔台 | `enchanting-table`、附魔 `enchantable`、稀有度 `table-chance`、`minimum-level` | 控制普通玩家自然获取速度。 |
| 村民交易 | `villager`、附魔 `tradeable`、稀有度 `villager-chance` | 控制图书管理员经济和刷交易价值。 |
| 自然战利品 | `loot`、附魔 `discoverable`、分来源开关 | 控制探索奖励和地牢价值。 |
| 铁砧合并 | `anvil`、类型限制、冲突和前置 | 控制最终装备成型成本。 |
| 管理命令 | `/enchant`、`/ecoenchants giverandombook` | 用于活动、补偿、测试和管理员干预。 |
| GUI 浏览 | `/ecoenchants gui`、`/enchantinfo` | 帮助玩家理解附魔、冲突和获取来源。 |

## 类型模型

默认 `types.yml` 定义了 4 类：

| 类型 | 默认显示 | 默认限制 | 高等级偏置 | 砂轮 |
| --- | --- | --- | --- | --- |
| `normal` | 灰色 | 不限制 | 0 | 可移除 |
| `curse` | 红色 | 不限制 | 0 | 不可移除 |
| `spell` | 蓝色渐变 | 同类最多 1 个 | 0.5 | 可移除 |
| `special` | 粉色渐变 | 同类最多 1 个 | 0.7 | 可移除 |

`limit` 是装备成型上限的重要工具。想让强力主动类或特殊类附魔不会堆满装备，应保持 `spell` 和 `special` 的 `limit: 1`。`high-level-bias` 会降低高等级出现频率，适合给强力类型增加长期追求。

## 稀有度模型

默认稀有度决定附魔台、村民和战利品概率：

| 稀有度 | 附魔台概率 | 最低等级 | 村民概率 | 战利品概率 |
| --- | ---: | ---: | ---: | ---: |
| `common` | 30 | 1 | 10.5 | 12 |
| `uncommon` | 20 | 5 | 9 | 16 |
| `rare` | 20 | 15 | 7.5 | 18 |
| `epic` | 10 | 16 | 6 | 20 |
| `legendary` | 8 | 20 | 4.5 | 15 |
| `special` | 2 | 30 | 3 | 5 |
| `veryspecial` | 1 | 30 | 1.5 | 2 |

这些概率不是孤立结果，还会被全局 multiplier 和 reduction 影响。比如附魔台有 `book-multiplier: 0.5`、`cap: 5`、`reduction: 2.2`，表示同一次附魔中越靠后的附魔越难出现。

## 获取渠道建议

生存服推荐：

- `common` 到 `rare` 允许附魔台和村民自然流通。
- `epic` 可保留自然战利品，提高探索价值。
- `legendary` 建议降低村民权重，避免刷交易量产。
- `special` 和 `veryspecial` 只保留极低概率，或改为活动、任务、宝箱奖励。
- 诅咒类不要完全关闭，保留少量能让战利品更有风险，但不要让它们进入所有主流获取渠道。

小游戏或赛季服推荐：

- 提高 `enchanting-table.cap` 加快装备成型。
- 降低 `anvil.cost-exponent` 或开启 `vanilla-costs`，减少铁砧成本门槛。
- 用 `/ecoenchants giverandombook` 做赛季奖励，但限制等级范围。

## 铁砧与砂轮

`anvil` 配置决定玩家能否把多个自定义附魔合在一起：

```yaml
anvil:
  vanilla-costs: false
  cost-exponent: 0.95
  enchant-limit: -1
  use-rework-penalty: true
  max-repair-cost: 40
  clamp-repair-cost: true
```

关键含义：

| 配置 | 建议 |
| --- | --- |
| `vanilla-costs` | 想完全贴近原版成本时开启；想让自定义附魔更可控时保持关闭。 |
| `cost-exponent` | 越低越能缓解“过于昂贵”。 |
| `enchant-limit` | 公平竞技服建议设置上限；RPG 服可保持 `-1`。 |
| `max-repair-cost` | 控制最终成本天花板。 |
| `clamp-repair-cost` | 开启时会把成本压到上限；关闭时超过上限会阻止结果。 |

砂轮行为还受类型的 `no-grindstone` 控制。默认诅咒不可通过砂轮移除，这符合原版直觉。

## 展示规则

`display` 控制物品 lore 中如何显示附魔：

- `collapse.enabled` 可在附魔数量超过阈值时折叠显示，减少 lore 爆炸。
- `descriptions.enabled` 可显示附魔描述，但超过阈值不显示，避免装备说明过长。
- `sort.type`、`sort.rarity`、`sort.length` 可改变排序方式。
- `require-enchantable` 可避免非可附魔物品显示 EcoEnchants 信息。

大型服务器建议开启折叠显示，并允许玩家用 `/ecoenchants toggledescriptions` 自己控制描述显示。
