package com.willfp.ecoenchants.commands

import com.willfp.eco.core.command.impl.Subcommand
import com.willfp.ecoenchants.experience.PlayerExperience
import com.willfp.ecoenchants.plugin
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object CommandGuide : Subcommand(
    plugin,
    "guide",
    "ecoenchants.command.guide",
    false
) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        if (args.firstOrNull().equals("book", ignoreCase = true) && sender is Player) {
            PlayerExperience.giveGuideBook(sender)
            return
        }

        PlayerExperience.sendGuide(sender)
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> {
        return if (args.size == 1 && sender is Player) {
            listOf("book").filter { it.startsWith(args[0], ignoreCase = true) }
        } else {
            emptyList()
        }
    }
}
