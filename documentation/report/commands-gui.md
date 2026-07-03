# 命令、权限与 GUI

本章整理服主、管理员和普通玩家常用入口。权限来自 `plugin.yml` 和命令源码。

## 命令总表

| 命令 | 用途 | 权限 | 默认 |
| --- | --- | --- | --- |
| `/ecoenchants` | 显示权限可见的帮助列表 | `ecoenchants.command.ecoenchants` | true |
| `/ecoenchants help` | 显示帮助列表 | `ecoenchants.command.ecoenchants` | true |
| `/ecoenchants gui` | 打开附魔浏览 GUI | `ecoenchants.command.gui` | true |
| `/ecoenchants guide` | 查看聊天版玩家指南 | `ecoenchants.command.guide` | true |
| `/ecoenchants guide book` | 给玩家一本指南书 | `ecoenchants.command.guide` | true |
| `/ecoenchants toggledescriptions` | 玩家切换物品 lore 中的附魔描述 | `ecoenchants.command.toggledescriptions` | true |
| `/enchantinfo <name> [level]` | 打开指定附魔的信息 GUI | `ecoenchants.command.enchantinfo` | true |
| `/enchant <id> [level]` | 管理员给自己手持物品添加或移除附魔，等级 `0` 为移除 | `ecoenchants.command.enchant` | op |
| `/enchant <player> <id> [level]` | 控制台或管理员给目标玩家手持物品操作附魔 | `ecoenchants.command.enchant` | op |
| `/ecoenchants giverandombook <player> [type/rarity] [min] [max]` | 给玩家随机附魔书，可按类型或稀有度过滤 | `ecoenchants.command.giverandombook` | op |
| `/ecoenchants reload` | 重载配置 | `ecoenchants.command.reload` | op |
| `/ecoenchants services` | 查看授权、后端、远程运维、遥测和环境探针状态 | `ecoenchants.command.services` | op |
| `/ecoenchants experience` | 查看玩家提示设置和空结果统计 | `ecoenchants.command.experience` | op |

## 权限组建议

普通玩家建议开放：

```text
ecoenchants.command.ecoenchants
ecoenchants.command.gui
ecoenchants.command.guide
ecoenchants.command.toggledescriptions
ecoenchants.command.enchantinfo
ecoenchants.fromtable.*
```

管理员建议开放：

```text
ecoenchants.command.*
ecoenchants.anvil.color
```

如果你希望某些玩家不能从附魔台获得指定附魔，可以按单个附魔控制：

```text
ecoenchants.fromtable.<enchant_id>
```

例如不给某组 `ecoenchants.fromtable.lifesteal`，他们就不会从附魔台自然获得 `lifesteal`。这不一定阻止战利品、村民或管理员命令来源，其他来源要通过附魔文件和全局配置控制。

## 附魔浏览 GUI

`/ecoenchants gui` 是玩家理解插件的主入口。当前分支的 GUI 支持：

- 顶部中间槽放入物品后自动筛选兼容附魔。
- 类型筛选，按 `types.yml` 循环。
- 稀有度筛选，按 `rarity.yml` 循环。
- 目标筛选，按 `targets.yml` 循环。
- 仅兼容当前物品开关。
- 冲突查看按钮，提示当前物品已有附魔会阻止哪些附魔。
- 分页按钮和页数显示。
- 空结果提示，会区分“无附魔加载”“分组为空”“物品无可用附魔”“筛选无结果”等情况。
- 可选分组 GUI，按类型、稀有度或目标先进入分组，再浏览附魔。
- 管理员工具入口，具备权限的玩家可以在 GUI 中快速重载或给自己随机书。

## GUI 配置位置

主要配置位于：

| 配置段 | 用途 |
| --- | --- |
| `enchantinfo` | `/enchantinfo` 信息 GUI 的行数、背景、展示物品、lore key。 |
| `enchant-gui` | 主附魔浏览器的行数、标题、槽位、分页、筛选、冲突查看、兼容过滤、管理员入口。 |
| `group-gui` | 分组浏览器，只有 `enchant-gui.grouped: true` 时使用。 |
| `admin-gui` | 管理员工具 GUI，包含重载和随机书按钮。 |
| `lang.yml -> gui` | GUI 按钮名称、lore、提示和双语文本。 |

如果要改按钮位置，优先调整 `row` 和 `column`。保持一个格子只放一个按钮，尤其要避开 `item-row` / `item-column` 的放入物品槽。

## 玩家引导建议

服主可以把 `/ecoenchants gui` 放入菜单、出生点 NPC 或教程书。新玩家最需要知道三件事：

1. 可以不放物品直接浏览全部附魔。
2. 放入装备后会只显示可添加附魔。
3. 用 `/enchantinfo <name> [level]` 查看最大等级、目标、冲突和来源。

`advanced` 分支已经内置自动提示，默认会在首次进服、首次打开浏览器、切换筛选和空结果时发送短提示。若服务器聊天较繁忙，可以提高 `player-experience.auto-hints.cooldown-seconds` 或关闭部分触发点。
