# advanced：GUI 与玩家体验

advanced 分支把 `/ecoenchants gui` 从简单浏览器扩展为玩家可用的附魔工作台，也新增了自动提示、指南书和管理员工具。目标是减少玩家不知道怎么找附魔、为什么没结果、哪些附魔冲突的问题。

## 玩家主流程

推荐告诉玩家：

1. 执行 `/ecoenchants gui`。
2. 不放物品时可以浏览全部附魔。
3. 把装备放到顶部中间槽后，只看适合该物品的附魔。
4. 点击类型、稀有度、目标筛选按钮缩小范围。
5. 点击冲突查看按钮，了解已有附魔阻止了什么。
6. 用 `/enchantinfo <name> [level]` 随时查看详情。
7. 用 `/ecoenchants toggledescriptions` 控制物品 lore 里的描述显示。

## 主 GUI 控件

默认控件：

| 控件 | 默认物品 | 配置段 | 用途 |
| --- | --- | --- | --- |
| 信息按钮 | player head | `enchant-gui.info` | 告诉玩家怎么放物品和浏览。 |
| 放入物品槽 | 第 1 行第 5 列 | `item-row` / `item-column` | 玩家放装备后触发兼容过滤。 |
| 类型筛选 | compass | `enchant-gui.filters.type` | 在 `types.yml` 的类型之间循环。 |
| 稀有度筛选 | emerald | `enchant-gui.filters.rarity` | 在 `rarity.yml` 的稀有度之间循环。 |
| 目标筛选 | anvil | `enchant-gui.filters.target` | 在 `targets.yml` 的目标之间循环。 |
| 仅兼容 | lime dye | `enchant-gui.filters.compatible-only` | 只显示当前物品可添加附魔。 |
| 冲突查看 | knowledge book | `enchant-gui.conflict-view` | 分析当前物品已有附魔造成的冲突。 |
| 管理员工具 | command block | `enchant-gui.admin-tools` | 有权限时打开管理员 GUI。 |
| 关闭按钮 | barrier | `enchant-gui.close-button` | 关闭界面。 |
| 翻页按钮 | arrow | `enchant-gui.page-change` | 浏览大附魔池。 |

修改按钮位置时，不要占用同一个 `row` / `column`。尤其不要占用放入物品槽。

## 空结果提示

advanced 分支会区分空结果原因：

| 原因 | 玩家看到的含义 | 常见处理 |
| --- | --- | --- |
| `none-loaded` | 当前没有可浏览附魔 | 管理员检查配置和 reload。 |
| `filter` | 当前筛选没有结果 | 继续点击筛选或回到全部。 |
| `item-compatible` | 当前物品没有可添加附魔 | 换装备、关闭仅兼容、查看冲突。 |
| `item-and-group` | 当前分组对该物品无结果 | 返回分组或取出物品。 |
| `item-and-filter` | 当前筛选对该物品无结果 | 切换类型、稀有度或目标。 |

这些提示由 `lang.yml` 的 `hints.empty-results` 和 `gui.enchant.empty-results` 控制。

## 分组浏览

如果默认附魔池太大，建议开启分组：

```yaml
enchant-gui:
  grouped: true
  group-by: type
```

`group-by` 可选：

| 值 | 分组来源 | 使用场景 |
| --- | --- | --- |
| `type` | `types.yml` | 普通、法术、特殊、诅咒。最适合新手。 |
| `rarity` | `rarity.yml` | 按价值层级浏览。适合经济服。 |
| `target` | `targets.yml` | 按装备类型浏览。适合大附魔池。 |

注意：`group-gui.groups[].id` 必须匹配对应来源文件里的 ID。比如 `group-by: rarity` 时，组 ID 应该是 `common`、`rare`、`legendary` 等，而不是 `normal`、`spell`。

## 管理员 GUI

默认开启：

```yaml
admin-gui:
  enabled: true
  tools:
    reload:
      enabled: true
    random-book:
      enabled: true
```

显示条件：

- 玩家有 `ecoenchants.command.reload` 或 `ecoenchants.command.giverandombook`。
- `enchant-gui.admin-tools.enabled: true`。
- `admin-gui.enabled: true`。

工具：

| 工具 | 权限 | 行为 |
| --- | --- | --- |
| reload | `ecoenchants.command.reload` | 调用插件 reload。 |
| random-book | `ecoenchants.command.giverandombook` | 给自己一本随机附魔书。 |

生产服建议只给可信管理员这两个权限。

## 自动提示

默认：

```yaml
player-experience:
  auto-hints:
    enabled: true
    cooldown-seconds: 90
    once-per-player: true
    on-first-join: true
    on-browser-open: true
    on-empty-results: true
    on-filter-change: true
```

触发点：

| 触发 | 说明 |
| --- | --- |
| 首次进服 | 提示 `/ecoenchants gui` 和 `/ecoenchants guide`。 |
| 首次打开浏览器 | 告诉玩家放物品会自动筛选。 |
| 空结果 | 根据原因给下一步建议。 |
| 筛选变化 | 告诉玩家当前筛选值。 |

如果服务器已有教程系统，可以关闭：

```yaml
player-experience:
  auto-hints:
    on-first-join: false
    on-filter-change: false
```

如果玩家反馈提示太频繁，提高冷却：

```yaml
player-experience:
  auto-hints:
    cooldown-seconds: 180
```

## 指南命令

| 命令 | 用途 |
| --- | --- |
| `/ecoenchants guide` | 发送聊天版简短指南。 |
| `/ecoenchants guide book` | 给玩家一本写好的指南书。 |
| `/ecoenchants experience` | 给管理员查看提示设置和空结果统计。 |

`/ecoenchants experience` 的空结果统计只统计当前运行会话，重载会清理部分内存状态，重启后不会保留长期趋势。

## 语言与文案

当前默认 `lang.yml` 已包含中英双语文案。服主可以按服务器风格改：

- `gui.enchant.info`
- `gui.enchant.empty-results`
- `gui.enchant.filters`
- `gui.enchant.conflict-view`
- `gui.group`
- `gui.admin`
- `hints`
- `commands.guide`

建议保持每行 lore 短句，不要写长段落。Minecraft 物品 lore 太长会降低可读性。

## 上线验收

- 普通玩家能打开 `/ecoenchants gui`。
- 放入剑、镐、护甲时结果会变化。
- 类型、稀有度、目标筛选能循环。
- 没有结果时显示 barrier 空结果提示。
- 冲突查看在没放物品时播放无效点击音效并提示。
- 管理员能看到工具入口，普通玩家看不到。
- `/ecoenchants guide book` 能给出指南书。
