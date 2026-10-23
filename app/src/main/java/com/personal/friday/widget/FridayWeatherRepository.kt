package com.personal.friday.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object FridayWeatherRepository {

    private const val TAG = "FridayWeather"
    private const val PREFS_NAME = "friday_weather_cache"
    private const val KEY_TEMP = "cached_temp"
    private const val KEY_CONDITION = "cached_condition"
    private const val KEY_EMOJI = "cached_emoji"
    private const val KEY_CODE = "cached_code"
    private const val KEY_IS_DAY = "cached_is_day"
    private const val KEY_CITY = "cached_city"
    private const val KEY_HUMIDITY = "cached_humidity"
    private const val KEY_RAIN = "cached_rain"
    private const val KEY_WIND = "cached_wind"
    private const val KEY_LAST_UPDATE = "cached_last_update"
    private const val KEY_LAT = "cached_lat"
    private const val KEY_LON = "cached_lon"
    private const val KEY_HOURLY_JSON = "cached_hourly_json"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun getCachedWeather(context: Context): FridayWeatherData {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val temp = prefs.getFloat(KEY_TEMP, 26.0f)
        val condition = prefs.getString(KEY_CONDITION, "Mainly Clear") ?: "Mainly Clear"
        val emoji = prefs.getString(KEY_EMOJI, "🌤️") ?: "🌤️"
        val code = prefs.getInt(KEY_CODE, 1)
        val isDay = prefs.getBoolean(KEY_IS_DAY, true)
        val city = prefs.getString(KEY_CITY, "My Location") ?: "My Location"
        val humidity = prefs.getInt(KEY_HUMIDITY, 54)
        val rain = prefs.getFloat(KEY_RAIN, 0.0f)
        val wind = prefs.getFloat(KEY_WIND, 4.5f)
        val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L)

        val hourlyList = mutableListOf<HourlyForecastItem>()
        val hourlyRaw = prefs.getString(KEY_HOURLY_JSON, null)
        if (!hourlyRaw.isNullOrEmpty()) {
            try {
                val array = JSONArray(hourlyRaw)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    hourlyList.add(
                        HourlyForecastItem(
                            timeLabel = obj.getString("time"),
                            iconEmoji = obj.getString("icon"),
                            tempLabel = obj.getString("temp"),
                            tempValue = obj.optDouble("val", 26.0).toFloat(),
                            secondaryValue = obj.optDouble("sec", 15.0).toFloat()
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        if (hourlyList.isEmpty()) {
            hourlyList.addAll(
                listOf(
                    HourlyForecastItem("09:00", "☀️", "25°", 25f, 10f),
                    HourlyForecastItem("11:00", "🌤️", "27°", 27f, 15f),
                    HourlyForecastItem("13:00", "☀️", "29°", 29f, 25f),
                    HourlyForecastItem("15:00", "🌤️", "28°", 28f, 30f),
                    HourlyForecastItem("17:00", "⛅", "26°", 26f, 20f),
                    HourlyForecastItem("19:00", "🌙", "24°", 24f, 10f)
                )
            )
        }

        return FridayWeatherData(
            tempCelsius = temp,
            conditionText = condition,
            iconEmoji = emoji,
            weatherCode = code,
            isDay = isDay,
            cityName = city,
            humidityPercent = humidity,
            rainMm = rain,
            windSpeedKmh = wind,
            lastUpdatedMillis = lastUpdate,
            hourlyForecast = hourlyList
        )
    }

    private fun saveCachedWeather(
        context: Context,
        data: FridayWeatherData,
        lat: Double,
        lon: Double,
        hourlyJson: String
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putFloat(KEY_TEMP, data.tempCelsius)
            putString(KEY_CONDITION, data.conditionText)
            putString(KEY_EMOJI, data.iconEmoji)
            putInt(KEY_CODE, data.weatherCode)
            putBoolean(KEY_IS_DAY, data.isDay)
            putString(KEY_CITY, data.cityName)
            putInt(KEY_HUMIDITY, data.humidityPercent)
            putFloat(KEY_RAIN, data.rainMm)
            putFloat(KEY_WIND, data.windSpeedKmh)
            putLong(KEY_LAST_UPDATE, data.lastUpdatedMillis)
            putFloat(KEY_LAT, lat.toFloat())
            putFloat(KEY_LON, lon.toFloat())
            putString(KEY_HOURLY_JSON, hourlyJson)
            apply()
        }
    }

    suspend fun fetchCurrentWeather(context: Context): Result<FridayWeatherData> = withContext(Dispatchers.IO) {
        try {
            val (lat, lon, detectedCity) = resolveCoordinates(context)
            Log.d(TAG, "Fetching Open-Meteo weather with hourly forecast: ($lat, $lon), City: $detectedCity")

            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,is_day,precipitation,weather_code,wind_speed_10m&hourly=temperature_2m,precipitation_probability,weather_code&forecast_hours=10&timezone=auto"
            val request = Request.Builder().url(url).build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Open-Meteo HTTP error: ${response.code}"))
            }

            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val current = json.getJSONObject("current")

            val temp = current.getDouble("temperature_2m").toFloat()
            val humidity = current.optInt("relative_humidity_2m", 45)
            val isDay = current.optInt("is_day", 1) == 1
            val weatherCode = current.optInt("weather_code", 0)
            val rain = current.optDouble("precipitation", 0.0).toFloat()
            val wind = current.optDouble("wind_speed_10m", 0.0).toFloat()

            val (icon, condition) = FridayWeatherData.getWeatherDetails(weatherCode, isDay)

            // Parse Hourly Forecast for Wave Graph
            val hourlyItems = mutableListOf<HourlyForecastItem>()
            val hourlyJsonArray = JSONArray()

            val hourly = json.optJSONObject("hourly")
            if (hourly != null) {
                val times = hourly.optJSONArray("time")
                val temps = hourly.optJSONArray("temperature_2m")
                val rainProbs = hourly.optJSONArray("precipitation_probability")
                val codes = hourly.optJSONArray("weather_code")

                if (times != null && temps != null && codes != null) {
                    val count = times.length()
                    var added = 0
                    for (i in 0 until count) {
                        if (added >= 8) break
                        val rawTime = times.optString(i, "")
                        val hourLabel = formatHourLabel(rawTime, added + 1)
                        val hTemp = temps.optDouble(i, temp.toDouble()).toFloat()
                        val hRainProb = rainProbs?.optDouble(i, 0.0)?.toFloat() ?: 0f
                        val hCode = codes.optInt(i, 0)
                        val (hIcon, _) = FridayWeatherData.getWeatherDetails(hCode, true)
                        val tempStr = "${hTemp.toInt()}°"

                        val item = HourlyForecastItem(
                            timeLabel = hourLabel,
                            iconEmoji = hIcon,
                            tempLabel = tempStr,
                            tempValue = hTemp,
                            secondaryValue = hRainProb
                        )
                        hourlyItems.add(item)

                        val obj = JSONObject().apply {
                            put("time", hourLabel)
                            put("icon", hIcon)
                            put("temp", tempStr)
                            put("val", hTemp)
                            put("sec", hRainProb)
                        }
                        hourlyJsonArray.put(obj)
                        added++
                    }
                }
            }

            if (hourlyItems.isEmpty()) {
                hourlyItems.addAll(
                    listOf(
                        HourlyForecastItem("09:00", "☀️", "${temp.toInt()}°", temp, 10f),
                        HourlyForecastItem("11:00", "🌤️", "${(temp + 2).toInt()}°", temp + 2, 15f),
                        HourlyForecastItem("13:00", "☀️", "${(temp + 3).toInt()}°", temp + 3, 20f),
                        HourlyForecastItem("15:00", "🌤️", "${(temp + 2).toInt()}°", temp + 2, 25f),
                        HourlyForecastItem("17:00", "⛅", "${temp.toInt()}°", temp, 15f),
                        HourlyForecastItem("19:00", "🌙", "${(temp - 2).toInt()}°", temp - 2, 5f)
                    )
                )
            }

            val weatherData = FridayWeatherData(
                tempCelsius = temp,
                conditionText = condition,
                iconEmoji = icon,
                weatherCode = weatherCode,
                isDay = isDay,
                cityName = detectedCity,
                humidityPercent = humidity,
                rainMm = rain,
                windSpeedKmh = wind,
                lastUpdatedMillis = System.currentTimeMillis(),
                hourlyForecast = hourlyItems
            )

            saveCachedWeather(context, weatherData, lat, lon, hourlyJsonArray.toString())
            Log.i(TAG, "Weather & Hourly forecast fetched successfully! (${temp}°C, $condition)")
            Result.success(weatherData)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch weather: ${e.message}")
            Result.failure(e)
        }
    }

    private fun formatHourLabel(rawTime: String, fallbackHour: Int): String {
        return try {
            if (rawTime.contains("T")) {
                val parts = rawTime.split("T")
                if (parts.size > 1) {
                    val timePart = parts[1] // e.g. "09:00"
                    return timePart.take(5)
                }
            }
            "+${fallbackHour}h"
        } catch (_: Exception) {
            "+${fallbackHour}h"
        }
    }

    @SuppressLint("MissingPermission")
    private fun resolveCoordinates(context: Context): Triple<Double, Double, String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Try Device LocationManager if permission is granted
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                var loc: Location? = lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (loc == null) {
                    loc = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                }
                if (loc != null) {
                    val city = prefs.getString(KEY_CITY, "My Location") ?: "My Location"
                    return Triple(loc.latitude, loc.longitude, city)
                }
            } catch (e: Exception) {
                Log.w(TAG, "LocationManager check note: ${e.message}")
            }
        }

        // 2. Try fast IP Geolocation (Zero-permission, works everywhere)
        try {
            val ipReq = Request.Builder().url("https://freeipapi.com/api/json").build()
            val ipResp = httpClient.newCall(ipReq).execute()
            if (ipResp.isSuccessful) {
                val ipJson = JSONObject(ipResp.body?.string() ?: "")
                val lat = ipJson.optDouble("latitude", 0.0)
                val lon = ipJson.optDouble("longitude", 0.0)
                val cityName = ipJson.optString("cityName", "My City")
                if (lat != 0.0 && lon != 0.0) {
                    return Triple(lat, lon, cityName)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "IP location note: ${e.message}")
        }

        // 3. Fallback to cached or Default (New Delhi)
        val cachedLat = prefs.getFloat(KEY_LAT, 28.6139f).toDouble()
        val cachedLon = prefs.getFloat(KEY_LON, 77.2090f).toDouble()
        val cachedCity = prefs.getString(KEY_CITY, "New Delhi") ?: "New Delhi"
        return Triple(cachedLat, cachedLon, cachedCity)
    }
}
