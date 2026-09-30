package com.paulneja.quorvi.connections.services

import com.paulneja.quorvi.core.net.Http
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

@Serializable
data class WorkerRun(
    val id: String,
    val idempotencyKey: String = "",
    val command: String = "",
    val state: String,
    val exitCode: Int? = null,
    val lastSequence: Long = 0,
) {
    val terminal: Boolean get() = state != "RUNNING"
}

@Serializable
data class WorkerEvent(val seq: Long, val type: String, val data: String)

@Serializable
private data class Submitted(val run: WorkerRun, val existing: Boolean = false)

@Serializable
private data class RunBody(val run: WorkerRun)

@Serializable
private data class EventsBody(val events: List<WorkerEvent> = emptyList(), val run: WorkerRun)

@Serializable
data class WorkerHello(val workerId: String, val hostname: String = "", val os: String = "", val protocol: Int, val version: String = "")

@Serializable
data class Paired(val token: String, val workerId: String, val protocol: Int)

class WorkerClient(private val http: Http, baseUrl: String, private val token: String?) {
    private val root = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }
    private val headers get() = buildMap { token?.let { put("Authorization", "Bearer $it") } }

    suspend fun pair(code: String, device: String): Paired =
        json.decodeFromJsonElement(Paired.serializer(), http.postJson("$root/v1/pair", buildJsonObject { put("code", code); put("device", device) }, emptyMap())!!)

    suspend fun hello(): WorkerHello = json.decodeFromString(WorkerHello.serializer(), http.getText("$root/v1/hello", headers))

    suspend fun submit(idempotencyKey: String, command: String, timeoutSec: Int): Pair<WorkerRun, Boolean> {
        val body = http.postJson("$root/v1/runs", buildJsonObject { put("idempotencyKey", idempotencyKey); put("command", command); put("timeoutSec", timeoutSec) }, headers)!!
        val parsed = json.decodeFromJsonElement(Submitted.serializer(), body.jsonObject)
        return parsed.run to parsed.existing
    }

    suspend fun run(id: String): WorkerRun = json.decodeFromString(RunBody.serializer(), http.getText("$root/v1/runs/$id", headers)).run

    suspend fun events(id: String, after: Long, waitSec: Int = 25): Pair<List<WorkerEvent>, WorkerRun> {
        val body = json.decodeFromString(EventsBody.serializer(), http.getText("$root/v1/runs/$id/events?after=$after&wait=$waitSec", headers))
        return body.events to body.run
    }

    suspend fun cancel(id: String) {
        http.postJson("$root/v1/runs/$id/cancel", kotlinx.serialization.json.JsonObject(emptyMap()), headers)
    }
}
