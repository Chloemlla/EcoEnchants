package com.willfp.ecoenchants.backend

import com.willfp.ecoenchants.plugin
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.Locale

object BackendApiTrace {
    val enabled: Boolean
        get() = plugin.configYml.getBool("backend-api.logging.verbose")

    private val includePayloads: Boolean
        get() = plugin.configYml.getBool("backend-api.logging.include-payloads")

    private val maxPayloadChars: Int
        get() = plugin.configYml.getInt("backend-api.logging.max-payload-chars").coerceIn(128, 16384)

    fun mark(): Instant = Instant.now()

    fun request(
        area: String,
        requestId: String,
        method: String,
        uri: URI,
        body: String? = null
    ) {
        if (!enabled) {
            return
        }

        plugin.logger.info(
            "[EcoEnchants API] -> $area requestId=$requestId method=${method.uppercase(Locale.ROOT)} " +
                    "uri=${sanitizeUri(uri)} bodyBytes=${body?.toByteArray(Charsets.UTF_8)?.size ?: 0}"
        )

        if (includePayloads && body != null) {
            plugin.logger.info("[EcoEnchants API] -> $area requestId=$requestId body=${sanitizePayload(body)}")
        }
    }

    fun response(
        area: String,
        requestId: String,
        statusCode: Int,
        startedAt: Instant,
        body: String? = null
    ) {
        if (!enabled) {
            return
        }

        plugin.logger.info(
            "[EcoEnchants API] <- $area requestId=$requestId status=$statusCode " +
                    "durationMs=${durationMillis(startedAt)} bodyBytes=${body?.toByteArray(Charsets.UTF_8)?.size ?: 0}"
        )

        if (includePayloads && body != null) {
            plugin.logger.info("[EcoEnchants API] <- $area requestId=$requestId body=${sanitizePayload(body)}")
        }
    }

    fun failure(area: String, requestId: String, startedAt: Instant? = null, message: String) {
        if (!enabled) {
            return
        }

        val duration = if (startedAt != null) {
            " durationMs=${durationMillis(startedAt)}"
        } else {
            ""
        }

        plugin.logger.info("[EcoEnchants API] !! $area requestId=$requestId$duration error=${sanitizePayload(message)}")
    }

    fun event(area: String, message: String) {
        if (!enabled) {
            return
        }

        plugin.logger.info("[EcoEnchants API] ** $area ${sanitizePayload(message)}")
    }

    private fun durationMillis(startedAt: Instant): Long =
        Duration.between(startedAt, Instant.now()).toMillis().coerceAtLeast(0)

    private fun sanitizeUri(uri: URI): String {
        val query = uri.rawQuery?.let { "?${sanitizePayload(it)}" } ?: ""
        return "${uri.scheme}://${uri.authority}${uri.rawPath ?: ""}$query"
    }

    private fun sanitizePayload(value: String): String {
        var result = value
            .replace(Regex("""(?i)(authorization\s*[:=]\s*bearer\s+)[A-Za-z0-9._~+/=-]+"""), "\$1[redacted]")
            .replace(Regex("""(?i)("?(?:licenseKey|activationToken|sessionToken|token|secret|password|key-store-password|X-Eco-Signature)"?\s*[:=]\s*"?)[^",\s}]+("?|)"""), "\$1[redacted]\$2")
            .replace(Regex("""(?i)(Bearer\s+)[A-Za-z0-9._~+/=-]+"""), "\$1[redacted]")

        if (result.length > maxPayloadChars) {
            result = result.take(maxPayloadChars) + "...[truncated ${result.length - maxPayloadChars} chars]"
        }

        return result
    }
}
