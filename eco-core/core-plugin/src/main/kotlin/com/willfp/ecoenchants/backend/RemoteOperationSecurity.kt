package com.willfp.ecoenchants.backend

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.WebSocket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

object RemoteOperationSecurity {
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private val random = SecureRandom()
    private val seenNonces = ConcurrentHashMap<String, Long>()

    fun requireSecureUri(uri: URI) {
        if (!BackendApiPolicy.remoteOpsRequireSecureTransport) {
            return
        }

        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "https" && scheme != "wss") {
            throw RemoteOperationException("insecure_transport")
        }
    }

    fun configureClient(builder: HttpClient.Builder): HttpClient.Builder {
        val context = mtlsSslContext() ?: return builder
        return builder.sslContext(context)
    }

    fun signHttpRequest(
        builder: HttpRequest.Builder,
        method: String,
        uri: URI,
        body: String,
        fallbackSecret: String,
        fallbackKeyId: String
    ): HttpRequest.Builder {
        if (!BackendApiPolicy.remoteOpsHmacEnabled) {
            return builder
        }

        val signature = hmacSignature(
            secret = signingSecret(fallbackSecret),
            canonical = canonicalHttpRequest(method, uri, body)
        )

        return builder
            .header("X-Eco-Key-Id", keyId(fallbackKeyId))
            .header("X-Eco-Timestamp", signature.timestamp)
            .header("X-Eco-Nonce", signature.nonce)
            .header("X-Eco-Signature", signature.value)
    }

    fun signWebSocket(
        builder: WebSocket.Builder,
        uri: URI,
        fallbackSecret: String,
        fallbackKeyId: String
    ): WebSocket.Builder {
        if (!BackendApiPolicy.remoteOpsHmacEnabled) {
            return builder
        }

        val signature = hmacSignature(
            secret = signingSecret(fallbackSecret),
            canonical = canonicalHttpRequest("GET", uri, "")
        )

        return builder
            .header("X-Eco-Key-Id", keyId(fallbackKeyId))
            .header("X-Eco-Timestamp", signature.timestamp)
            .header("X-Eco-Nonce", signature.nonce)
            .header("X-Eco-Signature", signature.value)
    }

    fun verifyRpcMessage(message: String, fallbackSecret: String?) {
        if (!BackendApiPolicy.remoteOpsHmacEnabled || !BackendApiPolicy.remoteOpsRequireSignedRpc) {
            return
        }

        val signature = stringOrNumber(message, "signature")
            ?: BackendJson.stringField(message, "X-Eco-Signature")
            ?: throw RemoteOperationException("signature_missing")
        val timestamp = stringOrNumber(message, "timestamp")
            ?: BackendJson.stringField(message, "X-Eco-Timestamp")
            ?: throw RemoteOperationException("timestamp_missing")
        val nonce = stringOrNumber(message, "nonce")
            ?: BackendJson.stringField(message, "X-Eco-Nonce")
            ?: throw RemoteOperationException("nonce_missing")
        val providedKeyId = BackendJson.stringField(message, "keyId")
            ?: BackendJson.stringField(message, "X-Eco-Key-Id")
        val requiredKeyId = BackendApiPolicy.remoteOpsHmacKeyId
        if (requiredKeyId.isNotBlank()) {
            if (providedKeyId.isNullOrBlank()) {
                throw RemoteOperationException("key_id_missing")
            }
            if (providedKeyId != requiredKeyId) {
                throw RemoteOperationException("key_id_invalid")
            }
        }

        val timestampSeconds = timestamp.toLongOrNull()
            ?: throw RemoteOperationException("timestamp_invalid")
        val nowSeconds = Instant.now().epochSecond
        val skew = BackendApiPolicy.remoteOpsHmacMaxClockSkewSeconds
        if (kotlin.math.abs(nowSeconds - timestampSeconds) > skew) {
            throw RemoteOperationException("timestamp_out_of_range")
        }

        val issuedAt = BackendJson.stringField(message, "issuedAt")
        if (issuedAt != null && parseInstantOrNull(issuedAt)?.isAfter(Instant.now().plusSeconds(skew)) == true) {
            throw RemoteOperationException("issued_at_invalid")
        }

        val expiresAt = BackendJson.stringField(message, "expiresAt")
        if (expiresAt != null && parseInstantOrNull(expiresAt)?.isBefore(Instant.now()) == true) {
            throw RemoteOperationException("request_expired")
        }

        val secret = verifyingSecret(fallbackSecret)
        val expected = hmacHex(secret, canonicalRpcMessage(message, timestamp, nonce))
        if (!constantTimeEquals(expected, signature)) {
            throw RemoteOperationException("signature_invalid")
        }

        rememberNonce(nonce, nowSeconds)
    }

    private fun canonicalHttpRequest(method: String, uri: URI, body: String): String {
        val timestamp = currentTimestamp()
        val nonce = nonce()
        return listOf(
            method.uppercase(Locale.ROOT),
            uri.rawPath ?: "",
            uri.rawQuery ?: "",
            timestamp,
            nonce,
            sha256(body.toByteArray(StandardCharsets.UTF_8))
        ).joinToString("\n")
    }

    private fun hmacSignature(secret: String, canonical: String): HmacSignature {
        val parts = canonical.split('\n')
        val timestamp = parts.getOrNull(3) ?: currentTimestamp()
        val nonce = parts.getOrNull(4) ?: nonce()
        return HmacSignature(
            timestamp = timestamp,
            nonce = nonce,
            value = hmacHex(secret, canonical)
        )
    }

    private fun canonicalRpcMessage(message: String, timestamp: String, nonce: String): String {
        val scalarFields = listOf(
            "type",
            "requestId",
            "jobId",
            "method",
            "commandId",
            "mount",
            "path",
            "mode",
            "contentSha256",
            "backupId",
            "archiveSha256",
            "redactionPolicy",
            "format",
            "offset",
            "limitBytes"
        ).joinToString("\n") { field ->
            stringOrNumber(message, field) ?: ""
        }

        val arrayFields = listOf(
            "mounts",
            "paths",
            "restorePaths"
        ).joinToString("\n") { field ->
            BackendJson.stringArrayField(message, field).joinToString(",")
        }

        return listOf(
            "RPC",
            timestamp,
            nonce,
            scalarFields,
            arrayFields
        ).joinToString("\n")
    }

    private fun mtlsSslContext(): SSLContext? {
        if (!BackendApiPolicy.remoteOpsMtlsEnabled) {
            return null
        }

        val keyStorePath = BackendApiPolicy.remoteOpsMtlsKeyStore
        if (keyStorePath.isBlank()) {
            throw RemoteOperationException("mtls_keystore_missing")
        }

        val password = BackendApiPolicy.remoteOpsMtlsKeyStorePassword.toCharArray()
        val keyStore = KeyStore.getInstance(BackendApiPolicy.remoteOpsMtlsKeyStoreType)
        Files.newInputStream(Paths.get(keyStorePath)).use { input ->
            keyStore.load(input, password)
        }

        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, password)

        return SSLContext.getInstance("TLS").apply {
            init(keyManagerFactory.keyManagers, null, random)
        }
    }

    private fun signingSecret(fallback: String): String =
        BackendApiPolicy.remoteOpsHmacSecret.ifBlank { fallback }

    private fun verifyingSecret(fallback: String?): String =
        BackendApiPolicy.remoteOpsHmacSecret.ifBlank {
            fallback ?: throw RemoteOperationException("hmac_secret_missing")
        }

    private fun keyId(fallback: String): String =
        BackendApiPolicy.remoteOpsHmacKeyId.ifBlank { fallback }

    private fun currentTimestamp(): String =
        Instant.now().epochSecond.toString()

    private fun nonce(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun rememberNonce(nonce: String, nowSeconds: Long) {
        if (seenNonces.size > 4096) {
            val cutoff = nowSeconds - BackendApiPolicy.remoteOpsHmacMaxClockSkewSeconds
            seenNonces.entries.removeIf { it.value < cutoff }
        }

        if (seenNonces.putIfAbsent(nonce, nowSeconds) != null) {
            throw RemoteOperationException("replay_detected")
        }
    }

    private fun stringOrNumber(json: String, name: String): String? =
        BackendJson.stringField(json, name)
            ?: BackendJson.longField(json, name)?.toString()

    private fun hmacHex(secret: String, canonical: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), HMAC_ALGORITHM))
        return mac.doFinal(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun constantTimeEquals(expected: String, actual: String): Boolean =
        MessageDigest.isEqual(
            expected.lowercase(Locale.ROOT).toByteArray(StandardCharsets.US_ASCII),
            actual.lowercase(Locale.ROOT).toByteArray(StandardCharsets.US_ASCII)
        )

    private fun parseInstantOrNull(value: String): Instant? =
        runCatching { Instant.parse(value) }.getOrNull()

    private data class HmacSignature(
        val timestamp: String,
        val nonce: String,
        val value: String
    )
}
