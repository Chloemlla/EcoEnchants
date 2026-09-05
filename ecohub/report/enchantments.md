# 附魔库与自定义附魔

当前默认资源包含 102 个实际附魔配置文件，另有 `_example.yml` 用于说明配置格式。附魔文件可以放在 `enchants` 根目录，也可以按集成插件拆分子目录，例如 `enchants/ecoskills`、`enchants/ecojobs`、`enchants/ecopets`。

## 默认附魔统计

按类型统计：

| 类型 | 数量 | 运营含义 |
| --- | ---: | --- |
| `normal` | 79 | 主体附魔池，适合附魔台、村民和战利品。 |
| `spell` | 9 | 通常更像主动或特殊触发效果，默认同类最多 1 个。 |
| `special` | 8 | 高价值能力，默认同类最多 1 个。 |
| `curse` | 5 | 负面或限制型附魔，默认不可砂轮移除。 |
| `common` | 1 | 当前类型表未定义该类型，服主排查展示或分组异常时应优先检查。 |

按稀有度统计：

| 稀有度 | 数量 |
| --- | ---: |
| `legendary` | 31 |
| `rare` | 22 |
| `epic` | 18 |
| `uncommon` | 15 |
| `special` | 6 |
| `common` | 5 |
| `veryspecial` | 5 |

## 附魔文件结构

`_example.yml` 展示了一个附魔常见字段：

```yaml
display-name: "Example"
description: "Gives a &a%placeholder%%&r and a &a+%damage%&r bonus to damage"
placeholder: "%level%"
type: normal
targets:
  - sword
conflicts:
  - sharpness
maximum-level: 5
tradeable: true
discoverable: true
enchantable: true
effects: []
conditions: []
```

服主最常改的字段：

| 字段 | 作用 |
| --- | --- |
| `display-name` | 游戏内显示名，改名不会改变附魔 ID。 |
| `description` | 描述文本，可使用占位符。 |
| `placeholder` | 描述中 `%placeholder%` 的值。 |
| `type` | 对应 `types.yml`，影响颜色、类型限制和砂轮行为。 |
| `targets` | 对应 `targets.yml`，决定可用装备和生效槽位。 |
| `conflicts` | 与指定附魔冲突，防止强力组合。 |
| `requirements` | 前置附魔要求。 |
| `maximum-level` | 最大等级。 |
| `tradeable` | 是否进入村民交易池。 |
| `discoverable` | 是否进入发现类来源。 |
| `enchantable` | 是否能从附魔台获得。 |
| `effects` | libreforge 效果逻辑。 |
| `conditions` | 生效条件。 |

## 获取来源细分

当前分支的信息 GUI 会显示更细的发现来源，例如：

- `discoverable_chests`
- `discoverable_fishing`
- `discoverable_mob_drops`
- `discoverable_raids`

如果单个附魔文件使用了分来源配置，服主可以让某个附魔只在宝箱、钓鱼、怪物掉落或袭击奖励中出现。这样比全局开关更适合做地图探索或活动奖励。

## 自定义附魔建议

新增自定义附魔时建议按以下顺序做：

1. 从 `_example.yml` 复制成新的小写 ID 文件，例如 `shadow_edge.yml`。
2. 先设置 `display-name`、`description`、`type`、`targets`、`maximum-level`。
3. 先只允许管理员命令测试，暂时关闭 `tradeable`、`discoverable`、`enchantable`。
4. 用 `/enchant <id> [level]` 在测试服验证效果、描述和冲突。
5. 再决定是否进入附魔台、村民或战利品。
6. 新增附魔后安排玩家重新登录，生产服更稳妥的做法是重启。

## 平衡检查问题

发布新附魔前至少回答这些问题：

- 这个附魔应该在哪些装备上生效？
- 是否能与原版强力附魔叠加？
- 是否应该和同类插件附魔冲突？
- 最高等级是否会破坏 PvP 或 Boss 战？
- 是否允许村民量产？
- 是否应该只通过活动或稀有战利品出现？
- lore 描述是否足够短，玩家能否理解触发条件？
