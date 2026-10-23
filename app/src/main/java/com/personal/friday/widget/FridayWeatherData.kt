package com.personal.friday.widget

data class HourlyForecastItem(
    val timeLabel: String,
    val iconEmoji: String,
    val tempLabel: String,
    val tempValue: Float = 26f,
    val secondaryValue: Float = 20f
)

data class FridayWeatherData(
    val tempCelsius: Float,
    val conditionText: String,
    val iconEmoji: String,
    val weatherCode: Int,
    val isDay: Boolean,
    val cityName: String,
    val humidityPercent: Int,
    val rainMm: Float,
    val windSpeedKmh: Float,
    val lastUpdatedMillis: Long = System.currentTimeMillis(),
    val hourlyForecast: List<HourlyForecastItem> = emptyList()
) {
    companion object {
        fun default(): FridayWeatherData {
            return FridayWeatherData(
                tempCelsius = 26.0f,
                conditionText = "Clear Skies",
                iconEmoji = "🌤️",
                weatherCode = 0,
                isDay = true,
                cityName = "Locating...",
                humidityPercent = 45,
                rainMm = 0.0f,
                windSpeedKmh = 5.0f,
                hourlyForecast = listOf(
                    HourlyForecastItem("09:00", "☀️", "25°", 25f, 10f),
                    HourlyForecastItem("11:00", "🌤️", "27°", 27f, 15f),
                    HourlyForecastItem("13:00", "☀️", "29°", 29f, 25f),
                    HourlyForecastItem("15:00", "🌤️", "28°", 28f, 30f),
                    HourlyForecastItem("17:00", "⛅", "26°", 26f, 20f),
                    HourlyForecastItem("19:00", "🌙", "24°", 24f, 10f)
                )
            )
        }

        fun getWeatherDetails(code: Int, isDay: Boolean): Pair<String, String> {
            return when (code) {
                0 -> if (isDay) Pair("☀️", "Clear Sky") else Pair("🌙", "Clear Night")
                1 -> if (isDay) Pair("🌤️", "Mainly Clear") else Pair("🌙", "Mainly Clear")
                2 -> Pair("⛅", "Partly Cloudy")
                3 -> Pair("☁️", "Overcast")
                45, 48 -> Pair("🌫️", "Foggy")
                51, 53, 55 -> Pair("🌦️", "Light Drizzle")
                56, 57 -> Pair("🌧️", "Freezing Drizzle")
                61 -> Pair("🌧️", "Light Rain")
                63 -> Pair("🌧️", "Moderate Rain")
                65 -> Pair("🌧️", "Heavy Rain")
                71, 73, 75 -> Pair("🌨️", "Snowfall")
                77 -> Pair("🌨️", "Snow Grains")
                80, 81, 82 -> Pair("🌧️", "Rain Showers")
                85, 86 -> Pair("🌨️", "Snow Showers")
                95 -> Pair("⛈️", "Thunderstorm")
                96, 99 -> Pair("⛈️", "Severe Storm")
                else -> if (isDay) Pair("🌤️", "Fair Weather") else Pair("🌙", "Clear Night")
            }
        }
    }
}
