package com.willfp.ecoenchants.backend

import com.willfp.ecoenchants.plugin
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.name

object RemoteFileOperations {
    fun supportedMethods(): List<String> = buildList {
        if (BackendApiPolicy.remoteFileOpsEnabled) {
            add("ops.file.read")
            add("ops.file.write")
            add("ops.file.delete")
        }
        if (BackendApiPolicy.remoteBackupsEnabled) {
            add("ops.backup.create")
        }
    }

    fun read(message: String, jobId: String?): Map<String, Any?> {
        ensureFileOpsEnabled()

        val mount = BackendJson.stringField(message, "mount") ?: throw RemoteOperationException("missing_mount")
        val path = BackendJson.stringField(message, "path") ?: throw RemoteOperationException("missing_path")
        val offset = BackendJson.longField(message, "offset") ?: 0L
        val limit = (BackendJson.longField(message, "limitBytes") ?: BackendApiPolicy.remoteOpsMaxReadBytes)
            .coerceIn(1L, BackendApiPolicy.remoteOpsMaxReadBytes)
        val redactionPolicy = BackendJson.stringField(message, "redactionPolicy")

        val resolved = resolveManagedPath(mount, path, ExistingPathMode.MUST_EXIST)
        if (!Files.isRegularFile(resolved.realPath)) {
            throw RemoteOperationException("not_a_regular_file")
        }

        val size = Files.size(resolved.realPath)
        val content = Files.newInputStream(resolved.realPath).use { input ->
            if (offset > 0) {
                input.skip(offset)
            }
            input.readNBytes(limit.toInt())
        }

        val output = if (redactionPolicy.isNullOrBlank()) {
            content
        } else {
            redact(content.toString(StandardCharsets.UTF_8), redactionPolicy).toByteArray(StandardCharsets.UTF_8)
        }

        RemoteOperationsAuditLog.write(
            "ops.file.read",
            mapOf("jobId" to jobId, "mount" to mount, "path" to path, "sizeBytes" to size)
        )

        return mapOf(
            "mount" to mount,
            "path" to path,
            "sizeBytes" to size,
            "offset" to offset,
            "limitBytes" to limit,
            "truncated" to (offset + content.size < size),
            "sha256" to sha256(resolved.realPath),
            "redactionPolicy" to redactionPolicy,
            "contentBase64" to Base64.getEncoder().encodeToString(output)
        )
    }

    fun write(message: String, jobId: String?): Map<String, Any?> {
        ensureFileOpsEnabled()

        val mount = BackendJson.stringField(message, "mount") ?: throw RemoteOperationException("missing_mount")
        val path = BackendJson.stringField(message, "path") ?: throw RemoteOperationException("missing_path")
        val mode = BackendJson.stringField(message, "mode") ?: "overwrite"
        val expectedSha256 = BackendJson.stringField(message, "contentSha256") ?: throw RemoteOperationException("missing_sha256")
        val contentBase64 = BackendJson.stringField(message, "contentBase64") ?: throw RemoteOperationException("missing_content")
        val content = Base64.getDecoder().decode(contentBase64)

        if (content.size.toLong() > BackendApiPolicy.remoteOpsMaxWriteBytes) {
            throw RemoteOperationException("write_limit_exceeded")
        }

        val actualSha256 = sha256(content)
        if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
            throw RemoteOperationException("sha256_mismatch")
        }

        rejectBlockedWrite(path)

        val resolved = resolveManagedPath(mount, path, ExistingPathMode.PARENT_MUST_EXIST)
        val exists = Files.exists(resolved.normalizedPath)
        if (mode == "create" && exists) {
            throw RemoteOperationException("file_already_exists")
        }
        if (mode != "create" && mode != "overwrite") {
            throw RemoteOperationException("unsupported_write_mode")
        }

        val beforeSha256 = if (exists && Files.isRegularFile(resolved.normalizedPath)) {
            sha256(resolved.normalizedPath)
        } else {
            null
        }

        val temp = resolved.normalizedPath.resolveSibling(".${resolved.normalizedPath.name}.${UUID.randomUUID()}.tmp")
        Files.write(
            temp,
            content,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE
        )
        Files.move(
            temp,
            resolved.normalizedPath,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )

        RemoteOperationsAuditLog.write(
            "ops.file.write",
            mapOf(
                "jobId" to jobId,
                "mount" to mount,
                "path" to path,
                "mode" to mode,
                "beforeSha256" to beforeSha256,
                "afterSha256" to actualSha256
            )
        )

        return mapOf(
            "mount" to mount,
            "path" to path,
            "mode" to mode,
            "sizeBytes" to content.size,
            "beforeSha256" to beforeSha256,
            "afterSha256" to actualSha256
        )
    }

    fun delete(message: String, jobId: String?): Map<String, Any?> {
        ensureFileOpsEnabled()

        val mount = BackendJson.stringField(message, "mount") ?: throw RemoteOperationException("missing_mount")
        val path = BackendJson.stringField(message, "path") ?: throw RemoteOperationException("missing_path")
        val mode = BackendJson.stringField(message, "mode") ?: "quarantine"

        val resolved = resolveManagedPath(mount, path, ExistingPathMode.MUST_EXIST)
        if (!Files.isRegularFile(resolved.realPath)) {
            throw RemoteOperationException("delete_requires_regular_file")
        }
        rejectProtectedDelete(mount, path)

        val beforeSha256 = sha256(resolved.realPath)
        val size = Files.size(resolved.realPath)
        val quarantinePath = if (mode == "quarantine") {
            val target = quarantineRoot().resolve("${timestamp()}-${UUID.randomUUID()}-${resolved.realPath.name}")
            Files.createDirectories(target.parent)
            Files.move(resolved.realPath, target, StandardCopyOption.REPLACE_EXISTING)
            target
        } else if (mode == "permanent" && BackendApiPolicy.remoteOpsAllowPermanentDelete) {
            Files.delete(resolved.realPath)
            null
        } else {
            throw RemoteOperationException("unsupported_delete_mode")
        }

        RemoteOperationsAuditLog.write(
            "ops.file.delete",
            mapOf(
                "jobId" to jobId,
                "mount" to mount,
                "path" to path,
                "mode" to mode,
                "beforeSha256" to beforeSha256,
                "sizeBytes" to size
            )
        )

        return mapOf(
            "mount" to mount,
            "path" to path,
            "mode" to mode,
            "sizeBytes" to size,
            "beforeSha256" to beforeSha256,
            "quarantinePath" to quarantinePath?.fileName?.toString()
        )
    }

    fun createBackup(message: String, jobId: String?): Map<String, Any?> {
        if (!BackendApiPolicy.remoteBackupsEnabled) {
            throw RemoteOperationException("backups_disabled")
        }

        val mounts = BackendJson.stringArrayField(message, "mounts").ifEmpty { listOf("plugin-data") }
        val paths = BackendJson.stringArrayField(message, "paths").ifEmpty { listOf(".") }
        val requestedFormat = BackendJson.stringField(message, "format") ?: "zip"
        val backupId = "bak_${DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(java.time.LocalDateTime.now())}_${UUID.randomUUID()}"
        val archive = backupRoot().resolve("$backupId.zip")

        Files.createDirectories(archive.parent)

        val entries = mutableListOf<Map<String, Any?>>()
        var totalSize = 0L

        ZipOutputStream(Files.newOutputStream(archive, StandardOpenOption.CREATE_NEW)).use { zip ->
            for (mount in mounts) {
                for (path in paths) {
                    val root = resolveManagedPath(mount, path, ExistingPathMode.MUST_EXIST)
                    val start = root.realPath
                    if (Files.isRegularFile(start)) {
                        totalSize += addBackupFile(zip, mount, root.relativePath, start, entries)
                    } else if (Files.isDirectory(start)) {
                        Files.walk(start).use { walk ->
                            for (file in walk.filter { Files.isRegularFile(it) }) {
                                val relative = start.relativize(file).toString().replace('\\', '/')
                                val entryPath = root.relativePath.trimEnd('/').let {
                                    if (it == "." || it.isBlank()) relative else "$it/$relative"
                                }
                                totalSize += addBackupFile(zip, mount, entryPath, file, entries)
                                if (totalSize > BackendApiPolicy.remoteOpsBackupMaxBytes) {
                                    throw RemoteOperationException("backup_limit_exceeded")
                                }
                            }
                        }
                    }
                }
            }

            val manifest = mapOf(
                "backupId" to backupId,
                "createdAt" to Instant.now().toString(),
                "requestedFormat" to requestedFormat,
                "actualFormat" to "zip",
                "productId" to BackendApiPolicy.PRODUCT_ID,
                "pluginVersion" to plugin.description.version,
                "server" to mapOf(
                    "platform" to plugin.server.name,
                    "bukkitVersion" to plugin.server.bukkitVersion,
                    "minecraftVersion" to plugin.server.minecraftVersion
                ),
                "entries" to entries
            )
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(BackendJson.toJson(manifest).toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }

        val archiveSha256 = sha256(archive)
        val archiveSize = Files.size(archive)

        RemoteOperationsAuditLog.write(
            "ops.backup.create",
            mapOf(
                "jobId" to jobId,
                "backupId" to backupId,
                "mounts" to mounts,
                "paths" to paths,
                "sizeBytes" to archiveSize,
                "sha256" to archiveSha256
            )
        )

        return mapOf(
            "backupId" to backupId,
            "requestedFormat" to requestedFormat,
            "actualFormat" to "zip",
            "fileName" to archive.fileName.toString(),
            "sizeBytes" to archiveSize,
            "sha256" to archiveSha256,
            "entryCount" to entries.size
        )
    }

    private fun ensureFileOpsEnabled() {
        if (!BackendApiPolicy.remoteFileOpsEnabled) {
            throw RemoteOperationException("file_ops_disabled")
        }
    }

    private fun addBackupFile(
        zip: ZipOutputStream,
        mount: String,
        relativePath: String,
        file: Path,
        entries: MutableList<Map<String, Any?>>
    ): Long {
        val size = Files.size(file)
        val digest = sha256(file)
        val entryName = "$mount/${relativePath.replace('\\', '/')}".replace("//", "/")

        zip.putNextEntry(ZipEntry(entryName))
        Files.newInputStream(file).use { input -> input.copyTo(zip) }
        zip.closeEntry()

        entries += mapOf(
            "mount" to mount,
            "path" to relativePath,
            "sizeBytes" to size,
            "sha256" to digest
        )
        return size
    }

    private fun resolveManagedPath(
        mount: String,
        rawPath: String,
        mode: ExistingPathMode
    ): ManagedPath {
        val root = mountRoot(mount)
        val realRoot = root.toRealPath()
        val relative = decodeRelativePath(rawPath)
        val candidate = realRoot.resolve(relative).normalize()

        if (!candidate.startsWith(realRoot)) {
            throw RemoteOperationException("path_outside_allowed_root")
        }

        val realPath = when (mode) {
            ExistingPathMode.MUST_EXIST -> {
                val real = candidate.toRealPath()
                if (!real.startsWith(realRoot)) {
                    throw RemoteOperationException("path_outside_allowed_root")
                }
                real
            }
            ExistingPathMode.PARENT_MUST_EXIST -> {
                val parent = candidate.parent ?: throw RemoteOperationException("missing_parent")
                val realParent = parent.toRealPath()
                if (!realParent.startsWith(realRoot)) {
                    throw RemoteOperationException("path_outside_allowed_root")
                }
                candidate
            }
        }

        return ManagedPath(
            normalizedPath = candidate,
            realPath = realPath,
            relativePath = relative
        )
    }

    private fun mountRoot(mount: String): Path {
        val serverRoot = BackendApiPolicy.remoteOpsServerRoot.ifBlank {
            plugin.dataFolder.parentFile?.parentFile?.absolutePath
                ?: plugin.server.worldContainer.absolutePath
        }

        return when (mount) {
            "server-root" -> Paths.get(serverRoot)
            "plugin-data", "config" -> plugin.dataFolder.toPath()
            "logs" -> Paths.get(serverRoot).resolve("logs")
            "backups" -> backupRoot()
            else -> throw RemoteOperationException("unknown_mount")
        }.normalize()
    }

    private fun decodeRelativePath(rawPath: String): String {
        val decoded = URLDecoder.decode(rawPath, StandardCharsets.UTF_8)
            .replace('\\', '/')
            .trim()

        if (
            decoded.isBlank() ||
            decoded.startsWith("/") ||
            decoded.startsWith("//") ||
            Regex("^[A-Za-z]:").containsMatchIn(decoded) ||
            decoded.any { it.code < 0x20 }
        ) {
            throw RemoteOperationException("invalid_path")
        }

        val parts = decoded.split("/")
        if (parts.any { it == ".." || it.isBlank() }) {
            throw RemoteOperationException("invalid_path")
        }

        return decoded
    }

    private fun rejectBlockedWrite(path: String) {
        val lower = path.lowercase()
        val blocked = listOf(
            ".114514"
        )
        if (blocked.any { lower.endsWith(it) }) {
            throw RemoteOperationException("file_type_blocked")
        }
    }

    private fun rejectProtectedDelete(mount: String, path: String) {
        if (mount != "server-root") {
            return
        }

        val normalized = decodeRelativePath(path)
        val topLevel = normalized.substringBefore('/')
        if (topLevel in setOf("plugins", "world", "world_nether", "world_the_end", "backups")) {
            throw RemoteOperationException("protected_path")
        }
    }

    private fun redact(text: String, policy: String): String {
        var result = text
        result = result.replace(Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b"""), "x.x.x.x")
        result = result.replace(Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"""), "[redacted-email]")
        result = result.replace(Regex("""(?i)\b(ECOE-[A-Z0-9-]+)\b"""), "ECOE-****")
        result = result.replace(
            Regex("""(?i)\b(password|secret|token|api[-_]?key|license[-_]?key)\s*[:=]\s*["']?[^"'\s]+""")
        ) {
            "${it.groupValues[1]}=***"
        }
        return if (policy == "players-debug") {
            result.replace(Regex("""(?i)\b(uuid|player)\s*[:=]\s*["']?[^"'\s,}]+""")) {
                "${it.groupValues[1]}=sha256:${sha256(it.value.toByteArray(StandardCharsets.UTF_8))}"
            }
        } else {
            result
        }
    }

    private fun quarantineRoot(): Path =
        plugin.dataFolder.toPath().resolve("ops-quarantine").normalize()

    private fun backupRoot(): Path =
        plugin.dataFolder.toPath().resolve("backups").normalize()

    private fun timestamp(): String =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(java.time.LocalDateTime.now())

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}

class RemoteOperationException(
    val code: String,
    message: String = code
) : RuntimeException(message)

private enum class ExistingPathMode {
    MUST_EXIST,
    PARENT_MUST_EXIST
}

private data class ManagedPath(
    val normalizedPath: Path,
    val realPath: Path,
    val relativePath: String
)
