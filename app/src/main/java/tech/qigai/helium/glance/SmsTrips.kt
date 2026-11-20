package tech.qigai.helium.glance

import android.Manifest
import android.content.Context
import android.provider.Telephony
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A flight / train trip parsed out of one SMS, for the Home hero card (M6).
 *
 * Only ever read on-device and parsed locally: the raw SMS text never leaves the phone and is never
 * uploaded. [SmsTripParser] is pure (no Android classes) so it is unit-tested with fake SMS text;
 * [SmsSource] is the thin ContentResolver wrapper around it.
 */
data class SmsTrip(
    val kind: Kind,
    /** e.g. "航班 MU5101", "高铁 G1234次". */
    val title: String,
    /** Departure, epoch millis in the device zone. */
    val begin: Long,
    /** e.g. "南京南-上海虹桥", null when the SMS gives no route. */
    val route: String?,
    /** Sender id from the inbox (e.g. "1069…", "铁路12306"), for the hero's source line. */
    val sender: String?,
) {
    enum class Kind { FLIGHT, TRAIN }
}

object SmsTripParser {
    private val flightNo = Regex("\\b([A-Z]{2}\\d{3,4})\\b")
    private val trainNo = Regex("([GDCZTK]\\d{1,4})次")
    private val flightWord = Regex("航班|登机|起飞|到达|机票")
    private val trainWord = Regex("高铁|火车|动车|列车|12306|车票|车次|开车")
    private val fullDate = Regex("(\\d{4})年(\\d{1,2})月(\\d{1,2})日")
    private val shortDate = Regex("(\\d{1,2})月(\\d{1,2})日")
    private val dashDate = Regex("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})")
    private val dashShortDate = Regex("(?<!\\d)(\\d{1,2})[-/](\\d{1,2})(?!\\d)")
    private val timeRe = Regex("(\\d{1,2})[:：](\\d{2})")
    private val departTime = Regex("(?:起飞|出发|开车|登机|发车)[^0-9]{0,8}(\\d{1,2})[:：](\\d{2})")
    private val departTimeAfter = Regex("(\\d{1,2})[:：](\\d{2})\\s*(?:起飞|出发|开车|开)")
    private val routeRe = Regex("([\u4e00-\u9fa5]{2,8}(?:机场|站)?)\\s*[-—–→～至]\\s*([\u4e00-\u9fa5]{2,8}(?:机场|站)?)")

    /**
     * Parses one SMS. Returns null when it isn't a flight/train notice, when it has no usable
     * date+time, or when the trip isn't upcoming: the departure must fall within
     * [now − 30 min, now + 60 days]. A month/day without a year is read as this year, or next year
     * when that date has already passed (a Dec 30 SMS about a Jan 2 flight); SMS older than 90 days
     * are ignored so an old trip never resurfaces a year later.
     */
    fun parse(body: String, sender: String?, smsDate: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): SmsTrip? {
        if (body.isBlank()) return null
        if (smsDate < now - 90L * 24 * 3600_000L || smsDate > now + 24 * 3600_000L) return null
        val text = body.replace('\u00a0', ' ')
        val train = trainNo.find(text)
        val flight = if (train == null) flightNo.find(text) else null
        val kind = when {
            train != null && trainWord.containsMatchIn(text) -> SmsTrip.Kind.TRAIN
            train != null && fullDate.containsMatchIn(text) || train != null && shortDate.containsMatchIn(text) -> SmsTrip.Kind.TRAIN
            flight != null && flightWord.containsMatchIn(text) -> SmsTrip.Kind.FLIGHT
            else -> return null
        }
        val (year, month, day) = parseDate(text, now, zone) ?: return null
        val (hour, minute) = parseTime(text) ?: return null
        val begin = runCatching {
            var at = LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
            if (at < now) {
                // No year in the SMS and the date already passed this year: it must be next year's.
                at = LocalDateTime.of(year + 1, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
            }
            at
        }.getOrNull() ?: return null
        if (begin < now - 30 * 60_000L || begin > now + 60L * 24 * 3600_000L) return null
        val route = routeRe.find(text)?.let { "${it.groupValues[1]}-${it.groupValues[2]}" }
        val title = when (kind) {
            SmsTrip.Kind.FLIGHT -> "航班 ${flight!!.groupValues[1]}"
            SmsTrip.Kind.TRAIN -> {
                val no = train!!.groupValues[1]
                val prefix = when (no.first()) {
                    'G', 'D' -> "高铁"
                    'C' -> "城际"
                    else -> "火车"
                }
                "$prefix ${no}次"
            }
        }
        return SmsTrip(kind, title, begin, route, sender?.trim()?.ifBlank { null })
    }

    /** Nearest upcoming trip, or null. Trips are already filtered to upcoming by [parse]. */
    fun pick(trips: List<SmsTrip>, now: Long): SmsTrip? = trips.filter { it.begin >= now - 30 * 60_000L }.minByOrNull { it.begin }

    private fun parseDate(text: String, now: Long, zone: ZoneId): Triple<Int, Int, Int>? {
        val thisYear = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zone).year
        fullDate.find(text)?.let { return Triple(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()) }
        shortDate.find(text)?.let { return Triple(thisYear, it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
        dashDate.find(text)?.let { return Triple(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()) }
        dashShortDate.find(text)?.let { return Triple(thisYear, it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
        return null
    }

    private fun parseTime(text: String): Pair<Int, Int>? {
        val m = departTime.find(text) ?: departTimeAfter.find(text) ?: timeRe.find(text) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || min !in 0..59) return null
        return h to min
    }
}

/** Read-only inbox scan for trip SMS. No SMS content is stored or uploaded; only parsed trips are kept in memory. */
object SmsSource {
    fun granted(context: Context) = context.hasPermission(Manifest.permission.READ_SMS)

    /** Upcoming flight/train trips from SMS received in the last 90 days, nearest first. */
    fun upcoming(context: Context, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): List<SmsTrip> {
        if (!granted(context)) return emptyList()
        val out = mutableListOf<SmsTrip>()
        runCatching {
            val uri = Telephony.Sms.Inbox.CONTENT_URI
            val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
            val since = (now - 90L * 24 * 3600_000L).toString()
            context.contentResolver.query(uri, projection, "${Telephony.Sms.DATE} > ?", arrayOf(since), "${Telephony.Sms.DATE} DESC LIMIT 200")
                ?.use { c ->
                    while (c.moveToNext()) {
                        val sender = c.getString(0)
                        val body = c.getString(1).orEmpty()
                        val date = c.getLong(2)
                        SmsTripParser.parse(body, sender, date, now, zone)?.let { out += it }
                    }
                }
        }
        return out.distinctBy { it.title to it.begin }.sortedBy { it.begin }
    }
}
