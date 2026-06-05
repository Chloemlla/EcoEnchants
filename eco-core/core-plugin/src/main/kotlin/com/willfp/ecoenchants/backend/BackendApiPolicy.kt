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

    val channel: String
        get() = plugin.configYml.getString("license.channel")

    val timeoutMillis: Int
        get() = plugin.configYml.getInt("license.timeout-ms").coerceIn(500, 5000)

    val sendServerName: Boolean
        get() = plugin.configYml.getBool("license.send-server-name")

    val sendBuildFingerprint: Boolean
        get() = plugin.configYml.getBool("license.send-build-fingerprint")

    val licenseKey: String
        get() = plugin.configYml.getString("license.key").trim()

    val contractBasePath: String
        get() = "$API_ROOT_PATH/$API_VERSION"

    fun statusLines(): List<String> {
        val result = OnlineLicenseGate.lastResult

        return listOf(
            "EcoEnchants license gate",
            "Mode: required-online",
            "API URL: $apiUrl",
            "Contract: $contractBasePath",
            "Product ID: $PRODUCT_ID",
            "Channel: $channel",
            "Timeout: ${timeoutMillis}ms",
            "Send server name: $sendServerName",
            "Send build fingerprint: $sendBuildFingerprint",
            "Core runtime gating allowed: $CORE_RUNTIME_GATING_ALLOWED",
            "Required startup network allowed: $REQUIRED_STARTUP_NETWORK_ALLOWED",
            "Player privacy collection allowed: $PLAYER_PRIVACY_COLLECTION_ALLOWED",
            "Last check: ${result.summary}"
        )
    }
}
