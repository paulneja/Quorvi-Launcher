package com.paulneja.quorvi.connections.http

import com.paulneja.quorvi.agent.policy.HostResolver
import com.paulneja.quorvi.agent.policy.PolicyEngine
import com.paulneja.quorvi.agent.policy.Verdict
import com.paulneja.quorvi.connections.HttpWidgetEntity
import com.paulneja.quorvi.core.net.Http
import com.paulneja.quorvi.core.security.CredentialRef
import com.paulneja.quorvi.core.security.Vault
import java.net.InetAddress
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

enum class HttpDisplay { TEXT, COUNTER, STATUS, SERIES, LIST, BUTTON, TOGGLE }

object JsonPath {
    private val token = Regex("""\.([A-Za-z_$][\w$-]*)|\[(\d+)]|\['([^']*)']|\["([^"]*)"]|\[\*]""")

    fun select(root: JsonElement, path: String): JsonElement? {
        val p = path.trim()
        if (p.isEmpty() || p == "$") return root
        require(p.startsWith("$")) { "JSONPath must start with \$" }
        var current: List<JsonElement> = listOf(root)
        var spread = false
        var index = 1
        while (index < p.length) {
            val match = token.matchAt(p, index) ?: throw IllegalArgumentException("Unsupported JSONPath near \"${p.substring(index)}\"")
            val (key, number, single, double) = match.destructured
            current = when {
                match.value == "[*]" -> { spread = true; current.flatMap { (it as? JsonArray).orEmpty() } }
                number.isNotEmpty() -> current.mapNotNull { (it as? JsonArray)?.getOrNull(number.toInt()) }
                else -> current.mapNotNull { (it as? JsonObject)?.get(key.ifEmpty { single.ifEmpty { double } }) }
            }
            index = match.range.last + 1
        }
        return if (spread) JsonArray(current) else current.firstOrNull()
    }

    fun text(element: JsonElement?): String = when (element) {
        null, JsonNull -> ""
        is JsonPrimitive -> element.contentOrNull.orEmpty()
        is JsonArray -> element.joinToString(", ") { text(it) }
        is JsonObject -> element.toString()
    }
}

object Condition {
    private val pattern = Regex("""^\s*(==|!=|>=|<=|>|<|contains)\s*(.+?)\s*$""")

    fun ok(value: String, expression: String?): Boolean? {
        if (expression.isNullOrBlank()) return null
        val match = pattern.matchEntire(expression) ?: return null
        val (op, raw) = match.destructured
        val expected = raw.trim('"', '\'')
        val a = value.trim().toDoubleOrNull()
        val b = expected.toDoubleOrNull()
        return if (a != null && b != null && op != "contains") when (op) {
            "==" -> a == b
            "!=" -> a != b
            ">=" -> a >= b
            "<=" -> a <= b
            ">" -> a > b
            "<" -> a < b
            else -> null
        } else when (op) {
            "==" -> value.trim() == expected
            "!=" -> value.trim() != expected
            "contains" -> value.contains(expected, ignoreCase = true)
            else -> null
        }
    }
}

data class WidgetReading(val status: Int, val value: String, val ok: Boolean?)

class HttpWidgetFetcher(
    private val http: Http,
    private val vault: Vault,
    private val resolver: HostResolver = HostResolver { InetAddress.getAllByName(it).toList() },
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun authRef(widgetId: String) = CredentialRef("http_auth_$widgetId")

    fun check(url: String, allowLan: Boolean): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return "Invalid URL"
        if (uri.scheme == "tcp") return checkTcp(uri.host ?: return "URL without host", allowLan)
        if (uri.scheme !in setOf("http", "https")) return "Only http, https and tcp are allowed"
        val host = uri.host ?: return "URL without host"
        val addresses = runCatching { resolver.resolve(host) }.getOrElse { return "Could not resolve $host" }
        if (addresses.any { it.hostAddress == "169.254.169.254" || it.hostAddress == "100.100.100.200" || it.hostAddress?.startsWith("fd00:ec2::254") == true }) return "Cloud metadata endpoints are blocked"
        val local = addresses.all { it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress || (it.address.size == 16 && (it.address[0].toInt() and 0xfe) == 0xfc) }
        if (uri.scheme == "http" && !local) return "Use https for services on the internet"
        if (!allowLan) {
            val decision = PolicyEngine(resolver = resolver).network(url)
            if (decision.verdict != Verdict.ALLOW) return decision.reason + " (enable local network for this widget to allow it)"
        }
        return null
    }

    private fun checkTcp(host: String, allowLan: Boolean): String? {
        val addresses = runCatching { resolver.resolve(host) }.getOrElse { return "Could not resolve $host" }
        if (addresses.any { it.hostAddress == "169.254.169.254" || it.hostAddress == "100.100.100.200" }) return "Cloud metadata endpoints are blocked"
        val local = addresses.any { it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress }
        if (local && !allowLan) return "Enable local network for this monitor to check a device on your network"
        return null
    }

    suspend fun fetch(widget: HttpWidgetEntity, action: Boolean = false): WidgetReading = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        check(widget.url, widget.allowLan)?.let { throw IllegalArgumentException(it) }
        val target = MonitorTarget.parse(widget.url)
        if (target?.kind == MonitorKind.TCP) {
            val ms = runCatching { MonitorTarget.probe(target.host, target.effectivePort) }
                .getOrElse { throw IllegalStateException("Port ${target.effectivePort} closed or unreachable") }
            return@withContext WidgetReading(200, "$ms ms", true)
        }
        val headers = buildMap {
            runCatching { json.parseToJsonElement(widget.headersJson).jsonObject }.getOrNull()?.forEach { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { put(k, it) } }
            vault.get(authRef(widget.id))?.let { put("Authorization", it) }
        }
        val method = if (action) widget.method else if (widget.display == HttpDisplay.BUTTON.name || widget.display == HttpDisplay.TOGGLE.name) "GET" else widget.method
        val reply = http.request(method, widget.url, headers, if (method == "GET") null else widget.body, MAX_BYTES)
        if (reply.status !in 200..399 && widget.display != HttpDisplay.STATUS.name) throw IllegalStateException("HTTP ${reply.status}")
        val value = when {
            widget.jsonPath.isNullOrBlank() && widget.display == HttpDisplay.STATUS.name -> reply.status.toString()
            widget.jsonPath.isNullOrBlank() -> reply.body.trim().take(200)
            else -> {
                val root = runCatching { json.parseToJsonElement(reply.body) }.getOrElse { throw IllegalArgumentException("Response is not JSON (HTTP ${reply.status})") }
                JsonPath.text(JsonPath.select(root, widget.jsonPath))
            }
        }
        val ok = Condition.ok(value, widget.okWhen) ?: if (widget.display == HttpDisplay.STATUS.name) reply.status in 200..299 else null
        WidgetReading(reply.status, value, ok)
    }

    companion object {
        const val MAX_BYTES = 256L * 1024
        const val HISTORY = 24

        fun appendHistory(history: String, value: String): String {
            val number = value.trim().toDoubleOrNull() ?: return history
            return (history.split(',').filter { it.isNotBlank() } + number.toString()).takeLast(HISTORY).joinToString(",")
        }
    }
}
