package tech.qigai.helium.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.time.Instant
import java.time.ZoneId

private fun fixture(name: String): String =
    requireNotNull(GlanceParsingTest::class.java.getResource("/glance/$name")) { "missing fixture $name" }.readText()

class GlanceParsingTest {
    private val parser = FeedParser { KXmlParser() }
    private val now = Instant.parse("2026-11-20T04:30:00Z").toEpochMilli()

    // ------------------------------------------------------------- weather

    @Test fun `open-meteo live Nanjing fixture parses`() {
        val w = OpenMeteo.parseForecast(fixture("open-meteo-nanjing.json"))
        assertEquals(11.9, w.temperature, 0.001)
        assertEquals(8.6, w.apparent!!, 0.001)
        assertEquals(46, w.humidity)
        assertEquals(0, w.code)
        assertTrue(w.isDay)
        assertEquals("Asia/Shanghai", w.timezone)
        assertEquals("2026-11-20T12:30", w.observedAt)
        assertEquals(3, w.daily.size)
        assertEquals(12.7, w.today!!.max, 0.001)
        assertEquals(2.3, w.today!!.min, 0.001)
        assertEquals(0, w.today!!.precipitationChance)
        assertEquals(3, w.daily[2].code)
        assertEquals("12°", w.tempText)
        assertEquals("晴", w.label)
        assertEquals(WeatherKind.CLEAR, w.kind)
        assertEquals("体感 9° · 2°~13° · 降水 0%", w.detail())
    }

    @Test fun `wmo codes map to kinds and labels`() {
        assertEquals(WeatherKind.CLEAR, Wmo.kind(1))
        assertEquals(WeatherKind.PARTLY_CLOUDY, Wmo.kind(2))
        assertEquals(WeatherKind.CLOUDY, Wmo.kind(3))
        assertEquals(WeatherKind.FOG, Wmo.kind(48))
        listOf(51, 57, 61, 65, 67, 80, 82).forEach { assertEquals("code $it", WeatherKind.RAIN, Wmo.kind(it)) }
        listOf(71, 75, 85, 86).forEach { assertEquals(WeatherKind.SNOW, Wmo.kind(it)) }
        assertEquals(WeatherKind.SLEET, Wmo.kind(77))
        assertEquals(WeatherKind.THUNDER, Wmo.kind(99))
        assertEquals("中雨", Wmo.label(63))
        assertEquals("阴", Wmo.label(3))
    }

    @Test fun `open-meteo error body throws`() {
        val r = runCatching { OpenMeteo.parseForecast("""{"error":true,"reason":"Latitude must be in range"}""") }
        assertTrue(r.exceptionOrNull()?.message!!.contains("Latitude"))
    }

    @Test fun `geocoding returns places`() {
        val p = OpenMeteo.parseGeocode(fixture("open-meteo-geocode-nanjing.json"))
        assertEquals(2, p.size)
        assertEquals("南京", p[0].name)
        assertEquals("江苏", p[0].admin)
        assertEquals(32.06167, p[0].lat, 1e-6)
        assertEquals("南京 · 江苏", p[0].display)
        assertTrue(OpenMeteo.parseGeocode("""{"generationtime_ms":0.1}""").isEmpty())
    }

    @Test fun `forecast url carries coordinates and fields`() {
        val u = OpenMeteo.forecastUrl(32.02, 118.85)
        assertTrue(u, u.contains("latitude=32.0200") && u.contains("longitude=118.8500"))
        assertTrue(u.contains("weather_code") && u.contains("timezone=auto"))
    }

    // ------------------------------------------------------------- feeds

    @Test fun `rss 2 fixture`() {
        val f = parser.parse(fixture("rss2.xml"), "https://sspai.example/feed", now)
        assertEquals("rss", f.format)
        assertEquals("少数派 & 朋友", f.title)
        assertEquals("https://sspai.example/", f.siteUrl)
        assertEquals(4, f.items.size)
        val a = f.items[0]
        assertEquals("post-1", a.guid)
        assertEquals("Android 15 的 隐私 新功能", a.title)
        assertEquals("https://sspai.example/post/1", a.link)
        assertEquals("系统级的 权限面板。", a.summary)
        assertEquals(Instant.parse("2026-11-19T00:30:00Z").toEpochMilli(), a.publishedAt)
        val b = f.items[1]
        assertEquals("https://sspai.example/post/2", b.link)
        assertEquals(b.link, b.guid) // no <guid>: the link identifies it
        assertEquals("订阅才是\"信息差\"。", b.summary)
        assertEquals(Instant.parse("2026-11-18T22:00:00Z").toEpochMilli(), b.publishedAt)
        assertEquals(Instant.parse("2026-11-17T09:00:00Z").toEpochMilli(), f.items[2].publishedAt)
        // guid that is a URL doubles as the link; a future date is clamped to the fetch time.
        assertEquals("https://sspai.example/post/4", f.items[3].link)
        assertEquals(now, f.items[3].publishedAt)
    }

    @Test fun `atom fixture`() {
        val f = parser.parse(fixture("atom.xml"), "https://blog.example.org/feed.atom", now)
        assertEquals("atom", f.format)
        assertEquals("Kotlin Blog", f.title)
        assertEquals("https://blog.example.org/", f.siteUrl)
        assertEquals(2, f.items.size)
        val e = f.items[0]
        assertEquals("tag:blog.example.org,2026:kotlin-2-3", e.guid)
        assertEquals("https://blog.example.org/2026/11/kotlin-2-3", e.link)
        assertEquals("New K2 features", e.summary)
        assertEquals(Instant.parse("2026-11-19T09:00:00Z").toEpochMilli(), e.publishedAt) // published wins over updated
        assertEquals(Instant.parse("2026-11-18T08:00:00Z").toEpochMilli(), f.items[1].publishedAt)
        assertEquals("Body text", f.items[1].summary)
    }

    @Test fun `rss 1 rdf fixture`() {
        val f = parser.parse(fixture("rdf.xml"), null, now)
        assertEquals("rdf", f.format)
        assertEquals("Slash RDF", f.title)
        assertEquals(2, f.items.size)
        assertEquals("https://slash.example/story/1", f.items[0].guid)
        assertEquals("Hello & welcome", f.items[0].summary)
        assertEquals(Instant.parse("2026-11-19T06:00:00Z").toEpochMilli(), f.items[0].publishedAt)
        assertNull(f.items[1].publishedAt)
    }

    @Test fun `html is not a feed`() {
        assertFalse(Html.looksLikeFeed(fixture("page.html")))
        assertTrue(Html.looksLikeFeed(fixture("rss2.xml")))
        assertTrue(Html.looksLikeFeed(fixture("atom.xml")))
        val r = runCatching { parser.parse(fixture("page.html")) }
        assertTrue(r.isFailure)
    }

    @Test fun `rfc 822 date variants`() {
        val t = Instant.parse("2026-11-19T13:05:00Z").toEpochMilli()
        assertEquals(t, FeedDates.parse("Thu, 19 Nov 2026 13:05:00 GMT"))
        assertEquals(t, FeedDates.parse("19 Nov 2026 13:05 +0000"))
        assertEquals(t, FeedDates.parse("Thu, 19 Nov 2026 08:05:00 EST"))
        assertEquals(t, FeedDates.parse("Thu, 19 Nov 2026 21:05:00 +08:00"))
        assertEquals(t, FeedDates.parse("Thu,  19 Nov 2026 13:05:00"))
        assertEquals(t, FeedDates.parse("2026-11-19T21:05:00+08:00"))
        assertEquals(t, FeedDates.parse("2026-11-19T13:05:00Z"))
        assertNull(FeedDates.parse("yesterday"))
        assertNull(FeedDates.parse(""))
    }

    @Test fun `html meta and feed autodiscovery`() {
        val m = Html.meta(fixture("page.html"), "https://helium.example/changelog/")
        assertEquals("Helium 更新日志", m.title)
        assertEquals("Helium 每周更新 & 发布说明", m.description)
        assertEquals(listOf("https://helium.example/feed.xml", "https://helium.example/atom.xml"), m.feeds)
        assertEquals("1,299.00", Html.metaValue(fixture("page.html"), "product:price:amount"))
    }

    @Test fun `x handles and bridge urls`() {
        assertEquals("sama", XFeeds.handle("@sama"))
        assertEquals("elonmusk", XFeeds.handle("https://x.com/elonmusk/status/1"))
        assertEquals("jack", XFeeds.handle("twitter.com/jack"))
        assertNull(XFeeds.handle("https://example.com/sama"))
        assertNull(XFeeds.handle("https://x.com/home"))
        assertEquals("https://rsshub.app/twitter/user/sama", XFeeds.feedUrl("https://rsshub.app/", "sama"))
        assertEquals("https://nitter.net/sama/rss", XFeeds.feedUrl("nitter.net", "sama"))
        assertEquals("https://my.host/x/sama.xml", XFeeds.feedUrl("https://my.host/x/{handle}.xml", "sama"))
    }

    // ------------------------------------------------------------- cards

    @Test fun `btc prompt maps to coingecko without a model`() {
        val d = CardTemplates.match("做一张盯 BTC 价格的卡")!!
        assertEquals("BTC 价格", d.title)
        assertTrue(d.url.startsWith("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin"))
        assertEquals(60, d.intervalMinutes)
        val lines = CardExtractor.extract(d, fixture("coingecko-btc.json"))
        assertEquals(listOf("美元", "人民币", "24h 涨跌"), lines.map { it.label })
        assertEquals(listOf("$61,400", "¥409,536", "-3.15%"), lines.map { it.value })
        assertEquals(61400.0, lines[0].raw!!, 0.0)
        assertEquals("ETH 价格", CardTemplates.match("帮我盯一下以太坊")!!.title)
        assertEquals("人民币", CardTemplates.match("比特币人民币价格")!!.fields[0].label)
        assertNull(CardTemplates.match("每天给我看 X 上 AI agent 的热帖"))
        assertNull(CardTemplates.match("button 颜色")) // "ton" only as a whole word
    }

    @Test fun `definition round trips and validates`() {
        val d = CardTemplates.match("btc")!!
        val back = CardDefinition.parse(d.toJson().toString()).getOrThrow()
        assertEquals(d, back)
    }

    @Test fun `definition from a model reply in a fence is accepted and clamped`() {
        val reply = """好的：
```json
{"title":"美元汇率","url":"https://open.er-api.com/v6/latest/USD","format":"json",
 "fields":[{"label":"人民币","path":"$.rates.CNY","format":"number"},{"label":"日元","path":"$['rates']['JPY']"}],
 "refresh_minutes":5}
```"""
        val d = CardDefinition.parse(reply).getOrThrow()
        assertEquals(60, d.intervalMinutes) // clamped to ≥ 60 min
        assertEquals("text", d.fields[1].format)
        val lines = CardExtractor.extract(d, """{"result":"success","rates":{"CNY":7.1234,"JPY":150.5}}""")
        assertEquals("7.12", lines[0].value)
        assertEquals("150.5", lines[1].value)
    }

    @Test fun `invalid definitions are rejected`() {
        fun err(json: String) = CardDefinition.parse(json).exceptionOrNull()?.message.orEmpty()
        assertTrue(err("""{"error":"需要登录"}""").contains("需要登录"))
        assertTrue(err("""{"title":"x","url":"ftp://a/b","fields":[{"path":"$.a"}]}""").contains("地址"))
        assertTrue(err("""{"title":"x","url":"http://192.168.1.1/a","fields":[{"path":"$.a"}]}""").contains("内网"))
        assertTrue(err("""{"title":"x","url":"https://a.com","fields":[]}""").contains("字段"))
        assertTrue(err("""{"title":"x","url":"https://a.com","format":"html","fields":[{"path":"$.a"}]}""").contains("JSONPath"))
        assertTrue(err("""{"title":"x","url":"https://a.com","format":"html","fields":[{"path":"regex:(["}]}""").contains("正则"))
        assertTrue(err("""{"title":"x","url":"https://a.com","format":"json","fields":[{"path":"$.a[x]"}]}""").contains("下标"))
        assertTrue(err("""{"title":"","url":"https://a.com","fields":[{"path":"$.a"}]}""").contains("标题"))
        assertTrue(err("no json here").contains("JSON"))
        val big = CardDefinition.parse("""{"title":"x","url":"https://a.com","refresh_minutes":99999,"fields":[{"path":"$.a"}]}""").getOrThrow()
        assertEquals(CardDefinition.MAX_INTERVAL, big.intervalMinutes)
    }

    @Test fun `jsonpath subset`() {
        val root = org.json.JSONObject("""{"data":[{"p":1.5,"n":"a"},{"p":2,"n":"b"}],"m":{"k-1":"v"}}""")
        assertEquals(listOf("1.5"), JsonPath.select(root, "$.data[0].p").map { it.toString() })
        assertEquals(listOf<Any?>("b"), JsonPath.select(root, "data[-1].n"))
        assertEquals(listOf<Any?>("a", "b"), JsonPath.select(root, "$.data[*].n"))
        assertEquals(listOf<Any?>("v"), JsonPath.select(root, "$.m['k-1']"))
        assertTrue(JsonPath.select(root, "$.nope.x").isEmpty())
    }

    @Test fun `regex meta and items rules`() {
        val html = fixture("page.html")
        val d = CardDefinition("页", "https://helium.example/", "html",
            listOf(CardField("价格", "regex:class=\"price\">¥\\s*([\\d,]+)", "cny"), CardField("标题", "meta:og:title")), 60)
        assertEquals(listOf("¥1,299", "Helium 更新日志"), CardExtractor.extract(d, html).map { it.value })
        val feed = parser.parse(fixture("atom.xml"), "https://blog.example.org/", now)
        val r = CardDefinition("博客", "https://blog.example.org/feed.atom", "rss", listOf(CardField("最新", "items:1")), 60)
        assertEquals(listOf("Kotlin 2.3 Released"), CardExtractor.extract(r, "", feed).map { it.label })
        val miss = runCatching { CardExtractor.extract(d.copy(fields = listOf(CardField("x", "regex:nomatch(\\d)"))), html) }
        assertTrue(miss.exceptionOrNull() is CardExtractionException)
    }

    // ------------------------------------------------------------- reasons + hero

    @Test fun `reason replies map back to ids`() {
        val e = listOf(ReasonPrompt.Entry(10, "Kotlin 2.3", "Kotlin Blog"), ReasonPrompt.Entry(20, "Android 15", "少数派"))
        assertEquals(mapOf(10L to "你在写 Kotlin", 20L to "你在做 Android 应用"),
            ReasonPrompt.parse("```json\n{\"1\":\"你在写 Kotlin\",\"2\":\"你在做 Android 应用\"}\n```", e))
        assertEquals(mapOf(20L to "你订阅了少数派"), ReasonPrompt.parse("2. 你订阅了少数派\n9. 越界", e))
        val prompt = ReasonPrompt.user(e, listOf("在做 Helium Android app"))
        assertTrue(prompt.contains("1. [Kotlin Blog] Kotlin 2.3") && prompt.contains("- 在做 Helium"))
    }

    @Test fun `hero formats tomorrow morning meeting`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = Instant.parse("2026-11-20T12:00:00Z").toEpochMilli() // 20:00 CST
        val begin = Instant.parse("2026-11-21T01:30:00Z").toEpochMilli() // 09:30 CST tomorrow
        val h = HeroFormat.event("产品周会", begin, begin + 3600_000, "3 楼会议室", false, now, zone)
        assertEquals("明天 · 日程", h.tag)
        assertEquals("09:30", h.time)
        assertEquals("明天 上午 · 09:30–10:30 · 3 楼会议室", h.meta)
        assertEquals("13 小时后", h.route)
        val live = HeroFormat.event("站会", now - 60_000, now + 600_000, null, false, now, zone)
        assertEquals("进行中", live.route)
        assertNotNull(HeroFormat.event("", begin, begin, null, true, now, zone).title)
    }
}
