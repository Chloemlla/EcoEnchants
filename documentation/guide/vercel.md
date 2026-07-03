# Vercel 部署

本仓库已经在根目录提供 `vercel.json`，用于让 Vercel 直接构建 VitePress 文档站。

## 项目设置

在 Vercel 导入 GitHub 仓库后，保持项目根目录为仓库根目录。部署配置会从 `vercel.json` 读取：

| 设置项 | 值 |
| --- | --- |
| Application Preset | `VitePress` |
| Root Directory | `./` |
| Install Command | `npm install` |
| Build Command | `npm run build` |
| Output Directory | `documentation/.vitepress/dist` |

Vercel 的 VitePress 默认示例通常使用 `docs/.vitepress/dist`，但本仓库的文档目录是 `documentation`，因此输出目录需要使用 `documentation/.vitepress/dist`。这些值与 `.github/workflows/docs.yml` 中的文档构建流程保持一致。

## Node.js 版本

GitHub Actions 文档构建使用 Node.js 22。为了保持 Vercel 与 CI 一致，请在 Vercel 项目的 Node.js Version 设置中选择 `22.x`。

## 自动部署

Vercel 连接仓库后，会在推送到启用的生产分支时自动部署生产环境，并为 Pull Request 创建预览部署。文档源码位于 `documentation`，构建产物由 VitePress 写入 `documentation/.vitepress/dist`。

## 验证路径

部署完成后，访问站点首页确认以下入口可用：

- `/report/`
- `/ecoenchants/`
- `/guide/vercel`
