import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'EcoEnchants 服主报告',
  description: '面向 Minecraft 服务器主的 EcoEnchants 功能调研与使用指南',
  lang: 'zh-CN',
  cleanUrls: true,
  themeConfig: {
    nav: [
      { text: '报告', link: '/report/' },
      { text: '部署', link: '/guide/vercel' },
      { text: '原文档', link: '/ecoenchants/' }
    ],
    sidebar: [
      {
        text: '文档站部署',
        items: [
          { text: 'Vercel 部署', link: '/guide/vercel' }
        ]
      },
      {
        text: '服主调研报告',
        items: [
          { text: '总览', link: '/report/' },
          { text: '部署与运行边界', link: '/report/deployment-runtime' },
          { text: '玩法与平衡模型', link: '/report/gameplay-balance' },
          { text: '附魔库与自定义附魔', link: '/report/enchantments' },
          { text: '命令、权限与 GUI', link: '/report/commands-gui' },
          { text: '配置运营手册', link: '/report/configuration' },
          { text: '兼容性与集成', link: '/report/integrations' }
        ]
      },
      {
        text: 'Chloemlla advanced 新功能',
        items: [
          { text: '功能总览', link: '/report/chloemlla-advanced' },
          { text: '授权与服务状态', link: '/report/advanced-license-services' },
          { text: '远程运维与备份', link: '/report/advanced-remote-operations' },
          { text: '遥测与环境探针', link: '/report/advanced-telemetry-probe' },
          { text: 'GUI 与玩家体验', link: '/report/advanced-gui-experience' },
          { text: '排障与上线清单', link: '/report/advanced-troubleshooting' }
        ]
      },
      {
        text: '现有 EcoEnchants 文档',
        collapsed: true,
        items: [
          { text: 'EcoEnchants', link: '/ecoenchants/' },
          { text: 'Gameplay', link: '/ecoenchants/the-gameplay' },
          { text: 'Commands and Permissions', link: '/ecoenchants/commands-and-permissions' },
          { text: 'Plugin Config', link: '/ecoenchants/plugin-config' },
          { text: 'Player Experience Optimizations', link: '/ecoenchants/player-experience-optimizations' },
          { text: 'Runtime Telemetry API', link: '/ecoenchants/runtime-telemetry-api' },
          { text: 'Secure RPC Operations API', link: '/ecoenchants/secure-rpc-operations-api' }
        ]
      }
    ],
    search: {
      provider: 'local'
    },
    outline: {
      level: [2, 3],
      label: '本页目录'
    },
    docFooter: {
      prev: '上一页',
      next: '下一页'
    },
    lastUpdated: {
      text: '最后更新'
    }
  },
  lastUpdated: true
})
