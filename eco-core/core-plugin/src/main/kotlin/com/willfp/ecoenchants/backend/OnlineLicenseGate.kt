package com.willfp.ecoenchants.backend

import com.willfp.ecoenchants.plugin
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID

object OnlineLicenseGate {
    @Volatile
    var lastResult: LicenseCheckResult = LicenseCheckResult.NotChecked
        private set

    fun verifyStartup(): Boolean {
        val key = BackendApiPolicy.licenseKey
        if (key.isBlank()) {
            return fail("No license key is configured at license.key.")
        }

        val request = runCatching {
            HttpRequest.newBuilder()
                .uri(URI.create("${BackendApiPolicy.versionedApiUrl}/licenses/verify"))
                .timeout(Duration.ofMillis(BackendApiPolicy.timeoutMillis.toLong()))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("User-Agent", userAgent())
                .header("X-Request-Id", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(buildPayload(key), StandardCharsets.UTF_8))
                .build()
        }.getOrElse {
            return fail("Could not build license verification request: ${it.message}")
        }

        val response = runCatching {
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(BackendApiPolicy.timeoutMillis.toLong()))
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        }.getOrElse {
            return fail("License server is unreachable: ${it.message}")
        }

        if (response.statusCode() != 200) {
            return fail("License server returned HTTP ${response.statusCode()}.")
        }

        val body = response.body()
        val status = extractStatus(body)
        if (status == "valid" || status == "trial") {
            lastResult = LicenseCheckResult.Valid(
                status = status,
                activationToken = BackendJson.stringField(body, "activationToken"),
                activationId = BackendJson.stringField(body, "activationId")
            )
            plugin.logger.info("EcoEnchants license verified online with status '$status'.")
            return true
        }

        return fail("License server returned non-runnable status '${status ?: "missing"}'.")
    }

    private fun fail(message: String): Boolean {
        lastResult = LicenseCheckResult.Failed(message)
        plugin.logger.severe("EcoEnchants license verification failed: $message")
        plugin.logger.severe("EcoEnchants requires a successful online license check before core runtime is enabled.")
        return false
    }

    private fun buildPayload(licenseKey: String): String {
        val serverNameField = if (BackendApiPolicy.sendServerName) {
            ",\n                \"name\":\"${json(plugin.server.name)}\""
        } else {
            ""
        }

        val fingerprintField = if (BackendApiPolicy.sendBuildFingerprint) {
            ",\n                \"buildFingerprint\":\"${json(buildFingerprint())}\""
        } else {
            ""
        }

        return """
            {
              "productId":"${BackendApiPolicy.PRODUCT_ID}",
              "licenseKey":"${json(licenseKey)}",
              "installationId":"${json(installationId())}",
              "server":{
                "platform":"${json(plugin.server.name)}",
                "platformVersion":"${json(plugin.server.bukkitVersion)}",
                "minecraftVersion":"${json(plugin.server.minecraftVersion)}",
                "onlineMode":${plugin.server.onlineMode},
                "javaVersion":"${json(System.getProperty("java.version"))}"
                $serverNameField
              },
              "plugin":{
                "version":"${json(plugin.pluginMeta.version)}",
                "channel":"${json(BackendApiPolicy.channel)}"
                $fingerprintField
              }
            }
        """.trimIndent()
    }

    fun installationId(): String {
        val configured = plugin.configYml.getString("license.installation-id").trim()
        if (configured.isNotBlank()) {
            return configured
        }

        val file = plugin.dataFolder.toPath().resolve("license-installation-id.txt")
        if (Files.isRegularFile(file)) {
            return Files.readString(file, StandardCharsets.UTF_8).trim()
        }

        Files.createDirectories(plugin.dataFolder.toPath())
        val generated = UUID.randomUUID().toString()
        Files.writeString(file, generated, StandardCharsets.UTF_8)
        return generated
    }

    private fun buildFingerprint(): String {
        val location = runCatching {
            plugin.javaClass.protectionDomain.codeSource.location.toURI()
        }.getOrNull() ?: return "unavailable"

        val path = Paths.get(location)
        if (!Files.isRegularFile(path)) {
            return "development-directory"
        }

        return "sha256:${sha256(path)}"
    }

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

    private fun userAgent(): String {
        return "EcoEnchants/${plugin.pluginMeta.version} ${plugin.server.name}/${plugin.server.bukkitVersion} " +
                "Java/${System.getProperty("java.version")}"
    }

    private fun extractStatus(body: String): String? {
        return STATUS_REGEX.find(body)?.groupValues?.get(1)?.lowercase()
    }

    private fun json(value: String): String = BackendJson.escape(value)

    private val STATUS_REGEX = Regex(""""status"\s*:\s*"([^"]+)"""")
}

sealed class LicenseCheckResult {
    abstract val summary: String

    data object NotChecked : LicenseCheckResult() {
        override val summary = "not checked"
    }

    data class Valid(
        val status: String,
        val activationToken: String? = null,
        val activationId: String? = null
    ) : LicenseCheckResult() {
        override val summary = if (activationToken.isNullOrBlank()) {
            "$status (no activation token)"
        } else {
            "$status (activation token available)"
        }
    }

    data class Failed(
        val reason: String
    ) : LicenseCheckResult() {
        override val summary = "failed - $reason"
    }
}
