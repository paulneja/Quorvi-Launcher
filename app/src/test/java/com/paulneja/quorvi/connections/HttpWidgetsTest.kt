package com.paulneja.quorvi.connections

import com.paulneja.quorvi.agent.policy.HostResolver
import com.paulneja.quorvi.connections.http.Condition
import com.paulneja.quorvi.connections.http.HttpWidgetFetcher
import com.paulneja.quorvi.connections.http.JsonPath
import com.paulneja.quorvi.core.net.Http
import java.net.InetAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class HttpWidgetsTest {
    private val server = MockWebServer()
    private val vault = MemoryVault()
    private val fetcher = HttpWidgetFetcher(Http(OkHttpClient(), Json), vault)

    @Before fun start() = server.start()
    @After fun stop() = server.close()

    private fun widget(path: String? = null, display: String = "TEXT", okWhen: String? = null, allowLan: Boolean = true) =
        HttpWidgetEntity(id = "w1", name = "w", url = server.url("/api").toString(), jsonPath = path, display = display, okWhen = okWhen, allowLan = allowLan, createdAt = 0)

    @Test
    fun jsonPathSubset() {
        val root = Json.parseToJsonElement("""{"a":{"b":[{"c":1},{"c":2}]},"x y":"z","items":[{"n":"one"},{"n":"two"}]}""")
        assertEquals("2", JsonPath.text(JsonPath.select(root, "$.a.b[1].c")))
        assertEquals("z", JsonPath.text(JsonPath.select(root, "$['x y']")))
        assertEquals("one, two", JsonPath.text(JsonPath.select(root, "$.items[*].n")))
        assertEquals("", JsonPath.text(JsonPath.select(root, "$.missing.deep")))
    }

    @Test
    fun conditions() {
        assertEquals(true, Condition.ok("200", "== 200"))
        assertEquals(false, Condition.ok("91.5", "< 80"))
        assertEquals(true, Condition.ok("All systems OK", "contains ok"))
        assertEquals(true, Condition.ok("up", "!= down"))
        assertNull(Condition.ok("x", "garbage"))
    }

    @Test
    fun valueIsExtractedAndAuthComesFromTheVault() = runBlocking {
        vault.put(fetcher.authRef("w1"), "Bearer t0k")
        server.enqueue(MockResponse.Builder().body("""{"cpu":{"pct":42.5}}""").build())
        val reading = fetcher.fetch(widget("$.cpu.pct", "COUNTER", "< 80"))
        assertEquals("42.5", reading.value)
        assertEquals(true, reading.ok)
        assertEquals("Bearer t0k", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun statusWidgetReportsServerErrorsAsData() = runBlocking {
        server.enqueue(MockResponse.Builder().code(503).body("down").build())
        val reading = fetcher.fetch(widget(display = "STATUS"))
        assertEquals("503", reading.value)
        assertEquals(false, reading.ok)
    }

    @Test
    fun localNetworkNeedsOptInAndMetadataIsAlwaysBlocked() = runBlocking {
        try {
            fetcher.fetch(widget(allowLan = false))
            fail("loopback allowed without opt-in")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("local network"))
        }
        val metadata = HttpWidgetFetcher(Http(OkHttpClient(), Json), vault, HostResolver { listOf(InetAddress.getByName("169.254.169.254")) })
        assertTrue(metadata.check("http://meta.example/latest", allowLan = true)!!.contains("metadata"))
        assertNull(fetcher.check(server.url("/").toString(), allowLan = true))
    }

    @Test
    fun historyKeepsTheLastNumbers() {
        var h = ""
        repeat(30) { h = HttpWidgetFetcher.appendHistory(h, it.toString()) }
        assertEquals(24, h.split(',').size)
        assertEquals("29.0", h.split(',').last())
        assertEquals(h, HttpWidgetFetcher.appendHistory(h, "not a number"))
        assertFalse(h.isEmpty())
    }

    @Test
    fun plainHttpOnlyTowardsLocalAddresses() {
        val public = HttpWidgetFetcher(Http(OkHttpClient(), Json), vault, HostResolver { listOf(InetAddress.getByName("93.184.216.34")) })
        assertTrue(public.check("http://example.org/status", allowLan = true)!!.contains("https"))
        assertNull(public.check("https://example.org/status", allowLan = false))
        val lan = HttpWidgetFetcher(Http(OkHttpClient(), Json), vault, HostResolver { listOf(InetAddress.getByName("192.168.1.10")) })
        assertNull(lan.check("http://nas.lan:8080/api", allowLan = true))
    }
}
