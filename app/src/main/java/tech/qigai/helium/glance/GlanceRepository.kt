package tech.qigai.helium.glance

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import tech.qigai.helium.data.db.FeedItemEntity
import tech.qigai.helium.data.db.FeedItemRow
import tech.qigai.helium.data.db.FeedSourceEntity
import tech.qigai.helium.data.db.GlanceCardEntity
import tech.qigai.helium.data.db.HeliumDatabase
import tech.qigai.helium.data.db.RoomMemoryStore
import tech.qigai.helium.data.db.WeatherCacheEntity
import tech.qigai.helium.data.llm.ChatTurn
import tech.qigai.helium.data.llm.LlmClient
import tech.qigai.helium.data.llm.Role
import tech.qigai.helium.data.llm.StreamEvent
import tech.qigai.helium.data.settings.SettingsRepository
import tech.qigai.helium.data.model.HeroEvent
import tech.qigai.helium.ui.illo.IlloKind
import java.io.IOException
import java.nio.charset.Charset
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class WeatherView(val place: String, val placeSource: String, val report: WeatherReport, val fetchedAt: Long)

data class CardView(
    val id: String,
    val title: String,
    val prompt: String,
    val host: String,
    val intervalMinutes: Int,
    val size: String,
    val lines: List<CardLine>,
    val history: List<Double>,
    val lastRefreshAt: Long?,
    val error: String?,
)

/** Everything 速览 and the Home hero read: weather, feeds, custom cards and the calendar. */
class GlanceRepository(
    private val context: Context,
    private val db: HeliumDatabase,
    private val http: OkHttpClient,
    private val userAgent: String,
    private val settings: SettingsRepository,
    private val memories: RoomMemoryStore,
    private val llm: LlmClient = LlmClient(http),
) {
    private val dao = db.glance()
    private val prefs = context.getSharedPreferences("glance", Context.MODE_PRIVATE)
    /** Bounded client for plain fetches (the shared one is tuned for long model streams). */
    private val web = http.newBuilder().callTimeout(25, TimeUnit.SECONDS).build()
    private val meteo = OpenMeteoClient(web, userAgent)
    private val location = LocationResolver(context, web, userAgent)
    private val parser = FeedParser { Xml.newPullParser() }
    private val lock = Mutex()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val weather: Flow<WeatherView?> = dao.observeWeather().map { e ->
        e?.let { runCatching { WeatherView(it.place, it.placeSource, OpenMeteo.parseForecast(it.json), it.fetchedAt) }.getOrNull() }
    }
    val sources: Flow<List<FeedSourceEntity>> = dao.observeSources()
    val items: Flow<List<FeedItemRow>> = dao.observeItems(200)
    val cards: Flow<List<CardView>> = dao.observeCards().map { list -> list.map(::toView) }

    // ------------------------------------------------------------------ settings

    var permissionsAsked: Boolean
        get() = prefs.getBoolean("perms_asked", false)
        set(v) = prefs.edit().putBoolean("perms_asked", v).apply()

    var xBridge: String
        get() = prefs.getString("x_bridge", "").orEmpty()
        set(v) = prefs.edit().putString("x_bridge", v.trim()).apply()

    val manualPlace: Place?
        get() = prefs.getString("manual_city", null)?.let {
            Place(it, prefs.getFloat("manual_lat", 0f).toDouble(), prefs.getFloat("manual_lon", 0f).toDouble(), prefs.getString("manual_admin", null))
        }

    fun locationGranted() = location.granted()

    /** Geocodes [name] with Open-Meteo and pins weather to it. */
    suspend fun setManualCity(name: String): Result<Place> = runCatching {
        val place = meteo.geocode(name).firstOrNull() ?: throw IOException("找不到「$name」")
        prefs.edit().putString("manual_city", place.name).putFloat("manual_lat", place.lat.toFloat())
            .putFloat("manual_lon", place.lon.toFloat()).putString("manual_admin", place.admin).apply()
        refreshWeather(force = true, useLocation = false).getOrThrow()
        place
    }

    suspend fun useDeviceLocation(): Result<Unit> {
        prefs.edit().remove("manual_city").remove("manual_lat").remove("manual_lon").remove("manual_admin").apply()
        return refreshWeather(force = true, useLocation = true)
    }

    // ------------------------------------------------------------------ weather

    /**
     * Manual city > device location (foreground only) > last located place > 南京. Skips the network when the
     * cache is under 30 minutes old for the same place.
     */
    suspend fun refreshWeather(force: Boolean = false, useLocation: Boolean = true): Result<Unit> = runCatching {
        val cached = dao.weather()
        val (place, source) = resolvePlace(cached, useLocation)
        val samePlace = cached != null && kotlin.math.abs(cached.lat - place.lat) < 0.05 && kotlin.math.abs(cached.lon - place.lon) < 0.05
        if (!force && samePlace && System.currentTimeMillis() - cached!!.fetchedAt < 30 * 60_000L) return@runCatching
        val (_, body) = meteo.forecast(place)
        dao.putWeather(WeatherCacheEntity(0, place.display, place.lat, place.lon, source, body, System.currentTimeMillis()))
    }

    private suspend fun resolvePlace(cached: WeatherCacheEntity?, useLocation: Boolean): Pair<Place, String> {
        manualPlace?.let { return it to "manual" }
        if (useLocation) {
            val loc = runCatching { location.current() }.getOrNull()
            if (loc != null) {
                val near = cached?.takeIf { it.placeSource == "gps" && kotlin.math.abs(it.lat - loc.latitude) < 0.05 && kotlin.math.abs(it.lon - loc.longitude) < 0.05 }
                val name = near?.place ?: location.cityName(loc.latitude, loc.longitude) ?: "当前位置"
                return Place(name, loc.latitude, loc.longitude) to "gps"
            }
        }
        if (cached != null && cached.placeSource == "gps") return Place(cached.place, cached.lat, cached.lon) to "gps"
        return Place.NANJING to "default"
    }

    // ------------------------------------------------------------------ feeds

    /** Adds an RSS/Atom URL, a web page (feed autodiscovery, else page snapshot) or an X account. */
    suspend fun addSource(raw: String): Result<FeedSourceEntity> = runCatching {
        val input = raw.trim()
        require(input.isNotEmpty()) { "请输入网址或 @账号" }
        val now = System.currentTimeMillis()
        val handle = XFeeds.handle(input)
        val resolved: Triple<String, String, ParsedFeed> // kind, url, feed
        if (handle != null) {
            val bridge = xBridge
            require(bridge.isNotBlank()) { "X 没有公开订阅源。先在下方填写你的 RSSHub 或 Nitter 地址，再添加 @$handle" }
            val url = XFeeds.feedUrl(bridge, handle)
            val feed = parser.parse(fetch(url).first, url, now)
            resolved = Triple("X", url, feed.copy(title = "X · @$handle"))
        } else {
            val start = if (input.contains("://")) input else "https://$input"
            require(start.toHttpUrlOrNull() != null) { "网址无效" }
            resolved = discover(start, now)
        }
        val (kind, url, feed) = resolved
        dao.sourceByUrl(url)?.let { throw IllegalStateException("「${it.title}」已经在来源里了") }
        val src = FeedSourceEntity(
            kind = kind, input = input, url = url, title = feed.title.ifBlank { url.toHttpUrlOrNull()?.host ?: url }.take(40),
            siteUrl = feed.siteUrl, enabled = true, addedAt = now, lastFetchedAt = now, lastError = null,
        )
        val id = dao.insertSource(src)
        store(id, feed, now)
        src.copy(id = id)
    }

    private suspend fun discover(url: String, now: Long): Triple<String, String, ParsedFeed> {
        val (body, finalUrl) = fetch(url)
        if (Html.looksLikeFeed(body)) return Triple("RSS", finalUrl, parser.parse(body, finalUrl, now))
        val meta = Html.meta(body, finalUrl)
        val origin = finalUrl.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" }
        val candidates = meta.feeds + listOfNotNull(origin).flatMap { o -> listOf("$o/feed", "$o/rss.xml", "$o/atom.xml", "$o/index.xml") }
        for (c in candidates.distinct().take(6)) {
            val feed = runCatching { fetch(c).let { (b, u) -> if (Html.looksLikeFeed(b)) u to parser.parse(b, u, now) else null } }.getOrNull() ?: continue
            if (feed.second.items.isNotEmpty()) return Triple("RSS", feed.first, feed.second)
        }
        // No feed anywhere: follow the page itself; a changed title/description becomes a new item.
        return Triple("WEB", finalUrl, pageSnapshot(meta, finalUrl, now))
    }

    private fun pageSnapshot(meta: Html.PageMeta, url: String, now: Long): ParsedFeed {
        val title = meta.title.ifBlank { url }
        val item = ParsedItem("page:" + sha1(title + "|" + meta.description), title, url, meta.description.take(280), now)
        return ParsedFeed(title.take(40), url, listOf(item), "web")
    }

    private suspend fun store(sourceId: Long, feed: ParsedFeed, now: Long): Int {
        val rows = feed.items.take(50).mapIndexed { i, it ->
            // Undated items keep feed order by stepping back a second each.
            FeedItemEntity(0, sourceId, it.guid, it.title, it.link, it.summary, it.publishedAt ?: (now - i * 1000L), now, null)
        }
        val inserted = dao.insertItems(rows).count { it != -1L }
        dao.trim(sourceId, 100)
        return inserted
    }

    suspend fun removeSource(id: Long) {
        dao.deleteItemsOf(id)
        dao.deleteSource(id)
    }

    /** Fetches every enabled source; returns how many new items arrived. */
    suspend fun refreshFeeds(minAgeMs: Long = 0): Int {
        var fresh = 0
        val now = System.currentTimeMillis()
        for (s in dao.enabledSources()) {
            if (minAgeMs > 0 && s.lastFetchedAt != null && s.lastError == null && now - s.lastFetchedAt < minAgeMs) continue
            runCatching {
                val (body, u) = fetch(s.url)
                val feed = if (s.kind == "WEB") pageSnapshot(Html.meta(body, u), s.url, now) else parser.parse(body, u, now)
                fresh += store(s.id, feed, now)
                dao.markFetched(s.id, now, null, "")
            }.onFailure { dao.markFetched(s.id, now, (it.message ?: it.javaClass.simpleName).take(120), "") }
        }
        return fresh
    }

    // ------------------------------------------------------------------ reasons

    fun modelReady(): Boolean = runCatching { settings.activeEndpoint() }.getOrNull()
        ?.let { it.apiKey.isNotBlank() && it.model.isNotBlank() && it.baseUrl.isNotBlank() } == true

    /** Writes "为什么推给你" for up to 24 new items in batches of 12; does nothing without a model. */
    suspend fun writeReasons(): Int {
        if (!modelReady()) return 0
        val pending = dao.itemsWithoutReason(24)
        if (pending.isEmpty()) return 0
        val mem = runCatching { memories.recent(30).map { it.text } }.getOrDefault(emptyList())
        var n = 0
        for (batch in pending.chunked(12)) {
            val entries = batch.map { ReasonPrompt.Entry(it.id, it.title, it.sourceTitle) }
            val reply = runCatching { complete(ReasonPrompt.SYSTEM, ReasonPrompt.user(entries, mem), "helium-glance-reasons") }.getOrNull() ?: break
            ReasonPrompt.parse(reply, entries).forEach { (id, r) -> dao.setReason(id, r); n++ }
        }
        return n
    }

    private suspend fun complete(system: String, user: String, session: String): String {
        val sb = StringBuilder()
        llm.stream(settings.activeEndpoint(), system, listOf(ChatTurn(Role.USER, user)), session).collect { if (it is StreamEvent.Text) sb.append(it.delta) }
        return sb.toString()
    }

    // ------------------------------------------------------------------ custom cards

    /** One sentence → definition: built-in templates first (BTC works with no model), then the model. */
    suspend fun generateCard(prompt: String): Result<CardDefinition> = runCatching {
        CardTemplates.match(prompt)?.let { return@runCatching it }
        if (!modelReady()) throw IllegalStateException("这类卡片需要模型来生成。去「我的 → 模型与推理」配置后再试（盯 BTC / ETH 价格不需要模型）。")
        val reply = complete(CardTemplates.GENERATOR_SYSTEM, prompt, "helium-glance-cards")
        CardDefinition.parse(reply).getOrThrow()
    }

    suspend fun preview(def: CardDefinition): Result<List<CardLine>> = runCatching { run(def) }

    private suspend fun run(def: CardDefinition): List<CardLine> {
        val (body, u) = fetch(def.url)
        val feed = if (def.format == "rss") parser.parse(body, u) else null
        return CardExtractor.extract(def, body, feed)
    }

    suspend fun addCard(prompt: String, def: CardDefinition, lines: List<CardLine>?): String {
        val now = System.currentTimeMillis()
        val id = "card-" + UUID.randomUUID().toString().take(8)
        val position = dao.cards().size
        dao.upsertCard(
            GlanceCardEntity(
                id = id, title = def.title, prompt = prompt, definition = def.toJson().toString(),
                intervalMinutes = def.intervalMinutes, size = def.size,
                valuesJson = lines?.let { encodeValues(it, emptyList()) }, lastRefreshAt = lines?.let { now },
                nextRefreshAt = if (lines != null) now + def.intervalMinutes * 60_000L else now, lastError = null,
                position = position, createdAt = now,
            ),
        )
        if (lines == null) refreshCards(force = false)
        return id
    }

    suspend fun removeCard(id: String) = dao.deleteCard(id)

    /** Refreshes cards that are due (10 min slack so an hourly worker never skips an hourly card). */
    suspend fun refreshCards(force: Boolean): Int {
        val now = System.currentTimeMillis()
        var n = 0
        for (c in dao.cards()) {
            if (!force && c.nextRefreshAt > now + 10 * 60_000L) continue
            val def = CardDefinition.parse(c.definition).getOrNull() ?: continue
            val next = now + def.intervalMinutes * 60_000L
            runCatching { run(def) }.onSuccess { lines ->
                val history = decodeHistory(c.valuesJson) + listOfNotNull(lines.firstOrNull()?.raw)
                dao.upsertCard(c.copy(valuesJson = encodeValues(lines, history.takeLast(24)), lastRefreshAt = now, nextRefreshAt = next, lastError = null))
                n++
            }.onFailure {
                dao.upsertCard(c.copy(nextRefreshAt = next, lastError = (it.message ?: "刷新失败").take(120)))
            }
        }
        return n
    }

    private fun encodeValues(lines: List<CardLine>, history: List<Double>) = JSONObject()
        .put("lines", JSONArray().apply { lines.forEach { put(JSONObject().put("label", it.label).put("value", it.value).apply { it.raw?.let { r -> put("raw", r) } }) } })
        .put("history", JSONArray().apply { history.forEach { put(it) } })
        .toString()

    private fun decodeHistory(raw: String?): List<Double> = runCatching {
        val a = JSONObject(raw!!).getJSONArray("history")
        (0 until a.length()).map { a.getDouble(it) }
    }.getOrDefault(emptyList())

    private fun toView(c: GlanceCardEntity): CardView {
        val def = CardDefinition.parse(c.definition).getOrNull()
        val lines = runCatching {
            val a = JSONObject(c.valuesJson!!).getJSONArray("lines")
            (0 until a.length()).map { a.getJSONObject(it) }.map { CardLine(it.optString("label"), it.optString("value"), if (it.has("raw")) it.optDouble("raw") else null) }
        }.getOrDefault(emptyList())
        return CardView(
            c.id, c.title, c.prompt, def?.url?.toHttpUrlOrNull()?.host.orEmpty(), c.intervalMinutes, c.size,
            lines, decodeHistory(c.valuesJson), c.lastRefreshAt, c.lastError,
        )
    }

    // ------------------------------------------------------------------ hero

    /** Home hero: the nearest real calendar event, else today's weather; null when neither is available. */
    suspend fun hero(now: Long = System.currentTimeMillis()): HeroEvent? {
        val ev = withContext(Dispatchers.IO) { CalendarSource.pick(CalendarSource.upcoming(context, now), now) }
        if (ev != null) {
            val h = HeroFormat.event(ev.title, ev.begin, ev.end, ev.location, ev.allDay, now, ZoneId.systemDefault())
            val kind = when {
                Regex("航班|飞机|flight|登机", RegexOption.IGNORE_CASE).containsMatchIn(ev.title) -> IlloKind.FLIGHT
                Regex("高铁|火车|动车|列车|\\b[GDCZTK]\\d{1,4}\\b").containsMatchIn(ev.title) -> IlloKind.TRAIN
                else -> IlloKind.CALENDAR
            }
            return HeroEvent(h.tag, h.title, h.meta, h.time, h.route, kind)
        }
        val w = dao.weather()?.let { e -> runCatching { WeatherView(e.place, e.placeSource, OpenMeteo.parseForecast(e.json), e.fetchedAt) }.getOrNull() } ?: return null
        val r = w.report
        return HeroEvent(
            tag = "今天 · 天气",
            title = "${w.place.substringBefore(" · ")} · ${r.label}",
            meta = r.detail(),
            time = r.tempText,
            route = r.daily.getOrNull(1)?.let { "明天 ${Wmo.label(it.code)} ${it.min.roundToInt()}°~${it.max.roundToInt()}°" } ?: r.label,
            kind = illoFor(r.kind),
        )
    }

    // ------------------------------------------------------------------ all

    /** Worker + pull-to-refresh entry. [foreground] allows a location fix (background location isn't requested). */
    suspend fun refreshAll(force: Boolean, foreground: Boolean) {
        lock.withLock {
            _busy.value = true
            try {
                refreshWeather(force, useLocation = foreground)
                // Opening 速览 re-fetches a source at most every 15 min; the worker and the refresh button always do.
                refreshFeeds(minAgeMs = if (!force && foreground) 15 * 60_000L else 0)
                refreshCards(force)
                runCatching { writeReasons() }
            } finally {
                _busy.value = false
            }
        }
    }

    // ------------------------------------------------------------------ http

    /** GET with a 2 MB cap; decodes with the header charset, else the XML/HTML declaration, else UTF-8. Returns body + final URL. */
    private suspend fun fetch(url: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/html;q=0.8, application/json;q=0.8, */*;q=0.5")
            .build()
        web.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body ?: throw IOException("空响应")
            val bytes = body.source().let { src -> src.request(MAX_BYTES); src.buffer.let { b -> b.readByteArray(minOf(b.size, MAX_BYTES)) } }
            val head = String(bytes, 0, minOf(bytes.size, 1024), Charsets.ISO_8859_1)
            val charset = body.contentType()?.charset()
                ?: Regex("""(?i)(?:encoding|charset)\s*=\s*["']?([\w-]+)""").find(head)?.groupValues?.get(1)?.let { runCatching { Charset.forName(it) }.getOrNull() }
                ?: Charsets.UTF_8
            String(bytes, charset) to r.request.url.toString()
        }
    }

    companion object {
        private const val MAX_BYTES = 2L * 1024 * 1024

        fun illoFor(k: WeatherKind): IlloKind = when (k) {
            WeatherKind.CLEAR -> IlloKind.SUNNY
            WeatherKind.RAIN, WeatherKind.THUNDER -> IlloKind.RAIN
            else -> IlloKind.CLOUDY
        }
    }
}
