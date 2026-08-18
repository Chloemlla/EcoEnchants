---
layout: home
title: EcoEnchants 服主报告
titleTemplate: false
hero:
  name: EcoEnchants
  text: 附魔玩法、运营边界与 advanced 能力总控台
  tagline: 面向 Minecraft 服务器主，把部署判断、玩法调参、授权接入、远程运维、遥测审计和 GUI 体验整理成一套可落地的中文文档。
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
    - theme: alt
      text: 主页说明
      link: /guide/homepage
features:
  - title: 面向服主决策
    details: 从部署、玩法、附魔池、权限、GUI 和配置风险出发，帮助开服前先看清上线边界。
  - title: 覆盖 advanced 能力
    details: 单独整理授权门禁、后端 API、远程运维、备份恢复、遥测探针和上线排障清单。
  - title: 更适合检索
    details: 本地搜索、中文导航、代码行号、清晰表格和目录结构让日常排查更快定位。
  - title: 首页单独说明
    details: 新增主页导览文档，解释每个入口、指标和阅读路线适合什么场景。
---

<section class="home-section home-command">
  <div class="home-command-copy">
    <p class="home-eyebrow">CONTROL CENTER</p>
    <h2>一屏判断：先开服，后接入，再排障</h2>
    <p>首页按服主真实工作流组织：先判断插件是否适合当前服，再进入玩法与权限调参，最后按需启用 advanced 后端能力。每个入口都指向可执行的检查清单或配置说明。</p>
  </div>
  <div class="home-command-panel" aria-label="EcoEnchants 文档重点">
    <div>
      <span>当前重点</span>
      <strong>advanced 分支上线前评估</strong>
    </div>
    <div>
      <span>关键风险</span>
      <strong>授权、远程文件操作、备份恢复、遥测边界</strong>
    </div>
    <div>
      <span>推荐顺序</span>
      <strong>部署校验 → 玩法调参 → 权限收口 → 运维审计</strong>
    </div>
  </div>
</section>

<section class="home-section">
  <div class="home-section-header">
    <p class="home-eyebrow">READING PATH</p>
    <h2>从上线前判断到生产排障</h2>
    <p>首页只放核心入口，具体判断和配置建议拆到独立章节，避免把授权、远程运维、遥测和 GUI 说明混在同一页。</p>
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
    <a class="route-card" href="/report/advanced-troubleshooting">
      <span>4</span>
      <strong>上线前复核</strong>
      <small>对照排障与上线清单检查启动日志、权限边界、回滚方案和审计记录。</small>
    </a>
  </div>
</section>

<section class="home-section">
  <div class="home-section-header">
    <p class="home-eyebrow">SITE SIGNALS</p>
    <h2>把分散信息压缩成可扫描信号</h2>
  </div>
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
  <div class="home-section-header">
    <p class="home-eyebrow">ADVANCED MAP</p>
    <h2>高级能力按风险等级拆开阅读</h2>
    <p>advanced 分支不是单个开关，而是一组服务端能力。首页把它们分成授权、运维、遥测、体验与排障，便于按权限、审计和回滚要求逐项启用。</p>
  </div>
  <div class="home-capability-grid">
    <a href="/report/advanced-license-services">
      <span>Auth</span>
      <strong>授权与服务状态</strong>
      <small>启动门禁、后端 API、在线校验和服务健康检查。</small>
    </a>
    <a href="/report/advanced-remote-operations">
      <span>Ops</span>
      <strong>远程运维与备份</strong>
      <small>文件操作、备份恢复、审批边界和最小权限策略。</small>
    </a>
    <a href="/report/advanced-telemetry-probe">
      <span>Probe</span>
      <strong>遥测与环境探针</strong>
      <small>运行时审计、环境采样、上报字段与隐私边界。</small>
    </a>
    <a href="/report/advanced-gui-experience">
      <span>GUI</span>
      <strong>GUI 与玩家体验</strong>
      <small>浏览入口、提示文案、管理员工具和玩家引导。</small>
    </a>
  </div>
</section>

<section class="home-section">
  <div class="home-note">
    <p><strong>生产建议：</strong>先完成基础玩法和权限配置，再逐步启用 advanced 后端能力。远程文件操作与备份恢复默认保持关闭，只有明确的审批、审计和回滚流程后再开放。主页结构说明见 <a href="/guide/homepage">主页说明</a>。</p>
  </div>
</section>
