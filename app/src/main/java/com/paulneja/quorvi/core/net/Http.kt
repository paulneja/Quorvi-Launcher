package com.paulneja.quorvi.core.net

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody

enum class NetFailure { OFFLINE, TIMEOUT }

class NetException(val failure: NetFailure, cause: Throwable? = null) : IOException(
    failure.name + (cause?.let { c -> ": " + (c::class.simpleName ?: "IOException") + (c.message?.let { " " + it.take(200) } ?: "") } ?: ""),
    cause,
)

class HttpStatusException(val status: Int, val body: String, val headers: Map<String, String> = emptyMap()) : IOException("HTTP $status") {
    val unauthorized: Boolean get() = status == 401 || status == 403 && !rateLimited
    val rateLimited: Boolean get() = status == 429 || (status == 403 && headers["x-ratelimit-remaining"] == "0")
    val transient: Boolean get() = status == 429 || status in 500..599
}

data class SseEvent(val event: String?, val data: String)

@Module
@InstallIn(SingletonComponent::class)
object NetModule {
    @Provides
    @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    @Provides
    @Singleton
    fun client(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followSslRedirects(false)
        .build()
}

@Singleton
open class Http @Inject constructor(client: OkHttpClient, val json: Json) {
    private val plain = client
    private val streaming: OkHttpClient = client.newBuilder()
        .followRedirects(false)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    open fun stream(url: String, body: JsonElement, headers: Map<String, String>): Flow<SseEvent> = flow {
        val request = Request.Builder().url(url)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .header("Accept", "text/event-stream")
            .cacheControl(CacheControl.FORCE_NETWORK)
            .post(body.toString().toRequestBody(JSON))
            .build()
        exchange(streaming, request) { r ->
            if (!r.isSuccessful) throw HttpStatusException(r.code, r.body.string().take(2000), r.headers.toMap())
            val source = r.body.source()
            val parser = SseParser()
            while (true) {
                val line = try { source.readUtf8Line() } catch (e: IOException) { currentCoroutineContext().ensureActive(); throw NetException(NetFailure.OFFLINE, e) } ?: break
                parser.feed(line)?.let { emit(it) }
            }
            parser.finish()?.let { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

    data class Reply(val status: Int, val body: String, val headers: Map<String, String>)

    open suspend fun request(method: String, url: String, headers: Map<String, String>, body: String?, limit: Long): Reply = withContext(Dispatchers.IO) {
        val payload = body?.toRequestBody((headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value ?: "application/json").toMediaType())
        val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }
            .method(method.uppercase(), if (method.uppercase() in setOf("GET", "HEAD")) null else payload ?: ByteArray(0).toRequestBody(null))
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        exchange(plain, request) { r ->
            val src = r.body.source()
            src.request(limit + 1)
            if (src.buffer.size > limit) throw HttpStatusException(413, "Response larger than $limit bytes")
            Reply(r.code, src.readUtf8(), r.headers.toMap())
        }
    }

    open suspend fun getJson(url: String, headers: Map<String, String>): JsonElement = json.parseToJsonElement(getText(url, headers))

    open suspend fun getText(url: String, headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.cacheControl(CacheControl.FORCE_NETWORK).build()
        exchange(plain, request) { r ->
            if (!r.isSuccessful) throw HttpStatusException(r.code, r.body.string().take(2000), r.headers.toMap())
            r.body.string()
        }
    }

    open suspend fun getBytes(url: String, headers: Map<String, String>, limit: Long): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        exchange(plain, request) { r ->
            if (!r.isSuccessful) throw HttpStatusException(r.code, r.body.string().take(2000), r.headers.toMap())
            val length = r.body.contentLength()
            if (length > limit) throw HttpStatusException(413, "Response larger than $limit bytes")
            r.body.source().let { src ->
                src.request(limit + 1)
                if (src.buffer.size > limit) throw HttpStatusException(413, "Response larger than $limit bytes")
                src.readByteArray()
            }
        }
    }

    open suspend fun postJson(url: String, body: JsonElement, headers: Map<String, String>): JsonElement? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }
            .post(body.toString().toRequestBody(JSON)).build()
        exchange(plain, request) { r ->
            val text = r.body.string()
            if (!r.isSuccessful) throw HttpStatusException(r.code, text.take(2000), r.headers.toMap())
            text.takeIf { it.isNotBlank() }?.let { json.parseToJsonElement(it) }
        }
    }

    open suspend fun postForm(url: String, form: Map<String, String>, headers: Map<String, String>): JsonElement = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.post(body).build()
        exchange(plain, request) { r ->
            val text = r.body.string()
            if (!r.isSuccessful) throw HttpStatusException(r.code, text.take(2000), r.headers.toMap())
            json.parseToJsonElement(text)
        }
    }

    open suspend fun postMultipart(url: String, fields: Map<String, String>, file: Triple<String, String, ByteArray>, mime: String, headers: Map<String, String>): JsonElement = withContext(Dispatchers.IO) {
        val body = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
            .apply { fields.forEach { (k, v) -> addFormDataPart(k, v) } }
            .addFormDataPart(file.first, file.second, file.third.toRequestBody(mime.toMediaType()))
            .build()
        val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.post(body).build()
        exchange(streaming, request) { r ->
            val text = r.body.string()
            if (!r.isSuccessful) throw HttpStatusException(r.code, text.take(2000), r.headers.toMap())
            json.parseToJsonElement(text)
        }
    }

    open suspend fun postJsonForBytes(url: String, body: JsonElement, headers: Map<String, String>, limit: Long): Pair<ByteArray, String> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }
            .post(body.toString().toRequestBody(JSON)).build()
        exchange(streaming, request) { r ->
            if (!r.isSuccessful) throw HttpStatusException(r.code, r.body.string().take(2000), r.headers.toMap())
            val src = r.body.source()
            src.request(limit + 1)
            if (src.buffer.size > limit) throw HttpStatusException(413, "Response larger than $limit bytes")
            src.readByteArray() to (r.header("Content-Type") ?: "")
        }
    }

    // A blocked socket read ignores coroutine cancellation, so a child job cancels the OkHttp call instead.
    private suspend fun <T> exchange(client: OkHttpClient, request: Request, block: suspend (Response) -> T): T {
        val call = client.newCall(request)
        val watcher = CoroutineScope(currentCoroutineContext()).launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            val response = try {
                call.execute()
            } catch (e: SocketTimeoutException) {
                throw NetException(NetFailure.TIMEOUT, e)
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                throw NetException(NetFailure.OFFLINE, e)
            }
            return response.use { block(it) }
        } finally {
            watcher.cancel()
        }
    }

    private fun okhttp3.Headers.toMap(): Map<String, String> = names().associate { it.lowercase() to (get(it) ?: "") }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

class SseParser {
    private var event: String? = null
    private val data = StringBuilder()

    fun feed(line: String): SseEvent? {
        when {
            line.isEmpty() -> return dispatch()
            line.startsWith(":") -> Unit
            line.startsWith("event:") -> event = line.substring(6).trim()
            line.startsWith("data:") -> { if (data.isNotEmpty()) data.append('\n'); data.append(line.substring(5).trimStart()) }
        }
        return null
    }

    fun finish(): SseEvent? = dispatch()

    private fun dispatch(): SseEvent? {
        val out = if (data.isNotEmpty()) SseEvent(event, data.toString()) else null
        event = null
        data.setLength(0)
        return out
    }
}

object NetGuard {
    fun secureEnough(url: String, resolve: (String) -> List<java.net.InetAddress> = { java.net.InetAddress.getAllByName(it).toList() }): Boolean {
        val uri = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return false
        if (uri.scheme == "https") return true
        if (uri.scheme != "http") return false
        val host = uri.host ?: return false
        val addresses = runCatching { resolve(host) }.getOrNull() ?: return false
        return addresses.isNotEmpty() && addresses.all { it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress || (it.address.size == 16 && (it.address[0].toInt() and 0xfe) == 0xfc) || it.hostAddress?.startsWith("100.") == true && isCgnat(it) }
    }

    private fun isCgnat(address: java.net.InetAddress): Boolean {
        val b = address.address
        return b.size == 4 && (b[0].toInt() and 0xff) == 100 && (b[1].toInt() and 0xc0) == 64
    }
}
