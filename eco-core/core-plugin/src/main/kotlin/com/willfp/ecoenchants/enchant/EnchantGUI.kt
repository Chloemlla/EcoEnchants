package com.willfp.ecoenchants.enchant

import com.github.benmanes.caffeine.cache.Caffeine
import com.willfp.eco.core.config.base.LangYml
import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.drops.DropQueue
import com.willfp.eco.core.fast.fast
import com.willfp.eco.core.gui.GUIComponent
import com.willfp.eco.core.gui.menu
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.menu.MenuLayer
import com.willfp.eco.core.gui.page.Page
import com.willfp.eco.core.gui.slot
import com.willfp.eco.core.gui.slot.ConfigSlot
import com.willfp.eco.core.gui.slot.FillerMask
import com.willfp.eco.core.gui.slot.MaskItems
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.HashedItem
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.EnchantedBookBuilder
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.items.isEcoEmpty
import com.willfp.eco.util.NumberUtils
import com.willfp.eco.util.StringUtils
import com.willfp.eco.util.formatEco
import com.willfp.eco.util.lineWrap
import com.willfp.eco.util.toNiceString
import com.willfp.ecoenchants.display.EnchantSorter.sortForDisplay
import com.willfp.ecoenchants.display.HideStoredEnchantsProxy
import com.willfp.ecoenchants.display.getFormattedDescription
import com.willfp.ecoenchants.display.getFormattedName
import com.willfp.ecoenchants.plugin
import com.willfp.ecoenchants.rarity.EnchantmentRarities
import com.willfp.ecoenchants.target.EnchantmentTargets.applicableEnchantments
import com.willfp.ecoenchants.target.EnchantmentTargets
import com.willfp.ecoenchants.type.EnchantmentTypes
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil

object EnchantGUI : Listener {
    private lateinit var menu: Menu
    private var groupMenu: Menu? = null
    private var adminMenu: Menu? = null
    private val enchantInfoMenus = Caffeine.newBuilder().build<Pair<EcoEnchant, Int>, Menu>()
    private var allEnchantsSorted: List<EcoEnchant> = emptyList()
    private val returnedOnDisconnect = mutableSetOf<UUID>()

    internal fun reload() {
        cachedEnchantmentSlots.invalidateAll()
        applicableEnchantmentsSorted.invalidateAll()
        enchantInfoMenus.invalidateAll()
        allEnchantsSorted = EcoEnchants.values().sortForGui()

        menu = menu(plugin.configYml.getInt("enchant-gui.rows")) {
            title = getConfiguredGuiTitle("enchant-gui")

            allowChangingHeldItem()

            setMask(
                FillerMask(
                    MaskItems.fromItemNames(
                        plugin.configYml.getStrings("enchant-gui.mask.items")
                    ),
                    *plugin.configYml.getStrings("enchant-gui.mask.pattern").toTypedArray()
                )
            )

            setSlot(
                plugin.configYml.getInt("enchant-gui.info.row"),
                plugin.configYml.getInt("enchant-gui.info.column"),
                slot(buildGuiItem("enchant-gui.info"))
            )

            val captiveRow = plugin.configYml.getInt("enchant-gui.item-row")
            val captiveColumn = plugin.configYml.getInt("enchant-gui.item-column")

            setSlot(
                captiveRow, captiveColumn, slot(ItemStack(Material.AIR)) {
                    setCaptive(true)
                }
            )

            onRender { player, menu ->
                val atCaptive = menu.getCaptiveItem(player, captiveRow, captiveColumn)
                val hasItem = !atCaptive.isEcoEmpty && atCaptive != null && atCaptive.type != Material.BOOK

                val baseEnchants = if (!hasItem) {
                    allEnchantsSorted
                } else {
                    val currentEnchants = atCaptive.fast().enchants.keys
                    applicableEnchantmentsSorted.get(HashedItem.of(atCaptive)) {
                        atCaptive.applicableEnchantments.sortForGui()
                    }.filterNot { it.enchantment in currentEnchants }
                }

                // Apply group filter if a groupId is set in menu state
                val groupId = menu.getState<String>(player, "groupId")
                val filteredEnchants = if (groupId != null) {
                    val groupBy = plugin.configYml.getString("enchant-gui.group-by")
                    baseEnchants.filter { enchantment ->
                        when (groupBy) {
                            "type" -> enchantment.type.id == groupId
                            "rarity" -> enchantment.enchantmentRarity.id == groupId
                            "target" -> enchantment.targets.any { it.id == groupId }
                            else -> true
                        }
                    }
                } else {
                    baseEnchants
                }

                menu.setState(player, "enchants", filteredEnchants)

                // Reset to page 1 when an item is placed or removed from the captive slot
                val previousHasItem = menu.getState<Boolean>(player, "hasItem") ?: false
                if (hasItem != previousHasItem) {
                    menu.setState(player, Page.PAGE_KEY, 1)
                }
                menu.setState(player, "hasItem", hasItem)

                // Safety net: also reset if the current page now exceeds the new max.
                // Compute directly from filteredEnchants to avoid a stale getMaxPage() value
                // (maxPages may be evaluated before onRender fires).
                val perPage = plugin.configYml.getInt("enchant-gui.enchant-area.width") * plugin.configYml.getInt("enchant-gui.enchant-area.height")
                val maxPage = if (filteredEnchants.isEmpty()) {
                    0
                } else {
                    ceil(filteredEnchants.size.toDouble() / perPage).toInt()
                }
                if (menu.getPage(player) > maxPage) {
                    menu.setState(player, Page.PAGE_KEY, 1)
                }
            }

            val pane = EnchantmentScrollPane()

            addComponent(
                plugin.configYml.getInt("enchant-gui.enchant-area.row"),
                plugin.configYml.getInt("enchant-gui.enchant-area.column"),
                pane
            )

            for (direction in GuiPageDirection.entries) {
                val directionName = direction.name.lowercase(Locale.ROOT)

                addComponent(
                    MenuLayer.TOP,
                    plugin.configYml.getInt("enchant-gui.page-change.$directionName.row"),
                    plugin.configYml.getInt("enchant-gui.page-change.$directionName.column"),
                    EnchantPageChanger(direction)
                )
            }

            addComponent(
                MenuLayer.TOP,
                plugin.configYml.getInt("enchant-gui.admin-tools.row"),
                plugin.configYml.getInt("enchant-gui.admin-tools.column"),
                AdminToolsButton()
            )

            if (plugin.configYml.getBool("enchant-gui.close-button.enabled")) {
                setSlot(
                    plugin.configYml.getInt("enchant-gui.close-button.row"),
                    plugin.configYml.getInt("enchant-gui.close-button.column"),
                    slot(buildGuiItem("enchant-gui.close-button")) {
                        onLeftClick { event, _ -> event.whoClicked.closeInventory() }
                    }
                )
            }

            // Back button to return to the group selection menu
            if (plugin.configYml.getBool("enchant-gui.grouped")
                && plugin.configYml.getBool("enchant-gui.back-button.enabled")) {
                setSlot(
                    plugin.configYml.getInt("enchant-gui.back-button.row"),
                    plugin.configYml.getInt("enchant-gui.back-button.column"),
                    slot(buildGuiItem("enchant-gui.back-button")) {
                        onLeftClick { event, _ ->
                            val groupGui = groupMenu ?: return@onLeftClick
                            val player = event.whoClicked as Player
                            returnCaptiveItems(player)
                            groupGui.open(player)
                        }
                    }
                )
            }

            maxPages { player ->
                val enchants = menu.getState<List<EcoEnchant>>(player, "enchants") ?: emptyList()
                val total = enchants.size
                val perPage = pane.size

                val pages = if (total == 0) {
                    0
                } else {
                    ceil(total.toDouble() / perPage).toInt()
                }
                pages
            }

            onClose { event, menu ->
                val player = event.player as Player
                if (returnedOnDisconnect.remove(player.uniqueId)) {
                    return@onClose
                }

                returnCaptiveItems(player, menu)
            }

            for (config in plugin.configYml.getSubsections("enchant-gui.custom-slots")) {
                setSlot(
                    config.getInt("row"),
                    config.getInt("column"),
                    ConfigSlot(config)
                )
            }
        }

        // Build the group selection menu (only when grouped mode is enabled)
        if (plugin.configYml.getBool("enchant-gui.grouped")) {
            groupMenu = menu(plugin.configYml.getInt("group-gui.rows")) {
                title = getConfiguredGuiTitle("group-gui")

                setMask(
                    FillerMask(
                        MaskItems.fromItemNames(
                            plugin.configYml.getStrings("group-gui.mask.items")
                        ),
                        *plugin.configYml.getStrings("group-gui.mask.pattern").toTypedArray()
                    )
                )

                addComponent(
                    MenuLayer.TOP,
                    plugin.configYml.getInt("group-gui.admin-tools.row"),
                    plugin.configYml.getInt("group-gui.admin-tools.column"),
                    AdminToolsButton("group-gui.admin-tools")
                )

                // Add a clickable slot for each configured group
                for (config in plugin.configYml.getSubsections("group-gui.groups")) {
                    val groupId = config.getString("id")

                    // Validate the group ID exists in the registry matching the group-by axis
                    val groupBy = plugin.configYml.getString("enchant-gui.group-by")
                    val valid = when (groupBy) {
                        "type" -> EnchantmentTypes[groupId] != null
                        "rarity" -> EnchantmentRarities[groupId] != null
                        "target" -> EnchantmentTargets[groupId] != null
                        else -> false
                    }

                    if (!valid) {
                        continue
                    }

                    setSlot(
                        config.getInt("row"),
                        config.getInt("column"),
                        slot(buildGuiItem(config)) {
                            onLeftClick { event, _ ->
                                openGroupGUI(event.whoClicked as Player, groupId)
                            }
                        }
                    )
                }

                // Custom decorator slots for the group menu
                for (config in plugin.configYml.getSubsections("group-gui.custom-slots")) {
                    setSlot(
                        config.getInt("row"),
                        config.getInt("column"),
                        ConfigSlot(config)
                    )
                }
            }
        } else {
            groupMenu = null
        }

        adminMenu = if (plugin.configYml.getBool("admin-gui.enabled")) {
            menu(plugin.configYml.getInt("admin-gui.rows")) {
                title = getConfiguredGuiTitle("admin-gui")

                setMask(
                    FillerMask(
                        MaskItems.fromItemNames(
                            plugin.configYml.getStrings("admin-gui.mask.items")
                        ),
                        *plugin.configYml.getStrings("admin-gui.mask.pattern").toTypedArray()
                    )
                )

                for (tool in AdminTool.entries) {
                    if (!plugin.configYml.getBool("${tool.configPath}.enabled")) {
                        continue
                    }

                    addComponent(
                        MenuLayer.TOP,
                        plugin.configYml.getInt("${tool.configPath}.row"),
                        plugin.configYml.getInt("${tool.configPath}.column"),
                        AdminToolButton(tool)
                    )
                }

                if (plugin.configYml.getBool("admin-gui.back-button.enabled")) {
                    setSlot(
                        plugin.configYml.getInt("admin-gui.back-button.row"),
                        plugin.configYml.getInt("admin-gui.back-button.column"),
                        slot(buildGuiItem("admin-gui.back-button")) {
                            onLeftClick { event, _ ->
                                openGUI(event.whoClicked as Player)
                            }
                        }
                    )
                }

                if (plugin.configYml.getBool("admin-gui.close-button.enabled")) {
                    setSlot(
                        plugin.configYml.getInt("admin-gui.close-button.row"),
                        plugin.configYml.getInt("admin-gui.close-button.column"),
                        slot(buildGuiItem("admin-gui.close-button")) {
                            onLeftClick { event, _ -> event.whoClicked.closeInventory() }
                        }
                    )
                }

                for (config in plugin.configYml.getSubsections("admin-gui.custom-slots")) {
                    setSlot(
                        config.getInt("row"),
                        config.getInt("column"),
                        ConfigSlot(config)
                    )
                }
            }
        } else {
            null
        }
    }

    fun openGUI(player: Player) {
        if (plugin.configYml.getBool("enchant-gui.grouped") && groupMenu != null) {
            groupMenu!!.open(player)
        } else {
            menu.open(player)
        }
    }

    fun openInfoGUI(player: Player, enchant: EcoEnchant, level: Int = -1) {
        val effectiveLevel = if (level == -1) {
            if (plugin.configYml.getBool("enchantinfo.item.show-max-level")) enchant.maximumLevel else 1
        } else {
            level.coerceIn(1, enchant.maximumLevel)
        }

        enchantInfoMenus.get(enchant to effectiveLevel) {
            menu(plugin.configYml.getInt("enchantinfo.rows")) {
                title = enchant.getFormattedName(effectiveLevel)

                setSlot(
                    plugin.configYml.getInt("enchantinfo.item.row"),
                    plugin.configYml.getInt("enchantinfo.item.column"),
                    enchant.getInformationSlot(player, effectiveLevel)
                )

                setMask(
                    FillerMask(
                        MaskItems.fromItemNames(plugin.configYml.getStrings("enchantinfo.mask.items")),
                        *plugin.configYml.getStrings("enchantinfo.mask.pattern").toTypedArray()
                    )
                )

                for (config in plugin.configYml.getSubsections("enchantinfo.custom-slots")) {
                    setSlot(
                        config.getInt("row"),
                        config.getInt("column"),
                        ConfigSlot(config)
                    )
                }
            }
        }.open(player)
    }

    private fun openAdminGUI(player: Player) {
        val targetMenu = adminMenu ?: run {
            player.sendLangMessage("admin-gui-disabled")
            return
        }

        if (!player.hasAdminToolsPermission()) {
            player.sendMessage(plugin.langYml.getMessage("no-permission"))
            return
        }

        targetMenu.open(player)
        player.sendLangMessage("opened-admin-gui")
    }

    private fun runAdminTool(player: Player, tool: AdminTool) {
        if (!player.hasPermission(tool.permission)) {
            player.sendMessage(plugin.langYml.getMessage("no-permission"))
            return
        }

        player.playConfiguredSound("${tool.configPath}.sound")

        when (tool) {
            AdminTool.RELOAD -> reloadFromAdmin(player)
            AdminTool.RANDOM_BOOK -> giveRandomBookToSelf(player)
        }
    }

    private fun reloadFromAdmin(player: Player) {
        player.closeInventory()

        val message = plugin.langYml.getMessage("reloaded", StringUtils.FormatOption.WITHOUT_PLACEHOLDERS)
        val time = plugin.reloadWithTime().toNiceString()

        player.sendMessage(
            message
                .replace("%time%", time)
                .replace("%count%", EcoEnchants.values().size.toString())
        )
    }

    private fun giveRandomBookToSelf(player: Player) {
        val enchantment = EcoEnchants.values().randomOrNull() ?: run {
            player.sendMessage(plugin.langYml.getMessage("no-enchantments-found"))
            return
        }

        val level = NumberUtils.randInt(1, enchantment.maximumLevel)

        val item = EnchantedBookBuilder()
            .addStoredEnchantment(enchantment.enchantment, level)
            .build()

        DropQueue(player)
            .addItem(item)
            .forceTelekinesis()
            .push()

        player.sendMessage(
            plugin.langYml.getMessage("gave-random-book", StringUtils.FormatOption.WITHOUT_PLACEHOLDERS)
                .replace("%player%", player.name)
                .replace("%enchantment%", enchantment.getFormattedName(level))
        )
    }

    private fun openGroupGUI(player: Player, groupId: String) {
        menu.setState(player, "groupId", groupId)
        menu.setState(player, Page.PAGE_KEY, 1)
        menu.open(player)
        player.sendLangMessage("opened-enchant-group", "group" to getGroupDisplayName(groupId))
    }

    @EventHandler
    fun handleQuit(event: PlayerQuitEvent) {
        returnCaptiveItemsOnDisconnect(event.player)
    }

    @EventHandler
    fun handleKick(event: PlayerKickEvent) {
        returnCaptiveItemsOnDisconnect(event.player)
    }

    private fun returnCaptiveItemsOnDisconnect(player: Player) {
        if (returnCaptiveItems(player, notify = false)) {
            returnedOnDisconnect.add(player.uniqueId)
        }
    }

    private fun returnCaptiveItems(player: Player, sourceMenu: Menu? = null, notify: Boolean = true): Boolean {
        if (!::menu.isInitialized) {
            return false
        }

        val activeMenu = sourceMenu ?: menu
        val captiveItems = activeMenu.getCaptiveItems(player)
            .filterNot { it.isEcoEmpty || it.type == Material.AIR }

        if (captiveItems.isEmpty()) {
            activeMenu.clearState(player)
            return false
        }

        val overflow = player.inventory.addItem(*captiveItems.map { it.clone() }.toTypedArray())

        for (item in overflow.values) {
            player.world.dropItemNaturally(player.location, item)
        }

        activeMenu.clearState(player)

        if (notify) {
            val returnedAmount = captiveItems.sumOf { it.amount }
            val droppedAmount = overflow.values.sumOf { it.amount }

            if (droppedAmount > 0) {
                player.sendLangMessage(
                    "returned-gui-items-with-overflow",
                    "amount" to returnedAmount.toString(),
                    "dropped" to droppedAmount.toString()
                )
            } else {
                player.sendLangMessage(
                    "returned-gui-items",
                    "amount" to returnedAmount.toString()
                )
            }
        }

        return true
    }

    private class AdminToolsButton(
        private val configPath: String = "enchant-gui.admin-tools"
    ) : GUIComponent {
        private val emptySlot = slot(ItemStack(Material.AIR))

        override fun getSlotAt(row: Int, column: Int, player: Player, menu: Menu): Slot {
            if (!plugin.configYml.getBool("$configPath.enabled")
                || !player.hasAdminToolsPermission()) {
                return emptySlot
            }

            return slot(
                buildGuiItem(configPath)
            ) {
                onLeftClick { event, _ ->
                    openAdminGUI(event.whoClicked as Player)
                }
            }
        }

        override fun getRows() = 1
        override fun getColumns() = 1
    }

    private class AdminToolButton(
        private val tool: AdminTool
    ) : GUIComponent {
        private val emptySlot = slot(ItemStack(Material.AIR))

        override fun getSlotAt(row: Int, column: Int, player: Player, menu: Menu): Slot {
            if (!player.hasPermission(tool.permission)) {
                return emptySlot
            }

            return slot(
                buildGuiItem(tool.configPath)
            ) {
                onLeftClick { event, _ ->
                    runAdminTool(event.whoClicked as Player, tool)
                }
            }
        }

        override fun getRows() = 1
        override fun getColumns() = 1
    }

    private enum class AdminTool(
        val configKey: String,
        val permission: String
    ) {
        RELOAD("reload", "ecoenchants.command.reload"),
        RANDOM_BOOK("random-book", "ecoenchants.command.giverandombook");

        val configPath = "admin-gui.tools.$configKey"
    }
}

private class EnchantmentScrollPane : GUIComponent {
    private val defaultSlot = slot(Items.lookup(plugin.configYml.getString("enchant-gui.empty-item")))

    override fun getSlotAt(row: Int, column: Int, player: Player, menu: Menu): Slot {
        val index = column + ((row - 1) * columns) - 1
        val page = menu.getPage(player)

        val enchants = menu.getState<List<EcoEnchant>>(player, "enchants") ?: return defaultSlot
        if (enchants.isEmpty()) {
            return if (row == (rows + 1) / 2 && column == (columns + 1) / 2) {
                getEmptyResultsSlot(player, menu)
            } else {
                defaultSlot
            }
        }

        val enchant = enchants.getOrNull(index + size * (page - 1)) ?: return defaultSlot

        val displayLevel = if (plugin.configYml.getBool("enchantinfo.item.show-max-level")) enchant.maximumLevel else 1
        return enchant.getInformationSlot(player, displayLevel)
    }

    private fun getEmptyResultsSlot(player: Player, menu: Menu): Slot {
        val configPath = "enchant-gui.empty-results"
        if (!plugin.configYml.has("$configPath.item")) {
            return defaultSlot
        }

        val hasItem = menu.getState<Boolean>(player, "hasItem") ?: false
        val groupId = menu.getState<String>(player, "groupId")
        val placeholders = mapOf(
            "group" to (groupId?.let { getGroupDisplayName(it) } ?: plugin.langYml.getFormattedString("all"))
        )

        val loreKeyPath = when {
            hasItem && groupId != null && plugin.configYml.has("$configPath.with-item-and-group-lore-key") ->
                "$configPath.with-item-and-group-lore-key"
            hasItem && plugin.configYml.has("$configPath.with-item-lore-key") ->
                "$configPath.with-item-lore-key"
            groupId != null && plugin.configYml.has("$configPath.group-lore-key") ->
                "$configPath.group-lore-key"
            else ->
                "$configPath.lore-key"
        }

        val loreKeyOverride = if (plugin.configYml.has(loreKeyPath)) {
            plugin.configYml.getString(loreKeyPath)
        } else {
            null
        }

        return slot(
            buildGuiItem(
                configPath,
                placeholders,
                loreKeyOverride
            )
        )
    }

    override fun getRows() = plugin.configYml.getInt("enchant-gui.enchant-area.height")
    override fun getColumns() = plugin.configYml.getInt("enchant-gui.enchant-area.width")

    val size = rows * columns
}

private val cachedEnchantmentSlots = Caffeine.newBuilder()
    .build<Pair<EcoEnchant, Int>, Slot>()

private val applicableEnchantmentsSorted = Caffeine.newBuilder()
    .build<HashedItem, List<EcoEnchant>>()

private enum class GuiPageDirection {
    FORWARDS,
    BACKWARDS
}

private class EnchantPageChanger(
    private val direction: GuiPageDirection
) : GUIComponent {
    private val configPath = "enchant-gui.page-change.${direction.name.lowercase(Locale.ROOT)}"
    private val emptySlot = slot(ItemStack(Material.AIR))

    override fun getSlotAt(row: Int, column: Int, player: Player, menu: Menu): Slot {
        val maxPage = getMaxPage(player, menu)
        val currentPage = menu.getPage(player).coerceAtLeast(1)

        if (!canChangePage(currentPage, maxPage)) {
            return emptySlot
        }

        val item = buildGuiItem(
            configPath,
            mapOf(
                "page" to currentPage.toString(),
                "max_page" to maxPage.toString()
            )
        )

        return slot(item) {
            onLeftClick { event, _ ->
                val clickedPlayer = event.whoClicked as Player
                val clickedMaxPage = getMaxPage(clickedPlayer, menu)
                val clickedPage = menu.getPage(clickedPlayer).coerceAtLeast(1)

                if (!canChangePage(clickedPage, clickedMaxPage)) {
                    return@onLeftClick
                }

                val nextPage = when (direction) {
                    GuiPageDirection.FORWARDS -> clickedPage + 1
                    GuiPageDirection.BACKWARDS -> clickedPage - 1
                }

                menu.setState(clickedPlayer, Page.PAGE_KEY, nextPage.coerceIn(1, clickedMaxPage))
                clickedPlayer.playPageChangeSound(configPath)
                clickedPlayer.sendLangMessage(
                    "changed-enchant-page",
                    "page" to nextPage.toString(),
                    "max_page" to clickedMaxPage.toString()
                )
            }
        }
    }

    override fun getRows() = 1
    override fun getColumns() = 1

    private fun canChangePage(currentPage: Int, maxPage: Int): Boolean {
        return when (direction) {
            GuiPageDirection.FORWARDS -> currentPage < maxPage
            GuiPageDirection.BACKWARDS -> currentPage > 1 && maxPage > 1
        }
    }

    private fun getMaxPage(player: Player, menu: Menu): Int {
        val enchants = menu.getState<List<EcoEnchant>>(player, "enchants") ?: emptyList()
        val perPage = plugin.configYml.getInt("enchant-gui.enchant-area.width") *
                plugin.configYml.getInt("enchant-gui.enchant-area.height")

        if (enchants.isEmpty() || perPage <= 0) {
            return 0
        }

        return ceil(enchants.size.toDouble() / perPage).toInt()
    }
}

private fun Collection<EcoEnchant>.sortForGui(): List<EcoEnchant> {
    val byEnchantment = this.associateBy { it.enchantment }
    return this.map { it.enchantment }.sortForDisplay()
        .mapNotNull { byEnchantment[it] }
}

private fun getConfiguredGuiTitle(configPath: String): String {
    return if (plugin.configYml.has("$configPath.title-key")) {
        plugin.langYml.getFormattedString(plugin.configYml.getString("$configPath.title-key"))
    } else {
        plugin.configYml.getFormattedString("$configPath.title")
    }
}

private fun buildGuiItem(
    configPath: String,
    placeholders: Map<String, String> = emptyMap(),
    loreKeyOverride: String? = null
): ItemStack {
    return buildGuiItem(plugin.configYml.getSubsection(configPath), placeholders, loreKeyOverride)
}

private fun buildGuiItem(
    config: Config,
    placeholders: Map<String, String> = emptyMap(),
    loreKeyOverride: String? = null
): ItemStack {
    val builder = ItemStackBuilder(
        Items.lookup(config.getString("item").replacePlaceholders(placeholders))
    )

    if (config.has("name-key")) {
        builder.setDisplayName(
            plugin.langYml.getFormattedString(config.getString("name-key"))
                .replacePlaceholders(placeholders)
        )
    } else if (config.has("name")) {
        builder.setDisplayName(config.getString("name").replacePlaceholders(placeholders).formatEco())
    }

    if (loreKeyOverride != null) {
        builder.addLoreLines(plugin.langYml.getStrings(loreKeyOverride).map {
            it.replacePlaceholders(placeholders)
        }.formatEco())
    } else if (config.has("lore-key")) {
        builder.addLoreLines(getConfiguredGuiLore(config, placeholders).formatEco())
    } else if (config.has("lore")) {
        builder.addLoreLines(getConfiguredGuiLore(config, placeholders).formatEco())
    }

    return builder.build()
}

private fun getConfiguredGuiLore(configPath: String, placeholders: Map<String, String> = emptyMap()): List<String> {
    return getConfiguredGuiLore(plugin.configYml.getSubsection(configPath), placeholders)
}

private fun getConfiguredGuiLore(config: Config, placeholders: Map<String, String> = emptyMap()): List<String> {
    val lore = if (config.has("lore-key")) {
        plugin.langYml.getStrings(config.getString("lore-key"))
    } else if (config.has("lore")) {
        config.getStrings("lore")
    } else {
        emptyList()
    }

    return lore.map { it.replacePlaceholders(placeholders) }
}

private fun String.replacePlaceholders(placeholders: Map<String, String>): String {
    var result = this
    for ((key, value) in placeholders) {
        result = result.replace("%$key%", value)
    }
    return result
}

private fun Player.sendLangMessage(key: String, vararg replacements: Pair<String, String>) {
    var message = plugin.langYml.getMessage(key, StringUtils.FormatOption.WITHOUT_PLACEHOLDERS)

    for ((placeholder, value) in replacements) {
        message = message.replace("%$placeholder%", value)
    }

    this.sendMessage(message)
}

private fun getGroupDisplayName(groupId: String): String {
    val groupBy = plugin.configYml.getString("enchant-gui.group-by")

    return getConfiguredGroupDisplayName(groupId) ?: when (groupBy) {
        "type" -> EnchantmentTypes[groupId]?.id?.toDisplayName()
        "rarity" -> EnchantmentRarities[groupId]?.displayName
        "target" -> EnchantmentTargets[groupId]?.displayName
        else -> null
    } ?: groupId
}

private fun getConfiguredGroupDisplayName(groupId: String): String? {
    val config = plugin.configYml.getSubsections("group-gui.groups")
        .firstOrNull { it.getString("id") == groupId }
        ?: return null

    return when {
        config.has("name-key") -> plugin.langYml.getFormattedString(config.getString("name-key"))
        config.has("name") -> config.getString("name").formatEco()
        else -> null
    }
}

private fun String.toDisplayName(): String {
    return this.split('_', '-')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString()
            }
        }
}

private fun Player.hasAdminToolsPermission(): Boolean {
    return this.hasPermission("ecoenchants.command.reload")
            || this.hasPermission("ecoenchants.command.giverandombook")
}

private fun Player.playPageChangeSound(configPath: String) {
    this.playConfiguredSound("$configPath.sound")
}

private fun Player.playConfiguredSound(soundPath: String) {
    if (!plugin.configYml.has(soundPath)) {
        return
    }

    val sound = plugin.configYml.getString(soundPath)
    if (sound.isBlank()) {
        return
    }

    val configPath = soundPath.removeSuffix(".sound")
    val volume = if (plugin.configYml.has("$configPath.sound-volume")) {
        plugin.configYml.getDouble("$configPath.sound-volume").toFloat()
    } else {
        1.0f
    }

    val pitch = if (plugin.configYml.has("$configPath.sound-pitch")) {
        plugin.configYml.getDouble("$configPath.sound-pitch").toFloat()
    } else {
        1.0f
    }

    this.playSound(this.location, sound, volume, pitch)
}

private fun EcoEnchant.getInformationSlot(player: Player, level: Int): Slot {
    return cachedEnchantmentSlots.get(this to level) {
        slot(
            EnchantedBookBuilder()
                .addStoredEnchantment(enchantment, level)
                .addItemFlag(ItemFlag.HIDE_ENCHANTS)
                .setDisplayName(this.getFormattedName(level))
                .addLoreLines(this.getFormattedDescription(level, player))
                .addLoreLines {
                    getConfiguredGuiLore(
                        "enchantinfo.item",
                        mapOf(
                            "max_level" to enchantment.maxLevel.toString(),
                            "rarity" to this.enchantmentRarity.displayName,
                            "targets" to this.targets.joinToString(", ") { target -> target.displayName },
                            "conflicts" to if (this.conflictsWithEverything) {
                                plugin.langYml.getFormattedString("all-conflicts")
                            } else {
                                this.conflicts.joinToString(", ") { conflict ->
                                    conflict.wrap().getFormattedName(0)
                                }.ifEmpty { plugin.langYml.getFormattedString("no-conflicts") }
                            },
                            "required" to this.required.joinToString(", ") { required ->
                                required.wrap().getFormattedName(0)
                            }.ifEmpty { plugin.langYml.getFormattedString("no-required") },
                            "tradeable" to this.isObtainableThroughTrading.parseYesOrNo(plugin.langYml),
                            "discoverable" to this.isObtainableThroughDiscovery.parseYesOrNo(plugin.langYml),
                            "enchantable" to this.isObtainableThroughEnchanting.parseYesOrNo(plugin.langYml)
                        )
                    )
                        .formatEco()
                        .flatMap {
                            it.lineWrap(32, true)
                        }
                }
                .build()
                .fast()
                .apply {
                    plugin.getProxy(HideStoredEnchantsProxy::class.java).hideStoredEnchants(this)
                }
                .unwrap()
        )
    }
}

fun Boolean.parseYesOrNo(langYml: LangYml): String {
    return if (this) langYml.getFormattedString("yes") else langYml.getFormattedString("no")
}
