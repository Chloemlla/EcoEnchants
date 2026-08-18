package com.willfp.ecoenchants.backend

import com.willfp.ecoenchants.plugin
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

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

                val previousHash = previousEntryHash(path)
                val event = linkedMapOf<String, Any?>(
                    "auditId" to "aud_${UUID.randomUUID()}",
                    "createdAt" to Instant.now().toString(),
                    "action" to action,
                    "decision" to (payload["decision"] ?: "recorded"),
                    "payload" to payload,
                    "previousEntryHash" to previousHash
                )
                val entryHash = sha256(BackendJson.toJson(event).toByteArray(StandardCharsets.UTF_8))
                event["entryHash"] = entryHash

                Files.writeString(
                    path,
                    "${BackendJson.toJson(event)}\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                )
                Files.writeString(
                    hashPath(path),
                    entryHash,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
                )
            }.onFailure {
                plugin.logger.warning("Could not write EcoEnchants remote operations audit log: ${it.message}")
            }
        }
    }

    private fun auditPath(): Path {
        val root = plugin.dataFolder.toPath().toAbsolutePath().normalize()
        Files.createDirectories(root)

        val path = root.resolve(BackendApiPolicy.remoteAuditLogFile).normalize()
        if (!path.startsWith(root)) {
            throw IllegalArgumentException("Remote operations audit log path must stay inside plugin data folder.")
        }

        return path
    }

    private fun previousEntryHash(path: Path): String? {
        val sidecar = hashPath(path)
        if (Files.isRegularFile(sidecar)) {
            return Files.readString(sidecar, StandardCharsets.UTF_8).trim().ifBlank { null }
        }

        if (!Files.isRegularFile(path)) {
            return null
        }

        var lastLine: String? = null
        Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isNotBlank()) {
                    lastLine = line
                }
            }
        }

        return lastLine?.let { BackendJson.stringField(it, "entryHash") }
    }

    private fun hashPath(path: Path): Path =
        path.resolveSibling("${path.fileName}.sha256")

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
