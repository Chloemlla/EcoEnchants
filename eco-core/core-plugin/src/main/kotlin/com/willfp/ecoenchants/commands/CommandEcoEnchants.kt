package com.willfp.ecoenchants.commands

import com.willfp.eco.core.command.impl.PluginCommand
import com.willfp.ecoenchants.experience.PlayerExperience
import com.willfp.ecoenchants.plugin
import org.bukkit.command.CommandSender

object CommandEcoEnchants : PluginCommand(
    plugin,
    "ecoenchants",
    "ecoenchants.command.ecoenchants",
    false
) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        if (args.isNotEmpty()) {
            sender.sendMessage(plugin.langYml.getMessage("invalid-command"))
        }

        PlayerExperience.sendHelp(sender)
    }

    init {
        addSubcommand(CommandHelp)
            .addSubcommand(CommandGuide)
            .addSubcommand(CommandSearch)
            .addSubcommand(CommandFavorites)
            .addSubcommand(CommandExperience)
            .addSubcommand(CommandReload)
            .addSubcommand(CommandToggleDescriptions)
            .addSubcommand(CommandGiveRandomBook)
            .addSubcommand(CommandGUI)
            .addSubcommand(CommandServices)
    }
}
