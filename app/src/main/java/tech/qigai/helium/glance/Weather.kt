package tech.qigai.helium.glance

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import kotlin.math.roundToInt

/** Coarse condition buckets the UI maps onto its illustrations (same grouping Breezy Weather uses for WMO codes). */
enum class WeatherKind { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, RAIN, SNOW, SLEET, THUNDER }

/** WMO 4677 weather interpretation codes as returned by Open-Meteo's `weather_code`. */
object Wmo {
    fun kind(code: Int): WeatherKind = when (code) {
        0, 1 -> WeatherKind.CLEAR
        2 -> WeatherKind.PARTLY_CLOUDY
        3 -> WeatherKind.CLOUDY
        45, 48 -> WeatherKind.FOG
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherKind.RAIN
        71, 73, 75, 85, 86 -> WeatherKind.SNOW
        77 -> WeatherKind.SLEET
        95, 96, 99 -> WeatherKind.THUNDER
        else -> WeatherKind.CLOUDY
    }

    fun label(code: Int): String = when (code) {
        0 -> "晴"
        1 -> "晴间少云"
        2 -> "多云"
        3 -> "阴"
        45 -> "雾"
        48 -> "雾凇"
        51 -> "小毛毛雨"
        53 -> "毛毛雨"
        55 -> "大毛毛雨"
        56, 57 -> "冻毛毛雨"
        61 -> "小雨"
        63 -> "中雨"
        65 -> "大雨"
        66, 67 -> "冻雨"
        71 -> "小雪"
        73 -> "中雪"
        75 -> "大雪"
        77 -> "米雪"
        80 -> "阵雨"
        81 -> "中阵雨"
        82 -> "强阵雨"
        85 -> "阵雪"
        86 -> "强阵雪"
        95 -> "雷阵雨"
        96, 99 -> "雷阵雨伴冰雹"
        else -> "未知"
    }
}

data class DailyWeather(val date: String, val code: Int, val max: Double, val min: Double, val precipitationChance: Int?)

data class WeatherReport(
    val temperature: Double,
    val apparent: Double?,
    val humidity: Int?,
    val code: Int,
    val isDay: Boolean,
    val windKmh: Double?,
    /** Local time of the observation, ISO-8601 without zone ("2026-11-20T12:30"). */
    val observedAt: String,
    val timezone: String,
    val daily: List<DailyWeather>,
) {
    val kind get() = Wmo.kind(code)
    val label get() = Wmo.label(code)
    val tempText get() = "${temperature.roundToInt()}°"
    val today get() = daily.firstOrNull()

    /** "体感 9° · 2°~13° · 降水 0%" */
    fun detail(): String = buildList {
        apparent?.let { add("体感 ${it.roundToInt()}°") }
        today?.let { add("${it.min.roundToInt()}°~${it.max.roundToInt()}°") }
        today?.precipitationChance?.let { add("降水 $it%") }
    }.joinToString(" · ")
}

data class Place(val name: String, val lat: Double, val lon: Double, val admin: String? = null, val country: String? = null) {
    /** "南京 · 江苏" */
    val display get() = listOfNotNull(name, admin?.takeIf { it != name }).joinToString(" · ")

    companion object {
        /** Fallback when there is neither a location fix nor a city the user typed. */
        val NANJING = Place("南京", 32.0617, 118.7778, "江苏", "中国")
    }
}

/** Open-Meteo forecast + geocoding (free, no key). */
object OpenMeteo {
    const val FORECAST = "https://api.open-meteo.com/v1/forecast"
    const val GEOCODING = "https://geocoding-api.open-meteo.com/v1/search"

    fun forecastUrl(lat: Double, lon: Double): String = FORECAST.toHttpUrl().newBuilder()
        .addQueryParameter("latitude", "%.4f".format(java.util.Locale.US, lat))
        .addQueryParameter("longitude", "%.4f".format(java.util.Locale.US, lon))
        .addQueryParameter("current", "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m")
        .addQueryParameter("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
        .addQueryParameter("timezone", "auto")
        .addQueryParameter("forecast_days", "3")
        .build().toString()

    fun geocodeUrl(name: String): String = GEOCODING.toHttpUrl().newBuilder()
        .addQueryParameter("name", name.trim())
        .addQueryParameter("count", "5")
        .addQueryParameter("language", "zh")
        .addQueryParameter("format", "json")
        .build().toString()

    fun parseForecast(json: String): WeatherReport {
        val root = JSONObject(json)
        if (root.optBoolean("error")) throw IOException("Open-Meteo: " + root.optString("reason"))
        val c = root.getJSONObject("current")
        val d = root.optJSONObject("daily")
        val daily = if (d == null) emptyList() else {
            val times = d.getJSONArray("time")
            val codes = d.optJSONArray("weather_code")
            val max = d.optJSONArray("temperature_2m_max")
            val min = d.optJSONArray("temperature_2m_min")
            val pop = d.optJSONArray("precipitation_probability_max")
            (0 until times.length()).map { i ->
                DailyWeather(
                    date = times.getString(i),
                    code = codes?.optInt(i, -1) ?: -1,
                    max = max?.optDouble(i, Double.NaN) ?: Double.NaN,
                    min = min?.optDouble(i, Double.NaN) ?: Double.NaN,
                    precipitationChance = pop?.takeUnless { it.isNull(i) }?.optInt(i),
                )
            }
        }
        return WeatherReport(
            temperature = c.getDouble("temperature_2m"),
            apparent = c.optDoubleOrNull("apparent_temperature"),
            humidity = c.optDoubleOrNull("relative_humidity_2m")?.roundToInt(),
            code = c.optInt("weather_code", -1),
            isDay = c.optInt("is_day", 1) == 1,
            windKmh = c.optDoubleOrNull("wind_speed_10m"),
            observedAt = c.optString("time"),
            timezone = root.optString("timezone"),
            daily = daily,
        )
    }

    fun parseGeocode(json: String): List<Place> {
        val arr = JSONObject(json).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Place(
                name = o.getString("name"),
                lat = o.getDouble("latitude"),
                lon = o.getDouble("longitude"),
                admin = o.optString("admin1").ifBlank { null },
                country = o.optString("country").ifBlank { null },
            )
        }
    }
}

private fun JSONObject.optDoubleOrNull(key: String): Double? = if (has(key) && !isNull(key)) optDouble(key).takeUnless { it.isNaN() } else null

class OpenMeteoClient(private val http: OkHttpClient, private val userAgent: String) {
    suspend fun forecast(place: Place): Pair<WeatherReport, String> = withContext(Dispatchers.IO) {
        val body = get(OpenMeteo.forecastUrl(place.lat, place.lon))
        OpenMeteo.parseForecast(body) to body
    }

    suspend fun geocode(name: String): List<Place> = withContext(Dispatchers.IO) {
        OpenMeteo.parseGeocode(get(OpenMeteo.geocodeUrl(name)))
    }

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", userAgent).build()
        http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}: ${body.take(120)}")
            return body
        }
    }
}
