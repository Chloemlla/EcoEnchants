package com.willfp.ecoenchants.commands

import com.willfp.eco.core.command.impl.Subcommand
import com.willfp.ecoenchants.experience.PlayerExperience
import com.willfp.ecoenchants.plugin
import org.bukkit.command.CommandSender

object CommandExperience : Subcommand(
    plugin,
    "experience",
    "ecoenchants.command.experience",
    false
) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        for (line in PlayerExperience.statusLines()) {
            sender.sendMessage(line)
        }
    }
}
