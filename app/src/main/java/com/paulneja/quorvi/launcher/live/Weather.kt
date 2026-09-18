package com.paulneja.quorvi.launcher.live

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import com.paulneja.quorvi.core.net.Http
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

@Immutable
data class WeatherNow(
    val tempC: Double,
    val feelsC: Double?,
    val sky: Sky,
    val isDay: Boolean,
    val maxC: Double?,
    val minC: Double?,
    val rainChance: Int?,
    val place: String?,
    val fetchedAt: Long,
) {
    fun stale(now: Long): Boolean = now - fetchedAt > STALE_MS

    companion object {
        const val STALE_MS = 3 * 60 * 60 * 1000L
    }
}

@Immutable
sealed interface WeatherState {
    data object NeedsLocation : WeatherState
    data object Loading : WeatherState
    data class Ready(val weather: WeatherNow) : WeatherState
    data class Failed(val message: String, val last: WeatherNow?) : WeatherState
}

data class Place(val name: String, val lat: Double, val lon: Double)

object WeatherParse {
    fun sky(code: Int): Sky = when (code) {
        0 -> Sky.CLEAR
        1, 2 -> Sky.PARTLY
        3 -> Sky.CLOUDY
        45, 48 -> Sky.FOG
        in 51..57 -> Sky.DRIZZLE
        in 61..67, in 80..82 -> Sky.RAIN
        in 71..77, 85, 86 -> Sky.SNOW
        in 95..99 -> Sky.STORM
        else -> Sky.CLOUDY
    }

    fun forecast(json: JsonElement, place: String?, now: Long): WeatherNow? {
        val root = json as? JsonObject ?: return null
        val current = root["current"]?.jsonObject ?: return null
        val temp = current["temperature_2m"]?.jsonPrimitive?.doubleOrNull ?: return null
        val daily = root["daily"]?.jsonObject
        fun firstDouble(key: String) = daily?.get(key)?.jsonArray?.firstOrNull()?.jsonPrimitive?.doubleOrNull
        return WeatherNow(
            tempC = temp,
            feelsC = current["apparent_temperature"]?.jsonPrimitive?.doubleOrNull,
            sky = sky(current["weather_code"]?.jsonPrimitive?.intOrNull ?: 3),
            isDay = (current["is_day"]?.jsonPrimitive?.intOrNull ?: 1) == 1,
            maxC = firstDouble("temperature_2m_max"),
            minC = firstDouble("temperature_2m_min"),
            rainChance = daily?.get("precipitation_probability_max")?.jsonArray?.firstOrNull()?.jsonPrimitive?.intOrNull,
            place = place,
            fetchedAt = now,
        )
    }

    fun places(json: JsonElement): List<Place> =
        (json as? JsonObject)?.get("results")?.jsonArray.orEmpty().mapNotNull { e ->
            val o = e.jsonObject
            val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val region = listOfNotNull(o["admin1"]?.jsonPrimitive?.contentOrNull, o["country_code"]?.jsonPrimitive?.contentOrNull).joinToString(", ")
            Place(if (region.isBlank()) name else "$name · $region", o["latitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null, o["longitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null)
        }

    fun forecastUrl(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${"%.3f".format(Locale.US, lat)}&longitude=${"%.3f".format(Locale.US, lon)}" +
            "&current=temperature_2m,apparent_temperature,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=1"

    fun searchUrl(query: String, language: String): String =
        "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query, "UTF-8")}&count=6&language=$language&format=json"
}

@Singleton
class WeatherSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: Http,
    private val settings: com.paulneja.quorvi.core.settings.QuorviSettings,
    scope: CoroutineScope,
) {
    private val refresh = MutableStateFlow(0)

    fun hasLocation(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private suspend fun locate(): Place? = withContext(Dispatchers.IO) {
        if (!hasLocation()) return@withContext null
        val lm = context.getSystemService(LocationManager::class.java)
        val loc = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time } ?: return@withContext null
        @Suppress("DEPRECATION")
        val name = runCatching { Geocoder(context).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea } }.getOrNull()
        Place(name ?: "", loc.latitude, loc.longitude)
    }

    private suspend fun cached(): WeatherNow? {
        val raw = settings.weatherCache.first() ?: return null
        val parts = raw.split('\u0001', limit = 3)
        if (parts.size < 3) return null
        return runCatching { WeatherParse.forecast(http.json.parseToJsonElement(parts[2]), parts[0].ifBlank { null }, parts[1].toLong()) }.getOrNull()
    }

    private fun decodePlace(raw: String?): Place? {
        val parts = raw?.split('\u0001') ?: return null
        if (parts.size != 3) return null
        return Place(parts[0], parts[1].toDoubleOrNull() ?: return null, parts[2].toDoubleOrNull() ?: return null)
    }

    private suspend fun load(): WeatherState {
        val chosen = decodePlace(settings.weatherPlace.first())
        val place = locate() ?: chosen ?: return WeatherState.NeedsLocation
        val now = System.currentTimeMillis()
        val last = cached()
        if (last != null && now - last.fetchedAt < 30 * 60_000 && (last.place == place.name.ifBlank { null } || place.name.isBlank())) return WeatherState.Ready(last)
        return runCatching {
            val body = http.getText(WeatherParse.forecastUrl(place.lat, place.lon), emptyMap())
            val parsed = WeatherParse.forecast(http.json.parseToJsonElement(body), place.name.ifBlank { null }, now) ?: error("bad response")
            settings.setWeatherCache(listOf(parsed.place.orEmpty(), now.toString(), body).joinToString("\u0001"))
            WeatherState.Ready(parsed)
        }.getOrElse { WeatherState.Failed(it.message ?: "error", last) }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<WeatherState> = combine(refresh, settings.weatherPlace) { n, p -> n to p }
        .flatMapLatest {
            flow {
                while (true) {
                    emit(load())
                    delay(30 * 60_000L)
                }
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), WeatherState.Loading)

    fun retry() { refresh.value++ }

    suspend fun search(query: String): List<Place> = runCatching {
        WeatherParse.places(http.getJson(WeatherParse.searchUrl(query.trim(), Locale.getDefault().language), emptyMap()))
    }.getOrDefault(emptyList())

    suspend fun choose(place: Place) = settings.setWeatherPlace(listOf(place.name, place.lat.toString(), place.lon.toString()).joinToString("\u0001"))
}
