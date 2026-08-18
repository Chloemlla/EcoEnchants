package com.willfp.ecoenchants.commands

import com.willfp.eco.core.command.impl.Subcommand
import com.willfp.ecoenchants.experience.PlayerExperience
import com.willfp.ecoenchants.plugin
import org.bukkit.command.CommandSender

object CommandHelp : Subcommand(
    plugin,
    "help",
    "ecoenchants.command.ecoenchants",
    false
) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        PlayerExperience.sendHelp(sender)
    }
}
