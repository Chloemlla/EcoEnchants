import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'EcoEnchants',
  titleTemplate: ':title | 服主报告',
  description: '面向 Minecraft 服务器主的 EcoEnchants 功能调研与使用指南',
  lang: 'zh-CN',
  cleanUrls: true,
  appearance: true,
  markdown: {
    lineNumbers: true
  },
  head: [
    ['link', { rel: 'icon', href: '/favicon.svg', type: 'image/svg+xml' }],
    ['meta', { name: 'theme-color', content: '#16a34a' }],
    ['meta', { property: 'og:title', content: 'EcoEnchants 服主报告' }],
    ['meta', { property: 'og:description', content: '面向 Minecraft 服务器主的 EcoEnchants 功能调研、advanced 分支能力说明与运维指南。' }],
    ['meta', { property: 'og:type', content: 'website' }]
  ],
  themeConfig: {
    logo: '/brand-mark.svg',
    siteTitle: 'EcoEnchants',
    nav: [
      { text: '主页说明', link: '/guide/homepage', activeMatch: '^/guide/homepage' },
      { text: '报告', link: '/report/', activeMatch: '^/report/(?!advanced|chloemlla-advanced)' },
      { text: 'Advanced', link: '/report/chloemlla-advanced', activeMatch: '^/report/(advanced|chloemlla-advanced)' },
      { text: '部署', link: '/guide/vercel', activeMatch: '^/guide/vercel' },
      { text: '原文档', link: '/ecoenchants/', activeMatch: '^/ecoenchants/' }
    ],
    sidebar: [
      {
        text: '文档站部署',
        items: [
          { text: '主页说明', link: '/guide/homepage' },
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
      provider: 'local',
      options: {
        translations: {
          button: {
            buttonText: '搜索文档',
            buttonAriaLabel: '搜索文档'
          },
          modal: {
            noResultsText: '没有找到结果',
            resetButtonTitle: '清除搜索',
            footer: {
              selectText: '选择',
              navigateText: '切换',
              closeText: '关闭'
            }
          }
        }
      }
    },
    outline: {
      level: [2, 3],
      label: '本页目录'
    },
    editLink: {
      pattern: 'https://github.com/Chloemlla/EcoEnchants/edit/advanced/ecohub/:path',
      text: '在 GitHub 编辑此页'
    },
    socialLinks: [
      { icon: 'github', link: 'https://github.com/Chloemlla/EcoEnchants' }
    ],
    docFooter: {
      prev: '上一页',
      next: '下一页'
    },
    lastUpdated: {
      text: '最后更新'
    },
    footer: {
      message: 'EcoEnchants advanced 分支文档站',
      copyright: 'Released with repository documentation updates.'
    },
    darkModeSwitchLabel: '外观',
    lightModeSwitchTitle: '切换到浅色模式',
    darkModeSwitchTitle: '切换到深色模式',
    sidebarMenuLabel: '菜单',
    returnToTopLabel: '返回顶部'
  },
  lastUpdated: true
})
