package com.willfp.ecoenchants

import com.willfp.eco.util.formatEco
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player

// EcoEnchants formats text into legacy section (§) strings, including the
// §x§r§r§g§g§b§b hex sequences Spigot/eco emits for gradients and hex colors.
// This serializer converts those legacy strings back into Adventure components
// so we can attach click/hover events while keeping the original colours.
private val legacySerializer = LegacyComponentSerializer.builder()
    .character('§')
    .hexColors()
    .useUnusualXRepeatedCharacterHexFormat()
    .build()

/** Format an eco source string (& codes, MiniMessage tags) into an Adventure [Component]. */
internal fun String.toEnchantComponent(): Component =
    legacySerializer.deserialize(this.formatEco())

/**
 * Send [line] as a clickable chat message that runs [command] when clicked.
 * [line] is an eco source string; [hover], if given, is shown as the tooltip.
 */
internal fun Player.sendClickableLine(line: String, command: String, hover: String? = null) {
    var component = line.toEnchantComponent()
        .clickEvent(ClickEvent.runCommand(command))

    if (hover != null) {
        component = component.hoverEvent(HoverEvent.showText(hover.toEnchantComponent()))
    }

    this.sendMessage(component)
}

/** Send [text] (an eco source string) on the player's action bar. */
internal fun Player.sendActionBarHint(text: String) {
    this.sendActionBar(text.toEnchantComponent())
}
