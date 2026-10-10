package com.willfp.ecoenchants.display

import com.willfp.eco.core.display.DisplayContext
import com.willfp.eco.core.display.DisplayModule
import com.willfp.eco.core.display.DisplayPriority
import com.willfp.eco.core.fast.FastItemStack
import com.willfp.eco.core.fast.fast
import com.willfp.eco.util.formatEco
import com.willfp.eco.util.toComponent
import com.willfp.ecoenchants.commands.CommandToggleDescriptions.seesEnchantmentDescriptions
import com.willfp.ecoenchants.display.EnchantSorter.sortForDisplay
import com.willfp.ecoenchants.dragdrop.dragAndDropPriceDisplay
import com.willfp.ecoenchants.dragdrop.isDragAndDropEnabled
import com.willfp.ecoenchants.enchant.EcoEnchant
import com.willfp.ecoenchants.enchant.wrap
import com.willfp.ecoenchants.plugin
import com.willfp.ecoenchants.target.EnchantmentTargets.isEnchantable
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.ItemProvidedHolder
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.applyHolder
import com.willfp.libreforge.conditions.ConditionList
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.toPlaceholderContext
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType

// Works around HIDE_POTION_EFFECTS not existing in 1.20.5+
interface HideStoredEnchantsProxy {
    fun hideStoredEnchants(fis: FastItemStack)
    fun showStoredEnchants(fis: FastItemStack)
    fun areStoredEnchantsHidden(fis: FastItemStack): Boolean
}

@Suppress("DEPRECATION")
object EnchantDisplay : DisplayModule(plugin, DisplayPriority.HIGH) {
    private val hideStateKey =
        plugin.namespacedKeyFactory.create("ecoenchantlore-skip") // Same for backwards compatibility

    private val originalHideEnchantsKey =
        plugin.namespacedKeyFactory.create("ecoenchantlore-original-hide-enchants")

    private val originalHideStoredEnchantsKey =
        plugin.namespacedKeyFactory.create("ecoenchantlore-original-hide-stored-enchants")

    private val addedHideEnchantsKey =
        plugin.namespacedKeyFactory.create("ecoenchantlore-added-hide-enchants")

    private val addedHideStoredEnchantsKey =
        plugin.namespacedKeyFactory.create("ecoenchantlore-added-hide-stored-enchants")

    private val hse = plugin.getProxy(HideStoredEnchantsProxy::class.java)

    override fun display(context: DisplayContext) {
        val itemStack = context.itemStack
        val player = context.player

        if (!itemStack.isEnchantable && plugin.configYml.getBool("display.require-enchantable")) {
            return
        }

        val fast = itemStack.fast()
        val pdc = fast.persistentDataContainer

        // Get enchants mapped to EcoEnchantLike
        val unsorted = fast.getEnchants(true)
        if (unsorted.isEmpty()) {
            return
        }

        val originalHideState =
            fast.getOriginalHideState(context.varArgs, itemStack.type == Material.ENCHANTED_BOOK)

        pdc.set(originalHideEnchantsKey, PersistentDataType.INTEGER, originalHideState.hidesEnchants.toStoredInt())
        pdc.set(
            originalHideStoredEnchantsKey,
            PersistentDataType.INTEGER,
            originalHideState.hidesStoredEnchants.toStoredInt()
        )

        // Args represent hide enchants
        if (originalHideState.hidesEnchants || originalHideState.hidesStoredEnchants) {
            if (!originalHideState.hidesEnchants) {
                fast.addItemFlags(ItemFlag.HIDE_ENCHANTS)
                pdc.set(addedHideEnchantsKey, PersistentDataType.INTEGER, 1)
            } else {
                pdc.set(addedHideEnchantsKey, PersistentDataType.INTEGER, 0)
            }

            if (itemStack.type == Material.ENCHANTED_BOOK) {
                if (!originalHideState.hidesStoredEnchants) {
                    hse.hideStoredEnchants(fast)
                    pdc.set(addedHideStoredEnchantsKey, PersistentDataType.INTEGER, 1)
                } else {
                    pdc.set(addedHideStoredEnchantsKey, PersistentDataType.INTEGER, 0)
                }
            }
            pdc.set(hideStateKey, PersistentDataType.INTEGER, 1)
            return
        } else {
            pdc.set(hideStateKey, PersistentDataType.INTEGER, 0)
        }

        val enchantLore = mutableListOf<Component>()
        val enchants = unsorted.keys.sortForDisplay()
            .associateWith { unsorted[it]!! }

        val collapsePerLine = plugin.configYml.getInt("display.collapse.per-line")
        val enchantmentsBelowLore = plugin.configYml.getBool("display.enchantments-below-lore")
        val playerDispatcher = player?.toDispatcher()

        val shouldCollapse = plugin.configYml.getBool("display.collapse.enabled") &&
                enchants.size > plugin.configYml.getInt("display.collapse.threshold")

        val shouldDescribe = (plugin.configYml.getBool("display.descriptions.enabled") &&
                enchants.size <= plugin.configYml.getInt("display.descriptions.threshold")
                && player?.seesEnchantmentDescriptions ?: true)

        val shouldShowTargets = itemStack.type == Material.ENCHANTED_BOOK &&
                plugin.configYml.getBool("display.book-targets.enabled")

        val shouldShowDragAndDropPrice = itemStack.type == Material.ENCHANTED_BOOK &&
                plugin.configYml.getBool("display.book-drag-and-drop-price.enabled")

        val formattedNames = mutableMapOf<DisplayableEnchant, String>()

        val notMetLines = mutableListOf<Component>()

        for ((enchant, level) in enchants) {
            var showNotMet = false
            if (playerDispatcher != null && enchant is EcoEnchant) {
                val enchantLevel = enchant.getLevel(level)
                val holder = ItemProvidedHolder(enchantLevel, itemStack)

                val notMetDisplay = holder.getNotMetDisplay(playerDispatcher)
                notMetLines.addAll(notMetDisplay.lines.map { it.toComponent() })
                showNotMet = notMetDisplay.showNameAsNotMet
            }

            val wrapped = enchant.wrap()
            formattedNames[DisplayableEnchant(wrapped, level)] =
                wrapped.getFormattedName(level, showNotMet = showNotMet)
        }

        if (shouldCollapse) {
            for (names in formattedNames.values.chunked(collapsePerLine)) {
                enchantLore.add(
                    names.joinToString(
                        plugin.configYml.getFormattedString("display.collapse.delimiter")
                    ).toComponent()
                )
            }
        } else {
            for ((displayable, formattedName) in formattedNames) {
                val (enchant, level) = displayable

                enchantLore.add(formattedName.toComponent())

                if (shouldDescribe) {
                    enchantLore.addAll(
                        enchant.getFormattedDescription(level, player)
                            .filter { it.isNotEmpty() }
                            .map { it.toComponent() }
                    )
                }

                if (shouldShowTargets && enchant.targets.isNotEmpty()) {
                    enchantLore.add(
                        plugin.configYml.getFormattedString("display.book-targets.format")
                            .replace("%targets%", enchant.targets.joinToString(", ") { it.displayName })
                            .toComponent()
                    )
                }

                if (shouldShowDragAndDropPrice && player != null && enchant.isDragAndDropEnabled()) {
                    enchantLore.add(
                        plugin.configYml.getFormattedString("display.book-drag-and-drop-price.format")
                            .replace("%price%", enchant.dragAndDropPriceDisplay(player, level))
                            .toComponent()
                    )
                }
            }
        }

        fast.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        pdc.set(addedHideEnchantsKey, PersistentDataType.INTEGER, 1)
        if (itemStack.type == Material.ENCHANTED_BOOK) {
            hse.hideStoredEnchants(fast)
            pdc.set(addedHideStoredEnchantsKey, PersistentDataType.INTEGER, 1)
        }

        if (enchantmentsBelowLore) {
            context.lore.append(enchantLore + notMetLines)
        } else {
            context.lore.prepend(enchantLore)
            context.lore.append(notMetLines)
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun revert(itemStack: ItemStack) {
        if (!itemStack.isEnchantable && plugin.configYml.getBool("display.require-enchantable")) {
            return
        }

        val fast = itemStack.fast()
        val pdc = fast.persistentDataContainer

        val originallyHidEnchants = pdc.get(originalHideEnchantsKey, PersistentDataType.INTEGER)?.toBool()
            ?: (pdc.hideState == 1)
        val originallyHidStoredEnchants = pdc.get(originalHideStoredEnchantsKey, PersistentDataType.INTEGER)?.toBool()
            ?: (pdc.hideState == 1)
        val addedHideEnchants = pdc.get(addedHideEnchantsKey, PersistentDataType.INTEGER)?.toBool()
            ?: (!originallyHidEnchants && pdc.hideState != -1)
        val addedHideStoredEnchants = pdc.get(addedHideStoredEnchantsKey, PersistentDataType.INTEGER)?.toBool()
            ?: (!originallyHidStoredEnchants && pdc.hideState != -1)

        if (originallyHidEnchants) {
            fast.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        } else if (addedHideEnchants) {
            fast.removeItemFlags(ItemFlag.HIDE_ENCHANTS)
        }

        if (itemStack.type == Material.ENCHANTED_BOOK) {
            if (originallyHidStoredEnchants) {
                hse.hideStoredEnchants(fast)
            } else if (addedHideStoredEnchants) {
                hse.showStoredEnchants(fast)
            }
        }

        pdc.remove(hideStateKey)
        pdc.remove(originalHideEnchantsKey)
        pdc.remove(originalHideStoredEnchantsKey)
        pdc.remove(addedHideEnchantsKey)
        pdc.remove(addedHideStoredEnchantsKey)
    }

    override fun generateVarArgs(itemStack: ItemStack): Array<Any> {
        val fast = itemStack.fast()
        val pdc = fast.persistentDataContainer

        val originallyHidEnchants = pdc.get(originalHideEnchantsKey, PersistentDataType.INTEGER)?.toBool()
        val originallyHidStoredEnchants = pdc.get(originalHideStoredEnchantsKey, PersistentDataType.INTEGER)?.toBool()

        if (originallyHidEnchants != null || originallyHidStoredEnchants != null) {
            return arrayOf(
                originallyHidEnchants ?: false,
                originallyHidStoredEnchants ?: false
            )
        }

        val hidesEnchants = fast.hasItemFlag(ItemFlag.HIDE_ENCHANTS)
        val hidesStoredEnchants = if (itemStack.type == Material.ENCHANTED_BOOK) {
            hse.areStoredEnchantsHidden(fast)
        } else {
            false
        }

        return when (fast.hideState) {
            1 -> arrayOf(true, hidesStoredEnchants)
            0 -> arrayOf(false, hidesStoredEnchants)
            else -> arrayOf(
                hidesEnchants,
                hidesStoredEnchants
            )
        }
    }

    private val FastItemStack.hideState: Int
        get() = this.persistentDataContainer.hideState

    private val PersistentDataContainer.hideState: Int
        get() = this.get(hideStateKey, PersistentDataType.INTEGER) ?: -1

    private fun Boolean.toStoredInt(): Int = if (this) 1 else 0

    private fun Int.toBool(): Boolean = this == 1

    private fun FastItemStack.getOriginalHideState(args: Array<out Any>, isEnchantedBook: Boolean): HideState {
        val pdc = this.persistentDataContainer

        val hidesEnchants = pdc.get(originalHideEnchantsKey, PersistentDataType.INTEGER)?.toBool()
            ?: (args.getOrNull(0) as? Boolean)
            ?: this.hasItemFlag(ItemFlag.HIDE_ENCHANTS)

        val hidesStoredEnchants = if (isEnchantedBook) {
            pdc.get(originalHideStoredEnchantsKey, PersistentDataType.INTEGER)?.toBool()
                ?: (args.getOrNull(1) as? Boolean)
                ?: hse.areStoredEnchantsHidden(this)
        } else {
            false
        }

        return HideState(hidesEnchants, hidesStoredEnchants)
    }

    private data class HideState(
        val hidesEnchants: Boolean,
        val hidesStoredEnchants: Boolean
    )
}

private data class NotMetDisplay(
    val lines: List<String>,
    val showNameAsNotMet: Boolean
)

private fun ProvidedHolder.getNotMetDisplay(dispatcher: Dispatcher<*>): NotMetDisplay {
    val lines = mutableListOf<String>()
    var showNameAsNotMet = false

    fun collect(conditionList: ConditionList) {
        for (block in conditionList) {
            if (!block.showNotMet) {
                continue
            }

            if (block.isMet(dispatcher, this@getNotMetDisplay)) {
                continue
            }

            showNameAsNotMet = true

            if (block.notMetLines.isEmpty()) {
                continue
            }

            val context = block.config.applyHolder(this@getNotMetDisplay, dispatcher).toPlaceholderContext()
            lines += block.notMetLines.map {
                it.formatEco(context)
            }
        }
    }

    collect(holder.conditions)
    for (effect in holder.effects) {
        collect(effect.conditions)
    }

    return NotMetDisplay(lines, showNameAsNotMet)
}
