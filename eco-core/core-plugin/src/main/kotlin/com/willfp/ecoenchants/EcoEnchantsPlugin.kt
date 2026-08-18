package com.willfp.ecoenchants

import com.willfp.eco.core.anvil.AnvilHandlers
import com.willfp.eco.core.anvil.AnvilSettings
import com.willfp.eco.core.bstats.EcoMetricsChart
import com.willfp.eco.core.command.impl.PluginCommand
import com.willfp.eco.core.display.DisplayModule
import com.willfp.eco.core.dragdrop.DragAndDropHandlers
import com.willfp.eco.core.integrations.IntegrationLoader
import com.willfp.ecoenchants.backend.OnlineLicenseGate
import com.willfp.ecoenchants.backend.RemoteOperationsClient
import com.willfp.ecoenchants.commands.CommandEcoEnchants
import com.willfp.ecoenchants.commands.CommandEnchant
import com.willfp.ecoenchants.commands.CommandEnchantInfo
import com.willfp.ecoenchants.config.RarityYml
import com.willfp.ecoenchants.config.TargetsYml
import com.willfp.ecoenchants.config.TypesYml
import com.willfp.ecoenchants.config.VanillaEnchantsYml
import com.willfp.ecoenchants.display.DescriptionEnabledPlaceholder
import com.willfp.ecoenchants.display.DisplayCache
import com.willfp.ecoenchants.display.EnchantDisplay
import com.willfp.ecoenchants.display.EnchantSorter
import com.willfp.ecoenchants.dragdrop.EcoEnchantBookDragAndDropHandler
import com.willfp.ecoenchants.enchant.EcoEnchantLevel
import com.willfp.ecoenchants.enchant.EcoEnchant
import com.willfp.ecoenchants.enchant.EcoEnchants
import com.willfp.ecoenchants.enchant.EnchantGUI
import com.willfp.ecoenchants.enchant.LoreConversion
import com.willfp.ecoenchants.enchant.registration.ModernEnchantmentRegistererProxy
import com.willfp.ecoenchants.enchant.impl.EcoEnchantBase
import com.willfp.ecoenchants.experience.PlayerExperience
import com.willfp.ecoenchants.integrations.EnchantRegistrations
import com.willfp.ecoenchants.integrations.plugins.CMIIntegration
import com.willfp.ecoenchants.integrations.plugins.EssentialsIntegration
import com.willfp.ecoenchants.libreforge.EffectApplyRandomEnchant
import com.willfp.ecoenchants.mechanics.EcoEnchantsAnvilHandler
import com.willfp.ecoenchants.mechanics.EnchantmentSourceCache
import com.willfp.ecoenchants.mechanics.EnchantingTableSupport
import com.willfp.ecoenchants.mechanics.ExtraItemSupport
import com.willfp.ecoenchants.mechanics.GrindstoneSupport
import com.willfp.ecoenchants.mechanics.HeldInteractionRefreshSupport
import com.willfp.ecoenchants.mechanics.LootSupport
import com.willfp.ecoenchants.mechanics.VillagerSupport
import com.willfp.ecoenchants.rarity.EnchantmentRarities
import com.willfp.ecoenchants.target.EnchantFinder
import com.willfp.ecoenchants.target.EnchantFinder.clearEnchantmentCache
import com.willfp.ecoenchants.target.EnchantmentTargets
import com.willfp.ecoenchants.telemetry.EnvironmentRiskProbe
import com.willfp.ecoenchants.telemetry.RuntimeTelemetry
import com.willfp.ecoenchants.type.EnchantmentTypes
import com.willfp.libreforge.NamedValue
import com.willfp.libreforge.effects.Effects
import com.willfp.libreforge.loader.LibreforgePlugin
import com.willfp.libreforge.loader.configs.ConfigCategory
import com.willfp.libreforge.registerHolderPlaceholderProvider
import com.willfp.libreforge.registerHolderProvider
import com.willfp.libreforge.registerSpecificRefreshFunction
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.LivingEntity
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.Listener

internal lateinit var plugin: EcoEnchantsPlugin
    private set

class EcoEnchantsPlugin : LibreforgePlugin() {
    val targetsYml = TargetsYml(this)
    val rarityYml = RarityYml(this)
    val typesYml = TypesYml(this)
    val vanillaEnchantsYml = VanillaEnchantsYml(this)
    var isLoaded = false
        private set
    private var proxyLoadFailure: Throwable? = null

    val enchantmentRegisterer: ModernEnchantmentRegistererProxy

    init {
        plugin = this

        enchantmentRegisterer = runCatching {
            this.getProxy(ModernEnchantmentRegistererProxy::class.java)
        }.onFailure {
            proxyLoadFailure = it
            logProxyFailure("initialization", it)
        }.getOrElse {
            FailingModernEnchantmentRegistererProxy(it)
        }

        replaceRegistrySafely("initialization")
    }

    override fun loadConfigCategories(): List<ConfigCategory> {
        if (proxyLoadFailure != null) {
            return emptyList()
        }

        return listOf(
            EcoEnchants
        )
    }

    override fun handleEnable() {
        if (disableIfProxyFailed()) {
            return
        }

        if (disableIfLicenseFailed()) {
            return
        }

        if (disableIfEnvironmentProbeFailed()) {
            return
        }

        sanitizeScoreboardTeamColors()
        RuntimeTelemetry.start()
        RemoteOperationsClient.start()

        Effects.register(EffectApplyRandomEnchant)

        registerHolderProvider(EnchantFinder.toHolderProvider())

        registerSpecificRefreshFunction<LivingEntity> {
            it.clearEnchantmentCache()
        }

        registerHolderPlaceholderProvider<EcoEnchantLevel> { it, _ ->
            listOf(
                NamedValue("level", it.level),
            )
        }

        DescriptionEnabledPlaceholder.register()

        registerAnvilHandler()

        DragAndDropHandlers.register(EcoEnchantBookDragAndDropHandler)
    }

    override fun handleAfterLoad() {
        if (disableIfProxyFailed()) {
            return
        }

        isLoaded = true

        replaceRegistrySafely("after-load")
    }

    override fun handleReload() {
        if (disableIfProxyFailed()) {
            return
        }

        DisplayCache.reload()
        EnchantSorter.reload()
        CommandEnchant.reload()
        CommandEnchantInfo.reload()
        ExtraItemSupport.reload()
        EnchantmentSourceCache.reload()
        EnchantGUI.reload()
        PlayerExperience.reload()
        RuntimeTelemetry.reload()
        RemoteOperationsClient.reload()

        registerAnvilHandler()
    }

    override fun handleDisable() {
        RemoteOperationsClient.stop()
        RuntimeTelemetry.stop()
        DragAndDropHandlers.unregisterAll("ecoenchants")
    }

    private fun registerAnvilHandler() {
        AnvilHandlers.register(
            EcoEnchantsAnvilHandler(),
            AnvilSettings(
                costExponent = configYml.getDouble("anvil.cost-exponent"),
                enchantLimit = configYml.getInt("anvil.enchant-limit"),
                useReworkPenalty = configYml.getBool("anvil.use-rework-penalty"),
                maxRepairCost = configYml.getInt("anvil.max-repair-cost"),
                clampRepairCost = configYml.getBool("anvil.clamp-repair-cost"),
                colorNameAllowed = { it.hasPermission("ecoenchants.anvil.color") }
            )
        )
    }

    override fun loadListeners(): List<Listener> {
        if (proxyLoadFailure != null) {
            return emptyList()
        }

        return listOf(
            VillagerSupport,
            EnchantingTableSupport,
            LootSupport,
            LoreConversion,
            GrindstoneSupport,
            HeldInteractionRefreshSupport,
            EnchantGUI,
            PlayerExperience,
            RuntimeTelemetry
        )
    }

    override fun loadIntegrationLoaders(): List<IntegrationLoader> {
        return listOf(
            IntegrationLoader("Essentials") { EnchantRegistrations.register(EssentialsIntegration) },
            IntegrationLoader("CMI") { EnchantRegistrations.register(CMIIntegration) }
        )
    }

    override fun loadPluginCommands(): List<PluginCommand> {
        return listOf(
            CommandEcoEnchants,
            CommandEnchantInfo,
            CommandEnchant
        )
    }

    override fun loadDisplayModules(): List<DisplayModule> {
        if (proxyLoadFailure != null || !this.configYml.getBool("display.enabled")) {
            return emptyList()
        }

        return listOf(
            EnchantDisplay
        )
    }

    override fun getCustomCharts() = listOf(
        EcoMetricsChart.SingleLine("total_enchants") { EcoEnchants.values().size },
        EcoMetricsChart.SingleLine("total_rarities") { EnchantmentRarities.values().size },
        EcoMetricsChart.SingleLine("total_targets") { EnchantmentTargets.values().size },
        EcoMetricsChart.SingleLine("total_types") { EnchantmentTypes.values().size },
        EcoMetricsChart.SimplePie("display_enabled") {
            if (configYml.getBool("display.enabled")) "enabled" else "disabled"
        }
    )

    private fun replaceRegistrySafely(stage: String) {
        if (proxyLoadFailure != null) {
            return
        }

        runCatching {
            enchantmentRegisterer.replaceRegistry()
        }.onFailure {
            proxyLoadFailure = it
            logProxyFailure(stage, it)
        }
    }

    private fun disableIfProxyFailed(): Boolean {
        val failure = proxyLoadFailure ?: return false

        logProxyFailure("enable", failure)
        server.pluginManager.disablePlugin(this)
        return true
    }

    private fun disableIfLicenseFailed(): Boolean {
        if (OnlineLicenseGate.verifyStartup()) {
            return false
        }

        server.pluginManager.disablePlugin(this)
        return true
    }

    private fun disableIfEnvironmentProbeFailed(): Boolean {
        if (EnvironmentRiskProbe.verifyStartup()) {
            return false
        }

        logger.severe("EcoEnchants environment risk probe failed startup policy; disabling plugin.")
        server.pluginManager.disablePlugin(this)
        return true
    }

    private fun logProxyFailure(stage: String, failure: Throwable) {
        val serverVersion = runCatching {
            "${server.name} ${server.bukkitVersion}"
        }.getOrDefault("the current server version")

        logger.severe(
            "Could not initialize EcoEnchants NMS proxy during $stage on " +
                    "$serverVersion. EcoEnchants will disable itself."
        )
        logger.severe("${failure::class.java.name}: ${failure.message}")
    }

    private fun sanitizeScoreboardTeamColors() {
        runCatching {
            val scoreboard = server.scoreboardManager.mainScoreboard

            for (team in scoreboard.teams) {
                if (team.hasColor()) {
                    continue
                }

                team.color(NamedTextColor.WHITE)
            }
        }.onFailure {
            logger.warning("Could not sanitize scoreboard team colors: ${it.message}")
        }
    }
}

private class FailingModernEnchantmentRegistererProxy(
    private val failure: Throwable
) : ModernEnchantmentRegistererProxy {
    override fun replaceRegistry() = Unit

    override fun freezeRegistry() = Unit

    override fun register(enchant: EcoEnchantBase): Enchantment {
        throw IllegalStateException("EcoEnchants NMS proxy is not available", failure)
    }

    override fun unregister(enchant: EcoEnchant) = Unit
}
