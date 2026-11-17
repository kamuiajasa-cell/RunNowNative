package com.runnow.data

import android.content.Context
import com.runnow.domain.ClimateConditions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ClimateLocation(val latitude: Double, val longitude: Double, val name: String? = null, val manual: Boolean = false)
data class WeatherBundle(val fetchedAtMs: Long, val source: String, val hourly: List<Pair<Long, ClimateConditions>>)

class OpenMeteoProvider(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("runnow_climate", Context.MODE_PRIVATE)

    fun savedLocation(): ClimateLocation? {
        val lat = prefs.getString("lat", null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString("lon", null)?.toDoubleOrNull() ?: return null
        return ClimateLocation(lat, lon, prefs.getString("name", null), prefs.getBoolean("manual", false))
    }

    fun saveLocation(location: ClimateLocation) {
        require(location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0)
        prefs.edit().putString("lat", location.latitude.toString()).putString("lon", location.longitude.toString())
            .putString("name", location.name).putBoolean("manual", location.manual).apply()
    }

    suspend fun forecast(location: ClimateLocation, pastDays: Int = 1, forecastDays: Int = 3): WeatherBundle =
        request(location, "https://api.open-meteo.com/v1/forecast", mapOf(
            "past_days" to pastDays.toString(), "forecast_days" to forecastDays.toString(),
        ))

    suspend fun archive(location: ClimateLocation, day: LocalDate): WeatherBundle =
        request(location, "https://archive-api.open-meteo.com/v1/archive", mapOf(
            "start_date" to day.toString(), "end_date" to day.toString(),
        ))

    private suspend fun request(location: ClimateLocation, endpoint: String, extra: Map<String, String>): WeatherBundle =
        withContext(Dispatchers.IO) {
            val params = linkedMapOf(
                "latitude" to "%.2f".format(java.util.Locale.US, location.latitude),
                "longitude" to "%.2f".format(java.util.Locale.US, location.longitude),
                "timezone" to "auto",
                "hourly" to "temperature_2m,relative_humidity_2m,apparent_temperature,wind_speed_10m,precipitation,shortwave_radiation,cloud_cover,weather_code",
            ).apply { putAll(extra) }
            val query = params.entries.joinToString("&") { (k, v) ->
                java.net.URLEncoder.encode(k, "UTF-8") + "=" + java.net.URLEncoder.encode(v, "UTF-8")
            }
            val connection = (URL("$endpoint?$query").openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 20_000
            }
            try {
                if (connection.responseCode !in 200..299) throw IllegalStateException("Open-Meteo HTTP ${connection.responseCode}")
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val hourly = json.getJSONObject("hourly")
                val times = hourly.getJSONArray("time")
                val out = ArrayList<Pair<Long, ClimateConditions>>(times.length())
                for (i in 0 until times.length()) {
                    val time = runCatching { java.time.LocalDateTime.parse(times.getString(i)).atZone(ZoneId.of(json.optString("timezone", "UTC"))).toInstant().toEpochMilli() }.getOrNull() ?: continue
                    fun num(key: String): Double? = hourly.optJSONArray(key)?.let { if (it.isNull(i)) null else it.optDouble(i).takeIf { v -> v.isFinite() } }
                    val c = ClimateConditions(
                        temperatureC = num("temperature_2m"), humidityPct = num("relative_humidity_2m"),
                        apparentTemperatureC = num("apparent_temperature"), windKmh = num("wind_speed_10m"),
                        precipitationMm = num("precipitation") ?: 0.0,
                        solarRadiationWm2 = num("shortwave_radiation") ?: 0.0,
                        weatherCode = num("weather_code")?.toInt() ?: 0,
                        samples = 1,
                    )
                    out += time to c
                }
                WeatherBundle(System.currentTimeMillis(), "Open-Meteo", out)
            } finally { connection.disconnect() }
        }
}
