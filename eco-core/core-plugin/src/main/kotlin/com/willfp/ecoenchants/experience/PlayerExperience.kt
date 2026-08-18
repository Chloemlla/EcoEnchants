package com.willfp.ecoenchants.experience

import com.willfp.eco.core.data.keys.PersistentDataKey
import com.willfp.eco.core.data.keys.PersistentDataKeyType
import com.willfp.eco.core.data.profile
import com.willfp.eco.util.NamespacedKeyUtils
import com.willfp.eco.util.formatEco
import com.willfp.ecoenchants.enchant.EcoEnchants
import com.willfp.ecoenchants.plugin
import com.willfp.ecoenchants.sendActionBarHint
import com.willfp.ecoenchants.sendClickableLine
import com.willfp.ecoenchants.target.EnchantmentTargets.applicableEnchantments
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PlayerExperience : Listener {
    private val seenJoinHintKey = PersistentDataKey(
        NamespacedKeyUtils.create("ecoenchants", "seen_join_hint"),
        PersistentDataKeyType.BOOLEAN,
        false
    )

    private val seenBrowserHintKey = PersistentDataKey(
        NamespacedKeyUtils.create("ecoenchants", "seen_browser_hint"),
        PersistentDataKeyType.BOOLEAN,
        false
    )

    private val seenHoldHintKey = PersistentDataKey(
        NamespacedKeyUtils.create("ecoenchants", "seen_hold_hint"),
        PersistentDataKeyType.BOOLEAN,
        false
    )

    private val lastHintByPlayer = ConcurrentHashMap<UUID, MutableMap<String, Long>>()
    private val lastEmptyReasonByPlayer = ConcurrentHashMap<UUID, String>()
    private val emptyResultCounts = ConcurrentHashMap<String, Int>()

    fun reload() {
        lastHintByPlayer.clear()
        lastEmptyReasonByPlayer.clear()
    }

    @EventHandler
    fun handleJoin(event: PlayerJoinEvent) {
        if (!plugin.configYml.getBool("player-experience.auto-hints.enabled")
            || !plugin.configYml.getBool("player-experience.auto-hints.on-first-join")) {
            return
        }

        val player = event.player
        if (player.profile.read(seenJoinHintKey)) {
            return
        }

        player.profile.write(seenJoinHintKey, true)
        sendLangLines(player, "hints.join")
    }

    @EventHandler
    fun handleItemHeld(event: PlayerItemHeldEvent) {
        if (!plugin.configYml.getBool("player-experience.auto-hints.enabled")
            || !plugin.configYml.getBool("player-experience.auto-hints.on-hold-enchantable")) {
            return
        }

        val player = event.player
        val item = player.inventory.getItem(event.newSlot) ?: return
        if (item.type == Material.AIR) {
            return
        }

        val enchantable = item.type == Material.ENCHANTED_BOOK || item.applicableEnchantments.isNotEmpty()
        if (!enchantable) {
            return
        }

        if (!canSendHint(player, "hold-enchantable")) {
            return
        }

        val actionBar = plugin.langYml.getStrings("hints.hold-enchantable.actionbar").firstOrNull()
        if (!actionBar.isNullOrBlank()) {
            player.sendActionBarHint(actionBar)
        }

        // The first time only, also send a clickable chat prompt that opens the browser.
        if (!player.profile.read(seenHoldHintKey)) {
            player.profile.write(seenHoldHintKey, true)
            val chat = plugin.langYml.getStrings("hints.hold-enchantable.chat").firstOrNull()
            if (!chat.isNullOrBlank()) {
                player.sendClickableLine(chat, "/ecoenchants gui")
            }
        }
    }

    fun handleBrowserOpen(player: Player) {
        if (!plugin.configYml.getBool("player-experience.auto-hints.enabled")
            || !plugin.configYml.getBool("player-experience.auto-hints.on-browser-open")) {
            return
        }

        if (plugin.configYml.getBool("player-experience.auto-hints.once-per-player")
            && player.profile.read(seenBrowserHintKey)) {
            return
        }

        if (!canSendHint(player, "browser-open")) {
            return
        }

        player.profile.write(seenBrowserHintKey, true)
        sendLangLines(player, "hints.browser-open")
    }

    fun handleEmptyResults(player: Player, reason: String) {
        if (lastEmptyReasonByPlayer[player.uniqueId] == reason) {
            return
        }

        lastEmptyReasonByPlayer[player.uniqueId] = reason
        emptyResultCounts.merge(reason, 1, Int::plus)

        if (!plugin.configYml.getBool("player-experience.auto-hints.enabled")
            || !plugin.configYml.getBool("player-experience.auto-hints.on-empty-results")
            || !canSendHint(player, "empty-results")) {
            return
        }

        sendLangLines(
            player,
            "hints.empty-results.$reason",
            "reason" to reason.toDisplayName()
        )
    }

    fun clearEmptyResults(player: Player) {
        lastEmptyReasonByPlayer.remove(player.uniqueId)
    }

    fun handleFilterChanged(player: Player, filter: String, value: String) {
        if (!plugin.configYml.getBool("player-experience.auto-hints.enabled")
            || !plugin.configYml.getBool("player-experience.auto-hints.on-filter-change")
            || !canSendHint(player, "filter-change")) {
            return
        }

        sendLangLines(
            player,
            "hints.filter-changed",
            "filter" to filter.toDisplayName(),
            "value" to value
        )
    }

    fun sendGuide(sender: CommandSender) {
        sendLangLines(
            sender,
            "commands.guide.lines",
            "enchant_count" to EcoEnchants.values().size.toString()
        )
    }

    fun giveGuideBook(player: Player) {
        val item = ItemStack(Material.WRITTEN_BOOK)
        val meta = item.itemMeta as? BookMeta ?: return

        meta.setTitle(stripFormatting(plugin.langYml.getFormattedString("commands.guide.book-title")))
        meta.setAuthor(stripFormatting(plugin.langYml.getFormattedString("commands.guide.book-author")))
        meta.setPages(
            plugin.langYml.getStrings("commands.guide.book-pages")
                .map { stripFormatting(it.replace("%enchant_count%", EcoEnchants.values().size.toString())) }
        )
        item.itemMeta = meta

        player.inventory.addItem(item).values.forEach {
            player.world.dropItemNaturally(player.location, it)
        }
        sendLangLines(player, "commands.guide.book-given")
    }

    fun sendHelp(sender: CommandSender) {
        sendLangLines(sender, "commands.help.header")

        for (entry in helpEntries) {
            if (!sender.hasPermission(entry.permission)) {
                continue
            }

            sendLangLines(sender, entry.langKey)
        }
    }

    fun statusLines(): List<String> {
        val lines = mutableListOf(
            "&aEcoEnchants Player Experience",
            "&7Auto hints: &f${plugin.configYml.getBool("player-experience.auto-hints.enabled")}",
            "&7Hint cooldown: &f${plugin.configYml.getInt("player-experience.auto-hints.cooldown-seconds")}s",
            "&7Empty result reasons tracked: &f${emptyResultCounts.size}"
        )

        if (emptyResultCounts.isEmpty()) {
            lines += "&8No empty result cases have been tracked this session."
        } else {
            emptyResultCounts.entries
                .sortedByDescending { it.value }
                .take(8)
                .forEach { (reason, count) ->
                    lines += "&7- &f${reason.toDisplayName()}&7: &a$count"
                }
        }

        return lines.formatEco()
    }

    private fun canSendHint(player: Player, key: String): Boolean {
        val cooldownMillis = plugin.configYml.getInt("player-experience.auto-hints.cooldown-seconds")
            .coerceAtLeast(0) * 1000L
        val now = System.currentTimeMillis()
        val playerHints = lastHintByPlayer.computeIfAbsent(player.uniqueId) { mutableMapOf() }
        val previous = playerHints[key] ?: 0L

        if (now - previous < cooldownMillis) {
            return false
        }

        playerHints[key] = now
        return true
    }

    fun sendLangLines(
        sender: CommandSender,
        key: String,
        vararg replacements: Pair<String, String>
    ) {
        val lines = plugin.langYml.getStrings(key)
        if (lines.isEmpty()) {
            return
        }

        for (line in lines) {
            var message = line
            for ((placeholder, value) in replacements) {
                message = message.replace("%$placeholder%", value)
            }
            sender.sendMessage(message.formatEco())
        }
    }

    private fun stripFormatting(value: String): String {
        return value
            .replace(Regex("&[0-9a-fk-orA-FK-OR]"), "")
            .replace(Regex("<[^>]+>"), "")
    }

    private fun String.toDisplayName(): String {
        return this.split('-', '_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase() else char.toString()
                }
            }
    }

    private data class HelpEntry(
        val permission: String,
        val langKey: String
    )

    private val helpEntries = listOf(
        HelpEntry("ecoenchants.command.gui", "commands.help.gui"),
        HelpEntry("ecoenchants.command.search", "commands.help.search"),
        HelpEntry("ecoenchants.command.enchantinfo", "commands.help.enchantinfo"),
        HelpEntry("ecoenchants.command.favorites", "commands.help.favorites"),
        HelpEntry("ecoenchants.command.toggledescriptions", "commands.help.toggledescriptions"),
        HelpEntry("ecoenchants.command.guide", "commands.help.guide"),
        HelpEntry("ecoenchants.command.enchant", "commands.help.enchant"),
        HelpEntry("ecoenchants.command.giverandombook", "commands.help.giverandombook"),
        HelpEntry("ecoenchants.command.reload", "commands.help.reload"),
        HelpEntry("ecoenchants.command.services", "commands.help.services"),
        HelpEntry("ecoenchants.command.experience", "commands.help.experience")
    )
}
