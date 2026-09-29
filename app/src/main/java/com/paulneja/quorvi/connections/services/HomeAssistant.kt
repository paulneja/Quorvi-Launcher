package com.paulneja.quorvi.connections.services

import com.paulneja.quorvi.core.net.Http
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

data class HaState(val entityId: String, val state: String, val name: String, val unit: String?)

object HaParsing {
    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

    fun state(element: JsonElement): HaState? {
        val o = element as? JsonObject ?: return null
        val id = o.s("entity_id") ?: return null
        val attributes = o["attributes"] as? JsonObject
        return HaState(id, o.s("state").orEmpty(), attributes?.s("friendly_name") ?: id, attributes?.s("unit_of_measurement"))
    }

    fun states(element: JsonElement): List<HaState> = (element as? JsonArray).orEmpty().mapNotNull(::state)

    fun changed(message: JsonObject): HaState? {
        if (message.s("type") != "event") return null
        val data = message["event"]?.jsonObject?.get("data") as? JsonObject ?: return null
        return data["new_state"]?.let(::state)
    }

    fun toggleService(entityId: String): Pair<String, String>? = when (entityId.substringBefore('.')) {
        "light", "switch", "fan", "input_boolean", "automation" -> entityId.substringBefore('.') to "toggle"
        "scene" -> "scene" to "turn_on"
        "script" -> "script" to "turn_on"
        "cover" -> "cover" to "toggle"
        "lock" -> null
        else -> null
    }
}

class HomeAssistantClient(private val http: Http, baseUrl: String, private val token: String) {
    private val root = baseUrl.trimEnd('/')
    private val headers get() = mapOf("Authorization" to "Bearer $token", "Content-Type" to "application/json")

    suspend fun check(): String = http.getJson("$root/api/", headers).jsonObject["message"]?.jsonPrimitive?.contentOrNull ?: "ok"

    suspend fun states(): List<HaState> = HaParsing.states(http.getJson("$root/api/states", headers))

    suspend fun callService(domain: String, service: String, entityId: String) {
        require(Regex("^[a-z_]+$").matches(domain) && Regex("^[a-z_]+$").matches(service)) { "Invalid service" }
        http.postJson("$root/api/services/$domain/$service", buildJsonObject { put("entity_id", entityId) }, headers)
    }

    fun events(client: OkHttpClient): Flow<HaState> = callbackFlow {
        val wsUrl = root.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/api/websocket"
        val socket = client.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching { http.json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                when ((message["type"] as? JsonPrimitive)?.contentOrNull) {
                    "auth_required" -> webSocket.send(buildJsonObject { put("type", "auth"); put("access_token", token) }.toString())
                    "auth_ok" -> webSocket.send(buildJsonObject { put("id", 1); put("type", "subscribe_events"); put("event_type", "state_changed") }.toString())
                    "auth_invalid" -> close(IllegalStateException("Home Assistant rejected the token"))
                    else -> HaParsing.changed(message)?.let { trySend(it) }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }
        })
        awaitClose { socket.cancel() }
    }
}
