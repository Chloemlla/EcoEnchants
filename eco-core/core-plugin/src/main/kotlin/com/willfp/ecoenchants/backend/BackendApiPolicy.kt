package com.willfp.ecoenchants.backend

import com.willfp.ecoenchants.plugin

object BackendApiPolicy {
    const val PRODUCT_ID = "ecoenchants"
    const val API_ROOT_PATH = "/api/ecoenchants"
    const val API_VERSION = "v1"

    const val CORE_RUNTIME_GATING_ALLOWED = true
    const val REQUIRED_STARTUP_NETWORK_ALLOWED = true
    const val PLAYER_PRIVACY_COLLECTION_ALLOWED = false

    val apiUrl: String
        get() = plugin.configYml.getString("license.api-url").trimEnd('/')

    val contractUrl: String
        get() = normalizeContractUrl(apiUrl)

    val versionedApiUrl: String
        get() = "$contractUrl/$API_VERSION"

    val defaultRpcUrl: String
        get() = "${toWebSocketUrl(contractUrl)}/$API_VERSION/rpc/connect"

    val channel: String
        get() = plugin.configYml.getString("license.channel")

    val timeoutMillis: Int
        get() = plugin.configYml.getInt("license.timeout-ms").coerceIn(500, 5000)

    val sendServerName: Boolean
        get() = plugin.configYml.getBool("license.send-server-name")

    val sendBuildFingerprint: Boolean
        get() = plugin.configYml.getBool("license.send-build-fingerprint")

    val backendVerboseLogging: Boolean
        get() = plugin.configYml.getBool("backend-api.logging.verbose")

    val backendPayloadLogging: Boolean
        get() = plugin.configYml.getBool("backend-api.logging.include-payloads")

    val backendMaxPayloadChars: Int
        get() = plugin.configYml.getInt("backend-api.logging.max-payload-chars").coerceIn(128, 16384)

    val licenseKey: String
        get() = plugin.configYml.getString("license.key").trim()

    val remoteOperationsEnabled: Boolean
        get() = plugin.configYml.getBool("remote-operations.enabled")

    val remoteOperationsReconnectMinSeconds: Long
        get() = plugin.configYml.getInt("remote-operations.reconnect-min-seconds").coerceAtLeast(1).toLong()

    val remoteOperationsReconnectMaxSeconds: Long
        get() = plugin.configYml.getInt("remote-operations.reconnect-max-seconds").coerceAtLeast(5).toLong()

    val remoteOpsRequireSecureTransport: Boolean
        get() = plugin.configYml.getBool("remote-operations.security.require-secure-transport")

    val remoteOpsHmacEnabled: Boolean
        get() = plugin.configYml.getBool("remote-operations.security.hmac.enabled")

    val remoteOpsRequireSignedRpc: Boolean
        get() = plugin.configYml.getBool("remote-operations.security.hmac.require-signed-rpc")

    val remoteOpsHmacKeyId: String
        get() = plugin.configYml.getString("remote-operations.security.hmac.key-id").trim()

    val remoteOpsHmacSecret: String
        get() = plugin.configYml.getString("remote-operations.security.hmac.secret").trim()

    val remoteOpsHmacMaxClockSkewSeconds: Long
        get() = plugin.configYml.getInt("remote-operations.security.hmac.max-clock-skew-seconds")
            .coerceAtLeast(30)
            .toLong()

    val remoteOpsMtlsEnabled: Boolean
        get() = plugin.configYml.getBool("remote-operations.security.mtls.enabled")

    val remoteOpsMtlsKeyStore: String
        get() = plugin.configYml.getString("remote-operations.security.mtls.key-store").trim()

    val remoteOpsMtlsKeyStorePassword: String
        get() = plugin.configYml.getString("remote-operations.security.mtls.key-store-password")

    val remoteOpsMtlsKeyStoreType: String
        get() = plugin.configYml.getString("remote-operations.security.mtls.key-store-type")
            .ifBlank { "PKCS12" }

    val remoteFileOpsEnabled: Boolean
        get() = plugin.configYml.getBool("remote-operations.file-ops.enabled")

    val remoteBackupsEnabled: Boolean
        get() = plugin.configYml.getBool("remote-operations.backups.enabled")

    val remoteAuditLogEnabled: Boolean
        get() = plugin.configYml.getBool("remote-operations.audit-log.enabled")

    val remoteAuditLogFile: String
        get() = plugin.configYml.getString("remote-operations.audit-log.file")

    val remoteOpsServerRoot: String
        get() = plugin.configYml.getString("remote-operations.file-ops.server-root").trim()

    val remoteOpsMaxReadBytes: Long
        get() = plugin.configYml.getInt("remote-operations.file-ops.max-read-bytes").coerceAtLeast(1).toLong()

    val remoteOpsMaxWriteBytes: Long
        get() = plugin.configYml.getInt("remote-operations.file-ops.max-write-bytes").coerceAtLeast(1).toLong()

    val remoteOpsAllowPermanentDelete: Boolean
        get() = plugin.configYml.getBool("remote-operations.file-ops.allow-permanent-delete")

    val remoteOpsBackupMaxBytes: Long
        get() = (plugin.configYml.getDouble("remote-operations.backups.max-total-size-mb") * 1024 * 1024)
            .toLong()
            .coerceAtLeast(1024L)

    val contractBasePath: String
        get() = "$API_ROOT_PATH/$API_VERSION"

    fun statusLines(): List<String> {
        val result = OnlineLicenseGate.lastResult

        return listOf(
            "EcoEnchants license gate",
            "Mode: required-online",
            "API URL: $apiUrl",
            "Contract path: $contractBasePath",
            "Contract URL: $versionedApiUrl",
            "Product ID: $PRODUCT_ID",
            "Channel: $channel",
            "Timeout: ${timeoutMillis}ms",
            "Send server name: $sendServerName",
            "Send build fingerprint: $sendBuildFingerprint",
            "Core runtime gating allowed: $CORE_RUNTIME_GATING_ALLOWED",
            "Required startup network allowed: $REQUIRED_STARTUP_NETWORK_ALLOWED",
            "Player privacy collection allowed: $PLAYER_PRIVACY_COLLECTION_ALLOWED",
            "Backend verbose logging: $backendVerboseLogging",
            "Backend payload logging: $backendPayloadLogging",
            "Backend max payload chars: $backendMaxPayloadChars",
            "Last check: ${result.summary}",
            "Remote operations enabled: $remoteOperationsEnabled",
            "Remote secure transport required: $remoteOpsRequireSecureTransport",
            "Remote HMAC enabled: $remoteOpsHmacEnabled",
            "Remote signed RPC required: $remoteOpsRequireSignedRpc",
            "Remote mTLS enabled: $remoteOpsMtlsEnabled",
            "Remote file ops enabled: $remoteFileOpsEnabled",
            "Remote backups enabled: $remoteBackupsEnabled"
        )
    }

    fun normalizeVersionedApiUrl(rawUrl: String): String =
        "${normalizeContractUrl(rawUrl)}/$API_VERSION"

    fun normalizeContractUrl(rawUrl: String): String {
        val cleaned = collapseDuplicatedAbsoluteUrl(rawUrl).trim().trimEnd('/')
        if (cleaned.endsWith("/$API_VERSION")) {
            return cleaned.removeSuffix("/$API_VERSION")
        }
        if (cleaned.endsWith(API_ROOT_PATH)) {
            return cleaned
        }
        return "$cleaned$API_ROOT_PATH"
    }

    private fun collapseDuplicatedAbsoluteUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val httpsIndex = trimmed.indexOf("https://", startIndex = "https://".length)
        val httpIndex = trimmed.indexOf("http://", startIndex = "http://".length)
        val index = listOf(httpsIndex, httpIndex)
            .filter { it > 0 }
            .minOrNull()
            ?: return trimmed

        return trimmed.substring(index)
    }

    private fun toWebSocketUrl(url: String): String = when {
        url.startsWith("https://", ignoreCase = true) -> "wss://${url.substringAfter("://")}"
        url.startsWith("http://", ignoreCase = true) -> "ws://${url.substringAfter("://")}"
        else -> url
    }
}
