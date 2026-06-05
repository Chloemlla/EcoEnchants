package com.willfp.ecoenchants.telemetry

import com.willfp.ecoenchants.plugin
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant

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
        if (!RuntimeTelemetryPolicy.enabled || !RuntimeTelemetryPolicy.auditLogEnabled) {
            return
        }

        synchronized(lock) {
            runCatching {
                val path = auditLogPath()
                Files.createDirectories(path.parent)
                rotateIfNeeded(path)

                val event = linkedMapOf<String, Any?>(
                    "timestamp" to Instant.now().toString(),
                    "category" to category,
                    "payload" to payload
                )

                Files.writeString(
                    path,
                    "${toJson(event)}\n",
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
            "${plugin.description.name}:${plugin.server.name}:${plugin.server.port}"
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

    private fun toJson(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> value.toString()
        is Number -> value.toString()
        is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (key, item) ->
            "${toJson(key.toString())}:${toJson(item)}"
        }
        is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { toJson(it) }
        is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { toJson(it) }
        else -> "\"${escapeJson(value.toString())}\""
    }

    private fun escapeJson(value: String): String = buildString {
        for (char in value) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
    }
}
