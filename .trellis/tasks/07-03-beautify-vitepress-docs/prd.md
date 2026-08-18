# Beautify VitePress documentation site

## Goal

Refresh the VitePress documentation site so it feels polished, coherent, and useful for Minecraft server owners evaluating EcoEnchants. The homepage should become a stronger entry point, and there must be a dedicated documentation page that explains the homepage structure and intent.

## What I already know

* The documentation root is `documentation/`.
* VitePress is configured in `documentation/.vitepress/config.mts`.
* The custom theme imports `documentation/.vitepress/theme/custom.css`.
* The current homepage is `documentation/index.md` and already uses VitePress `layout: home`.
* Existing public assets include `hero-ecoenchants.png`, `brand-mark.svg`, and `favicon.svg`.
* The repository policy forbids local build/test commands and installation commands. Verification must avoid local build/test execution.
* Existing unrecognized dirty files before this task: `.agents/`, `.trellis/`, `Install-CodexTrellis.ps1`.

## Requirements

* Beautify the full VitePress documentation experience without adding new dependencies.
* Improve homepage layout, content density, visual rhythm, and navigation paths.
* Add a standalone documentation page introducing the homepage.
* Wire the new homepage-introduction page into VitePress navigation/sidebar.
* Keep the design responsive for desktop and mobile.
* Preserve existing Chinese documentation structure and existing EcoEnchants report links.
* Keep edits scoped to documentation and task bookkeeping files.

## Acceptance Criteria

* [x] `documentation/index.md` presents a richer, more polished homepage.
* [x] `documentation/.vitepress/theme/custom.css` improves global page, navigation, homepage, and documentation reading styles.
* [x] A standalone page under `documentation/guide/` introduces the homepage.
* [x] `documentation/.vitepress/config.mts` links the new page from navigation/sidebar.
* [x] No installation command is run.
* [x] No local build or test command is run.

## Definition of Done

* Code/docs edited according to the requirements.
* Changes reviewed manually against repo conventions.
* Local build/test is not run because repository instructions require build/test in GitHub workflow.
* Commit message is generated and submitted; push attempted as requested by repository instructions.

## Technical Approach

Use VitePress native home layout plus HTML sections in Markdown. Extend the existing CSS variables and component classes in `custom.css` instead of adding a new framework. Add one guide page dedicated to the homepage and link it from the guide navigation group.

## Decision (ADR-lite)

**Context**: The site already has a VitePress theme file, assets, and Chinese navigation. Adding dependencies or a custom app shell would increase maintenance and conflict with the no-install constraint.

**Decision**: Enhance the existing VitePress default theme with scoped CSS and Markdown/HTML sections.

**Consequences**: The implementation remains deployable through the existing VitePress pipeline. Visual verification is limited to static review in this environment because local build/test commands are prohibited.

## Out of Scope

* Adding npm dependencies.
* Running local VitePress build/test commands.
* Rewriting all existing report content.
* Changing backend/plugin behavior.

## Technical Notes

* Relevant files inspected:
  * `package.json`
  * `documentation/.vitepress/config.mts`
  * `documentation/.vitepress/theme/custom.css`
  * `documentation/index.md`
  * `documentation/ecoenchants/index.md`
  * `documentation/report/index.md`
* Static verification performed:
  * `git diff --check -- documentation/.vitepress/config.mts documentation/.vitepress/theme/custom.css documentation/index.md documentation/guide/homepage.md`
  * Manual link target existence check for homepage and guide links.
* Local VitePress build/test was intentionally not run because repository instructions require build/test execution in GitHub workflow only.
