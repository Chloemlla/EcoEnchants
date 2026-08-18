package com.willfp.ecoenchants.telemetry

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.ecoenchants.backend.BackendApiPolicy
import com.willfp.ecoenchants.plugin

object RuntimeTelemetryPolicy {
    private val config: Config
        get() = plugin.configYml

    val enabled: Boolean
        get() = bool("runtime-telemetry.enabled", true)

    val auditLogEnabled: Boolean
        get() = bool("runtime-telemetry.audit-log.enabled", true)

    val auditLogFile: String
        get() = string("runtime-telemetry.audit-log.file", "telemetry/audit.jsonl")

    val maxAuditLogSizeBytes: Long
        get() = (double("runtime-telemetry.audit-log.max-file-size-mb", 10.0) * 1024 * 1024).toLong()
            .coerceAtLeast(0L)

    val remoteReportingEnabled: Boolean
        get() = bool("runtime-telemetry.remote-reporting.enabled", true)

    val remoteReportingApiUrl: String
        get() = string("runtime-telemetry.remote-reporting.api-url", BackendApiPolicy.versionedApiUrl)

    val remoteReportingEndpoint: String
        get() = string("runtime-telemetry.remote-reporting.endpoint", "/telemetry/events")

    val remoteReportingUrl: String
        get() = "${BackendApiPolicy.normalizeVersionedApiUrl(remoteReportingApiUrl)}" +
                "/${remoteReportingEndpoint.trim().trimStart('/')}"

    val remoteReportingIntervalTicks: Long
        get() = int("runtime-telemetry.remote-reporting.interval-ticks", 1200).toLong().coerceAtLeast(20L)

    val remoteReportingBatchSize: Int
        get() = int("runtime-telemetry.remote-reporting.batch-size", 100).coerceIn(1, 1000)

    val remoteReportingMaxQueuedEvents: Int
        get() = int("runtime-telemetry.remote-reporting.max-queued-events", 5000).coerceAtLeast(1)

    val remoteReportingTimeoutMillis: Int
        get() = int("runtime-telemetry.remote-reporting.timeout-ms", 3000).coerceIn(500, 10000)

    val remoteReportingRequireActivationToken: Boolean
        get() = bool("runtime-telemetry.remote-reporting.require-activation-token", true)

    val hashSalt: String
        get() = string("runtime-telemetry.privacy.hash-salt", "")

    val includeRawNetworkAddresses: Boolean
        get() = bool("runtime-telemetry.privacy.include-raw-network-addresses", false)

    val identityEnabled: Boolean
        get() = bool("runtime-telemetry.identity.enabled", true)

    val movementEnabled: Boolean
        get() = bool("runtime-telemetry.movement.enabled", true)

    val movementSampleIntervalMillis: Long
        get() = int("runtime-telemetry.movement.sample-interval-ms", 1000).toLong().coerceAtLeast(250L)

    val maxDistancePerMovementSample: Double
        get() = double("runtime-telemetry.movement.max-distance-per-sample", 24.0).coerceAtLeast(1.0)

    val maxBlocksPerSecond: Double
        get() = double("runtime-telemetry.movement.max-blocks-per-second", 30.0).coerceAtLeast(1.0)

    val logMovementSamples: Boolean
        get() = bool("runtime-telemetry.movement.log-samples", false)

    val stateDeltaEnabled: Boolean
        get() = bool("runtime-telemetry.state-delta.enabled", true)

    val includeInventorySummary: Boolean
        get() = bool("runtime-telemetry.state-delta.include-inventory-summary", true)

    val textTelemetryEnabled: Boolean
        get() = bool("runtime-telemetry.text.enabled", true)

    val captureRawText: Boolean
        get() = bool("runtime-telemetry.text.capture-raw", false)

    val logAllTextMetadata: Boolean
        get() = bool("runtime-telemetry.text.log-all-metadata", false)

    val logCommandRoot: Boolean
        get() = bool("runtime-telemetry.text.log-command-root", true)

    val logMatchedTextTerms: Boolean
        get() = bool("runtime-telemetry.text.log-matched-terms", true)

    val textRiskTerms: List<String>
        get() = strings(
            "runtime-telemetry.text.risk-terms",
            listOf("dupe", "crash", "lag machine", "xray", "kill aura")
        )

    val environmentProbeEnabled: Boolean
        get() = bool("runtime-telemetry.environment-probe.enabled", true)

    val environmentProbeIntervalTicks: Long
        get() = int("runtime-telemetry.environment-probe.interval-ticks", 1200).toLong().coerceAtLeast(200L)

    val environmentRedlineAction: String
        get() = string("runtime-telemetry.environment-probe.redline-action", "disable-plugin")
            .lowercase()

    val deniedJvmArgs: List<String>
        get() = strings("runtime-telemetry.environment-probe.denied-jvm-args", listOf("-agentlib:jdwp", "-Xdebug"))

    val blockJavaAgents: Boolean
        get() = bool("runtime-telemetry.environment-probe.block-java-agents", false)

    val deniedEnvironmentVariables: List<String>
        get() = strings("runtime-telemetry.environment-probe.denied-env-vars", emptyList())

    val deniedSystemProperties: List<String>
        get() = strings("runtime-telemetry.environment-probe.denied-system-properties", emptyList())

    fun statusLines(): List<String> = listOf(
        "Runtime telemetry",
        "Enabled: $enabled",
        "Audit log enabled: $auditLogEnabled",
        "Audit log file: $auditLogFile",
        "Remote reporting enabled: $remoteReportingEnabled",
        "Remote reporting URL: $remoteReportingUrl",
        "Remote reporting interval: ${remoteReportingIntervalTicks} ticks",
        "Identity anchors: $identityEnabled",
        "Movement sampling: $movementEnabled (${movementSampleIntervalMillis}ms)",
        "State delta logging: $stateDeltaEnabled",
        "Text telemetry: $textTelemetryEnabled",
        "Raw network addresses: $includeRawNetworkAddresses",
        "Raw text capture: $captureRawText",
        "Environment probe: $environmentProbeEnabled",
        "Environment redline action: $environmentRedlineAction"
    )

    private fun bool(path: String, default: Boolean): Boolean =
        config.getBoolOrNull(path) ?: default

    private fun int(path: String, default: Int): Int =
        config.getIntOrNull(path) ?: default

    private fun double(path: String, default: Double): Double =
        config.getDoubleOrNull(path) ?: default

    private fun string(path: String, default: String): String =
        config.getStringOrNull(path) ?: default

    private fun strings(path: String, default: List<String>): List<String> =
        config.getStringsOrNull(path) ?: default
}
