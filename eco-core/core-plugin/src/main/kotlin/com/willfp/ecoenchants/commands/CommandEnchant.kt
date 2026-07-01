package com.willfp.ecoenchants.commands

import com.willfp.eco.core.command.impl.PluginCommand
import com.willfp.eco.core.items.isEcoEmpty
import com.willfp.eco.util.StringUtils
import com.willfp.eco.util.savedDisplayName
import com.willfp.ecoenchants.display.getFormattedName
import com.willfp.ecoenchants.enchant.getEnchantmentByID
import com.willfp.ecoenchants.enchant.wrap
import com.willfp.ecoenchants.plugin
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import org.bukkit.util.StringUtil

object CommandEnchant : PluginCommand(
    plugin,
    "enchant",
    "ecoenchants.command.enchant",
    false
) {
    private var enchantmentCompletions: List<String> = emptyList()
    private var levelCompletionsByEnchant = emptyMap<String, List<String>>()
    private val defaultLevelCompletions = (0..5).map { it.toString() }

    internal fun reload() {
        @Suppress("DEPRECATION")
        val enchantments = Enchantment.values()

        enchantmentCompletions = enchantments.map { it.key.key }
        levelCompletionsByEnchant = enchantments.associate {
            it.key.key to (0..it.maxLevel).map { level -> level.toString() }
        }
    }

    override fun onExecute(sender: CommandSender, rawArgs: List<String>) {
        val usageMessage = if (sender is Player) {
            "enchant-usage"
        } else {
            "enchant-usage-console"
        }

        val (player, args) = if (sender is Player) {
            sender to rawArgs
        } else {
            val playerName = rawArgs.getOrNull(0)
            if (playerName == null) {
                sender.sendMessage(plugin.langYml.getMessage(usageMessage))
                return
            }

            val target = Bukkit.getPlayer(playerName)
            if (target == null) {
                sender.sendMessage(plugin.langYml.getMessage("invalid-player"))
                return
            }

            target to rawArgs.drop(1)
        }

        val enchantName = args.getOrNull(0)
        if (enchantName == null) {
            sender.sendMessage(plugin.langYml.getMessage(usageMessage))
            return
        }

        val enchant = getEnchantmentByID(enchantName.lowercase())
        if (enchant == null) {
            sender.sendMessage(plugin.langYml.getMessage("invalid-enchantment"))
            sender.sendMessage(plugin.langYml.getMessage(usageMessage))
            return
        }

        val levelArg = args.getOrNull(1)
        val level = if (levelArg == null) {
            1
        } else {
            levelArg.toIntOrNull() ?: run {
                sender.sendMessage(plugin.langYml.getMessage("invalid-level"))
                sender.sendMessage(plugin.langYml.getMessage(usageMessage))
                return
            }
        }

        val item = player.inventory.itemInMainHand
        val meta = item.itemMeta
        if (item.isEcoEmpty || meta == null) {
            sender.sendMessage(
                plugin.langYml.getMessage("requires-held-item", StringUtils.FormatOption.WITHOUT_PLACEHOLDERS)
                    .replace("%player%", player.savedDisplayName)
            )
            return
        }

        if (level > 0) {
            if (meta is EnchantmentStorageMeta) {
                meta.addStoredEnchant(enchant, level, true)
            }
            meta.addEnchant(enchant, level, true)

            sender.sendMessage(
                plugin.langYml.getMessage("added-enchant", StringUtils.FormatOption.WITHOUT_PLACEHOLDERS)
                    .replace("%enchant%", enchant.wrap().getFormattedName(0))
                    .replace("%player%", player.savedDisplayName)
            )
        } else {
            if (meta is EnchantmentStorageMeta) {
                meta.removeStoredEnchant(enchant)
            }
            meta.removeEnchant(enchant)

            sender.sendMessage(
                plugin.langYml.getMessage("removed-enchant", StringUtils.FormatOption.WITHOUT_PLACEHOLDERS)
                    .replace("%enchant%", enchant.wrap().getFormattedName(0))
                    .replace("%player%", player.savedDisplayName)
            )
        }

        item.itemMeta = meta
    }

    override fun tabComplete(sender: CommandSender, rawArgs: List<String>): List<String> {
        if (enchantmentCompletions.isEmpty()) {
            reload()
        }

        val completions = mutableListOf<String>()

        val args = if (sender !is Player) {
            if (rawArgs.size <= 1) {
                StringUtil.copyPartialMatches(
                    rawArgs.getOrNull(0) ?: "",
                    Bukkit.getOnlinePlayers().map { it.name },
                    completions
                )
                completions.sort()
                return completions
            }

            rawArgs.drop(1)
        } else {
            rawArgs
        }

        if (args.isEmpty()) {
            completions.addAll(enchantmentCompletions)
        } else if (args.size == 1) {
            StringUtil.copyPartialMatches(
                args[0],
                enchantmentCompletions,
                completions
            )
        } else if (args.size == 2) {
            val enchant = getEnchantmentByID(args[0].lowercase())

            val levels = if (enchant != null) {
                levelCompletionsByEnchant[enchant.key.key] ?: defaultLevelCompletions
            } else {
                defaultLevelCompletions
            }

            StringUtil.copyPartialMatches(
                args[1],
                levels,
                completions
            )
        }

        completions.sort()
        return completions
    }
}
