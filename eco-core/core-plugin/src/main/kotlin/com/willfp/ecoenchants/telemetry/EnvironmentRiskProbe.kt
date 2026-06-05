package com.willfp.ecoenchants.telemetry

import com.willfp.ecoenchants.plugin
import java.lang.management.ManagementFactory
import org.bukkit.scheduler.BukkitTask

object EnvironmentRiskProbe {
    @Volatile
    private var lastFindings: List<EnvironmentRiskFinding> = emptyList()

    private var task: BukkitTask? = null

    fun verifyStartup(): Boolean {
        if (!RuntimeTelemetryPolicy.environmentProbeEnabled) {
            lastFindings = emptyList()
            return true
        }

        val findings = probe()
        lastFindings = findings
        logFindings(findings)

        return !findings.any { it.redline } || RuntimeTelemetryPolicy.environmentRedlineAction != "disable-plugin"
    }

    fun start() {
        stop()

        if (!RuntimeTelemetryPolicy.enabled || !RuntimeTelemetryPolicy.environmentProbeEnabled) {
            return
        }

        task = plugin.scheduler.runAsyncTimer(
            RuntimeTelemetryPolicy.environmentProbeIntervalTicks,
            RuntimeTelemetryPolicy.environmentProbeIntervalTicks
        ) {
            val findings = probe()
            lastFindings = findings
            logFindings(findings)

            if (findings.any { it.redline } && RuntimeTelemetryPolicy.environmentRedlineAction == "disable-plugin") {
                plugin.scheduler.run {
                    plugin.logger.severe("EcoEnchants environment risk redline reached; disabling plugin.")
                    plugin.server.pluginManager.disablePlugin(plugin)
                }
            }
        }
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    fun statusLines(): List<String> {
        val findings = lastFindings
        return buildList {
            add("Environment risk probe")
            add("Enabled: ${RuntimeTelemetryPolicy.environmentProbeEnabled}")
            add("Last finding count: ${findings.size}")
            add("Redline findings: ${findings.count { it.redline }}")
            for (finding in findings.take(5)) {
                add("${finding.severity}: ${finding.signal} - ${finding.detail}")
            }
        }
    }

    private fun probe(): List<EnvironmentRiskFinding> {
        val findings = mutableListOf<EnvironmentRiskFinding>()
        val jvmArgs = ManagementFactory.getRuntimeMXBean().inputArguments

        for (deniedArg in RuntimeTelemetryPolicy.deniedJvmArgs) {
            val matchedArg = jvmArgs.firstOrNull { it.contains(deniedArg, ignoreCase = true) }
            if (matchedArg != null) {
                findings += EnvironmentRiskFinding(
                    severity = "redline",
                    signal = "denied-jvm-arg",
                    detail = deniedArg,
                    redline = true
                )
            }
        }

        if (RuntimeTelemetryPolicy.blockJavaAgents) {
            for (arg in jvmArgs.filter { it.startsWith("-javaagent", ignoreCase = true) }) {
                findings += EnvironmentRiskFinding(
                    severity = "redline",
                    signal = "java-agent",
                    detail = arg.substringBefore('='),
                    redline = true
                )
            }
        }

        for (name in RuntimeTelemetryPolicy.deniedEnvironmentVariables) {
            if (System.getenv(name) != null) {
                findings += EnvironmentRiskFinding(
                    severity = "redline",
                    signal = "denied-env-var",
                    detail = name,
                    redline = true
                )
            }
        }

        for (name in RuntimeTelemetryPolicy.deniedSystemProperties) {
            if (System.getProperty(name) != null) {
                findings += EnvironmentRiskFinding(
                    severity = "redline",
                    signal = "denied-system-property",
                    detail = name,
                    redline = true
                )
            }
        }

        if (!plugin.server.onlineMode) {
            findings += EnvironmentRiskFinding(
                severity = "notice",
                signal = "server-offline-mode",
                detail = "identity anchors are weaker when online-mode is disabled",
                redline = false
            )
        }

        return findings
    }

    private fun logFindings(findings: List<EnvironmentRiskFinding>) {
        if (findings.isEmpty()) {
            TelemetryAuditLog.write("environment_probe", mapOf("status" to "clear"))
            return
        }

        for (finding in findings) {
            TelemetryAuditLog.write(
                "environment_probe",
                mapOf(
                    "severity" to finding.severity,
                    "signal" to finding.signal,
                    "detail" to finding.detail,
                    "redline" to finding.redline,
                    "action" to RuntimeTelemetryPolicy.environmentRedlineAction
                )
            )
        }
    }
}

data class EnvironmentRiskFinding(
    val severity: String,
    val signal: String,
    val detail: String,
    val redline: Boolean
)
