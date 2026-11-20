package tech.qigai.helium.glance

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

data class ParsedItem(
    val guid: String,
    val title: String,
    val link: String,
    val summary: String,
    /** Epoch millis; null when the feed gave no usable date. */
    val publishedAt: Long?,
)

data class ParsedFeed(val title: String, val siteUrl: String?, val items: List<ParsedItem>, val format: String)

class FeedParseException(message: String) : Exception(message)

/**
 * RSS 2.0 / RSS 0.9x, Atom 1.0 and RSS 1.0 (RDF) parser on top of an injected [XmlPullParser]
 * (Android's `Xml.newPullParser()` on device, kxml2 in JVM tests). The XML is read into a tiny tree first,
 * which keeps the three dialects' differences in one place.
 */
class FeedParser(private val newParser: () -> XmlPullParser) {

    fun parse(xml: String, baseUrl: String? = null, now: Long = System.currentTimeMillis()): ParsedFeed {
        val root = readTree(xml) ?: throw FeedParseException("不是有效的 XML")
        return when (root.name.lowercase(Locale.ROOT)) {
            "feed" -> atom(root, baseUrl, now)
            "rss" -> rss(root.child("channel") ?: throw FeedParseException("RSS 缺少 channel"), root, baseUrl, now, "rss")
            "rdf" -> rss(root.child("channel") ?: root, root, baseUrl, now, "rdf")
            else -> throw FeedParseException("不是 RSS/Atom 订阅（根节点 <${root.name}>）")
        }
    }

    private fun atom(feed: Node, base: String?, now: Long): ParsedFeed {
        val site = atomLink(feed)?.let { resolve(base, it) }
        val items = feed.children("entry").map { e ->
            val link = atomLink(e)?.let { resolve(site ?: base, it) }.orEmpty()
            val title = Html.toText(e.text("title"))
            item(
                guid = e.text("id").ifBlank { link },
                title = title,
                link = link,
                summary = e.text("summary").ifBlank { e.text("content") },
                date = e.text("published").ifBlank { e.text("updated") },
                now = now,
            )
        }
        return ParsedFeed(Html.toText(feed.text("title")), site, items, "atom")
    }

    private fun atomLink(n: Node): String? {
        val links = n.children("link")
        return (links.firstOrNull { it.attr("rel").let { r -> r.isEmpty() || r == "alternate" } } ?: links.firstOrNull())
            ?.attr("href")?.ifBlank { null }
    }

    private fun rss(channel: Node, root: Node, base: String?, now: Long, format: String): ParsedFeed {
        val site = channel.children("link").firstOrNull { it.text.isNotBlank() }?.text?.trim()?.let { resolve(base, it) }
        // RSS 2.0 nests <item> in <channel>; RSS 1.0 puts them next to it under <rdf:RDF>.
        val nodes = channel.children("item").ifEmpty { root.children("item") }
        val items = nodes.map { i ->
            val link = (i.children("link").firstOrNull { it.text.isNotBlank() }?.text?.trim()
                ?: i.attr("about").ifBlank { null }
                ?: i.text("guid").takeIf { it.startsWith("http") })
                ?.let { resolve(site ?: base, it) }.orEmpty()
            item(
                guid = i.text("guid").ifBlank { i.attr("about") }.ifBlank { link },
                title = Html.toText(i.text("title")),
                link = link,
                summary = i.text("description").ifBlank { i.text("encoded") },
                date = i.text("pubDate").ifBlank { i.text("date") }.ifBlank { i.text("published") }.ifBlank { i.text("updated") },
                now = now,
            )
        }
        return ParsedFeed(Html.toText(channel.text("title")), site, items, format)
    }

    private fun item(guid: String, title: String, link: String, summary: String, date: String, now: Long): ParsedItem {
        val text = Html.toText(summary).take(280)
        val t = title.ifBlank { text.take(60) }.ifBlank { link }
        val id = guid.trim().ifBlank { "h:" + sha1(t + "|" + text) }
        // Like Read You: a date in the future (bad clock on the server) is clamped to the fetch time.
        val at = FeedDates.parse(date)?.coerceAtMost(now)
        return ParsedItem(id, t, link, text, at)
    }

    private fun resolve(base: String?, href: String): String {
        val h = href.trim()
        if (h.startsWith("http://") || h.startsWith("https://")) return h
        return base?.toHttpUrlOrNull()?.resolve(h)?.toString() ?: h
    }

    // ---------------------------------------------------------------- tree

    private class Node(val name: String, val attrs: Map<String, String>) {
        val kids = mutableListOf<Node>()
        val sb = StringBuilder()
        val text get() = sb.toString()
        fun child(n: String) = kids.firstOrNull { it.name.equals(n, true) }
        fun children(n: String) = kids.filter { it.name.equals(n, true) }
        fun text(n: String) = child(n)?.text?.trim().orEmpty()
        fun attr(n: String) = attrs[n].orEmpty()
    }

    private fun readTree(xml: String): Node? {
        val p = newParser()
        runCatching { p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true) }
        // kxml2 relaxed mode tolerates HTML entities such as &nbsp; that many feeds leave undeclared.
        runCatching { p.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
        p.setInput(StringReader(xml.trimStart('\uFEFF', ' ', '\n', '\r', '\t')))
        val stack = ArrayDeque<Node>()
        var root: Node? = null
        try {
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> {
                        val attrs = (0 until p.attributeCount).associate { p.getAttributeName(it).substringAfter(':') to p.getAttributeValue(it) }
                        val n = Node(p.name.substringAfter(':'), attrs)
                        stack.lastOrNull()?.kids?.add(n)
                        if (root == null) root = n
                        stack.addLast(n)
                    }
                    XmlPullParser.END_TAG -> stack.removeLastOrNull()
                    XmlPullParser.TEXT -> stack.lastOrNull()?.sb?.append(p.text)
                }
                ev = p.next()
            }
        } catch (e: Exception) {
            // A truncated or slightly broken document still yields what was read before the error.
            if (root == null || root!!.kids.isEmpty()) throw FeedParseException("XML 解析失败：${e.message}")
        }
        return root
    }
}

object FeedDates {
    private val zones = mapOf(
        "UT" to "+0000", "UTC" to "+0000", "GMT" to "+0000", "Z" to "+0000",
        "EST" to "-0500", "EDT" to "-0400", "CST" to "-0600", "CDT" to "-0500",
        "MST" to "-0700", "MDT" to "-0600", "PST" to "-0800", "PDT" to "-0700",
        "CET" to "+0100", "CEST" to "+0200", "BST" to "+0100", "JST" to "+0900", "HKT" to "+0800",
    )

    private val rfc822: DateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .optionalStart().appendPattern("EEE, ").optionalEnd()
        .appendPattern("d MMM yyyy H:mm")
        .optionalStart().appendPattern(":ss").optionalEnd()
        .appendPattern(" xx")
        .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
        .toFormatter(Locale.ENGLISH)

    /** Parses RFC 822/1123 (RSS) and ISO-8601 (Atom, Dublin Core) dates to epoch millis. */
    fun parse(raw: String?): Long? {
        val s = raw?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
        if (s.isEmpty()) return null
        runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        runCatching { return Instant.parse(s).toEpochMilli() }
        runCatching { return ZonedDateTime.parse(s).toInstant().toEpochMilli() }
        runCatching { return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli() }
        runCatching { return LocalDate.parse(s).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }
        // RFC 822: normalise the zone to a numeric offset, drop a bad weekday, then parse.
        var t = s.replace(Regex("^[A-Za-z]{3,9},?\\s*"), "")
        val zone = t.substringAfterLast(' ')
        t = when {
            zones.containsKey(zone.uppercase()) -> t.substringBeforeLast(' ') + " " + zones.getValue(zone.uppercase())
            Regex("[+-]\\d{2}:\\d{2}").matches(zone) -> t.substringBeforeLast(' ') + " " + zone.replace(":", "")
            Regex("[+-]\\d{4}").matches(zone) -> t
            else -> "$t +0000"
        }
        return runCatching { ZonedDateTime.parse(t, rfc822).toInstant().toEpochMilli() }.getOrNull()
    }
}

/** Small HTML helpers: text extraction, &lt;head&gt; metadata and feed autodiscovery, all regex-based. */
object Html {
    private val entity = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);")
    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "mdash" to "—", "ndash" to "–", "hellip" to "…", "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”",
        "middot" to "·", "copy" to "©", "reg" to "®", "trade" to "™",
    )

    fun decode(s: String): String = entity.replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
            e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
            else -> named[e] ?: named[e.lowercase()] ?: m.value
        }
    }

    /** Strips tags (dropping script/style), decodes entities and collapses whitespace. */
    fun toText(html: String): String {
        if (html.isBlank()) return ""
        var s = html.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        s = s.replace(Regex("(?i)<br\\s*/?>|</p>"), " ")
        s = s.replace(Regex("(?s)<[^>]+>"), "")
        // Escaped markup (an RSS description holding "&lt;p&gt;") decodes into tags, so strip once more.
        s = decode(s).replace(Regex("(?s)<[^>]+>"), "")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    data class PageMeta(val title: String, val description: String, val feeds: List<String>)

    fun meta(html: String, baseUrl: String): PageMeta {
        val head = html.substringBefore("</head>", html.take(200_000))
        val tags = Regex("(?is)<(meta|link)\\b([^>]*)>").findAll(head).map { it.groupValues[1].lowercase() to attrs(it.groupValues[2]) }.toList()
        fun metaContent(vararg keys: String) = keys.firstNotNullOfOrNull { k ->
            tags.firstOrNull { (t, a) -> t == "meta" && (a["property"]?.equals(k, true) == true || a["name"]?.equals(k, true) == true) }?.second?.get("content")
        }?.let { toText(it) }?.ifBlank { null }
        val title = metaContent("og:title", "twitter:title")
            ?: Regex("(?is)<title[^>]*>(.*?)</title>").find(head)?.groupValues?.get(1)?.let { toText(it) }
            ?: ""
        val desc = metaContent("og:description", "description", "twitter:description") ?: ""
        val base = baseUrl.toHttpUrlOrNull()
        val feeds = tags.filter { (t, a) ->
            t == "link" && a["rel"]?.split(' ')?.any { it.equals("alternate", true) } == true &&
                a["type"]?.lowercase()?.let { it.contains("rss") || it.contains("atom") || it.contains("rdf") } == true
        }.mapNotNull { (_, a) -> a["href"]?.let { decode(it) } }
            .mapNotNull { h -> if (h.startsWith("http")) h else base?.resolve(h)?.toString() }
            .distinct()
        return PageMeta(title, desc, feeds)
    }

    fun metaValue(html: String, key: String): String? {
        val tags = Regex("(?is)<meta\\b([^>]*)>").findAll(html).map { attrs(it.groupValues[1]) }
        return tags.firstOrNull { a -> listOf("property", "name", "itemprop").any { a[it]?.equals(key, true) == true } }?.get("content")?.let { decode(it).trim() }
    }

    private fun attrs(s: String): Map<String, String> =
        Regex("([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))").findAll(s).associate { m ->
            m.groupValues[1].lowercase() to (m.groups[3]?.value ?: m.groups[4]?.value ?: m.groups[5]?.value ?: "")
        }

    fun looksLikeFeed(body: String): Boolean {
        val head = body.trimStart('\uFEFF', ' ', '\n', '\r', '\t').take(2048).lowercase()
        return head.contains("<rss") || head.contains("<feed") || head.contains("<rdf:rdf") ||
            (head.startsWith("<?xml") && !head.contains("<html"))
    }
}

/** X (Twitter) has no public feed; the user supplies an RSSHub or Nitter instance and we build the per-account URL. */
object XFeeds {
    private val handleRe = Regex("^[A-Za-z0-9_]{1,15}$")

    /** "@sama", "sama", "https://x.com/sama/status/1" → "sama"; null when it isn't an X account. */
    fun handle(input: String): String? {
        val s = input.trim()
        if (s.startsWith("@")) return s.drop(1).takeIf { handleRe.matches(it) }
        val url = (if (s.contains("://")) s else "https://$s").toHttpUrlOrNull()
        if (url != null && url.host.removePrefix("www.").removePrefix("mobile.") in setOf("x.com", "twitter.com")) {
            return url.pathSegments.firstOrNull()?.takeIf { handleRe.matches(it) && it !in setOf("home", "i", "search", "explore") }
        }
        return null
    }

    /**
     * Builds the feed URL on the user's bridge: Nitter serves `/<handle>/rss`, RSSHub `/twitter/user/<handle>`.
     * A bridge URL that already contains `{handle}` is used as a template.
     */
    fun feedUrl(bridge: String, handle: String): String {
        val b = bridge.trim().trimEnd('/').let { if (it.contains("://")) it else "https://$it" }
        return when {
            b.contains("{handle}") -> b.replace("{handle}", handle)
            b.contains("nitter", ignoreCase = true) -> "$b/$handle/rss"
            else -> "$b/twitter/user/$handle"
        }
    }
}

internal fun sha1(s: String): String =
    MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).take(10).joinToString("") { "%02x".format(it) }
