package com.paulneja.quorvi.agent.models

import com.paulneja.quorvi.core.net.Http
import com.paulneja.quorvi.core.net.HttpStatusException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

data class CopilotModel(val id: String, val name: String, val vendor: String?, val vision: Boolean, val responsesOnly: Boolean, val inPicker: Boolean)

data class CopilotQuota(val entitlement: Double, val remaining: Double, val unlimited: Boolean, val overagePermitted: Boolean)

data class CopilotStatus(val plan: String, val apiUrl: String, val chatEnabled: Boolean, val premium: CopilotQuota?, val resetDate: String?)

object CopilotParsing {
    const val DEFAULT_API = "https://api.githubcopilot.com"

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull

    fun status(json: JsonObject): CopilotStatus? {
        val plan = json.str("copilot_plan") ?: return null
        val snapshots = json["quota_snapshots"] as? JsonObject
        val premium = (snapshots?.get("premium_interactions") as? JsonObject)?.let { q ->
            CopilotQuota(q.num("entitlement") ?: 0.0, q.num("quota_remaining") ?: q.num("remaining") ?: 0.0, q.bool("unlimited") ?: false, q.bool("overage_permitted") ?: false)
        }
        val api = (json["endpoints"] as? JsonObject)?.str("api") ?: DEFAULT_API
        return CopilotStatus(plan, api, json.bool("chat_enabled") ?: true, premium, json.str("quota_reset_date_utc") ?: json.str("quota_reset_date"))
    }

    fun models(json: JsonObject): List<CopilotModel> = (json["data"] as? JsonArray).orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val caps = o["capabilities"] as? JsonObject
        if (caps?.str("type") != "chat") return@mapNotNull null
        if ((o["policy"] as? JsonObject)?.str("state") == "disabled") return@mapNotNull null
        val endpoints = (o["supported_endpoints"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        val chat = endpoints == null || "/chat/completions" in endpoints
        if (!chat && "/responses" !in endpoints.orEmpty()) return@mapNotNull null
        val id = o.str("id") ?: return@mapNotNull null
        CopilotModel(
            id = id,
            name = o.str("name") ?: id,
            vendor = o.str("vendor"),
            vision = (caps["supports"] as? JsonObject)?.bool("vision") == true,
            responsesOnly = !chat,
            inPicker = o.bool("model_picker_enabled") == true,
        )
    }.distinctBy { it.id }.sortedWith(compareByDescending<CopilotModel> { it.inPicker }.thenBy { it.vendor.orEmpty() }.thenBy { it.name })
}

class CopilotClient(private val http: Http, private val apiUrl: String, private val githubToken: String) : AiClient {
    private val chat = OpenAiCompatibleClient(http, apiUrl, githubToken)
    private val responses = ResponsesClient(http, apiUrl, githubToken)

    override fun stream(request: AiRequest): Flow<AiEvent> = flow {
        val model = models().firstOrNull { it.id == request.model }
            ?: runCatching { models(refresh = true) }.getOrDefault(emptyList()).firstOrNull { it.id == request.model }
        when {
            model?.responsesOnly == true -> emitAll(responses.stream(request))
            model != null -> emitAll(chat.stream(request))
            else -> try {
                emitAll(chat.stream(request))
            } catch (e: HttpStatusException) {
                if (e.status == 400 && (e.body.contains("responses", true) || e.body.contains("not supported", true))) emitAll(responses.stream(request)) else throw e
            }
        }
    }

    override suspend fun listModels(): List<AiModelInfo> = models(refresh = true).map { AiModelInfo(it.id, listOfNotNull(it.name, it.vendor).joinToString(" · "), it.vision) }

    private suspend fun models(refresh: Boolean = false): List<CopilotModel> {
        if (!refresh) cache[apiUrl]?.let { return it }
        val json = http.getJson("${apiUrl.trimEnd('/')}/models", mapOf("Authorization" to "Bearer $githubToken", "Accept" to "application/json")).jsonObject
        return CopilotParsing.models(json).also { cache[apiUrl] = it }
    }

    companion object {
        private val cache = ConcurrentHashMap<String, List<CopilotModel>>()
    }
}

class BridgeClient(private val http: Http, bridgeUrl: String, private val pairingCode: String, private val githubToken: String) : AiClient {
    private val root = normalize(bridgeUrl)

    override fun stream(request: AiRequest): Flow<AiEvent> =
        OpenAiCompatibleClient(http, "$root/v1", pairingCode, headers(request.conversationId)).stream(request)

    override suspend fun listModels(): List<AiModelInfo> = OpenAiCompatibleClient(http, "$root/v1", pairingCode, headers(null)).listModels()

    suspend fun health(): String {
        val json = http.getJson("$root/health", emptyMap()).jsonObject
        return listOfNotNull((json["name"] as? JsonPrimitive)?.contentOrNull, (json["version"] as? JsonPrimitive)?.contentOrNull).joinToString(" ")
    }

    private fun headers(conversationId: String?) = buildMap {
        put("X-GitHub-Token", githubToken)
        if (conversationId != null) put("X-Orbit-Conversation", conversationId)
    }

    companion object {
        fun normalize(url: String): String {
            var u = url.trim().trimEnd('/')
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
            return u.removeSuffix("/v1").trimEnd('/')
        }
    }
}
