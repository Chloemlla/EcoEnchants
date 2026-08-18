package com.willfp.ecoenchants.commands

import com.willfp.eco.core.command.impl.PluginCommand
import com.willfp.eco.core.fast.fast
import com.willfp.ecoenchants.display.getFormattedName
import com.willfp.ecoenchants.enchant.EcoEnchants
import com.willfp.ecoenchants.enchant.EnchantGUI
import com.willfp.ecoenchants.experience.PlayerExperience
import com.willfp.ecoenchants.plugin
import com.willfp.ecoenchants.sendClickableLine
import com.willfp.ecoenchants.stripLegacyFormatting
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.util.StringUtil

object CommandEnchantInfo : PluginCommand(
    plugin,
    "enchantinfo",
    "ecoenchants.command.enchantinfo",
    true
) {
    private var enchantmentCompletions: List<String> = emptyList()
    private var levelCompletionsByName = emptyMap<String, List<String>>()
    private var hiddenEnchantNames = emptySet<String>()

    internal fun reload() {
        val namesWithEnchantments = EcoEnchants.values().map { enchantment ->
            enchantment.getFormattedName(0).stripLegacyFormatting() to enchantment
        }

        enchantmentCompletions = namesWithEnchantments.map { it.first }
        levelCompletionsByName = namesWithEnchantments.associate { (name, enchantment) ->
            name.lowercase() to (1..enchantment.maximumLevel).map { it.toString() }
        }
        hiddenEnchantNames = namesWithEnchantments
            .filter { it.second.isHiddenFromGui }
            .map { it.first.lowercase() }
            .toSet()
    }

    override fun onExecute(sender: CommandSender, args: List<String>) {
        sender as Player

        if (args.isEmpty()) {
            if (showHeldItemEnchants(sender)) {
                return
            }

            sender.sendMessage(this.plugin.langYml.getMessage("missing-enchant"))
            sender.sendMessage(this.plugin.langYml.getMessage("enchantinfo-usage"))
            return
        }

        val level = if (args.size > 1) args.last().toIntOrNull() else null
        val nameArgs = if (level != null) args.dropLast(1) else args
        val searchName = nameArgs.joinToString(" ")

        val enchantment = EcoEnchants.getByName(searchName)

        if (enchantment == null || (enchantment.isHiddenFromGui && !sender.hasPermission("ecoenchants.seehidden"))) {
            val message = plugin.langYml.getMessage("not-found").replace("%name%", searchName)
            sender.sendMessage(message)
            sender.sendMessage(plugin.langYml.getMessage("enchantinfo-browse-hint"))
            return
        }

        EnchantGUI.openInfoGUI(sender, enchantment, level ?: -1)
    }

    /**
     * Lists the EcoEnchants on the player's main-hand item as clickable chat lines
     * (each reopens the info GUI via /enchantinfo). Returns false when the held item
     * has no EcoEnchants enchantments so the caller can fall back to the usage message.
     */
    private fun showHeldItemEnchants(player: Player): Boolean {
        val item = player.inventory.itemInMainHand
        if (item.type.isAir) {
            return false
        }

        val canSeeHidden = player.hasPermission("ecoenchants.seehidden")
        val ecoByEnchantment = EcoEnchants.values().associateBy { it.enchantment }
        val onItem = item.fast().getEnchants(true)
            .mapNotNull { (enchantment, level) -> ecoByEnchantment[enchantment]?.let { it to level } }
            .filter { (enchant, _) -> !enchant.isHiddenFromGui || canSeeHidden }
            .sortedBy { it.first.getFormattedName(0).stripLegacyFormatting().lowercase() }

        if (onItem.isEmpty()) {
            return false
        }

        PlayerExperience.sendLangLines(
            player,
            "commands.enchantinfo.held-header",
            "count" to onItem.size.toString()
        )

        val line = plugin.langYml.getStrings("commands.enchantinfo.held-line").firstOrNull() ?: "&7- %enchant%"
        val hover = plugin.langYml.getStrings("commands.enchantinfo.held-hover").firstOrNull()

        for ((enchant, level) in onItem) {
            val plainName = enchant.getFormattedName(0).stripLegacyFormatting()
            player.sendClickableLine(
                line.replace("%enchant%", enchant.getFormattedName(level)),
                "/enchantinfo $plainName $level",
                hover?.replace("%enchant%", enchant.getFormattedName(level))
            )
        }

        return true
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> {
        if (enchantmentCompletions.isEmpty()) {
            reload()
        }

        val completions = mutableListOf<String>()
        val canSeeHidden = sender.hasPermission("ecoenchants.seehidden")
        val visibleCompletions = if (canSeeHidden) {
            enchantmentCompletions
        } else {
            enchantmentCompletions.filterNot { it.lowercase() in hiddenEnchantNames }
        }

        if (args.isEmpty()) {
            // Currently, this case is not ever reached
            return visibleCompletions
        }

        // If all args except the last form a complete enchant name, suggest level numbers
        if (args.size > 1) {
            val namePrefix = args.dropLast(1).joinToString(" ")
            val levels = levelCompletionsByName[namePrefix.lowercase()]
            val matched = EcoEnchants.getByName(namePrefix)
            if (levels != null && matched != null && (!matched.isHiddenFromGui || canSeeHidden)) {
                StringUtil.copyPartialMatches(args.last(), levels, completions)
                return completions
            }
        }

        StringUtil.copyPartialMatches(args.joinToString(" "), visibleCompletions, completions)

        if (args.size > 1) {
            val prefix = args.dropLast(1).joinToString(" ") + " "
            val trimmed = completions.mapNotNull { completion ->
                if (completion.startsWith(prefix, ignoreCase = true)) {
                    completion.substring(prefix.length)
                } else null
            }
            completions.clear()
            completions.addAll(trimmed)
        }

        completions.sort()
        return completions
    }
}
