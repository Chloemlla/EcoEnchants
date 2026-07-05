package com.willfp.ecoenchants.commands

import com.willfp.eco.core.command.impl.Subcommand
import com.willfp.ecoenchants.display.getFormattedName
import com.willfp.ecoenchants.enchant.EcoEnchants
import com.willfp.ecoenchants.experience.PlayerExperience
import com.willfp.ecoenchants.plugin
import com.willfp.ecoenchants.sendClickableLine
import com.willfp.ecoenchants.stripLegacyFormatting
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.util.StringUtil

object CommandSearch : Subcommand(
    plugin,
    "search",
    "ecoenchants.command.search",
    false
) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        if (args.isEmpty()) {
            PlayerExperience.sendLangLines(sender, "commands.search.usage")
            return
        }

        val query = args.joinToString(" ").trim()

        val matches = EcoEnchants.values()
            .map { it to it.getFormattedName(0).stripLegacyFormatting() }
            .filter { it.second.contains(query, ignoreCase = true) }
            .sortedBy { it.second.lowercase() }
            .take(plugin.configYml.getInt("player-experience.search.max-results").coerceAtLeast(1))

        if (matches.isEmpty()) {
            PlayerExperience.sendLangLines(sender, "commands.search.no-results", "query" to query)
            return
        }

        PlayerExperience.sendLangLines(
            sender,
            "commands.search.header",
            "query" to query,
            "count" to matches.size.toString()
        )

        val resultLine = plugin.langYml.getStrings("commands.search.result").firstOrNull()

        for ((enchant, plainName) in matches) {
            val level = enchant.maximumLevel
            val line = (resultLine ?: "&7- %enchant%").replace("%enchant%", enchant.getFormattedName(level))

            if (sender is Player) {
                sender.sendClickableLine(
                    line,
                    "/enchantinfo $plainName $level",
                    plugin.langYml.getStrings("commands.search.result-hover")
                        .firstOrNull()
                        ?.replace("%enchant%", enchant.getFormattedName(level))
                )
            } else {
                PlayerExperience.sendLangLines(sender, "commands.search.result", "enchant" to enchant.getFormattedName(level))
            }
        }
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> {
        if (args.isEmpty()) {
            return emptyList()
        }

        val completions = mutableListOf<String>()
        val names = EcoEnchants.values().map { it.getFormattedName(0).stripLegacyFormatting() }
        StringUtil.copyPartialMatches(args.joinToString(" "), names, completions)
        completions.sort()
        return completions
    }
}
