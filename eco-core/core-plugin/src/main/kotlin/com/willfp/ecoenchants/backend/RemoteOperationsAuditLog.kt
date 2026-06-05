package com.willfp.ecoenchants.backend

import com.willfp.ecoenchants.plugin
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

object RemoteOperationsAuditLog {
    private val lock = Any()

    fun write(action: String, payload: Map<String, Any?> = emptyMap()) {
        if (!BackendApiPolicy.remoteAuditLogEnabled) {
            return
        }

        synchronized(lock) {
            runCatching {
                val path = auditPath()
                Files.createDirectories(path.parent)

                val event = linkedMapOf<String, Any?>(
                    "timestamp" to Instant.now().toString(),
                    "action" to action,
                    "payload" to payload
                )

                Files.writeString(
                    path,
                    "${BackendJson.toJson(event)}\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                )
            }.onFailure {
                plugin.logger.warning("Could not write EcoEnchants remote operations audit log: ${it.message}")
            }
        }
    }

    private fun auditPath(): Path =
        plugin.dataFolder.toPath().resolve(BackendApiPolicy.remoteAuditLogFile).normalize()
}
