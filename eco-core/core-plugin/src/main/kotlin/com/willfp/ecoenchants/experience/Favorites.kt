package com.willfp.ecoenchants.experience

import com.willfp.eco.core.data.keys.PersistentDataKey
import com.willfp.eco.core.data.keys.PersistentDataKeyType
import com.willfp.eco.core.data.profile
import com.willfp.ecoenchants.enchant.EcoEnchant
import com.willfp.ecoenchants.enchant.EcoEnchants
import com.willfp.ecoenchants.plugin
import org.bukkit.entity.Player

/**
 * Per-player enchantment bookmarks. Stores enchant IDs in a STRING_LIST profile key,
 * mirroring the persistence pattern used by [com.willfp.ecoenchants.enchant.impl.hardcoded.EnchantmentSoulbound].
 */
object Favorites {
    private val favoritesKey = PersistentDataKey(
        plugin.namespacedKeyFactory.create("favorite_enchants"),
        PersistentDataKeyType.STRING_LIST,
        emptyList()
    )

    /** The player's favorited enchants, dropping any IDs that no longer resolve. */
    fun list(player: Player): List<EcoEnchant> =
        player.profile.read(favoritesKey).mapNotNull { EcoEnchants.getByID(it) }

    fun contains(player: Player, enchant: EcoEnchant): Boolean =
        player.profile.read(favoritesKey).contains(enchant.id)

    /** Toggles [enchant] in the player's favorites. Returns true if it is now a favorite. */
    fun toggle(player: Player, enchant: EcoEnchant): Boolean {
        val current = player.profile.read(favoritesKey).toMutableList()

        val nowFavorite = if (current.contains(enchant.id)) {
            current.remove(enchant.id)
            false
        } else {
            current.add(enchant.id)
            true
        }

        player.profile.write(favoritesKey, current)
        return nowFavorite
    }
}
