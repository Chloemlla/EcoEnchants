package com.willfp.ecoenchants.telemetry

import com.willfp.ecoenchants.plugin
import io.papermc.paper.event.player.AsyncChatEvent
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.enchantment.EnchantItemEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerExpChangeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerLevelChangeEvent
import org.bukkit.event.player.PlayerLoginEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.player.PlayerToggleFlightEvent
import org.bukkit.inventory.ItemStack
import kotlin.math.pow
import kotlin.math.sqrt

object RuntimeTelemetry : Listener {
    private val routeVectors = ConcurrentHashMap<UUID, RouteVector>()
    private val movementSamples = ConcurrentHashMap<UUID, MovementSample>()
    private val inventoryHashes = ConcurrentHashMap<UUID, String>()

    fun start() {
        TelemetryReporter.start()
        TelemetryAuditLog.start()
        EnvironmentRiskProbe.start()
    }

    fun reload() {
        routeVectors.clear()
        movementSamples.clear()
        inventoryHashes.clear()
        TelemetryAuditLog.write("telemetry_lifecycle", mapOf("state" to "reloaded"))
        TelemetryReporter.reload()
        EnvironmentRiskProbe.start()
    }

    fun stop() {
        EnvironmentRiskProbe.stop()
        routeVectors.clear()
        movementSamples.clear()
        inventoryHashes.clear()
        TelemetryAuditLog.stop()
        TelemetryReporter.stop()
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun handleLogin(event: PlayerLoginEvent) {
        if (!RuntimeTelemetryPolicy.enabled || !RuntimeTelemetryPolicy.identityEnabled) {
            return
        }

        val route = event.player.toRouteVector(event.address, event.realAddress, event.hostname)
        routeVectors[event.player.uniqueId] = route

        TelemetryAuditLog.write(
            "identity_anchor",
            mapOf(
                "uuid" to event.player.uniqueId.toString(),
                "name" to event.player.name,
                "onlineMode" to plugin.server.onlineMode,
                "network" to route.toLogMap()
            )
        )
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun handleJoin(event: PlayerJoinEvent) {
        if (!RuntimeTelemetryPolicy.enabled) {
            return
        }

        movementSamples[event.player.uniqueId] = event.player.location.toMovementSample()
        seedInventoryBaseline(event.player, "join")

        plugin.scheduler.runLater(20L) {
            if (!event.player.isOnline || !RuntimeTelemetryPolicy.identityEnabled) {
                return@runLater
            }

            TelemetryAuditLog.write(
                "client_context",
                mapOf(
                    "uuid" to event.player.uniqueId.toString(),
                    "protocol" to runCatching { event.player.protocolVersion }.getOrNull(),
                    "clientBrand" to runCatching { event.player.clientBrandName }.getOrNull(),
                    "locale" to runCatching { event.player.locale().toLanguageTag() }.getOrNull(),
                    "viewDistance" to runCatching { event.player.clientViewDistance }.getOrNull(),
                    "ping" to runCatching { event.player.ping }.getOrNull()
                )
            )
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun handleQuit(event: PlayerQuitEvent) {
        if (RuntimeTelemetryPolicy.enabled) {
            TelemetryAuditLog.write(
                "session_end",
                mapOf("uuid" to event.player.uniqueId.toString())
            )
        }

        routeVectors.remove(event.player.uniqueId)
        movementSamples.remove(event.player.uniqueId)
        inventoryHashes.remove(event.player.uniqueId)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun handleMove(event: org.bukkit.event.player.PlayerMoveEvent) {
        if (!RuntimeTelemetryPolicy.enabled || !RuntimeTelemetryPolicy.movementEnabled) {
            return
        }

        if (!event.hasExplicitlyChangedPosition()) {
            return
        }

        val current = event.to.toMovementSample()
        val previous = movementSamples[event.player.uniqueId]
        val now = current.timestampMillis

        if (previous == null) {
            movementSamples[event.player.uniqueId] = current
            return
        }

        if (now - previous.timestampMillis < RuntimeTelemetryPolicy.movementSampleIntervalMillis) {
            return
        }

        movementSamples[event.player.uniqueId] = current

        val distance = previous.distanceTo(current)
        val elapsedSeconds = ((now - previous.timestampMillis) / 1000.0).coerceAtLeast(0.001)
        val velocity = distance / elapsedSeconds
        val changedWorld = previous.worldId != current.worldId

        if (changedWorld || RuntimeTelemetryPolicy.logMovementSamples) {
            TelemetryAuditLog.write(
                "trajectory_sample",
                event.player.trajectoryPayload(previous, current, distance, velocity, changedWorld)
            )
        }

        if (!changedWorld &&
            (distance > RuntimeTelemetryPolicy.maxDistancePerMovementSample ||
                    velocity > RuntimeTelemetryPolicy.maxBlocksPerSecond)
        ) {
            TelemetryAuditLog.write(
                "trajectory_anomaly",
                event.player.trajectoryPayload(previous, current, distance, velocity, false)
            )
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun handleTeleport(event: PlayerTeleportEvent) {
        if (!RuntimeTelemetryPolicy.enabled || !RuntimeTelemetryPolicy.movementEnabled) {
            return
        }

        movementSamples[event.player.uniqueId] = event.to.toMovementSample()
        TelemetryAuditLog.write(
            "trajectory_transition",
            mapOf(
                "uuid" to event.player.uniqueId.toString(),
                "cause" to event.cause.name,
                "from" to event.from.toWorldContext(),
                "to" to event.to.toWorldContext()
            )
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun handleFlightToggle(event: PlayerToggleFlightEvent) {
        TelemetryAuditLog.write(
            "state_transition",
            mapOf(
                "uuid" to event.player.uniqueId.toString(),
                "state" to "flight-toggle",
                "flying" to event.isFlying
            )
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun handleInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        scheduleInventoryDelta(
            player,
            mapOf(
                "event" to "inventory-click",
                "inventoryType" to event.view.type.name,
                "slot" to event.slot,
                "rawSlot" to event.rawSlot,
                "action" to event.action.name,
                "click" to event.click.name
            )
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun handleInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        scheduleInventoryDelta(
            player,
            mapOf(
                "event" to "inventory-drag",
                "inventoryType" to event.view.type.name,
                "dragType" to event.type.name,
                "slots" to event.rawSlots.sorted()
            )
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun handleDrop(event: PlayerDropItemEvent) {
        scheduleInventoryDelta(
            event.player,
            mapOf(
                "event" to "item-drop",
                "itemType" to event.itemDrop.itemStack.type.name,
                "amount" to event.itemDrop.itemStack.amount
            )
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun handlePickup(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        scheduleInventoryDelta(
            player,
            mapOf(
                "event" to "item-pickup",
                "itemType" to event.item.itemStack.type.name,
                "amount" to event.item.itemStack.amount
            )
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun handleEnchant(event: EnchantItemEvent) {
        val player = event.enchanter
        TelemetryAuditLog.write(
            "economy_delta",
            mapOf(
                "uuid" to player.uniqueId.toString(),
                "source" to "enchanting-table",
                "levelCost" to event.expLevelCost,
                "button" to event.whichButton(),
                "itemType" to event.item.type.name,
                "enchantsAdded" to event.enchantsToAdd.mapKeys { it.key.key.toString() }
            )
        )
        scheduleInventoryDelta(player, mapOf("event" to "enchanting-table"))
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun handleExpChange(event: PlayerExpChangeEvent) {
        if (!RuntimeTelemetryPolicy.enabled || !RuntimeTelemetryPolicy.stateDeltaEnabled) {
            return
        }

        TelemetryAuditLog.write(
            "economy_delta",
            mapOf(
                "uuid" to event.player.uniqueId.toString(),
                "source" to "experience",
                "amount" to event.amount
            )
        )
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun handleLevelChange(event: PlayerLevelChangeEvent) {
        if (!RuntimeTelemetryPolicy.enabled || !RuntimeTelemetryPolicy.stateDeltaEnabled) {
            return
        }

        TelemetryAuditLog.write(
            "economy_delta",
            mapOf(
                "uuid" to event.player.uniqueId.toString(),
                "source" to "level",
                "oldLevel" to event.oldLevel,
                "newLevel" to event.newLevel
            )
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun handleChat(event: AsyncChatEvent) {
        val text = PlainTextComponentSerializer.plainText().serialize(event.message())
        analyzeText(event.player, "chat", text)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun handleCommand(event: PlayerCommandPreprocessEvent) {
        analyzeText(event.player, "command", event.message)
    }

    private fun seedInventoryBaseline(player: Player, context: String) {
        if (!RuntimeTelemetryPolicy.stateDeltaEnabled) {
            return
        }

        val signature = player.inventorySignature()
        inventoryHashes[player.uniqueId] = signature
        TelemetryAuditLog.write(
            "state_baseline",
            mapOf(
                "uuid" to player.uniqueId.toString(),
                "context" to context,
                "inventoryHash" to signature,
                "summary" to player.inventorySummaryIfEnabled()
            )
        )
    }

    private fun scheduleInventoryDelta(player: Player, context: Map<String, Any?>) {
        if (!RuntimeTelemetryPolicy.enabled || !RuntimeTelemetryPolicy.stateDeltaEnabled) {
            return
        }

        plugin.scheduler.run {
            if (!player.isOnline) {
                return@run
            }

            val current = player.inventorySignature()
            val previous = inventoryHashes.put(player.uniqueId, current)

            if (previous == null) {
                TelemetryAuditLog.write(
                    "state_baseline",
                    mapOf(
                        "uuid" to player.uniqueId.toString(),
                        "context" to context,
                        "inventoryHash" to current,
                        "summary" to player.inventorySummaryIfEnabled()
                    )
                )
                return@run
            }

            if (previous == current) {
                return@run
            }

            TelemetryAuditLog.write(
                "state_delta",
                mapOf(
                    "uuid" to player.uniqueId.toString(),
                    "previousInventoryHash" to previous,
                    "currentInventoryHash" to current,
                    "context" to context,
                    "level" to player.level,
                    "totalExperience" to player.totalExperience,
                    "summary" to player.inventorySummaryIfEnabled()
                )
            )
        }
    }

    private fun analyzeText(player: Player, source: String, text: String) {
        if (!RuntimeTelemetryPolicy.enabled || !RuntimeTelemetryPolicy.textTelemetryEnabled) {
            return
        }

        val normalized = text.lowercase()
        val matchedTerms = RuntimeTelemetryPolicy.textRiskTerms
            .filter { it.isNotBlank() && normalized.contains(it.lowercase()) }
            .distinct()

        if (matchedTerms.isEmpty() && !RuntimeTelemetryPolicy.logAllTextMetadata) {
            return
        }

        val payload = linkedMapOf<String, Any?>(
            "uuid" to player.uniqueId.toString(),
            "source" to source,
            "length" to text.length,
            "textHash" to TelemetryAuditLog.hash(text),
            "risk" to matchedTerms.isNotEmpty()
        )

        if (RuntimeTelemetryPolicy.logCommandRoot && source == "command") {
            payload["commandRoot"] = text.trim().substringBefore(' ').lowercase()
        }

        if (RuntimeTelemetryPolicy.logMatchedTextTerms) {
            payload["matchedTerms"] = matchedTerms
        }

        if (RuntimeTelemetryPolicy.captureRawText) {
            payload["rawText"] = text
        }

        TelemetryAuditLog.write("behavioral_text", payload)
    }

    private fun Player.toRouteVector(
        address: InetAddress?,
        realAddress: InetAddress?,
        hostname: String
    ): RouteVector {
        val socketAddress = runCatching { this.address }.getOrNull()
        val virtualHost = runCatching { this.virtualHost }.getOrNull()
        val protocol = runCatching { this.protocolVersion }.getOrNull()

        return RouteVector(
            address = address?.hostAddress,
            realAddress = realAddress?.hostAddress,
            socketAddress = socketAddress.toAddressString(),
            hostname = hostname,
            virtualHost = virtualHost.toAddressString(),
            protocolVersion = protocol
        )
    }

    private fun RouteVector.toLogMap(): Map<String, Any?> {
        val rawAddressFields = if (RuntimeTelemetryPolicy.includeRawNetworkAddresses) {
            mapOf(
                "address" to address,
                "realAddress" to realAddress,
                "socketAddress" to socketAddress,
                "hostname" to hostname,
                "virtualHost" to virtualHost
            )
        } else {
            emptyMap()
        }

        return rawAddressFields + mapOf(
            "addressHash" to TelemetryAuditLog.hash(address),
            "realAddressHash" to TelemetryAuditLog.hash(realAddress),
            "socketAddressHash" to TelemetryAuditLog.hash(socketAddress),
            "hostnameHash" to TelemetryAuditLog.hash(hostname),
            "virtualHostHash" to TelemetryAuditLog.hash(virtualHost),
            "protocolVersion" to protocolVersion,
            "routeHash" to TelemetryAuditLog.hash("$address|$realAddress|$socketAddress|$hostname|$virtualHost|$protocolVersion"),
            "proxyRoute" to (address != null && realAddress != null && address != realAddress)
        )
    }

    private fun Player.trajectoryPayload(
        previous: MovementSample,
        current: MovementSample,
        distance: Double,
        velocity: Double,
        changedWorld: Boolean
    ): Map<String, Any?> = mapOf(
        "uuid" to uniqueId.toString(),
        "from" to previous.toLogMap(),
        "to" to current.toLogMap(),
        "distance" to distance.roundTelemetry(),
        "blocksPerSecond" to velocity.roundTelemetry(),
        "changedWorld" to changedWorld,
        "gameMode" to gameMode.name,
        "flying" to isFlying,
        "allowFlight" to allowFlight,
        "gliding" to isGliding,
        "insideVehicle" to isInsideVehicle,
        "worldContext" to current.worldId
    )

    private fun Location.toMovementSample(): MovementSample = MovementSample(
        worldId = this.world?.uid?.toString() ?: "unknown",
        worldNameHash = TelemetryAuditLog.hash(this.world?.name),
        x = this.x,
        y = this.y,
        z = this.z,
        yaw = this.yaw,
        pitch = this.pitch,
        timestampMillis = System.currentTimeMillis()
    )

    private fun MovementSample.distanceTo(other: MovementSample): Double {
        if (worldId != other.worldId) {
            return Double.POSITIVE_INFINITY
        }

        return sqrt((x - other.x).pow(2) + (y - other.y).pow(2) + (z - other.z).pow(2))
    }

    private fun MovementSample.toLogMap(): Map<String, Any?> = mapOf(
        "world" to worldId,
        "worldNameHash" to worldNameHash,
        "x" to x.roundTelemetry(),
        "y" to y.roundTelemetry(),
        "z" to z.roundTelemetry(),
        "yaw" to yaw.toDouble().roundTelemetry(),
        "pitch" to pitch.toDouble().roundTelemetry(),
        "timestampMillis" to timestampMillis
    )

    private fun Location.toWorldContext(): Map<String, Any?> = mapOf(
        "world" to (world?.uid?.toString() ?: "unknown"),
        "worldNameHash" to TelemetryAuditLog.hash(world?.name),
        "x" to x.roundTelemetry(),
        "y" to y.roundTelemetry(),
        "z" to z.roundTelemetry()
    )

    private fun Player.inventorySignature(): String {
        val inventory = this.inventory
        val parts = buildList {
            addAll(inventory.contents.toFingerprints("contents"))
            addAll(inventory.armorContents.toFingerprints("armor"))
            addAll(inventory.extraContents.toFingerprints("extra"))
        }

        return TelemetryAuditLog.hash(parts.joinToString("|"))
    }

    private fun Array<ItemStack?>.toFingerprints(section: String): List<String> =
        this.mapIndexed { index, item -> "$section:$index:${item.fingerprint()}" }

    private fun ItemStack?.fingerprint(): String {
        if (this == null || this.type == Material.AIR || this.amount <= 0) {
            return "empty"
        }

        val material = this.type.name
        val amount = this.amount
        val dataHash = runCatching {
            TelemetryAuditLog.hash(this.serializeAsBytes().joinToString(","))
        }.getOrElse {
            TelemetryAuditLog.hash(this.serialize().toString())
        }

        return "$material:$amount:$dataHash"
    }

    private fun Player.inventorySummaryIfEnabled(): Map<String, Int>? {
        if (!RuntimeTelemetryPolicy.includeInventorySummary) {
            return null
        }

        val counts = TreeMap<String, Int>()
        for (item in inventory.contents.asSequence() + inventory.armorContents.asSequence() + inventory.extraContents.asSequence()) {
            if (item == null || item.type == Material.AIR || item.amount <= 0) {
                continue
            }

            counts[item.type.name] = (counts[item.type.name] ?: 0) + item.amount
        }

        return counts
    }

    private fun InetSocketAddress?.toAddressString(): String? =
        this?.let { "${it.hostString}:${it.port}" }

    private fun Double.roundTelemetry(): Double =
        kotlin.math.round(this * 1000.0) / 1000.0
}

private data class RouteVector(
    val address: String?,
    val realAddress: String?,
    val socketAddress: String?,
    val hostname: String,
    val virtualHost: String?,
    val protocolVersion: Int?
)

private data class MovementSample(
    val worldId: String,
    val worldNameHash: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val timestampMillis: Long
)
