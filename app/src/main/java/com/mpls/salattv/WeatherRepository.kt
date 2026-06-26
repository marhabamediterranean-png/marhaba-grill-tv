package com.mpls.salattv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Broad visual category used to pick the animated icon. */
enum class WeatherKind { CLEAR, PARTLY, CLOUD, FOG, RAIN, SNOW, STORM }

data class WeatherData(
    val tempF: Int,
    val condition: String,
    val kind: WeatherKind,
    val isDay: Boolean
)

object WeatherRepository {

    // Minneapolis 55408 (Uptown / Lyndale) centroid.
    private const val LAT = 44.948
    private const val LON = -93.298

    private const val URL_STR =
        "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$LAT&longitude=$LON" +
            "&current=temperature_2m,weather_code,is_day" +
            "&temperature_unit=fahrenheit&wind_speed_unit=mph&timezone=auto"

    suspend fun fetch(retries: Int = 2): WeatherData? = withContext(Dispatchers.IO) {
        repeat(retries) { attempt ->
            try {
                return@withContext requestOnce()
            } catch (e: Exception) {
                kotlinx.coroutines.delay((1000L * (attempt + 1)))
            }
        }
        null
    }

    private fun requestOnce(): WeatherData {
        val conn = (URL(URL_STR).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("User-Agent", "MarhabaTV/1.0")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode !in 200..299) throw RuntimeException("HTTP ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val cur = JSONObject(body).getJSONObject("current")
            val temp = cur.getDouble("temperature_2m")
            val code = cur.getInt("weather_code")
            val isDay = cur.optInt("is_day", 1) == 1
            return WeatherData(
                tempF = Math.round(temp).toInt(),
                condition = conditionText(code),
                kind = kindFor(code),
                isDay = isDay
            )
        } finally {
            conn.disconnect()
        }
    }

    // WMO weather interpretation codes -> human text
    private fun conditionText(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly Clear"
        2 -> "Partly Cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing Drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing Rain"
        71, 73, 75 -> "Snow"
        77 -> "Snow Grains"
        80, 81, 82 -> "Showers"
        85, 86 -> "Snow Showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm"
        else -> "—"
    }

    private fun kindFor(code: Int): WeatherKind = when (code) {
        0, 1 -> WeatherKind.CLEAR
        2 -> WeatherKind.PARTLY
        3 -> WeatherKind.CLOUD
        45, 48 -> WeatherKind.FOG
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherKind.RAIN
        71, 73, 75, 77, 85, 86 -> WeatherKind.SNOW
        95, 96, 99 -> WeatherKind.STORM
        else -> WeatherKind.CLOUD
    }
}
