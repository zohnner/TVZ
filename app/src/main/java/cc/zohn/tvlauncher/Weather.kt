package cc.zohn.tvlauncher

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.roundToInt

private const val TAG = "TvLauncher"

// "City" or "City, State/Country" (e.g. "Austin, Texas"). Blank hides the weather line.
const val WEATHER_CITY = "Kansas City, Missouri"

data class WeatherNow(val tempF: Int, val summary: String)

// Open-Meteo: free, no account or API key.
suspend fun fetchWeather(city: String): WeatherNow? = withContext(Dispatchers.IO) {
    try {
        val name = city.substringBefore(',').trim()
        val region = city.substringAfter(',', "").trim()
        val geo = httpGetJson(
            "https://geocoding-api.open-meteo.com/v1/search?count=10&language=en&format=json&name=" +
                URLEncoder.encode(name, "UTF-8")
        )
        val results = geo.optJSONArray("results") ?: return@withContext null
        val places = (0 until results.length()).map { results.getJSONObject(it) }
        val place = places.firstOrNull {
            region.isEmpty() ||
                it.optString("admin1").equals(region, ignoreCase = true) ||
                it.optString("country").equals(region, ignoreCase = true) ||
                it.optString("country_code").equals(region, ignoreCase = true)
        } ?: places.first()

        val forecast = httpGetJson(
            "https://api.open-meteo.com/v1/forecast?latitude=${place.getDouble("latitude")}" +
                "&longitude=${place.getDouble("longitude")}" +
                "&current=temperature_2m,weather_code&temperature_unit=fahrenheit"
        )
        val current = forecast.getJSONObject("current")
        WeatherNow(current.getDouble("temperature_2m").roundToInt(), describe(current.getInt("weather_code")))
    } catch (e: Exception) {
        Log.w(TAG, "weather fetch failed", e)
        null
    }
}


// WMO weather interpretation codes, as used by Open-Meteo.
private fun describe(code: Int): String = when (code) {
    0 -> "Clear"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61, 63, 65 -> "Rain"
    66, 67 -> "Freezing rain"
    71, 73, 75, 77 -> "Snow"
    80, 81, 82 -> "Showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunderstorm, hail"
    else -> ""
}
