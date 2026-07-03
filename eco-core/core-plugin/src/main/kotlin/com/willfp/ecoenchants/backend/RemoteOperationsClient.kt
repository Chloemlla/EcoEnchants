package com.willfp.ecoenchants.backend

import com.willfp.eco.util.toNiceString
import com.willfp.ecoenchants.enchant.EcoEnchants
import com.willfp.ecoenchants.plugin
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object RemoteOperationsClient {
    private val stopping = AtomicBoolean(false)
    private val reconnectAttempt = AtomicInteger(0)

    @Volatile
    private var status: String = "not started"

    @Volatile
    private var instanceId: String? = null

    @Volatile
    private var policyVersion: String? = null

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var currentSessionToken: String? = null

    fun start() {
        stop()

        if (!BackendApiPolicy.remoteOperationsEnabled) {
            status = "disabled by config"
            return
        }

        stopping.set(false)
        reconnectAttempt.set(0)
        connectAsync()
    }

    fun reload() {
        stop()
        start()
    }

    fun stop() {
        stopping.set(true)
        webSocket?.abort()
        webSocket = null
        currentSessionToken = null
        instanceId = null
        policyVersion = null
        status = "stopped"
    }

    fun statusLines(): List<String> = listOf(
        "Remote operations",
        "Enabled: ${BackendApiPolicy.remoteOperationsEnabled}",
        "Status: $status",
        "Instance ID: ${instanceId ?: "unregistered"}",
        "Policy version: ${policyVersion ?: "unknown"}",
        "RPC URL: ${BackendApiPolicy.defaultRpcUrl}",
        "Supported methods: ${supportedMethods().joinToString(", ")}"
    )

    private fun connectAsync() {
        CompletableFuture.runAsync {
            if (stopping.get()) {
                return@runAsync
            }

            val license = OnlineLicenseGate.lastResult as? LicenseCheckResult.Valid
            val activationToken = license?.activationToken
            if (activationToken.isNullOrBlank()) {
                status = "waiting for activation token from license verification"
                plugin.logger.warning(
                    "EcoEnchants remote operations are enabled, but the license response did not include activationToken."
                )
                return@runAsync
            }

            status = "registering"
            val client = runCatching {
                httpClient()
            }.getOrElse {
                status = "register failed: ${it.message}"
                BackendApiTrace.failure("ops.register", "client-setup", message = "HTTP client setup failed: ${it.message}")
                scheduleReconnect("HTTP client setup failed")
                return@runAsync
            }

            val registerRequestId = UUID.randomUUID().toString()
            val registerPayload = registrationPayload()
            val registerUri = URI.create("${BackendApiPolicy.versionedApiUrl}/ops/instances/register")
            BackendApiTrace.request("ops.register", registerRequestId, "POST", registerUri, registerPayload)
            val registerStartedAt = BackendApiTrace.mark()
            val response = runCatching {
                client.send(
                    registerRequest(activationToken, registerRequestId, registerUri, registerPayload),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                )
            }.getOrElse {
                status = "register failed: ${it.message}"
                BackendApiTrace.failure("ops.register", registerRequestId, registerStartedAt, "register failed: ${it.message}")
                scheduleReconnect("register failed")
                return@runAsync
            }

            BackendApiTrace.response("ops.register", registerRequestId, response.statusCode(), registerStartedAt, response.body())
            if (response.statusCode() !in 200..299) {
                status = "register failed: HTTP ${response.statusCode()}"
                scheduleReconnect("register HTTP ${response.statusCode()}")
                return@runAsync
            }

            val body = response.body()
            val registeredInstanceId = BackendJson.stringField(body, "instanceId")
            val sessionToken = BackendJson.stringField(body, "sessionToken")
            val rpcUrl = BackendJson.stringField(body, "rpcUrl") ?: BackendApiPolicy.defaultRpcUrl

            if (registeredInstanceId.isNullOrBlank() || sessionToken.isNullOrBlank()) {
                status = "register failed: missing instanceId or sessionToken"
                scheduleReconnect("register payload incomplete")
                return@runAsync
            }

            instanceId = registeredInstanceId
            policyVersion = BackendJson.stringField(body, "policyVersion")
            currentSessionToken = sessionToken
            BackendApiTrace.event(
                "ops.register",
                "registered instanceId=$registeredInstanceId policyVersion=${policyVersion ?: "unknown"} rpcUrl=$rpcUrl"
            )
            connectWebSocket(client, normalizeWebSocketUrl(rpcUrl), sessionToken)
        }
    }

    private fun connectWebSocket(client: HttpClient, rpcUrl: String, sessionToken: String) {
        if (stopping.get()) {
            return
        }

        status = "connecting websocket"
        val uri = runCatching {
            URI.create(rpcUrl).also(RemoteOperationSecurity::requireSecureUri)
        }.getOrElse {
            status = "websocket failed: ${it.message}"
            BackendApiTrace.failure("ops.websocket", "uri", message = "websocket URI rejected: ${it.message}")
            scheduleReconnect("websocket URI rejected")
            return
        }

        val requestId = UUID.randomUUID().toString()
        val builder = client.newWebSocketBuilder()
            .connectTimeout(Duration.ofMillis(BackendApiPolicy.timeoutMillis.toLong()))
            .header("Authorization", "Bearer $sessionToken")
            .header("User-Agent", userAgent())
            .header("X-Request-Id", requestId)

        BackendApiTrace.request("ops.websocket", requestId, "GET", uri)
        val startedAt = BackendApiTrace.mark()
        RemoteOperationSecurity.signWebSocket(
            builder,
            uri,
            sessionToken,
            instanceId ?: OnlineLicenseGate.installationId()
        )
            .buildAsync(uri, RpcListener())
            .whenComplete { socket, error ->
                if (error != null) {
                    status = "websocket failed: ${error.message}"
                    BackendApiTrace.failure(
                        "ops.websocket",
                        requestId,
                        startedAt,
                        "websocket connect failed: ${error.message}"
                    )
                    scheduleReconnect("websocket connect failed")
                    return@whenComplete
                }

                webSocket = socket
                BackendApiTrace.event("ops.websocket", "connected requestId=$requestId durationMs=${Duration.between(startedAt, Instant.now()).toMillis().coerceAtLeast(0)}")
            }
    }

    private fun registerRequest(
        activationToken: String,
        requestId: String,
        uri: URI,
        payload: String
    ): HttpRequest {
        RemoteOperationSecurity.requireSecureUri(uri)

        val builder = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(Duration.ofMillis(BackendApiPolicy.timeoutMillis.toLong()))
            .header("Authorization", "Bearer $activationToken")
            .header("Content-Type", "application/json; charset=utf-8")
            .header("User-Agent", userAgent())
            .header("X-Request-Id", requestId)

        RemoteOperationSecurity.signHttpRequest(
            builder,
            "POST",
            uri,
            payload,
            activationToken,
            (OnlineLicenseGate.lastResult as? LicenseCheckResult.Valid)?.activationId ?: OnlineLicenseGate.installationId()
        )

        return builder
            .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
            .build()
    }

    private fun registrationPayload(): String = BackendJson.toJson(
        mapOf(
            "productId" to BackendApiPolicy.PRODUCT_ID,
            "activationId" to ((OnlineLicenseGate.lastResult as? LicenseCheckResult.Valid)?.activationId ?: ""),
            "installationId" to OnlineLicenseGate.installationId(),
            "server" to mapOf(
                "name" to if (BackendApiPolicy.sendServerName) plugin.server.name else null,
                "platform" to plugin.server.name,
                "platformVersion" to plugin.server.bukkitVersion,
                "minecraftVersion" to plugin.server.minecraftVersion,
                "onlineMode" to plugin.server.onlineMode,
                "javaVersion" to System.getProperty("java.version")
            ),
            "plugin" to mapOf(
                "version" to plugin.pluginMeta.version,
                "channel" to BackendApiPolicy.channel
            ),
            "capabilities" to mapOf(
                "fileOps" to BackendApiPolicy.remoteFileOpsEnabled,
                "backupArchive" to BackendApiPolicy.remoteBackupsEnabled,
                "redactedExport" to BackendApiPolicy.remoteFileOpsEnabled,
                "supportedMethods" to supportedMethods()
            )
        )
    )

    private fun supportedMethods(): List<String> = listOf(
        "ops.diagnostics.snapshot",
        "ops.command.runManaged"
    ) + RemoteFileOperations.supportedMethods()

    private fun handleMessage(socket: WebSocket, message: String) {
        val method = BackendJson.stringField(message, "method")
        val requestId = BackendJson.stringField(message, "requestId") ?: UUID.randomUUID().toString()
        val jobId = BackendJson.stringField(message, "jobId")

        if (BackendJson.stringField(message, "type") == "rpc.ping") {
            BackendApiTrace.event("ops.rpc", "received ping requestId=$requestId")
            send(socket, mapOf("type" to "rpc.pong", "requestId" to requestId, "serverTime" to Instant.now().toString()))
            return
        }

        BackendApiTrace.event(
            "ops.rpc",
            "received requestId=$requestId jobId=${jobId ?: "none"} method=${method ?: "missing"} bytes=${message.toByteArray(StandardCharsets.UTF_8).size}"
        )
        runCatching {
            RemoteOperationSecurity.verifyRpcMessage(message, currentSessionToken)
        }.onFailure {
            val code = (it as? RemoteOperationException)?.code ?: "signature_invalid"
            BackendApiTrace.failure("ops.rpc", requestId, message = "signature verification failed code=$code message=${it.message ?: code}")
            sendFailure(socket, requestId, jobId, code, it.message ?: code)
            return
        }

        if (method.isNullOrBlank()) {
            sendFailure(socket, requestId, jobId, "missing_method", "RPC method is required.")
            return
        }

        send(
            socket,
            mapOf(
                "type" to "rpc.ack",
                "requestId" to requestId,
                "jobId" to jobId,
                "status" to "accepted",
                "acceptedAt" to Instant.now().toString()
            )
        )

        CompletableFuture.runAsync {
            val result = runCatching {
                execute(method, message, jobId)
            }

            result.onSuccess {
                BackendApiTrace.event(
                    "ops.rpc",
                    "succeeded requestId=$requestId jobId=${jobId ?: "none"} method=$method"
                )
                send(
                    socket,
                    mapOf(
                        "type" to "rpc.result",
                        "requestId" to requestId,
                        "jobId" to jobId,
                        "status" to "succeeded",
                        "result" to it,
                        "completedAt" to Instant.now().toString()
                    )
                )
            }.onFailure {
                val code = (it as? RemoteOperationException)?.code ?: "operation_failed"
                BackendApiTrace.failure("ops.rpc", requestId, message = "failed jobId=${jobId ?: "none"} method=$method code=$code message=${it.message ?: code}")
                sendFailure(socket, requestId, jobId, code, it.message ?: code)
            }
        }
    }

    private fun execute(method: String, message: String, jobId: String?): Map<String, Any?> {
        RemoteOperationsAuditLog.write(
            "rpc.request",
            mapOf("jobId" to jobId, "method" to method)
        )

        return when (method) {
            "ops.diagnostics.snapshot" -> diagnosticsSnapshot()
            "ops.command.runManaged" -> runManagedCommand(message)
            "ops.file.read" -> RemoteFileOperations.read(message, jobId)
            "ops.file.write" -> RemoteFileOperations.write(message, jobId)
            "ops.file.delete" -> RemoteFileOperations.delete(message, jobId)
            "ops.backup.create" -> RemoteFileOperations.createBackup(message, jobId)
            "ops.backup.restore" -> RemoteFileOperations.restoreBackup(message, jobId)
            else -> throw RemoteOperationException("unsupported_method")
        }
    }

    private fun runManagedCommand(message: String): Map<String, Any?> {
        val commandId = BackendJson.stringField(message, "commandId") ?: throw RemoteOperationException("missing_command_id")

        return when (commandId) {
            "ecoenchants.reload" -> runReload()
            "ecoenchants.services.status" -> mapOf(
                "commandId" to commandId,
                "lines" to (BackendApiPolicy.statusLines() + statusLines())
            )
            else -> throw RemoteOperationException("command_not_allowed")
        }
    }

    private fun runReload(): Map<String, Any?> {
        val future = CompletableFuture<Map<String, Any?>>()
        plugin.scheduler.run {
            runCatching {
                val time = plugin.reloadWithTime()
                mapOf(
                    "commandId" to "ecoenchants.reload",
                    "time" to time.toNiceString(),
                    "enchantCount" to EcoEnchants.values().size
                )
            }.onSuccess {
                future.complete(it)
            }.onFailure {
                future.completeExceptionally(it)
            }
        }
        return future.get(30, TimeUnit.SECONDS)
    }

    private fun diagnosticsSnapshot(): Map<String, Any?> = mapOf(
        "productId" to BackendApiPolicy.PRODUCT_ID,
        "plugin" to mapOf(
            "version" to plugin.pluginMeta.version,
            "loaded" to plugin.isLoaded,
            "enchantCount" to EcoEnchants.values().size
        ),
        "server" to mapOf(
            "platform" to plugin.server.name,
            "bukkitVersion" to plugin.server.bukkitVersion,
            "minecraftVersion" to plugin.server.minecraftVersion,
            "onlineMode" to plugin.server.onlineMode,
            "onlinePlayers" to plugin.server.onlinePlayers.size,
            "maxPlayers" to plugin.server.maxPlayers
        ),
        "license" to OnlineLicenseGate.lastResult.summary,
        "remoteOperations" to status
    )

    private fun sendHello(socket: WebSocket) {
        send(
            socket,
            mapOf(
                "type" to "rpc.hello",
                "requestId" to UUID.randomUUID().toString(),
                "instanceId" to instanceId,
                "policyVersion" to policyVersion,
                "supportedMethods" to supportedMethods()
            )
        )
    }

    private fun sendFailure(
        socket: WebSocket,
        requestId: String,
        jobId: String?,
        code: String,
        message: String
    ) {
        send(
            socket,
            mapOf(
                "type" to "rpc.result",
                "requestId" to requestId,
                "jobId" to jobId,
                "status" to "failed",
                "error" to mapOf(
                    "code" to code,
                    "message" to message
                ),
                "completedAt" to Instant.now().toString()
            )
        )
    }

    private fun send(socket: WebSocket, payload: Map<String, Any?>) {
        socket.sendText(BackendJson.toJson(payload), true)
    }

    private fun scheduleReconnect(reason: String) {
        if (stopping.get()) {
            return
        }

        val attempt = reconnectAttempt.getAndIncrement().coerceAtMost(6)
        val min = BackendApiPolicy.remoteOperationsReconnectMinSeconds
        val max = BackendApiPolicy.remoteOperationsReconnectMaxSeconds.coerceAtLeast(min)
        val delay = (min * (1L shl attempt)).coerceAtMost(max)
        status = "reconnecting in ${delay}s ($reason)"
        BackendApiTrace.event("ops.reconnect", "attempt=${attempt + 1} delaySeconds=$delay reason=$reason")

        CompletableFuture.delayedExecutor(delay, TimeUnit.SECONDS).execute {
            if (!stopping.get()) {
                connectAsync()
            }
        }
    }

    private fun normalizeWebSocketUrl(url: String): String = when {
        url.startsWith("https://", ignoreCase = true) -> "wss://${url.substringAfter("://")}"
        url.startsWith("http://", ignoreCase = true) -> "ws://${url.substringAfter("://")}"
        else -> url
    }

    private fun httpClient(): HttpClient =
        RemoteOperationSecurity.configureClient(HttpClient.newBuilder())
            .connectTimeout(Duration.ofMillis(BackendApiPolicy.timeoutMillis.toLong()))
            .build()

    private fun userAgent(): String {
        return "EcoEnchants/${plugin.pluginMeta.version} ${plugin.server.name}/${plugin.server.bukkitVersion} " +
                "Java/${System.getProperty("java.version")}"
    }

    private class RpcListener : WebSocket.Listener {
        private val buffer = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            status = "connected"
            reconnectAttempt.set(0)
            webSocket.request(1)
            sendHello(webSocket)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buffer.append(data)
            if (last) {
                val message = buffer.toString()
                buffer.setLength(0)
                handleMessage(webSocket, message)
            }
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            status = "closed: $statusCode $reason"
            scheduleReconnect("websocket closed")
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            status = "websocket error: ${error.message}"
            scheduleReconnect("websocket error")
        }
    }
}
