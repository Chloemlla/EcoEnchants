---
title: "Player Experience Optimizations"
sidebar_position: 6
---

This page lists player experience features that make EcoEnchants easier for players to discover, understand, and use. Most player-facing text lives in `lang.yml`, and most behavior can be tuned in `config.yml`.

## Implemented Features

| Feature | Where | Notes |
|---------|-------|-------|
| First-join hint | `player-experience.auto-hints.on-first-join` | Introduces `/ecoenchants gui` and `/ecoenchants guide` once per player |
| First browser-open hint | `player-experience.auto-hints.on-browser-open` | Explains the top-middle item slot and compatible filtering |
| Empty-result hints | `player-experience.auto-hints.on-empty-results` | Sends a contextual tip for no loaded enchants, active filters, item filters, or group filters |
| Filter-change hints | `player-experience.auto-hints.on-filter-change` | Confirms the changed filter and suggests the next action |
| Hold-item action-bar tip | `player-experience.auto-hints.on-hold-enchantable` | When a player holds an enchantable item (or enchanted book), shows an action-bar prompt, plus a one-time clickable chat prompt that opens the browser |
| Hint cooldown | `player-experience.auto-hints.cooldown-seconds` | Prevents repeated chat noise; also throttles the hold-item tip |
| Enchant search | `/ecoenchants search <query>` (`player-experience.search.max-results`) | Finds enchants by name and returns clickable chat results that open their info GUI |
| Held-item enchant list | `/enchantinfo` with no arguments | Lists the EcoEnchants on the player's held item as clickable chat lines |
| Enchant favorites | `/ecoenchants favorites`, info-GUI star button, browser "favorites only" filter | Bookmark enchants (stored per player) and revisit or filter to them quickly |
| Browser sort toggle | `enchant-gui.sort` button | Each player can cycle the browser between name, rarity, and max-level order for their own view |
| Permission-aware help | `/ecoenchants` and `/ecoenchants help` | Only lists commands the sender can use |
| Player guide | `/ecoenchants guide` | Sends a short chat guide |
| Guide book | `/ecoenchants guide book` | Gives players an in-game written book |
| Experience stats | `/ecoenchants experience` | Shows hint settings and empty-result counts for staff |
| GUI filters | Top row of `/ecoenchants gui` | Cycle type, rarity, target, compatible-only, and favorites-only filters |
| Conflict view | Top row of `/ecoenchants gui` | Shows which enchantments are blocked by the current item |
| Invalid-click feedback | `player-experience.sounds.invalid-click` | Plays a sound when a convenience action needs an item or enchants |

## Already Supported Entry Points

Use these player-facing places for convenience hints:

| Place | What to show | Why it helps |
|-------|--------------|--------------|
| `/ecoenchants gui` info item | How to place an item, browse all enchants, and toggle descriptions | Gives first-time players a clear next action |
| Empty enchant results | Explain whether nothing is loaded, the group is empty, or the item has no compatible enchants | Prevents players from thinking the GUI is broken |
| Group buttons | Summarize what normal, spell, special, and curse enchantments are for | Helps players choose a category quickly |
| Enchant info GUI | Show max level, rarity, targets, conflicts, requirements, discovery sources, and the `/enchantinfo` command | Turns the GUI into a reusable reference |
| Page buttons | Show current page and next/previous direction | Makes large enchant lists easier to scan |
| Returned GUI item messages | Tell players when items are returned or dropped because the inventory is full | Reduces item-loss anxiety |
| `/ecoenchants toggledescriptions` result | Tell players they can run the command again to reverse the setting | Makes the toggle self-explanatory |
| Search / favorites / held-item results | Clickable chat lines that reopen an enchant's info GUI | Lets players jump straight to details without paging the GUI |
| Holding an enchantable item | An action-bar prompt pointing to `/ecoenchants gui` and `/enchantinfo` | Surfaces the feature exactly when it is relevant |
| Admin tools GUI | Explain reload and random-book testing actions | Makes live testing faster for staff |

## Recommended Additions

### Low-Risk Configuration Improvements

1. Add concise bilingual lore to all GUI controls.
2. Keep the info item visible in the same slot across flat and grouped browsing.
3. Use different empty-result messages for no item, filtered group, item-only filter, and item plus group filter.
4. Put the most useful command in failure messages, such as `/enchantinfo <name>` after a failed lookup.
5. Keep all hint text short enough for Minecraft lore lines.
6. Prefer actionable hints over feature descriptions, for example "Place gear to filter results" instead of "This GUI filters results."
7. Add custom decorative or shortcut slots through `custom-slots` only when they reduce clicks.

### Future Feature Ideas

1. Add per-player controls for disabling automatic tips.
2. Add persistent analytics export for empty-result cases across restarts.
3. Add optional MiniMessage hover text with richer formatting on clickable lines.

> Previously listed ideas now shipped: clickable chat results (search, favorites, held-item lists),
> a text search flow (`/ecoenchants search`), and optional action-bar hints
> (`player-experience.auto-hints.on-hold-enchantable`).

## Suggested Player Tips

These short hints fit well in GUI lore and chat messages:

| Situation | Suggested hint |
|-----------|----------------|
| Player opens the browser | "Place gear in the top slot to filter compatible enchantments." |
| Player browses without an item | "Browsing all enchantments. Add an item to narrow the list." |
| No compatible enchantments appear | "Try another item, remove conflicts, or browse without an item." |
| Player views an enchant | "Use `/enchantinfo <name> [level]` to reopen this later." |
| Player wants a specific enchant | "Use `/ecoenchants search <name>` to jump straight to it." |
| Player holds an enchantable item | "This item can be enchanted — open `/ecoenchants gui` to browse." |
| Player revisits favorites | "Star an enchant on its info screen, then use `/ecoenchants favorites`." |
| Player toggles descriptions | "Run the command again to switch back." |
| Player changes groups | "Groups are filters; return to groups to choose another type." |
| Player inventory is full | "Returned items that do not fit are dropped nearby." |
| Staff opens admin tools | "Reload after config edits; players should relog after new enchants are added." |

## Implementation Notes

- Keep player text in `lang.yml` wherever possible.
- Keep GUI structure in `config.yml` so server owners can move controls without code changes.
- Use `custom-slots` for server-specific help links, guide items, or shortcut buttons.
- Avoid long lore paragraphs; Minecraft inventory UI is best with short, scannable lines.
