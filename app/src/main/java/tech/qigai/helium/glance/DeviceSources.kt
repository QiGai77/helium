package tech.qigai.helium.glance

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.coroutines.resume

fun Context.hasPermission(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

/**
 * Coarse location without Play Services: the freshest last-known fix from the platform providers, or a single
 * network fix on Android 11+. City names come from the platform [Geocoder] when present (it often isn't on
 * phones without GMS), else from BigDataCloud's keyless client-side reverse geocoder.
 */
class LocationResolver(private val context: Context, private val http: OkHttpClient, private val userAgent: String) {

    fun granted() = context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ||
        context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    @SuppressLint("MissingPermission")
    suspend fun current(): Location? {
        if (!granted()) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
        val last = providers.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }.maxByOrNull { it.time }
        val fresh = last != null && System.currentTimeMillis() - last.time < 3 * 3600_000L
        if (fresh || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return last
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { it in providers } ?: return last
        val fix = withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = android.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching {
                    lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { loc -> if (cont.isActive) cont.resume(loc) }
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
        return fix ?: last
    }

    suspend fun cityName(lat: Double, lon: Double): String? =
        platformGeocode(lat, lon)?.let { a -> a.locality ?: a.subAdminArea ?: a.adminArea }?.let(::cleanCity)
            ?: bigDataCloud(lat, lon)

    @Suppress("DEPRECATION")
    private suspend fun platformGeocode(lat: Double, lon: Double): Address? {
        if (!Geocoder.isPresent()) return null
        val g = Geocoder(context, Locale.SIMPLIFIED_CHINESE)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(6_000) {
                suspendCancellableCoroutine<Address?> { cont ->
                    g.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) { if (cont.isActive) cont.resume(addresses.firstOrNull()) }
                        override fun onError(errorMessage: String?) { if (cont.isActive) cont.resume(null) }
                    })
                }
            }
        } else {
            withContext(Dispatchers.IO) { runCatching { g.getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull() }
        }
    }

    private suspend fun bigDataCloud(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=zh"
            http.newCall(Request.Builder().url(url).header("User-Agent", userAgent).build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val o = JSONObject(r.body?.string().orEmpty())
                o.optString("city").ifBlank { o.optString("locality") }.ifBlank { o.optString("principalSubdivision") }.ifBlank { null }
            }
        }.getOrNull()?.let(::cleanCity)
    }

    private fun cleanCity(s: String) = s.trim().removeSuffix("市").ifBlank { s }
}

/** Next real event from the device calendar (CalendarContract.Instances, so recurring events expand). */
object CalendarSource {
    data class Event(val title: String, val begin: Long, val end: Long, val location: String?, val allDay: Boolean)

    fun granted(context: Context) = context.hasPermission(Manifest.permission.READ_CALENDAR)

    fun upcoming(context: Context, now: Long = System.currentTimeMillis(), days: Int = 7): List<Event> {
        if (!granted(context)) return emptyList()
        val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(b, now - 24 * 3600_000L)
        ContentUris.appendId(b, now + days * 24 * 3600_000L)
        val projection = arrayOf(
            CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.STATUS,
        )
        val out = mutableListOf<Event>()
        val local = ZoneId.systemDefault()
        runCatching {
            context.contentResolver.query(
                b.build(), projection,
                "${CalendarContract.Instances.VISIBLE} = 1", null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    if (!c.isNull(5) && c.getInt(5) == CalendarContract.Instances.STATUS_CANCELED) continue
                    val allDay = c.getInt(3) == 1
                    var begin = c.getLong(1)
                    var end = c.getLong(2)
                    if (allDay) {
                        // All-day instances are stored at UTC midnight; move them onto the local day.
                        begin = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(local).toInstant().toEpochMilli()
                        end = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(local).toInstant().toEpochMilli()
                    }
                    if (end <= now) continue
                    out += Event(c.getString(0).orEmpty(), begin, end, c.getString(4), allDay)
                }
            }
        }
        return out
    }

    /** Timed events win over all-day ones; within the next 36 h, else today's all-day, else the next timed one. */
    fun pick(events: List<Event>, now: Long): Event? {
        val timed = events.filter { !it.allDay }.sortedBy { it.begin }
        return timed.firstOrNull { it.begin <= now + 36 * 3600_000L }
            ?: events.firstOrNull { it.allDay && it.begin <= now }
            ?: timed.firstOrNull()
            ?: events.firstOrNull { it.allDay }
    }
}

/** Opens an article in a Custom Tab, falling back to any browser. */
fun openInBrowser(context: Context, url: String) {
    val uri = Uri.parse(url)
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    } catch (e: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
