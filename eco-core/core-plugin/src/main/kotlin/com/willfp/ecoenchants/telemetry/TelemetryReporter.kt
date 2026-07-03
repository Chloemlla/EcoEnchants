package com.willfp.ecoenchants.telemetry

import com.willfp.ecoenchants.backend.BackendApiPolicy
import com.willfp.ecoenchants.backend.BackendApiTrace
import com.willfp.ecoenchants.backend.BackendJson
import com.willfp.ecoenchants.backend.LicenseCheckResult
import com.willfp.ecoenchants.backend.OnlineLicenseGate
import com.willfp.ecoenchants.plugin
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.bukkit.scheduler.BukkitTask

object TelemetryReporter {
    private val lock = Any()
    private val queue = ArrayDeque<Map<String, Any?>>()
    private val sequence = AtomicLong()
    private val warnedMissingToken = AtomicBoolean(false)

    @Volatile
    private var task: BukkitTask? = null

    @Volatile
    private var lastResult = "not started"

    @Volatile
    private var lastSuccessAt: String? = null

    @Volatile
    private var lastFailureAt: String? = null

    private val sentEvents = AtomicLong()
    private val sentBatches = AtomicLong()
    private val droppedEvents = AtomicLong()

    fun start() {
        synchronized(lock) {
            if (queue.isNotEmpty()) {
                ensureTaskLocked()
            }
        }
    }

    fun reload() {
        synchronized(lock) {
            task?.cancel()
            task = null
            if (queue.isNotEmpty()) {
                ensureTaskLocked()
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            task?.cancel()
            task = null
        }
    }

    fun enqueue(event: Map<String, Any?>) {
        if (!RuntimeTelemetryPolicy.enabled || !RuntimeTelemetryPolicy.remoteReportingEnabled) {
            return
        }

        val license = OnlineLicenseGate.lastResult as? LicenseCheckResult.Valid
        if (RuntimeTelemetryPolicy.remoteReportingRequireActivationToken &&
            license?.activationToken.isNullOrBlank()
        ) {
            droppedEvents.incrementAndGet()
            if (warnedMissingToken.compareAndSet(false, true)) {
                BackendApiTrace.event("telemetry.queue", "remote reporting requires activation token, events remain local only")
                plugin.logger.warning(
                    "EcoEnchants telemetry remote reporting is enabled, but the license response " +
                            "did not include an activation token. Events will remain local only."
                )
            }
            return
        }

        synchronized(lock) {
            queue.addLast(event)
            while (queue.size > RuntimeTelemetryPolicy.remoteReportingMaxQueuedEvents) {
                queue.removeFirstOrNull()
                droppedEvents.incrementAndGet()
                BackendApiTrace.event(
                    "telemetry.queue",
                    "dropped oldest event because queue exceeded ${RuntimeTelemetryPolicy.remoteReportingMaxQueuedEvents}"
                )
            }
            ensureTaskLocked()
        }
    }

    fun statusLines(): List<String> = listOf(
        "Telemetry remote reporting",
        "Enabled: ${RuntimeTelemetryPolicy.remoteReportingEnabled}",
        "URL: ${RuntimeTelemetryPolicy.remoteReportingUrl}",
        "Queue: ${queuedEvents()} events",
        "Task active: ${task != null}",
        "Sent batches: ${sentBatches.get()}",
        "Sent events: ${sentEvents.get()}",
        "Dropped events: ${droppedEvents.get()}",
        "Last success: ${lastSuccessAt ?: "never"}",
        "Last failure: ${lastFailureAt ?: "never"}",
        "Last result: $lastResult"
    )

    private fun ensureTaskLocked() {
        if (task != null) {
            return
        }

        task = plugin.scheduler.runAsyncTimer(
            RuntimeTelemetryPolicy.remoteReportingIntervalTicks,
            RuntimeTelemetryPolicy.remoteReportingIntervalTicks
        ) {
            flushOnce()
        }
        lastResult = "scheduled"
        BackendApiTrace.event(
            "telemetry.queue",
            "scheduled remote reporter intervalTicks=${RuntimeTelemetryPolicy.remoteReportingIntervalTicks}"
        )
    }

    private fun flushOnce() {
        val batch = synchronized(lock) {
            if (queue.isEmpty()) {
                task?.cancel()
                task = null
                lastResult = "idle"
                return
            }

            buildList {
                repeat(RuntimeTelemetryPolicy.remoteReportingBatchSize.coerceAtMost(queue.size)) {
                    add(queue.removeFirst())
                }
            }
        }

        BackendApiTrace.event("telemetry.flush", "sending batchSize=${batch.size} remainingQueue=${queuedEvents()}")
        val result = sendBatch(batch)
        synchronized(lock) {
            if (result.success) {
                sentBatches.incrementAndGet()
                sentEvents.addAndGet(batch.size.toLong())
                lastSuccessAt = Instant.now().toString()
                lastResult = "sent ${batch.size} event(s), HTTP ${result.statusCode}"
            } else {
                lastFailureAt = Instant.now().toString()
                lastResult = result.message
                for (event in batch.asReversed()) {
                    queue.addFirst(event)
                }
                while (queue.size > RuntimeTelemetryPolicy.remoteReportingMaxQueuedEvents) {
                    queue.removeLastOrNull()
                    droppedEvents.incrementAndGet()
                    BackendApiTrace.event(
                        "telemetry.queue",
                        "dropped newest event while restoring failed batch; queue exceeded ${RuntimeTelemetryPolicy.remoteReportingMaxQueuedEvents}"
                    )
                }
            }

            if (queue.isEmpty()) {
                task?.cancel()
                task = null
                if (result.success) {
                    lastResult = "idle after ${batch.size} event(s)"
                }
            }
        }
    }

    private fun sendBatch(events: List<Map<String, Any?>>): SendResult {
        val batchId = UUID.randomUUID().toString()
        val payload = batchPayload(batchId, events)
        val body = BackendJson.toJson(payload)
        val uri = URI.create(RuntimeTelemetryPolicy.remoteReportingUrl)
        val requestId = UUID.randomUUID().toString()

        val request = runCatching {
            val builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMillis(RuntimeTelemetryPolicy.remoteReportingTimeoutMillis.toLong()))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .header("User-Agent", userAgent())
                .header("X-Request-Id", requestId)
                .header("Idempotency-Key", batchId)
                .header("X-Eco-Product-Id", BackendApiPolicy.PRODUCT_ID)
                .header("X-Eco-Installation-Id", OnlineLicenseGate.installationId())
                .header("X-Eco-Plugin-Version", plugin.pluginMeta.version)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))

            val token = (OnlineLicenseGate.lastResult as? LicenseCheckResult.Valid)?.activationToken
            if (!token.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $token")
            }

            builder.build()
        }.getOrElse {
            BackendApiTrace.failure("telemetry.events", requestId, message = "could not build request: ${it.message}")
            return SendResult(false, -1, "could not build telemetry request: ${it.message}")
        }

        BackendApiTrace.request("telemetry.events", requestId, "POST", uri, body)
        val startedAt = BackendApiTrace.mark()
        val response = runCatching {
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(RuntimeTelemetryPolicy.remoteReportingTimeoutMillis.toLong()))
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        }.getOrElse {
            BackendApiTrace.failure("telemetry.events", requestId, startedAt, "telemetry endpoint unreachable: ${it.message}")
            return SendResult(false, -1, "telemetry endpoint unreachable: ${it.message}")
        }

        val status = response.statusCode()
        BackendApiTrace.response("telemetry.events", requestId, status, startedAt, response.body())
        if (status in 200..299) {
            return SendResult(true, status, "ok")
        }

        return SendResult(false, status, "telemetry endpoint returned HTTP $status")
    }

    private fun batchPayload(batchId: String, events: List<Map<String, Any?>>): Map<String, Any?> {
        val license = OnlineLicenseGate.lastResult as? LicenseCheckResult.Valid
        return linkedMapOf(
            "productId" to BackendApiPolicy.PRODUCT_ID,
            "installationId" to OnlineLicenseGate.installationId(),
            "activationId" to license?.activationId,
            "plugin" to mapOf(
                "version" to plugin.pluginMeta.version,
                "channel" to BackendApiPolicy.channel
            ),
            "server" to mapOf(
                "platform" to plugin.server.name,
                "platformVersion" to plugin.server.bukkitVersion,
                "minecraftVersion" to plugin.server.minecraftVersion,
                "onlineMode" to plugin.server.onlineMode,
                "javaVersion" to System.getProperty("java.version")
            ),
            "batch" to mapOf(
                "id" to batchId,
                "sequence" to sequence.incrementAndGet(),
                "createdAt" to Instant.now().toString(),
                "eventCount" to events.size
            ),
            "events" to events
        )
    }

    private fun queuedEvents(): Int = synchronized(lock) {
        queue.size
    }

    private fun userAgent(): String =
        "EcoEnchants/${plugin.pluginMeta.version} ${plugin.server.name}/${plugin.server.bukkitVersion} " +
                "Java/${System.getProperty("java.version")}"
}

private data class SendResult(
    val success: Boolean,
    val statusCode: Int,
    val message: String
)
