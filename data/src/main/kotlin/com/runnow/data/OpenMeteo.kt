package com.runnow.data

import com.runnow.domain.GeoLoc
import java.io.IOException
import java.net.URLEncoder

/** Alamat Open-Meteo, sama dengan wxGet() dan clLoc() di HTML. */
object OpenMeteo {
    const val HV =
        "temperature_2m,relative_humidity_2m,apparent_temperature,wind_speed_10m,precipitation,shortwave_radiation,cloud_cover,weather_code"

    private fun q(la: Double, lo: Double) = "latitude=" + toFixed(la, 2) + "&longitude=" + toFixed(lo, 2) + "&timezone=auto&hourly=" + HV

    fun forecastUrl(la: Double, lo: Double) =
        "https://api.open-meteo.com/v1/forecast?" + q(la, lo) + ",precipitation_probability&past_days=7&forecast_days=3"

    fun archiveUrl(la: Double, lo: Double, day: String) =
        "https://archive-api.open-meteo.com/v1/archive?" + q(la, lo) + "&start_date=" + day + "&end_date=" + day

    fun geocodeUrl(name: String) =
        "https://geocoding-api.open-meteo.com/v1/search?count=1&language=id&name=" + URLEncoder.encode(name, "UTF-8").replace("+", "%20")

    fun currentUrl(la: Double, lo: Double) =
        "https://api.open-meteo.com/v1/forecast?latitude=" + toFixed(la, 2) + "&longitude=" + toFixed(lo, 2) + "&current=temperature_2m"
}

/** [WeatherApi] lewat [HttpClient]. Kegagalan apa pun menjadi null (pemanggil memakai cache lama). */
class OpenMeteoHttp(private val http: HttpClient) : WeatherApi {
    private suspend fun json(url: String): JsonValue? =
        try {
            val r = http.get(url)
            if (r.status in 200..299) Json.parse(r.body) else null
        } catch (e: IOException) {
            null
        } catch (e: JsonException) {
            null
        }

    override suspend fun forecast(lat: Double, lon: Double) = json(OpenMeteo.forecastUrl(lat, lon))

    override suspend fun archive(lat: Double, lon: Double, day: String) = json(OpenMeteo.archiveUrl(lat, lon, day))

    override suspend fun geocode(name: String): GeoLoc? {
        val g = json(OpenMeteo.geocodeUrl(name))["results"].items?.firstOrNull() ?: return null
        val la = g["latitude"].number()
        val lo = g["longitude"].number()
        if (la.isNaN() || lo.isNaN()) return null
        return GeoLoc(toFixed(la, 2).toDouble(), toFixed(lo, 2).toDouble(), g["name"].text() ?: "", manual = true)
    }

    override suspend fun currentTemp(lat: Double, lon: Double): Double? {
        val t = json(OpenMeteo.currentUrl(lat, lon))["current"]["temperature_2m"]
        return if (t is JNum) t.v else null
    }
}
