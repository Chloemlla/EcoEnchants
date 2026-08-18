# EcoEnchants 服主功能调研报告

本报告面向 Minecraft 服务器主，目标是把当前插件内可用功能、配置边界、运营建议和 `advanced` 分支新增能力整理成可直接用于开服、调参、授权接入和日常维护的文档。

调研依据来自当前仓库源码与资源文件，包括 `config.yml`、`lang.yml`、`plugin.yml`、`types.yml`、`rarity.yml`、`targets.yml`、默认附魔配置、命令源码、GUI 源码、后端授权/远程运维源码和运行时遥测源码。本文档不假设未在当前分支出现的功能。

## 快速结论

EcoEnchants 的核心定位是“像原版附魔一样存在”的自定义附魔插件。它不是只在物品 lore 上写几行文字，而是将附魔注册进服务端生态，使附魔台、村民交易、战利品、铁砧、砂轮、命令和兼容插件能够围绕真实附魔工作。

当前默认资源包含 102 个可用附魔文件，另有一个 `_example.yml` 模板。按类型统计：`normal` 79 个、`spell` 9 个、`special` 8 个、`curse` 5 个，另有 1 个配置中写作 `common` 的附魔类型需要服主在排查时留意。按稀有度统计：`legendary` 31 个、`rare` 22 个、`epic` 18 个、`uncommon` 15 个、`special` 6 个、`common` 5 个、`veryspecial` 5 个。

`advanced` 分支新增了大量服主侧运营能力：在线授权启动门禁、后端 `/api/ecoenchants/v1` 接入、安全远程运维客户端、可选文件操作与备份、运行时遥测、环境探针、双语提示、改进后的附魔浏览 GUI、管理员工具、经验提示统计和更细的命令反馈。这些内容已经单独放在 [Chloemlla advanced 新功能](./chloemlla-advanced) 章节。

## 服主阅读路线

1. 首次部署先看 [部署与运行边界](./deployment-runtime)，确认依赖、授权、版本和启动要求。
2. 想调经济和平衡看 [玩法与平衡模型](./gameplay-balance)，重点是获取来源、稀有度和铁砧成本。
3. 想了解默认附魔与自定义附魔看 [附魔库与自定义附魔](./enchantments)。
4. 给玩家和管理组配置权限看 [命令、权限与 GUI](./commands-gui)。
5. 修改 `config.yml` 前看 [配置运营手册](./configuration)。
6. 使用 CMI、EssentialsX、Folia 或新版本 Paper 时看 [兼容性与集成](./integrations)。
7. 使用 Chloemlla `advanced` 分支时先看 [新增功能总览](./chloemlla-advanced)，再按需要阅读授权、远程运维、遥测、GUI 和排障细分页。

## 运维原则

生产服建议先把附魔获取渠道分层开启：附魔台负责普通获取，战利品负责探索奖励，村民交易负责经济流通，命令和 GUI 管理入口只给管理员。改动附魔文件后可先执行 `/ecoenchants reload`，但新增附魔或涉及注册行为时，应安排玩家重新登录，必要时重启服务器。

对 `advanced` 分支的后端能力要按最小权限启用。授权校验是启动前置；远程文件操作和备份默认应保持关闭，只有明确需要后端维护时再启用，并同步审计日志与审批流程。
