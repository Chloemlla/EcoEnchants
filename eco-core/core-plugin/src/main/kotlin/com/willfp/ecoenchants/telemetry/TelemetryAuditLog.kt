package com.willfp.ecoenchants.telemetry

import com.willfp.ecoenchants.backend.BackendJson
import com.willfp.ecoenchants.plugin
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

object TelemetryAuditLog {
    private val lock = Any()

    fun start() {
        if (!RuntimeTelemetryPolicy.enabled || !RuntimeTelemetryPolicy.auditLogEnabled) {
            return
        }

        write("telemetry_lifecycle", mapOf("state" to "started"))
    }

    fun stop() {
        write("telemetry_lifecycle", mapOf("state" to "stopped"))
    }

    fun write(category: String, payload: Map<String, Any?> = emptyMap()) {
        if (!RuntimeTelemetryPolicy.enabled) {
            return
        }

        val event = linkedMapOf<String, Any?>(
            "eventId" to UUID.randomUUID().toString(),
            "timestamp" to Instant.now().toString(),
            "category" to category,
            "payload" to payload
        )

        TelemetryReporter.enqueue(event)

        if (!RuntimeTelemetryPolicy.auditLogEnabled) {
            return
        }

        synchronized(lock) {
            runCatching {
                val path = auditLogPath()
                Files.createDirectories(path.parent)
                rotateIfNeeded(path)

                Files.writeString(
                    path,
                    "${BackendJson.toJson(event)}\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                )
            }.onFailure {
                plugin.logger.warning("Could not write EcoEnchants telemetry audit log: ${it.message}")
            }
        }
    }

    fun hash(value: String?): String {
        if (value == null) {
            return "null"
        }

        val salt = RuntimeTelemetryPolicy.hashSalt.ifBlank {
            "${plugin.pluginMeta.name}:${plugin.server.name}:${plugin.server.port}"
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$salt:$value".toByteArray(StandardCharsets.UTF_8))

        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun auditLogPath(): Path =
        plugin.dataFolder.toPath().resolve(RuntimeTelemetryPolicy.auditLogFile).normalize()

    private fun rotateIfNeeded(path: Path) {
        val maxSize = RuntimeTelemetryPolicy.maxAuditLogSizeBytes
        if (maxSize <= 0 || !Files.isRegularFile(path) || Files.size(path) <= maxSize) {
            return
        }

        val rotated = path.resolveSibling("${path.fileName}.1")
        Files.deleteIfExists(rotated)
        Files.move(path, rotated)
    }
}
