package com.willfp.ecoenchants.enchant

import com.github.benmanes.caffeine.cache.Caffeine
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
import com.willfp.ecoenchants.experience.PlayerExperience
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
                val compatibleOnly = menu.getState<Boolean>(player, "compatibleOnly")
                    ?: plugin.configYml.getBool("enchant-gui.filters.compatible-only.default-enabled")

                val baseEnchants = if (hasItem && compatibleOnly) {
                    val currentEnchants = atCaptive.fast().enchants.keys
                    applicableEnchantmentsSorted.get(HashedItem.of(atCaptive)) {
                        atCaptive.applicableEnchantments.sortForGui()
                    }.filterNot { it.enchantment in currentEnchants }
                } else {
                    allEnchantsSorted
                }

                // Apply group filter if a groupId is set in menu state
                val groupId = menu.getState<String>(player, "groupId")
                val filteredEnchants = baseEnchants
                    .filter { it.matchesGroupFilter(groupId) }
                    .filter { it.matchesCycleFilters(player, menu) }

                // Reset to page 1 when an item is placed or removed from the captive slot
                val previousHasItem = menu.getState<Boolean>(player, "hasItem") ?: false
                if (hasItem != previousHasItem) {
                    menu.setState(player, Page.PAGE_KEY, 1)
                }
                menu.setState(player, "enchants", filteredEnchants)
                menu.setState(player, "hasItem", hasItem)
                menu.setState(player, "compatibleOnly", compatibleOnly)

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

                if (filteredEnchants.isEmpty()) {
                    PlayerExperience.handleEmptyResults(
                        player,
                        getEmptyResultReason(hasItem, groupId, menu.hasCycleFilters(player), compatibleOnly)
                    )
                } else {
                    PlayerExperience.clearEmptyResults(player)
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

            for (axis in GuiFilterAxis.entries) {
                addComponent(
                    MenuLayer.TOP,
                    plugin.configYml.getInt("${axis.configPath}.row"),
                    plugin.configYml.getInt("${axis.configPath}.column"),
                    FilterCycleButton(axis)
                )
            }

            addComponent(
                MenuLayer.TOP,
                plugin.configYml.getInt("enchant-gui.filters.compatible-only.row"),
                plugin.configYml.getInt("enchant-gui.filters.compatible-only.column"),
                CompatibleOnlyButton()
            )

            addComponent(
                MenuLayer.TOP,
                plugin.configYml.getInt("enchant-gui.conflict-view.row"),
                plugin.configYml.getInt("enchant-gui.conflict-view.column"),
                ConflictViewButton()
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

                if (plugin.configYml.getBool("group-gui.all-enchants.enabled")) {
                    setSlot(
                        plugin.configYml.getInt("group-gui.all-enchants.row"),
                        plugin.configYml.getInt("group-gui.all-enchants.column"),
                        slot(buildGuiItem("group-gui.all-enchants")) {
                            onLeftClick { event, _ ->
                                openAllEnchantsGUI(event.whoClicked as Player)
                            }
                        }
                    )
                }

                if (plugin.configYml.getBool("group-gui.close-button.enabled")) {
                    setSlot(
                        plugin.configYml.getInt("group-gui.close-button.row"),
                        plugin.configYml.getInt("group-gui.close-button.column"),
                        slot(buildGuiItem("group-gui.close-button")) {
                            onLeftClick { event, _ -> event.whoClicked.closeInventory() }
                        }
                    )
                }

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
        PlayerExperience.handleBrowserOpen(player)

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

    private fun openAllEnchantsGUI(player: Player) {
        menu.clearState(player)
        menu.setState(player, Page.PAGE_KEY, 1)
        menu.open(player)
        player.sendLangMessage("opened-enchant-group", "group" to plugin.langYml.getFormattedString("all"))
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

    private class FilterCycleButton(
        private val axis: GuiFilterAxis
    ) : GUIComponent {
        private val emptySlot = slot(ItemStack(Material.AIR))

        override fun getSlotAt(row: Int, column: Int, player: Player, menu: Menu): Slot {
            if (!plugin.configYml.getBool("${axis.configPath}.enabled")) {
                return emptySlot
            }

            return slot(
                buildGuiItem(
                    axis.configPath,
                    mapOf("current" to axis.currentDisplay(player, menu))
                )
            ) {
                onLeftClick { event, _ ->
                    val clickedPlayer = event.whoClicked as Player
                    val next = axis.nextValue(clickedPlayer, menu)

                    if (next == null) {
                        menu.setState(clickedPlayer, axis.stateKey, "")
                    } else {
                        menu.setState(clickedPlayer, axis.stateKey, next.id)
                    }

                    menu.setState(clickedPlayer, Page.PAGE_KEY, 1)
                    clickedPlayer.playConfiguredSound("${axis.configPath}.sound")
                    PlayerExperience.handleFilterChanged(
                        clickedPlayer,
                        axis.label,
                        next?.displayName ?: plugin.langYml.getFormattedString("all")
                    )
                    menu.open(clickedPlayer)
                }
            }
        }

        override fun getRows() = 1
        override fun getColumns() = 1
    }

    private class CompatibleOnlyButton : GUIComponent {
        private val emptySlot = slot(ItemStack(Material.AIR))
        private val configPath = "enchant-gui.filters.compatible-only"

        override fun getSlotAt(row: Int, column: Int, player: Player, menu: Menu): Slot {
            if (!plugin.configYml.getBool("$configPath.enabled")) {
                return emptySlot
            }

            val enabled = menu.getState<Boolean>(player, "compatibleOnly")
                ?: plugin.configYml.getBool("$configPath.default-enabled")

            return slot(
                buildGuiItem(
                    configPath,
                    mapOf("state" to enabled.parseEnabledDisabled())
                )
            ) {
                onLeftClick { event, _ ->
                    val clickedPlayer = event.whoClicked as Player
                    val current = menu.getState<Boolean>(clickedPlayer, "compatibleOnly")
                        ?: plugin.configYml.getBool("$configPath.default-enabled")

                    menu.setState(clickedPlayer, "compatibleOnly", !current)
                    menu.setState(clickedPlayer, Page.PAGE_KEY, 1)
                    clickedPlayer.playConfiguredSound("$configPath.sound")
                    PlayerExperience.handleFilterChanged(
                        clickedPlayer,
                        "compatible-only",
                        (!current).parseEnabledDisabled()
                    )
                    menu.open(clickedPlayer)
                }
            }
        }

        override fun getRows() = 1
        override fun getColumns() = 1
    }

    private class ConflictViewButton : GUIComponent {
        private val emptySlot = slot(ItemStack(Material.AIR))
        private val configPath = "enchant-gui.conflict-view"

        override fun getSlotAt(row: Int, column: Int, player: Player, menu: Menu): Slot {
            if (!plugin.configYml.getBool("$configPath.enabled")) {
                return emptySlot
            }

            return slot(buildGuiItem(configPath)) {
                onLeftClick { event, _ ->
                    val clickedPlayer = event.whoClicked as Player
                    val captiveRow = plugin.configYml.getInt("enchant-gui.item-row")
                    val captiveColumn = plugin.configYml.getInt("enchant-gui.item-column")
                    val item = menu.getCaptiveItem(clickedPlayer, captiveRow, captiveColumn)

                    if (item.isEcoEmpty || item == null || item.type == Material.AIR) {
                        clickedPlayer.playConfiguredSound("player-experience.sounds.invalid-click.sound")
                        PlayerExperience.sendLangLines(clickedPlayer, "hints.conflict-view.no-item")
                        return@onLeftClick
                    }

                    val current = item.fast().getEnchants(true).keys
                    if (current.isEmpty()) {
                        clickedPlayer.playConfiguredSound("player-experience.sounds.invalid-click.sound")
                        PlayerExperience.sendLangLines(clickedPlayer, "hints.conflict-view.no-enchants")
                        return@onLeftClick
                    }

                    val conflicts = EcoEnchants.values()
                        .filter { enchantment ->
                            current.any { it.conflictsWithDeep(enchantment.enchantment) }
                        }
                        .map { enchantment ->
                            val level = if (plugin.configYml.getBool("enchantinfo.item.show-max-level")) {
                                enchantment.maximumLevel
                            } else {
                                1
                            }
                            enchantment.getFormattedName(level)
                        }
                        .distinct()
                        .take(plugin.configYml.getInt("$configPath.max-lines").coerceAtLeast(1))

                    clickedPlayer.playConfiguredSound("$configPath.sound")
                    if (conflicts.isEmpty()) {
                        PlayerExperience.sendLangLines(clickedPlayer, "hints.conflict-view.none")
                    } else {
                        PlayerExperience.sendLangLines(clickedPlayer, "hints.conflict-view.header")
                        for (conflict in conflicts) {
                            PlayerExperience.sendLangLines(clickedPlayer, "hints.conflict-view.line", "enchant" to conflict)
                        }
                    }
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

private enum class GuiFilterAxis(
    val label: String,
    val stateKey: String,
    val configPath: String
) {
    TYPE("type", "filterType", "enchant-gui.filters.type"),
    RARITY("rarity", "filterRarity", "enchant-gui.filters.rarity"),
    TARGET("target", "filterTarget", "enchant-gui.filters.target");

    fun options(): List<GuiFilterValue> {
        return when (this) {
            TYPE -> EnchantmentTypes.values()
                .map { GuiFilterValue(it.id, it.id.toDisplayName()) }
            RARITY -> EnchantmentRarities.values()
                .map { GuiFilterValue(it.id, it.displayName) }
            TARGET -> EnchantmentTargets.values()
                .filterNot { it.id.equals("all", ignoreCase = true) }
                .map { GuiFilterValue(it.id, it.displayName) }
        }.sortedBy { it.displayName.stripLegacyFormattingForSort() }
    }

    fun currentValue(player: Player, menu: Menu): GuiFilterValue? {
        val currentId = menu.getState<String>(player, stateKey).orEmpty()
        if (currentId.isBlank()) {
            return null
        }

        return options().firstOrNull { it.id.equals(currentId, ignoreCase = true) }
    }

    fun currentDisplay(player: Player, menu: Menu): String {
        return currentValue(player, menu)?.displayName ?: plugin.langYml.getFormattedString("all")
    }

    fun nextValue(player: Player, menu: Menu): GuiFilterValue? {
        val availableOptions = options()
        if (availableOptions.isEmpty()) {
            return null
        }

        val current = currentValue(player, menu) ?: return availableOptions.first()
        val currentIndex = availableOptions.indexOfFirst { it.id == current.id }
        val nextIndex = currentIndex + 1

        return if (currentIndex == -1 || nextIndex >= availableOptions.size) {
            null
        } else {
            availableOptions[nextIndex]
        }
    }
}

private data class GuiFilterValue(
    val id: String,
    val displayName: String
)

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

private fun EcoEnchant.matchesGroupFilter(groupId: String?): Boolean {
    if (groupId.isNullOrBlank()) {
        return true
    }

    return when (plugin.configYml.getString("enchant-gui.group-by")) {
        "type" -> this.type.id == groupId
        "rarity" -> this.enchantmentRarity.id == groupId
        "target" -> this.targets.any { it.id == groupId }
        else -> true
    }
}

private fun EcoEnchant.matchesCycleFilters(player: Player, menu: Menu): Boolean {
    val typeId = menu.getState<String>(player, GuiFilterAxis.TYPE.stateKey).orEmpty()
    val rarityId = menu.getState<String>(player, GuiFilterAxis.RARITY.stateKey).orEmpty()
    val targetId = menu.getState<String>(player, GuiFilterAxis.TARGET.stateKey).orEmpty()

    if (typeId.isNotBlank() && this.type.id != typeId) {
        return false
    }

    if (rarityId.isNotBlank() && this.enchantmentRarity.id != rarityId) {
        return false
    }

    if (targetId.isNotBlank() && this.targets.none { it.id == targetId }) {
        return false
    }

    return true
}

private fun Menu.hasCycleFilters(player: Player): Boolean {
    return GuiFilterAxis.entries.any {
        this.getState<String>(player, it.stateKey).orEmpty().isNotBlank()
    }
}

private fun getEmptyResultReason(
    hasItem: Boolean,
    groupId: String?,
    hasCycleFilters: Boolean,
    compatibleOnly: Boolean
): String {
    return when {
        hasItem && groupId != null -> "item-and-group"
        hasItem && hasCycleFilters -> "item-and-filter"
        hasItem && compatibleOnly -> "item-compatible"
        groupId != null || hasCycleFilters -> "filter"
        else -> "none-loaded"
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

private fun String.stripLegacyFormattingForSort(): String {
    return this.replace(Regex("&[0-9a-fk-orA-FK-OR]"), "")
        .replace(Regex("<[^>]+>"), "")
}

private fun Boolean.parseEnabledDisabled(): String {
    return if (this) {
        plugin.langYml.getFormattedString("enabled")
    } else {
        plugin.langYml.getFormattedString("disabled")
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
                            "tradeable" to this.isObtainableThroughTrading.parseYesOrNo(),
                            "discoverable" to this.isObtainableThroughDiscovery.parseYesOrNo(),
                            "discoverable_chests" to this.isObtainableThrough(DiscoveryType.CHESTS).parseYesOrNo(),
                            "discoverable_fishing" to this.isObtainableThrough(DiscoveryType.FISHING).parseYesOrNo(),
                            "discoverable_mob_drops" to this.isObtainableThrough(DiscoveryType.MOB_DROPS).parseYesOrNo(),
                            "discoverable_raids" to this.isObtainableThrough(DiscoveryType.RAIDS).parseYesOrNo(),
                            "enchantable" to this.isObtainableThroughEnchanting.parseYesOrNo()
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

fun Boolean.parseYesOrNo(): String =
    if (this) plugin.langYml.getFormattedString("yes") else plugin.langYml.getFormattedString("no")
