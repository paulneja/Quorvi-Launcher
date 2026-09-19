package com.paulneja.quorvi.launcher

import com.paulneja.quorvi.launcher.live.BatteryParse
import com.paulneja.quorvi.launcher.live.MediaPick
import com.paulneja.quorvi.launcher.live.NowPlaying
import com.paulneja.quorvi.launcher.live.SessionInfo
import com.paulneja.quorvi.launcher.live.Sky
import com.paulneja.quorvi.launcher.live.SystemInfo
import com.paulneja.quorvi.launcher.live.WeatherParse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSourcesTest {
    @Test
    fun forecastParsesCurrentAndDaily() {
        val body = """
            {"current":{"temperature_2m":18.4,"apparent_temperature":17.1,"weather_code":2,"is_day":1},
             "daily":{"temperature_2m_max":[21.0],"temperature_2m_min":[12.5],"precipitation_probability_max":[20]}}
        """.trimIndent()
        val w = WeatherParse.forecast(Json.parseToJsonElement(body), "Buenos Aires", 1_000)
        assertNotNull(w)
        assertEquals(18.4, w!!.tempC, 0.001)
        assertEquals(Sky.PARTLY, w.sky)
        assertEquals(21.0, w.maxC!!, 0.001)
        assertEquals(20, w.rainChance)
        assertFalse(w.stale(1_000 + 60_000))
        assertTrue(w.stale(1_000 + 4 * 3_600_000L))
    }

    @Test
    fun forecastWithoutCurrentIsRejected() {
        assertNull(WeatherParse.forecast(Json.parseToJsonElement("""{"daily":{}}"""), null, 0))
    }

    @Test
    fun wmoCodesMapToSky() {
        assertEquals(Sky.CLEAR, WeatherParse.sky(0))
        assertEquals(Sky.FOG, WeatherParse.sky(45))
        assertEquals(Sky.RAIN, WeatherParse.sky(81))
        assertEquals(Sky.SNOW, WeatherParse.sky(73))
        assertEquals(Sky.STORM, WeatherParse.sky(95))
    }

    @Test
    fun geocodingResultsKeepRegion() {
        val body = """{"results":[{"name":"Rosario","admin1":"Santa Fe","country_code":"AR","latitude":-32.95,"longitude":-60.64}]}"""
        val places = WeatherParse.places(Json.parseToJsonElement(body))
        assertEquals("Rosario · Santa Fe, AR", places.single().name)
        assertTrue(WeatherParse.searchUrl("San Martín", "es").contains("San+Mart%C3%ADn"))
    }

    @Test
    fun playingSessionWinsOverNewerPausedOne() {
        assertEquals(1, MediaPick.pick(listOf(SessionInfo(0, false, true), SessionInfo(1, true, true))))
        assertEquals(0, MediaPick.pick(listOf(SessionInfo(0, false, true), SessionInfo(1, false, true))))
        assertNull(MediaPick.pick(listOf(SessionInfo(0, true, false))))
    }

    @Test
    fun playbackPositionIsExtrapolatedOnlyWhilePlaying() {
        val now = NowPlaying("p", "t", null, null, playing = true, positionMs = 10_000, durationMs = 60_000, speed = 1f, updatedAt = 1_000)
        assertEquals(15_000, now.positionAt(6_000))
        assertEquals(60_000, now.positionAt(500_000))
        assertEquals(10_000, now.copy(playing = false).positionAt(6_000))
    }

    @Test
    fun batteryAndSystemParsing() {
        val b = BatteryParse.from(level = 39, scale = 50, status = 2, plugged = 1, tempTenths = 312)
        assertEquals(78, b!!.percent)
        assertTrue(b.charging)
        assertEquals(31.2f, b.temperatureC!!, 0.01f)
        assertNull(BatteryParse.from(-1, 100, 0, 0, 0))
        val s = SystemInfo.of(storageFree = 40, storageTotal = 100, ramAvailable = 3, ramTotal = 4)
        assertEquals(0.6f, s.storageFraction, 0.001f)
        assertEquals(0.25f, s.ramFraction, 0.001f)
    }
}
