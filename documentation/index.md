---
layout: home
title: EcoEnchants 服主报告
titleTemplate: false
hero:
  name: EcoEnchants
  text: 服主运营与 advanced 分支指南
  tagline: 把附魔玩法、配置边界、商业授权、远程运维、遥测审计和 GUI 体验整理成一套可落地的中文文档。
  image:
    src: /hero-ecoenchants.png
    alt: EcoEnchants operations overview
  actions:
    - theme: brand
      text: 阅读调研报告
      link: /report/
    - theme: alt
      text: Advanced 功能
      link: /report/chloemlla-advanced
    - theme: alt
      text: 原始文档
      link: /ecoenchants/
features:
  - title: 面向服主决策
    details: 从部署、玩法、附魔池、权限、GUI 和配置风险出发，帮助开服前先看清运营边界。
  - title: 覆盖 advanced 能力
    details: 单独整理授权门禁、后端 API、远程运维、备份恢复、遥测探针和上线排障清单。
  - title: 保留原始文档
    details: 现有 EcoEnchants 英文文档仍按原路径保留，便于对照官方能力和配置字段。
  - title: 更适合检索
    details: 本地搜索、中文导航、代码行号、清晰表格和目录结构让日常排查更快定位。
---

<section class="home-section">
  <div class="home-section-header">
    <p class="home-eyebrow">READING PATH</p>
    <h2>从上线前判断到生产排障</h2>
    <p>首页只放入口，具体判断和配置建议拆到独立章节，避免把授权、远程运维、遥测和 GUI 说明混在同一页。</p>
  </div>
  <div class="home-route-grid">
    <a class="route-card" href="/report/">
      <span>1</span>
      <strong>先看总览</strong>
      <small>确认插件定位、默认附魔规模、advanced 分支能力和生产服启用顺序。</small>
    </a>
    <a class="route-card" href="/report/gameplay-balance">
      <span>2</span>
      <strong>再调玩法</strong>
      <small>围绕附魔台、村民、战利品、铁砧、展示规则和经济流通做平衡。</small>
    </a>
    <a class="route-card" href="/report/chloemlla-advanced">
      <span>3</span>
      <strong>接入 advanced</strong>
      <small>按最小权限启用授权、远程运维、备份、遥测探针和管理员工具。</small>
    </a>
  </div>
</section>

<section class="home-section">
  <div class="home-metrics">
    <div class="metric-item">
      <strong>102</strong>
      <span>默认可用附魔配置，覆盖 normal、spell、special、curse 等类型。</span>
    </div>
    <div class="metric-item">
      <strong>7 类</strong>
      <span>advanced 新能力：授权、API、运维、备份、遥测、玩家体验、GUI 管理。</span>
    </div>
    <div class="metric-item">
      <strong>本地搜索</strong>
      <span>文档站内置 VitePress local search，部署后无需额外搜索服务。</span>
    </div>
    <div class="metric-item">
      <strong>中文入口</strong>
      <span>服主报告、部署说明和原始文档统一在同一个导航层级里。</span>
    </div>
  </div>
</section>

<section class="home-section">
  <div class="home-note">
    <p><strong>生产建议：</strong>先完成基础玩法和权限配置，再逐步启用 advanced 后端能力。远程文件操作与备份恢复默认保持关闭，只有明确的审批、审计和回滚流程后再开放。</p>
  </div>
</section>
