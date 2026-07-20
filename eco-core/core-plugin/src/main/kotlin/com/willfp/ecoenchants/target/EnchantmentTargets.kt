package com.willfp.ecoenchants.target

import com.willfp.eco.core.cache.EcoCache
import com.willfp.eco.core.fast.fast
import com.willfp.eco.core.items.HashedItem
import com.willfp.eco.core.registry.Registry
import com.willfp.ecoenchants.enchant.EcoEnchant
import com.willfp.ecoenchants.enchant.EcoEnchants
import com.willfp.ecoenchants.enchant.infiniteIfNegative
import com.willfp.ecoenchants.plugin
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.time.Duration

object EnchantmentTargets : Registry<EnchantmentTarget>() {
    init {
        register(AllEnchantmentTarget)
        update()
    }

    private fun getForItem(item: ItemStack): List<EnchantmentTarget> {
        return values()
            .filter { !it.id.equals("all", ignoreCase = true) }
            .filter { it.matches(item) }
    }

    val ItemStack.isEnchantable: Boolean
        get() = enchantableCache.get(HashedItem.of(this)) {
            getForItem(this).isNotEmpty() || this.type == Material.BOOK || this.type == Material.ENCHANTED_BOOK
        }

    val ItemStack.applicableEnchantments: List<EcoEnchant>
        get() = canEnchantCache.get(HashedItem.of(this)) {
            val currentEnchantments = this.fast().getEnchants(true).keys
            val enchantLimit = plugin.configYml.getInt("anvil.enchant-limit").infiniteIfNegative()

            EcoEnchants.values().filter { it.canEnchantItemConsidering(this, currentEnchantments, enchantLimit) }
        }

    @JvmStatic
    fun update() {
        for (target in values()) {
            if (target is AllEnchantmentTarget) {
                continue
            }
            remove(target)
        }

        for (config in plugin.targetsYml.getSubsections("targets")) {
            register(ConfiguredEnchantmentTarget(config))
        }

        AllEnchantmentTarget.updateItems()
    }
}

private val enchantableCache = EcoCache.builder<HashedItem, Boolean>()
    .expireAfterAccess(Duration.ofSeconds(5))
    .build()

private val canEnchantCache = EcoCache.builder<HashedItem, List<EcoEnchant>>()
    .expireAfterAccess(Duration.ofSeconds(5))
    .build()
