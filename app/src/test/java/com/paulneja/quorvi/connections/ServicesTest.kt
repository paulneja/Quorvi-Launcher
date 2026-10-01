package com.paulneja.quorvi.connections

import com.paulneja.quorvi.connections.services.GitLabClient
import com.paulneja.quorvi.connections.services.GlJob
import com.paulneja.quorvi.connections.services.GlPipeline
import com.paulneja.quorvi.connections.services.HaParsing
import com.paulneja.quorvi.connections.services.WorkerClient
import com.paulneja.quorvi.core.net.Http
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicesTest {
    @Test
    fun homeAssistantStatesAndEvents() {
        val states = HaParsing.states(Json.parseToJsonElement("""[{"entity_id":"light.sala","state":"on","attributes":{"friendly_name":"Sala"}},{"entity_id":"sensor.temp","state":"21.5","attributes":{"unit_of_measurement":"°C"}}]"""))
        assertEquals(listOf("Sala", "sensor.temp"), states.map { it.name })
        assertEquals("°C", states[1].unit)
        val event = Json.parseToJsonElement("""{"id":1,"type":"event","event":{"event_type":"state_changed","data":{"entity_id":"light.sala","new_state":{"entity_id":"light.sala","state":"off","attributes":{"friendly_name":"Sala"}}}}}""").jsonObject
        assertEquals("off", HaParsing.changed(event)!!.state)
        assertNull(HaParsing.changed(Json.parseToJsonElement("""{"type":"result","success":true}""").jsonObject))
        assertEquals("light" to "toggle", HaParsing.toggleService("light.sala"))
        assertNull(HaParsing.toggleService("lock.puerta"))
        assertNull(HaParsing.toggleService("sensor.temp"))
    }

    @Test
    fun gitLabPipelinesMapOntoTheCiTable() {
        val failed = GitLabClient.toEntity("group/app", GlPipeline(id = 77, iid = 12, status = "failed", ref = "main", sha = "abcdef123456", webUrl = "https://gitlab.com/group/app/-/pipelines/77"), 5)
        assertEquals("gitlab:group/app:77", failed.id)
        assertEquals("completed" to "failure", failed.status to failed.conclusion)
        assertEquals("group/app" to 77L, GitLabClient.parseId(failed.id))
        val running = GitLabClient.toEntity("group/sub/app", GlPipeline(id = 1, status = "running"), 5)
        assertEquals("in_progress", running.status)
        assertNull(running.conclusion)
        assertEquals("group/sub/app" to 1L, GitLabClient.parseId(running.id))
        assertNull(GitLabClient.parseId("github:a/b:1"))
        assertEquals("test · unit", GitLabClient.toJob(failed.id, GlJob(id = 9, name = "unit", stage = "test", status = "failed")).name)
    }

    @Test
    fun workerClientSpeaksTheProtocol() = runBlocking {
        val server = MockWebServer().apply { start() }
        server.enqueue(MockResponse.Builder().body("""{"run":{"id":"r1","idempotencyKey":"k","command":"make","state":"RUNNING","lastSequence":1},"existing":true}""").build())
        server.enqueue(MockResponse.Builder().body("""{"events":[{"seq":2,"at":"2026-10-09T00:00:00Z","type":"stdout","data":"ok\n"}],"run":{"id":"r1","state":"COMPLETED","exitCode":0,"lastSequence":3}}""").build())
        val client = WorkerClient(Http(OkHttpClient(), Json), server.url("/").toString(), "tok")
        val (run, existing) = client.submit("k", "make", 60)
        assertTrue(existing)
        assertEquals("r1", run.id)
        val (events, latest) = client.events("r1", 1, 1)
        assertEquals("ok\n", events.single().data)
        assertTrue(latest.terminal)
        assertEquals("Bearer tok", server.takeRequest().headers["Authorization"])
        val poll = server.takeRequest().url
        assertEquals("/v1/runs/r1/events", poll.encodedPath)
        assertEquals("1", poll.queryParameter("after"))
        server.close()
    }
}
