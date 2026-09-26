package com.paulneja.quorvi.agent.models

import android.util.Base64
import com.paulneja.quorvi.core.net.Http
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

object OpenAiImages {
    private const val LIMIT = 20L * 1024 * 1024

    suspend fun generate(http: Http, baseUrl: String, headers: Map<String, String>, model: String, prompt: String): ByteArray? {
        val body = buildJsonObject {
            put("model", model)
            put("prompt", prompt)
            put("n", 1)
            put("size", "1024x1024")
        }
        val reply = http.postJson("${baseUrl.trimEnd('/')}/images/generations", body, headers)?.jsonObject ?: return null
        val first = (reply["data"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        (first["b64_json"] as? JsonPrimitive)?.content?.let { return Base64.decode(it, Base64.DEFAULT) }
        val url = (first["url"] as? JsonPrimitive)?.content ?: return null
        return http.getBytes(url, emptyMap(), LIMIT)
    }
}
