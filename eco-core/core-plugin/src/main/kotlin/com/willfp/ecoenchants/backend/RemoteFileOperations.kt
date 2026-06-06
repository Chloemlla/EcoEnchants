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
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
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
            add("ops.backup.restore")
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

        val decodedPath = decodeRelativePath(path)
        rejectBlockedWrite(decodedPath)

        val resolved = resolveManagedPath(mount, path, ExistingPathMode.PARENT_MUST_EXIST)
        rejectExistingTargetOutsideRoot(mount, resolved.normalizedPath)
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
        val result = createBackupArchive(
            backupId = newBackupId("bak"),
            mounts = mounts,
            paths = paths,
            requestedFormat = requestedFormat,
            manifestType = "backup"
        )

        RemoteOperationsAuditLog.write(
            "ops.backup.create",
            mapOf(
                "jobId" to jobId,
                "backupId" to result.backupId,
                "mounts" to mounts,
                "paths" to paths,
                "sizeBytes" to result.sizeBytes,
                "sha256" to result.sha256
            )
        )

        return result.toResponse(requestedFormat)
    }

    fun restoreBackup(message: String, jobId: String?): Map<String, Any?> {
        if (!BackendApiPolicy.remoteBackupsEnabled) {
            throw RemoteOperationException("backups_disabled")
        }

        val backupId = BackendJson.stringField(message, "backupId") ?: throw RemoteOperationException("missing_backup_id")
        val mode = (BackendJson.stringField(message, "mode") ?: "staged").lowercase(Locale.ROOT)
        val expectedSha256 = BackendJson.stringField(message, "archiveSha256")
        val restorePaths = restorePathFilters(BackendJson.stringArrayField(message, "restorePaths"))
        val restoreMounts = BackendJson.stringArrayField(message, "mounts").toSet()
        val preRestoreBackup = BackendJson.booleanField(message, "preRestoreBackup") ?: true

        val archive = resolveBackupArchive(backupId)
        val archiveSha256 = sha256(archive)
        if (!expectedSha256.isNullOrBlank() && !archiveSha256.equals(expectedSha256, ignoreCase = true)) {
            throw RemoteOperationException("backup_integrity_failed")
        }

        val entries = restoreEntries(archive, backupId, restorePaths, restoreMounts)
        if (entries.isEmpty()) {
            throw RemoteOperationException("no_restore_entries")
        }

        return when (mode) {
            "staged" -> stageRestore(archive, backupId, archiveSha256, entries, jobId)
            "apply", "restore" -> applyRestore(
                archive = archive,
                backupId = backupId,
                archiveSha256 = archiveSha256,
                entries = entries,
                jobId = jobId,
                preRestoreBackup = preRestoreBackup
            )
            else -> throw RemoteOperationException("unsupported_restore_mode")
        }
    }

    private fun ensureFileOpsEnabled() {
        if (!BackendApiPolicy.remoteFileOpsEnabled) {
            throw RemoteOperationException("file_ops_disabled")
        }
    }

    private fun createBackupArchive(
        backupId: String,
        mounts: List<String>,
        paths: List<String>,
        requestedFormat: String,
        manifestType: String
    ): BackupArchiveResult {
        val archive = backupRoot().resolve("$backupId.zip").normalize()
        Files.createDirectories(archive.parent)

        val entries = mutableListOf<Map<String, Any?>>()
        var totalSize = 0L

        try {
            ZipOutputStream(Files.newOutputStream(archive, StandardOpenOption.CREATE_NEW)).use { zip ->
                for (mount in mounts) {
                    for (path in paths) {
                        val root = resolveManagedPath(mount, path, ExistingPathMode.MUST_EXIST)
                        val start = root.realPath
                        if (Files.isRegularFile(start) && !isExcludedFromBackup(start, archive)) {
                            totalSize = addBackupFileWithLimit(zip, mount, root.relativePath, start, entries, totalSize)
                        } else if (Files.isDirectory(start)) {
                            Files.walk(start).use { walk ->
                                for (file in walk.filter { Files.isRegularFile(it) && !isExcludedFromBackup(it, archive) }) {
                                    val relative = start.relativize(file).toString().replace('\\', '/')
                                    val entryPath = root.relativePath.trimEnd('/').let {
                                        if (it == "." || it.isBlank()) relative else "$it/$relative"
                                    }
                                    totalSize = addBackupFileWithLimit(zip, mount, entryPath, file, entries, totalSize)
                                }
                            }
                        }
                    }
                }

                val manifest = mapOf(
                    "backupId" to backupId,
                    "type" to manifestType,
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
        } catch (failure: Throwable) {
            Files.deleteIfExists(archive)
            throw failure
        }

        return BackupArchiveResult(
            backupId = backupId,
            fileName = archive.fileName.toString(),
            sizeBytes = Files.size(archive),
            sha256 = sha256(archive),
            entryCount = entries.size
        )
    }

    private fun stageRestore(
        archive: Path,
        backupId: String,
        archiveSha256: String,
        entries: List<RestoreEntry>,
        jobId: String?
    ): Map<String, Any?> {
        val stageRoot = restoreStagingRoot().resolve("${backupId}-${UUID.randomUUID()}").normalize()
        Files.createDirectories(stageRoot)

        ZipFile(archive.toFile()).use { zip ->
            for (entry in entries) {
                val zipEntry = zip.getEntry(entry.zipEntryName) ?: throw RemoteOperationException("backup_integrity_failed")
                val target = stageRoot.resolve(entry.zipEntryName).normalize()
                if (!target.startsWith(stageRoot)) {
                    throw RemoteOperationException("backup_integrity_failed")
                }

                Files.createDirectories(target.parent)
                zip.getInputStream(zipEntry).use { input ->
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }

        RemoteOperationsAuditLog.write(
            "ops.backup.restore.staged",
            mapOf(
                "jobId" to jobId,
                "backupId" to backupId,
                "archiveSha256" to archiveSha256,
                "entryCount" to entries.size,
                "stagingDirectory" to stageRoot.fileName.toString()
            )
        )

        return mapOf(
            "backupId" to backupId,
            "mode" to "staged",
            "archiveSha256" to archiveSha256,
            "entryCount" to entries.size,
            "stagingDirectory" to stageRoot.fileName.toString()
        )
    }

    private fun applyRestore(
        archive: Path,
        backupId: String,
        archiveSha256: String,
        entries: List<RestoreEntry>,
        jobId: String?,
        preRestoreBackup: Boolean
    ): Map<String, Any?> {
        entries.forEach { rejectBlockedWrite(it.relativePath) }

        val preRestore = if (preRestoreBackup) {
            createPreRestoreBackup(entries, backupId)
        } else {
            null
        }

        val changes = mutableListOf<Map<String, Any?>>()

        ZipFile(archive.toFile()).use { zip ->
            for (entry in entries) {
                val resolved = resolveManagedPath(entry.mount, entry.relativePath, ExistingPathMode.ROOT_MUST_EXIST)
                ensureWritableParent(entry.mount, resolved.normalizedPath)
                rejectExistingTargetOutsideRoot(entry.mount, resolved.normalizedPath)

                if (Files.exists(resolved.normalizedPath) && !Files.isRegularFile(resolved.normalizedPath)) {
                    throw RemoteOperationException("restore_requires_regular_file_target")
                }

                val beforeSha256 = if (Files.isRegularFile(resolved.normalizedPath)) {
                    sha256(resolved.normalizedPath)
                } else {
                    null
                }

                val temp = resolved.normalizedPath.resolveSibling(".${resolved.normalizedPath.name}.${UUID.randomUUID()}.restore")
                val zipEntry = zip.getEntry(entry.zipEntryName) ?: throw RemoteOperationException("backup_integrity_failed")
                zip.getInputStream(zipEntry).use { input ->
                    Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING)
                }
                Files.move(temp, resolved.normalizedPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)

                changes += mapOf(
                    "mount" to entry.mount,
                    "path" to entry.relativePath,
                    "beforeSha256" to beforeSha256,
                    "afterSha256" to sha256(resolved.normalizedPath)
                )
            }
        }

        RemoteOperationsAuditLog.write(
            "ops.backup.restore",
            mapOf(
                "jobId" to jobId,
                "backupId" to backupId,
                "archiveSha256" to archiveSha256,
                "entryCount" to entries.size,
                "preRestoreBackupId" to preRestore?.backupId
            )
        )

        return mapOf(
            "backupId" to backupId,
            "mode" to "apply",
            "archiveSha256" to archiveSha256,
            "entryCount" to entries.size,
            "preRestoreBackup" to preRestore?.toResponse("zip"),
            "changes" to changes
        )
    }

    private fun createPreRestoreBackup(entries: List<RestoreEntry>, sourceBackupId: String): BackupArchiveResult? {
        val existingTargets = entries
            .distinctBy { "${it.mount}:${it.relativePath}" }
            .mapNotNull { entry ->
                val resolved = resolveManagedPath(entry.mount, entry.relativePath, ExistingPathMode.ROOT_MUST_EXIST)
                rejectExistingTargetOutsideRoot(entry.mount, resolved.normalizedPath)
                if (Files.isRegularFile(resolved.normalizedPath)) {
                    entry to resolved.normalizedPath
                } else {
                    null
                }
            }

        if (existingTargets.isEmpty()) {
            return null
        }

        val backupId = newBackupId("pre")
        val archive = backupRoot().resolve("$backupId.zip").normalize()
        Files.createDirectories(archive.parent)

        val manifestEntries = mutableListOf<Map<String, Any?>>()
        var totalSize = 0L

        try {
            ZipOutputStream(Files.newOutputStream(archive, StandardOpenOption.CREATE_NEW)).use { zip ->
                for ((entry, file) in existingTargets) {
                    totalSize = addBackupFileWithLimit(zip, entry.mount, entry.relativePath, file, manifestEntries, totalSize)
                }

                val manifest = mapOf(
                    "backupId" to backupId,
                    "type" to "pre-restore",
                    "sourceBackupId" to sourceBackupId,
                    "createdAt" to Instant.now().toString(),
                    "actualFormat" to "zip",
                    "productId" to BackendApiPolicy.PRODUCT_ID,
                    "pluginVersion" to plugin.description.version,
                    "entries" to manifestEntries
                )
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(BackendJson.toJson(manifest).toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
        } catch (failure: Throwable) {
            Files.deleteIfExists(archive)
            throw failure
        }

        return BackupArchiveResult(
            backupId = backupId,
            fileName = archive.fileName.toString(),
            sizeBytes = Files.size(archive),
            sha256 = sha256(archive),
            entryCount = manifestEntries.size
        )
    }

    private fun restoreEntries(
        archive: Path,
        backupId: String,
        restorePaths: List<String>,
        restoreMounts: Set<String>
    ): List<RestoreEntry> {
        val entries = mutableListOf<RestoreEntry>()

        ZipFile(archive.toFile()).use { zip ->
            val manifestEntry = zip.getEntry("manifest.json") ?: throw RemoteOperationException("backup_integrity_failed")
            val manifest = zip.getInputStream(manifestEntry).use { input ->
                input.readBytes().toString(StandardCharsets.UTF_8)
            }
            val manifestBackupId = BackendJson.stringField(manifest, "backupId")
            if (manifestBackupId != backupId) {
                throw RemoteOperationException("backup_integrity_failed")
            }

            val zipEntries = zip.entries()
            while (zipEntries.hasMoreElements()) {
                val zipEntry = zipEntries.nextElement()
                if (zipEntry.isDirectory || zipEntry.name == "manifest.json") {
                    continue
                }

                val entryName = normalizeZipEntryName(zipEntry.name)
                val mount = entryName.substringBefore('/')
                val relativePath = entryName.substringAfter('/')
                val decodedRelativePath = decodeRelativePath(relativePath)
                if (decodedRelativePath.isBlank() || restoreMounts.isNotEmpty() && mount !in restoreMounts) {
                    continue
                }
                if (!matchesRestoreFilter(mount, decodedRelativePath, restorePaths)) {
                    continue
                }

                mountRoot(mount)
                entries += RestoreEntry(
                    zipEntryName = entryName,
                    mount = mount,
                    relativePath = decodedRelativePath,
                    sizeBytes = zipEntry.size
                )
            }
        }

        return entries
    }

    private fun resolveBackupArchive(backupId: String): Path {
        if (!Regex("""^[A-Za-z0-9_-]+$""").matches(backupId)) {
            throw RemoteOperationException("invalid_backup_id")
        }

        val root = backupRoot()
        val archive = root.resolve("$backupId.zip").normalize()
        if (!Files.isRegularFile(archive)) {
            throw RemoteOperationException("backup_not_found")
        }

        val realRoot = root.toRealPath()
        val realArchive = archive.toRealPath()
        if (!realArchive.startsWith(realRoot) || !Files.isRegularFile(realArchive)) {
            throw RemoteOperationException("backup_not_found")
        }
        return realArchive
    }

    private fun restorePathFilters(paths: List<String>): List<String> {
        val filters = paths.map { decodeRelativePath(it).trimEnd('/') }
        return if (filters.any { it == "." }) {
            emptyList()
        } else {
            filters
        }
    }

    private fun matchesRestoreFilter(mount: String, relativePath: String, filters: List<String>): Boolean {
        if (filters.isEmpty()) {
            return true
        }

        val entryPath = "$mount/$relativePath"
        return filters.any { filter ->
            relativePath == filter ||
                    relativePath.startsWith("$filter/") ||
                    entryPath == filter ||
                    entryPath.startsWith("$filter/")
        }
    }

    private fun normalizeZipEntryName(name: String): String {
        val normalized = name.replace('\\', '/').trim()
        if (
            normalized.isBlank() ||
            normalized.startsWith("/") ||
            normalized.startsWith("//") ||
            normalized.any { it.code < 0x20 }
        ) {
            throw RemoteOperationException("backup_integrity_failed")
        }

        val parts = normalized.split("/")
        if (parts.size < 2 || parts.any { it.isBlank() || it == ".." }) {
            throw RemoteOperationException("backup_integrity_failed")
        }

        return normalized
    }

    private fun ensureWritableParent(mount: String, target: Path) {
        val parent = target.parent ?: throw RemoteOperationException("missing_parent")
        Files.createDirectories(parent)

        val realRoot = mountRoot(mount).toRealPath()
        val realParent = parent.toRealPath()
        if (!realParent.startsWith(realRoot)) {
            throw RemoteOperationException("path_outside_allowed_root")
        }
    }

    private fun rejectExistingTargetOutsideRoot(mount: String, target: Path) {
        if (!Files.exists(target)) {
            return
        }

        val realRoot = mountRoot(mount).toRealPath()
        val realTarget = target.toRealPath()
        if (!realTarget.startsWith(realRoot)) {
            throw RemoteOperationException("path_outside_allowed_root")
        }
    }

    private fun addBackupFileWithLimit(
        zip: ZipOutputStream,
        mount: String,
        relativePath: String,
        file: Path,
        entries: MutableList<Map<String, Any?>>,
        currentSize: Long
    ): Long {
        val size = Files.size(file)
        if (currentSize + size > BackendApiPolicy.remoteOpsBackupMaxBytes) {
            throw RemoteOperationException("backup_limit_exceeded")
        }

        addBackupFile(zip, mount, relativePath, file, entries)
        return currentSize + size
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

    private fun isExcludedFromBackup(file: Path, archive: Path): Boolean {
        val normalized = file.toAbsolutePath().normalize()
        val excludedRoots = listOf(
            backupRoot(),
            quarantineRoot(),
            restoreStagingRoot()
        ).map { it.toAbsolutePath().normalize() }

        return normalized == archive.toAbsolutePath().normalize() ||
                excludedRoots.any { normalized.startsWith(it) }
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
            ExistingPathMode.ROOT_MUST_EXIST -> candidate
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
        val lower = path.lowercase(Locale.ROOT)
        val fileName = lower.substringAfterLast('/')
        val blockedExtensions = listOf(
            ".jar",
            ".class",
            ".exe",
            ".dll",
            ".so",
            ".dylib",
            ".bat",
            ".cmd",
            ".ps1",
            ".sh",
            ".bash",
            ".zsh",
            ".fish",
            ".vbs",
            ".js",
            ".jse",
            ".wsf",
            ".py",
            ".rb",
            ".pl",
            ".php"
        )
        val blockedNames = setOf(
            "user_jvm_args.txt",
            "start.sh",
            "start.bat",
            "run.sh",
            "run.bat",
            "server.jar",
            "paper.jar",
            "spigot.jar",
            "bukkit.jar"
        )

        if (blockedExtensions.any { fileName.endsWith(it) } || fileName in blockedNames) {
            throw RemoteOperationException("file_type_blocked")
        }
    }

    private fun newBackupId(prefix: String): String =
        "${prefix}_${timestamp()}_${UUID.randomUUID()}"

    private fun BackupArchiveResult.toResponse(requestedFormat: String): Map<String, Any?> =
        mapOf(
            "backupId" to backupId,
            "requestedFormat" to requestedFormat,
            "actualFormat" to "zip",
            "fileName" to fileName,
            "sizeBytes" to sizeBytes,
            "sha256" to sha256,
            "entryCount" to entryCount
        )

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

    private fun restoreStagingRoot(): Path =
        plugin.dataFolder.toPath().resolve("ops-restore-staging").normalize()

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
    PARENT_MUST_EXIST,
    ROOT_MUST_EXIST
}

private data class ManagedPath(
    val normalizedPath: Path,
    val realPath: Path,
    val relativePath: String
)

private data class BackupArchiveResult(
    val backupId: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val entryCount: Int
)

private data class RestoreEntry(
    val zipEntryName: String,
    val mount: String,
    val relativePath: String,
    val sizeBytes: Long
)
