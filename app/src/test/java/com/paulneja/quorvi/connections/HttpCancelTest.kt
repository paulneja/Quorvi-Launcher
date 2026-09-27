package com.paulneja.quorvi.connections

import com.paulneja.quorvi.core.net.Http
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpCancelTest {
    private val server = MockWebServer().apply { start() }

    @After
    fun tearDown() = server.close()

    @Test
    fun cancellingAStalledStreamReturnsPromptly() = runBlocking {
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/event-stream").body("data: {}\n\n").bodyDelay(60, TimeUnit.SECONDS).build())
        val http = Http(OkHttpClient(), Json)
        val job = launch(Dispatchers.Default) { http.stream(server.url("/v1/chat/completions").toString(), buildJsonObject { }, emptyMap()).collect { } }
        delay(500)
        val started = System.nanoTime()
        job.cancel()
        withTimeout(2_000) { job.join() }
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2_000)
    }
}
