package com.example.core

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Weather lookup via Open-Meteo — free, no API key required. Ported from the Iris project.
 * Two calls: geocode the spoken city name to coordinates, then fetch current conditions.
 */
object WeatherTask {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    data class WeatherResult(
        val resolvedLocationName: String,
        val temperatureCelsius: Double,
        val windSpeedKmh: Double,
        val weatherCode: Int,
        val isDay: Boolean
    )

    fun getWeather(cityName: String): WeatherResult? {
        val (lat, lon, resolvedName) = geocode(cityName) ?: return null
        return fetchCurrentWeather(lat, lon, resolvedName)
    }

    private fun geocode(cityName: String): Triple<Double, Double, String>? = runCatching {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("geocoding-api.open-meteo.com")
            .addPathSegments("v1/search")
            .addQueryParameter("name", cityName)
            .addQueryParameter("count", "1")
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string() ?: return@use null
            val results = JSONObject(body).optJSONArray("results") ?: return@use null
            if (results.length() == 0) return@use null
            val first = results.getJSONObject(0)
            Triple(first.getDouble("latitude"), first.getDouble("longitude"), first.optString("name", cityName))
        }
    }.getOrNull()

    private fun fetchCurrentWeather(lat: Double, lon: Double, locationName: String): WeatherResult? = runCatching {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("api.open-meteo.com")
            .addPathSegments("v1/forecast")
            .addQueryParameter("latitude", lat.toString())
            .addQueryParameter("longitude", lon.toString())
            .addQueryParameter("current_weather", "true")
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string() ?: return@use null
            val current = JSONObject(body).optJSONObject("current_weather") ?: return@use null
            WeatherResult(
                resolvedLocationName = locationName,
                temperatureCelsius = current.getDouble("temperature"),
                windSpeedKmh = current.getDouble("windspeed"),
                weatherCode = current.getInt("weathercode"),
                isDay = current.optInt("is_day", 1) == 1
            )
        }
    }.getOrNull()

    fun describeCode(code: Int): String = when (code) {
        0 -> "clear sky"
        1, 2, 3 -> "partly cloudy"
        45, 48 -> "foggy"
        51, 53, 55 -> "drizzle"
        56, 57 -> "freezing drizzle"
        61, 63, 65 -> "rain"
        66, 67 -> "freezing rain"
        71, 73, 75 -> "snow"
        77 -> "snow grains"
        80, 81, 82 -> "rain showers"
        85, 86 -> "snow showers"
        95 -> "thunderstorm"
        96, 99 -> "thunderstorm with hail"
        else -> "unknown conditions"
    }
}
