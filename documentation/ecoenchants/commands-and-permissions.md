---
title: "Commands and Permissions"
sidebar_position: 5
---

Every command and its permission node is listed below. Permissions follow the `ecoenchants.command.<name>` pattern and are granted to operators by default. `/ecoenchants` can also be run as `/ee`.

| Command                                                          | Description                                                             | Permission                               |
|------------------------------------------------------------------|-------------------------------------------------------------------------|------------------------------------------|
| `/ecoenchants` or `/ecoenchants help`                            | Show the commands available to you                                      | `ecoenchants.command.ecoenchants`        |
| `/ecoenchants reload`                                            | Reload the plugin configs (adding new enchantments requires re-logging) | `ecoenchants.command.reload`             |
| `/enchant <enchant> [level]`                                     | Add or remove an enchantment from your held item; use level `0` to remove | `ecoenchants.command.enchant`          |
| `/enchant <player> <enchant> [level]`                            | Console form of `/enchant`                                              | `ecoenchants.command.enchant`            |
| `/enchantinfo [enchant] [level]`                                 | Open the enchant info GUI; with no arguments, lists the enchantments on your held item as clickable chat lines | `ecoenchants.command.enchantinfo`   |
| `/ecoenchants search <query>`                                    | Search enchantments by name and return clickable results that open their info | `ecoenchants.command.search`       |
| `/ecoenchants favorites`                                         | List your bookmarked enchantments as clickable chat lines               | `ecoenchants.command.favorites`          |
| `/ecoenchants gui`                                               | Open the enchantment GUI                                                | `ecoenchants.command.gui`                |
| `/ecoenchants services`                                          | Show license, `/api/ecoenchants/v1`, remote operations, and local service status | `ecoenchants.command.services`     |
| `/ecoenchants guide [book]`                                      | Show the player guide, or give the player an in-game guide book                  | `ecoenchants.command.guide`        |
| `/ecoenchants experience`                                        | Show player guidance settings and empty-result hint statistics                   | `ecoenchants.command.experience`   |
| `/ecoenchants giverandombook <player> [type/rarity] [min] [max]` | Give a player a random enchanted book                                   | `ecoenchants.command.giverandombook`     |
| `/ecoenchants toggledescriptions`                                | Let players toggle enchantment descriptions                             | `ecoenchants.command.toggledescriptions` |

### PlaceholderAPI

| Placeholder                          | Description                                             |
|--------------------------------------|---------------------------------------------------------|
| `%ecoenchants_descriptions_enabled%` | Whether the player has enchantment descriptions enabled |

### Additional permissions

| Permission                   | Description                                                                                   |
|------------------------------|-----------------------------------------------------------------------------------------------|
| `ecoenchants.fromtable.<id>` | Permission to allow an enchantment to be obtained from an enchanting table (given by default) |

<hr/>

## Where to go next

- **Make an enchantment to use these on:** the [How to Make an Enchantment](how-to-make-a-custom-enchant) guide.
- **Configure the plugin:** every option in the [Plugin Config](plugin-config).
- **Improve player guidance:** use [Player Experience Optimizations](player-experience-optimizations) to place helpful hints in the GUI and chat flow.
