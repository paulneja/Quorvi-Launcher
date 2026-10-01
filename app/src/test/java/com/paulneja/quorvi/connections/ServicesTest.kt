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
}
