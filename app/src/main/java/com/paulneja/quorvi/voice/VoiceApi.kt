package com.paulneja.quorvi.voice

import android.util.Base64
import com.paulneja.quorvi.agent.models.ProviderDao
import com.paulneja.quorvi.agent.models.ProviderEntity
import com.paulneja.quorvi.agent.models.ProviderKind
import com.paulneja.quorvi.core.net.Http
import com.paulneja.quorvi.core.net.HttpStatusException
import com.paulneja.quorvi.core.security.SecretVault
import com.paulneja.quorvi.core.settings.VoiceConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class VoiceException(val reason: Reason, message: String? = null) : Exception(message ?: reason.name) {
    enum class Reason { NO_PROVIDER, NOT_UNDERSTOOD, FAILED }
}

class Speech(val pcm: Pcm?, val encoded: ByteArray?)

@Singleton
class VoiceApi @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val dao: ProviderDao,
    private val vault: SecretVault,
    private val http: Http,
) {
    private fun style(c: VoiceConfig) = c.style.ifBlank { context.getString(com.paulneja.quorvi.R.string.voice_default_style) }

    data class Endpoint(val provider: ProviderEntity, val baseUrl: String, val key: String?) {
        val gemini: Boolean get() = "generativelanguage.googleapis.com" in baseUrl
        val headers: Map<String, String> get() = key?.takeIf { it.isNotBlank() }?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()
    }

    suspend fun candidates(): List<ProviderEntity> = dao.all().filter { it.kind == ProviderKind.OPENAI_COMPATIBLE.name || it.kind == ProviderKind.OPENAI_RESPONSES.name }

    suspend fun endpoint(config: VoiceConfig): Endpoint? {
        val all = candidates()
        val p = all.firstOrNull { it.id == config.providerId } ?: all.firstOrNull() ?: return null
        return Endpoint(p, p.baseUrl.ifBlank { "https://api.openai.com/v1" }.trimEnd('/'), vault.get(SecretVault.provider(p.id)))
    }

    fun sttModel(e: Endpoint, c: VoiceConfig) = VoiceModels.clean(c.sttModel.ifBlank { if (e.gemini) e.provider.model else "gpt-4o-mini-transcribe" })
    fun ttsModel(e: Endpoint, c: VoiceConfig) = VoiceModels.clean(c.ttsModel.ifBlank { if (e.gemini) "gemini-2.5-flash-preview-tts" else "gpt-4o-mini-tts" })
    fun voice(e: Endpoint, c: VoiceConfig) = VoiceModels.voiceFor(c.voice, e.gemini)

    suspend fun transcribe(wav: ByteArray, config: VoiceConfig): String {
        val e = endpoint(config) ?: throw VoiceException(VoiceException.Reason.NO_PROVIDER)
        return logged("stt ${sttModel(e, config)}") { transcribeWith(e, config, wav) }
    }

    private suspend fun transcribeWith(e: Endpoint, config: VoiceConfig, wav: ByteArray): String {
        val model = sttModel(e, config)
        if (!e.gemini) {
            try {
                return transcription(e, model, wav)
            } catch (x: HttpStatusException) {
                if (x.status !in setOf(400, 404, 405, 415, 422, 501)) throw x
            }
        }
        val chatModel = if (Regex("transcribe|whisper", RegexOption.IGNORE_CASE).containsMatchIn(model)) e.provider.model else model
        return byChat(e, chatModel, wav)
    }

    private suspend fun transcription(e: Endpoint, model: String, wav: ByteArray): String {
        val reply = http.postMultipart("${e.baseUrl}/audio/transcriptions", mapOf("model" to model, "response_format" to "json"), Triple("file", "voice.wav", wav), "audio/wav", e.headers)
        return (reply.jsonObject["text"] as? JsonPrimitive)?.content.orEmpty().trim()
    }

    private suspend fun byChat(e: Endpoint, model: String, wav: ByteArray): String {
        val body = buildJsonObject {
            put("model", model)
            put("temperature", 0)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", "You transcribe speech. Reply with only the exact words spoken, in the language they were spoken, with normal punctuation. If nothing intelligible was said, reply with nothing.")
                }
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "input_audio")
                            putJsonObject("input_audio") { put("data", Base64.encodeToString(wav, Base64.NO_WRAP)); put("format", "wav") }
                        }
                    }
                }
            }
        }
        val reply = http.postJson("${e.baseUrl}/chat/completions", body, e.headers)?.jsonObject ?: return ""
        val message = ((reply["choices"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("message") as? JsonObject
        return (message?.get("content") as? JsonPrimitive)?.content.orEmpty().trim()
    }

    suspend fun speak(text: String, config: VoiceConfig): Speech {
        val e = endpoint(config) ?: throw VoiceException(VoiceException.Reason.NO_PROVIDER)
        return logged("tts ${ttsModel(e, config)} ${voice(e, config)}") {
            if (!e.gemini) return@logged openAiSpeech(e, ttsModel(e, config), voice(e, config), text, style(config))
            try {
                geminiSpeech(e, ttsModel(e, config), voice(e, config), text, style(config))
            } catch (x: HttpStatusException) {
                if (x.status != 400) throw x
                geminiSpeech(e, ttsModel(e, config), voice(e, config), text, "")
            }
        }
    }

    private suspend fun <T> logged(what: String, block: suspend () -> T): T = try {
        block()
    } catch (x: HttpStatusException) {
        val reason = explain(x.body)
        android.util.Log.w("NexoVoice", "$what → HTTP ${x.status}: $reason")
        throw VoiceException(VoiceException.Reason.FAILED, "HTTP ${x.status}: $reason")
    }

    private fun explain(body: String): String = runCatching {
        val error = http.json.parseToJsonElement(body).jsonObject["error"]
        ((error as? JsonObject)?.get("message") as? JsonPrimitive)?.content ?: (error as? JsonPrimitive)?.content
    }.getOrNull()?.take(240) ?: body.take(240)

    private suspend fun openAiSpeech(e: Endpoint, model: String, voice: String, text: String, style: String): Speech {
        val body = buildJsonObject {
            put("model", model)
            put("voice", voice)
            put("input", text)
            put("response_format", "wav")

            if (style.isNotBlank() && "gpt-4o" in model) put("instructions", style)
        }
        val (bytes, mime) = http.postJsonForBytes("${e.baseUrl}/audio/speech", body, e.headers, AUDIO_LIMIT)
        Wav.decode(bytes)?.let { return Speech(it, null) }
        if (mime.contains("L16", true) || mime.contains("pcm", true)) return Speech(Pcm(bytes, Wav.rateOf(mime)), null)
        return Speech(null, bytes)
    }

    private suspend fun geminiSpeech(e: Endpoint, model: String, voice: String, text: String, style: String): Speech {
        val native = e.baseUrl.substringBefore("/openai").trimEnd('/')

        val prompt = if (style.isBlank()) text else "${style.trim().trimEnd('.')}: $text"
        val body = buildJsonObject {
            putJsonArray("contents") { addJsonObject { putJsonArray("parts") { addJsonObject { put("text", prompt) } } } }
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") { add("AUDIO") }
                putJsonObject("speechConfig") { putJsonObject("voiceConfig") { putJsonObject("prebuiltVoiceConfig") { put("voiceName", voice) } } }
            }
        }
        val headers = e.key?.let { mapOf("x-goog-api-key" to it) }.orEmpty()
        val reply = http.postJson("$native/models/$model:generateContent", body, headers)?.jsonObject ?: throw VoiceException(VoiceException.Reason.FAILED, "empty reply")
        val part = ((((reply["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("content") as? JsonObject)?.get("parts") as? JsonArray)
            ?.firstOrNull { (it as? JsonObject)?.containsKey("inlineData") == true } as? JsonObject
        val inline = part?.get("inlineData") as? JsonObject ?: throw VoiceException(VoiceException.Reason.FAILED, "no audio in reply")
        val data = Base64.decode((inline["data"] as JsonPrimitive).content, Base64.DEFAULT)
        val mime = (inline["mimeType"] as? JsonPrimitive)?.content.orEmpty()
        return Wav.decode(data)?.let { Speech(it, null) } ?: Speech(Pcm(data, Wav.rateOf(mime)), null)
    }

    companion object {
        const val AUDIO_LIMIT = 24L * 1024 * 1024
    }
}
