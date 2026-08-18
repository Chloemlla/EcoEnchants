package com.willfp.ecoenchants.commands

import com.willfp.eco.core.command.impl.PluginCommand
import com.willfp.eco.core.drops.DropQueue
import com.willfp.eco.core.items.builder.EnchantedBookBuilder
import com.willfp.eco.util.NumberUtils
import com.willfp.eco.util.StringUtils
import com.willfp.ecoenchants.display.getFormattedName
import com.willfp.ecoenchants.enchant.EcoEnchants
import com.willfp.ecoenchants.plugin
import com.willfp.ecoenchants.rarity.EnchantmentRarities
import com.willfp.ecoenchants.rarity.EnchantmentRarity
import com.willfp.ecoenchants.type.EnchantmentType
import com.willfp.ecoenchants.type.EnchantmentTypes
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.util.StringUtil

object CommandGiveRandomBook : PluginCommand(
    plugin,
    "giverandombook",
    "ecoenchants.command.giverandombook",
    false
) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        val playerName = args.getOrNull(0)

        if (playerName == null) {
            sender.sendMessage(plugin.langYml.getMessage("requires-player"))
            sender.sendMessage(plugin.langYml.getMessage("giverandombook-usage"))
            return
        }

        val player = Bukkit.getPlayer(playerName)

        if (player == null) {
            sender.sendMessage(plugin.langYml.getMessage("invalid-player"))
            return
        }

        val filterName = args.getOrNull(1)

        val filter = if (filterName != null) {
            val normalizedFilterName = filterName.lowercase()
            EnchantmentTypes[normalizedFilterName] ?: EnchantmentRarities[normalizedFilterName] ?: run {
                sender.sendMessage(plugin.langYml.getMessage("invalid-filter"))
                sender.sendMessage(plugin.langYml.getMessage("giverandombook-usage"))
                return
            }
        } else null

        val minLevel = args.getOrNull(2)?.toIntOrNull() ?: run {
            if (args.size > 2) {
                sender.sendMessage(plugin.langYml.getMessage("invalid-level"))
                sender.sendMessage(plugin.langYml.getMessage("giverandombook-usage"))
                return
            }

            1
        }
        val maxLevel = args.getOrNull(3)?.toIntOrNull() ?: run {
            if (args.size > 3) {
                sender.sendMessage(plugin.langYml.getMessage("invalid-level"))
                sender.sendMessage(plugin.langYml.getMessage("giverandombook-usage"))
                return
            }

            Int.MAX_VALUE
        }

        if (minLevel > maxLevel) {
            sender.sendMessage(plugin.langYml.getMessage("invalid-levels"))
            sender.sendMessage(plugin.langYml.getMessage("giverandombook-usage"))
            return
        }

        if (minLevel < 1 || maxLevel < 1) {
            sender.sendMessage(plugin.langYml.getMessage("invalid-book-levels"))
            sender.sendMessage(plugin.langYml.getMessage("giverandombook-usage"))
            return
        }

        val enchantment = EcoEnchants.values()
            .filter {
                when (filter) {
                    is EnchantmentRarity -> it.enchantmentRarity == filter
                    is EnchantmentType -> it.type == filter
                    else -> true
                } && it.maximumLevel >= minLevel
            }
            .randomOrNull() ?: run {
            sender.sendMessage(plugin.langYml.getMessage("no-enchantments-found"))
            return
        }

        val level = NumberUtils.randInt(minLevel, maxLevel.coerceAtMost(enchantment.maximumLevel))

        val item = EnchantedBookBuilder()
            .addStoredEnchantment(enchantment.enchantment, level)
            .build()

        DropQueue(player)
            .addItem(item)
            .forceTelekinesis()
            .push()

        sender.sendMessage(
            plugin.langYml.getMessage("gave-random-book", StringUtils.FormatOption.WITHOUT_PLACEHOLDERS)
                .replace("%player%", player.name)
                .replace("%enchantment%", enchantment.getFormattedName(level))
        )
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> {
        val completions = mutableListOf<String>()
        val options = when (args.size) {
            1 -> Bukkit.getOnlinePlayers().map { it.name }
            2 -> (EnchantmentRarities.values().map { it.id } + EnchantmentTypes.values().map { it.id })
            3 -> (1..10).map { it.toString() }
            4 -> {
                val startLevel = args[2].toIntOrNull() ?: 1
                val endLevel = startLevel + 10
                (startLevel..endLevel).map { it.toString() }
            }

            else -> emptyList()
        }

        StringUtil.copyPartialMatches(args.lastOrNull() ?: "", options, completions)
        completions.sort()
        return completions
    }
}
