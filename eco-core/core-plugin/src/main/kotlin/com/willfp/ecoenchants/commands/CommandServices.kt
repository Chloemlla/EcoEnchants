package com.willfp.ecoenchants.commands

import com.willfp.eco.core.command.impl.Subcommand
import com.willfp.ecoenchants.backend.BackendApiPolicy
import com.willfp.ecoenchants.backend.RemoteOperationsClient
import com.willfp.ecoenchants.plugin
import com.willfp.ecoenchants.telemetry.EnvironmentRiskProbe
import com.willfp.ecoenchants.telemetry.RuntimeTelemetryPolicy
import org.bukkit.command.CommandSender

object CommandServices : Subcommand(
    plugin,
    "services",
    "ecoenchants.command.services",
    false
) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        for (line in BackendApiPolicy.statusLines()) {
            sender.sendMessage(line)
        }
        for (line in RemoteOperationsClient.statusLines()) {
            sender.sendMessage(line)
        }
        for (line in RuntimeTelemetryPolicy.statusLines()) {
            sender.sendMessage(line)
        }
        for (line in EnvironmentRiskProbe.statusLines()) {
            sender.sendMessage(line)
        }
    }
}
