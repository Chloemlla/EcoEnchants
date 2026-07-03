# 兼容性与集成

EcoEnchants 的优势是把自定义附魔注册成服务端能识别的真实附魔，因此兼容面比纯 lore 插件更广。服主要关注的是版本、依赖、注册时机和其他插件对物品 lore 的处理。

## 原版系统集成

| 系统 | 支持情况 | 服主注意点 |
| --- | --- | --- |
| 附魔台 | 支持 | 受 `enchanting-table`、稀有度和 `ecoenchants.fromtable.<id>` 权限控制。 |
| 村民交易 | 支持 | 受 `villager`、稀有度和单个附魔 `tradeable` 控制。 |
| 自然战利品 | 支持 | 受 `loot`、稀有度和单个附魔 `discoverable` 控制。 |
| 铁砧 | 支持 | 受 `anvil`、冲突、前置、类型限制控制。 |
| 砂轮 | 支持 | 受附魔类型 `no-grindstone` 控制。 |
| 物品 lore | 支持 | 由 `display` 配置控制，可折叠和显示描述。 |

## CMI 与 EssentialsX

`plugin.yml` 声明了 `CMI` 和 `EssentialsX` 软依赖。源码中有对应集成加载器，会在插件存在时注册兼容逻辑。服主应注意：

- 不需要为了 EcoEnchants 强制安装 CMI 或 EssentialsX。
- 如果服务器已经使用它们，建议在测试服确认附魔书、物品命令、修复命令和 lore 显示没有冲突。
- 若其他插件直接重写 item meta 或 lore，可能覆盖 EcoEnchants 展示，但真实附魔数据仍应保留。

## libreforge 与生态附魔

EcoEnchants 使用 libreforge 效果系统表达大量附魔逻辑。当前构建会把 libreforge shadow jar 嵌入最终产物，同时也能读取依赖型附魔配置。

部分附魔位于子目录：

| 目录 | 说明 |
| --- | --- |
| `enchants/ecoskills` | 与 EcoSkills 经验或技能相关的附魔。 |
| `enchants/ecojobs` | 与 EcoJobs 相关的附魔。 |
| `enchants/ecopets` | 与 EcoPets 相关的附魔。 |

这些附魔如果声明了依赖插件，缺少依赖时会被跳过或不可用。服主不要把生态附魔当作必定生效的基础池，先确认对应插件存在。

## Folia

插件声明 `folia-supported: true`。这表示插件作者已经声明 Folia 支持，但服主仍需要对以下内容做测试：

- GUI 点击与物品归还。
- 附魔触发效果。
- 遥测和远程运维的调度任务。
- 与其他非 Folia 插件的组合。

Folia 的问题往往不是单个插件，而是插件组合中的线程上下文不一致。

## 版本与注册表

当前分支包含多个现代版本代理模块，插件会在启动阶段替换、注册和冻结附魔注册表。advanced 分支还改进了代理加载失败时的保护逻辑，失败时会禁用插件而不是继续运行。

服主遇到“附魔不存在”“GUI 能看但物品无效果”“启动阶段报 registry 错误”时，优先检查：

1. 服务端版本是否落在当前构建支持范围。
2. 是否使用了非 Paper 兼容分支或深度修改核心。
3. 是否有其他插件也在启动阶段修改附魔注册表。
4. 是否新增了无效 `type`、`rarity` 或 `target`。
5. 是否需要玩家重新登录或重启。

## 从其他附魔插件迁移

`lore-conversion` 用于将其他插件的 lore 型附魔转换为同名 EcoEnchants 附魔：

```yaml
lore-conversion:
  enabled: false
  aggressive: false
```

迁移建议：

- 先在备份服开启并测试，不要直接在生产服全量转换。
- 保持 `aggressive: false`，只在玩家交互时逐步转换。
- 只有确认库存、箱子、离线玩家物品都需要扫描时，才考虑 aggressive 模式。
- 转换前保留完整备份，尤其是玩家数据和世界容器。
