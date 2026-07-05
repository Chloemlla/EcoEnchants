package com.willfp.ecoenchants.commands

import com.willfp.eco.core.command.impl.Subcommand
import com.willfp.ecoenchants.display.getFormattedName
import com.willfp.ecoenchants.experience.Favorites
import com.willfp.ecoenchants.experience.PlayerExperience
import com.willfp.ecoenchants.plugin
import com.willfp.ecoenchants.sendClickableLine
import com.willfp.ecoenchants.stripLegacyFormatting
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object CommandFavorites : Subcommand(
    plugin,
    "favorites",
    "ecoenchants.command.favorites",
    true
) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        sender as Player

        val favorites = Favorites.list(sender)
            .sortedBy { it.getFormattedName(0).stripLegacyFormatting().lowercase() }

        if (favorites.isEmpty()) {
            PlayerExperience.sendLangLines(sender, "commands.favorites.empty")
            return
        }

        PlayerExperience.sendLangLines(
            sender,
            "commands.favorites.header",
            "count" to favorites.size.toString()
        )

        val line = plugin.langYml.getStrings("commands.favorites.line").firstOrNull() ?: "&7- %enchant%"
        val hover = plugin.langYml.getStrings("commands.favorites.hover").firstOrNull()

        for (enchant in favorites) {
            val level = enchant.maximumLevel
            val plainName = enchant.getFormattedName(0).stripLegacyFormatting()
            sender.sendClickableLine(
                line.replace("%enchant%", enchant.getFormattedName(level)),
                "/enchantinfo $plainName $level",
                hover?.replace("%enchant%", enchant.getFormattedName(level))
            )
        }
    }
}
