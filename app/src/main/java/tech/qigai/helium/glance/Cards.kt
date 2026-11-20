package tech.qigai.helium.glance

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** How one value on a custom card is pulled out of the fetched document. */
data class CardField(
    val label: String,
    /** `$.a.b[0]` (JSONPath subset), `regex:<pattern>` (group 1), `meta:<name>` (HTML meta), or `items:<n>` (feed titles). */
    val rule: String,
    /** text · number · usd · cny · percent · change (signed percent). */
    val format: String = "text",
)

/** A generated card: data source + extraction rules + refresh interval. Stored as JSON in `glance_cards`. */
data class CardDefinition(
    val title: String,
    val url: String,
    /** json · html · rss */
    val format: String,
    val fields: List<CardField>,
    val intervalMinutes: Int,
    val size: String = "WIDE",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("title", title).put("url", url).put("format", format)
        .put("refresh_minutes", intervalMinutes).put("size", size)
        .put("fields", JSONArray().apply { fields.forEach { put(JSONObject().put("label", it.label).put("path", it.rule).put("format", it.format)) } })

    companion object {
        const val MIN_INTERVAL = 60
        const val MAX_INTERVAL = 24 * 60
        private val FORMATS = setOf("json", "html", "rss")
        private val VALUE_FORMATS = setOf("text", "number", "usd", "cny", "percent", "change")

        /**
         * Parses and validates a definition (from the model or from storage). Interval is clamped to ≥ 60 min,
         * the URL must be public http(s), and every rule must be one of the supported kinds.
         */
        fun parse(raw: String): Result<CardDefinition> = runCatching {
            val o = JSONObject(extractJsonObject(raw) ?: throw IllegalArgumentException("没有找到卡片定义 JSON"))
            o.optString("error").takeIf { it.isNotBlank() }?.let { throw IllegalArgumentException(it) }
            val title = o.optString("title").trim().take(24)
            require(title.isNotEmpty()) { "卡片缺少标题" }
            val url = o.optString("url").trim()
            checkUrl(url)
            val format = o.optString("format", "json").lowercase(Locale.ROOT).ifBlank { "json" }
            require(format in FORMATS) { "不支持的数据格式：$format" }
            val arr = o.optJSONArray("fields") ?: JSONArray()
            val fields = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { f ->
                val rule = f.optString("path").ifBlank { f.optString("rule") }.trim()
                val vf = f.optString("format", "text").lowercase(Locale.ROOT).let { if (it in VALUE_FORMATS) it else "text" }
                CardField(f.optString("label").trim().take(16).ifBlank { "值" }, rule, vf)
            }
            require(fields.isNotEmpty()) { "卡片至少需要一个字段" }
            require(fields.size <= 6) { "字段太多（最多 6 个）" }
            fields.forEach { checkRule(it.rule, format) }
            val minutes = o.optInt("refresh_minutes", o.optInt("interval_minutes", 60)).coerceIn(MIN_INTERVAL, MAX_INTERVAL)
            val size = o.optString("size", "WIDE").uppercase(Locale.ROOT).let { if (it in setOf("SMALL", "WIDE", "LARGE")) it else "WIDE" }
            CardDefinition(title, url, format, fields, minutes, size)
        }

        private fun checkUrl(url: String) {
            val u = url.toHttpUrlOrNull() ?: throw IllegalArgumentException("数据源地址无效：$url")
            val h = u.host
            val private = h == "localhost" || h.endsWith(".local") || h.startsWith("127.") || h.startsWith("10.") ||
                h.startsWith("192.168.") || Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(h) || h == "0.0.0.0"
            require(!private) { "不能使用内网地址作为数据源" }
        }

        private fun checkRule(rule: String, format: String) {
            when {
                rule.startsWith("regex:") -> runCatching { Regex(rule.removePrefix("regex:")) }.getOrElse { throw IllegalArgumentException("正则无效：${rule.take(40)}") }
                rule.startsWith("meta:") -> require(rule.length > 5) { "meta 规则缺少名称" }
                rule.startsWith("items:") -> require(format == "rss") { "items 规则只能用于 RSS 源" }
                else -> {
                    require(format == "json") { "JSONPath 规则只能用于 JSON 源：$rule" }
                    JsonPath.tokens(rule)
                }
            }
        }

        /** The first balanced `{…}` in a model reply (which may wrap it in prose or a ``` fence). */
        fun extractJsonObject(text: String): String? {
            val start = text.indexOf('{')
            if (start < 0) return null
            var depth = 0
            var inStr = false
            var esc = false
            for (i in start until text.length) {
                val c = text[i]
                if (inStr) {
                    if (esc) esc = false else if (c == '\\') esc = true else if (c == '"') inStr = false
                    continue
                }
                when (c) {
                    '"' -> inStr = true
                    '{' -> depth++
                    '}' -> if (--depth == 0) return text.substring(start, i + 1)
                }
            }
            return null
        }
    }
}

/** Minimal JSONPath: `$`, `.key`, `['key']`, `[n]`, `[*]`. The leading `$` is optional. */
object JsonPath {
    sealed interface Tok {
        data class Key(val k: String) : Tok
        data class Idx(val i: Int) : Tok
        data object All : Tok
    }

    fun tokens(path: String): List<Tok> {
        var p = path.trim()
        require(p.isNotEmpty()) { "JSONPath 为空" }
        if (p.startsWith("$")) p = p.drop(1)
        val out = mutableListOf<Tok>()
        var i = 0
        while (i < p.length) {
            when (p[i]) {
                '.' -> {
                    val end = p.indexOfAny(charArrayOf('.', '['), i + 1).let { if (it < 0) p.length else it }
                    val k = p.substring(i + 1, end)
                    require(k.isNotEmpty()) { "JSONPath 无效：$path" }
                    out += if (k == "*") Tok.All else Tok.Key(k)
                    i = end
                }
                '[' -> {
                    val end = p.indexOf(']', i)
                    require(end > i) { "JSONPath 缺少 ]：$path" }
                    val inner = p.substring(i + 1, end).trim()
                    out += when {
                        inner == "*" -> Tok.All
                        inner.startsWith("'") || inner.startsWith("\"") -> Tok.Key(inner.trim('\'', '"'))
                        else -> Tok.Idx(inner.toIntOrNull() ?: throw IllegalArgumentException("JSONPath 下标无效：$path"))
                    }
                    i = end + 1
                }
                else -> {
                    // "bitcoin.usd" without "$." — read a bare first key.
                    val end = p.indexOfAny(charArrayOf('.', '['), i).let { if (it < 0) p.length else it }
                    out += Tok.Key(p.substring(i, end))
                    i = end
                }
            }
        }
        return out
    }

    fun select(root: Any?, path: String): List<Any?> {
        var cur: List<Any?> = listOf(root)
        for (t in tokens(path)) {
            cur = cur.flatMap { v ->
                when (t) {
                    is Tok.Key -> if (v is JSONObject && v.has(t.k)) listOf(v.opt(t.k)) else emptyList()
                    is Tok.Idx -> if (v is JSONArray) {
                        val i = if (t.i < 0) v.length() + t.i else t.i
                        if (i in 0 until v.length()) listOf(v.opt(i)) else emptyList()
                    } else emptyList()
                    Tok.All -> when (v) {
                        is JSONArray -> (0 until v.length()).map { v.opt(it) }
                        is JSONObject -> v.keys().asSequence().map { v.opt(it) }.toList()
                        else -> emptyList()
                    }
                }
            }
        }
        return cur.filter { it != null && it != JSONObject.NULL }
    }
}

data class CardLine(val label: String, val value: String, val raw: Double? = null)

class CardExtractionException(message: String) : Exception(message)

object CardExtractor {
    /** Applies every field of [def] to a fetched [body]; feed bodies need [feed] already parsed. */
    fun extract(def: CardDefinition, body: String, feed: ParsedFeed? = null): List<CardLine> {
        val json: Any? = if (def.format == "json") runCatching {
            body.trim().let { if (it.startsWith("[")) JSONArray(it) else JSONObject(it) }
        }.getOrElse { throw CardExtractionException("返回的不是 JSON") } else null
        val lines = def.fields.flatMap { f ->
            val rule = f.rule
            when {
                rule.startsWith("regex:") -> {
                    val m = Regex(rule.removePrefix("regex:")).find(body)
                    listOfNotNull(m?.let { (it.groups[1] ?: it.groups[0])?.value }?.let { line(f, Html.toText(it)) })
                }
                rule.startsWith("meta:") -> listOfNotNull(Html.metaValue(body, rule.removePrefix("meta:"))?.let { line(f, it) })
                rule.startsWith("items:") -> {
                    val n = rule.removePrefix("items:").toIntOrNull()?.coerceIn(1, 10) ?: 3
                    (feed?.items ?: emptyList()).take(n).map { CardLine(it.title, "") }
                }
                else -> JsonPath.select(json, rule).take(5).map { line(f, it.toString()) }
            }
        }
        if (lines.isEmpty()) throw CardExtractionException("规则没有匹配到任何数据")
        return lines
    }

    private fun line(f: CardField, raw: String): CardLine {
        val num = raw.trim().replace(",", "").toDoubleOrNull()
        return CardLine(f.label, format(raw.trim(), num, f.format), num)
    }

    fun format(raw: String, num: Double?, format: String): String {
        if (num == null) return raw.take(80)
        return when (format) {
            "usd" -> "$" + grouped(num)
            "cny" -> "¥" + grouped(num)
            "number" -> grouped(num)
            "percent" -> BigDecimal(num).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%"
            "change" -> (if (num >= 0) "+" else "") + BigDecimal(num).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%"
            else -> raw.take(80)
        }
    }

    private fun grouped(n: Double): String {
        val pattern = when {
            kotlin.math.abs(n) >= 1000 -> "#,##0"
            kotlin.math.abs(n) >= 1 -> "#,##0.00"
            else -> "0.######"
        }
        return DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(n)
    }
}

/**
 * Built-in definitions for asks that don't need a model. "盯 BTC 价格" always maps to CoinGecko simple/price,
 * so the acceptance card works even before any model is configured.
 */
object CardTemplates {
    private data class Coin(val id: String, val symbol: String, val words: List<String>)

    private val coins = listOf(
        Coin("bitcoin", "BTC", listOf("btc", "比特币", "bitcoin", "大饼")),
        Coin("ethereum", "ETH", listOf("eth", "以太坊", "ethereum", "以太")),
        Coin("solana", "SOL", listOf("sol", "solana")),
        Coin("dogecoin", "DOGE", listOf("doge", "狗狗币", "dogecoin")),
        Coin("binancecoin", "BNB", listOf("bnb", "币安币")),
        Coin("ripple", "XRP", listOf("xrp", "瑞波")),
        Coin("toncoin", "TON", listOf("ton")),
    )

    fun match(prompt: String): CardDefinition? {
        val p = prompt.lowercase(Locale.ROOT)
        val coin = coins.firstOrNull { c ->
            c.words.any { w -> if (w.all { it in 'a'..'z' }) Regex("(?<![a-z])$w(?![a-z])").containsMatchIn(p) else p.contains(w) }
        } ?: return null
        val cnyFirst = p.contains("人民币") || p.contains("cny") || p.contains("¥") || p.contains("元")
        val url = "https://api.coingecko.com/api/v3/simple/price?ids=${coin.id}&vs_currencies=usd,cny&include_24hr_change=true&include_last_updated_at=true"
        val usd = CardField("美元", "$.${coin.id}.usd", "usd")
        val cny = CardField("人民币", "$.${coin.id}.cny", "cny")
        val change = CardField("24h 涨跌", "$.${coin.id}.usd_24h_change", "change")
        return CardDefinition(
            title = "${coin.symbol} 价格",
            url = url,
            format = "json",
            fields = if (cnyFirst) listOf(cny, usd, change) else listOf(usd, cny, change),
            intervalMinutes = 60,
            size = "WIDE",
        )
    }

    /** System prompt for the model-backed generator: one sentence → one JSON definition. */
    const val GENERATOR_SYSTEM = """你是 Helium 的卡片生成器。用户用一句话描述想在「速览」里盯住的信息，你只输出一个 JSON 对象（不要解释），格式：
{"title":"不超过12字的标题","url":"公开、免 Key 的数据地址","format":"json|html|rss","fields":[{"label":"字段名","path":"规则","format":"text|number|usd|cny|percent|change"}],"refresh_minutes":60}
规则 path 只能是：JSONPath（如 $.data[0].price，仅 json）、regex:正则（取第 1 组，html/json 均可）、meta:og:title（HTML meta）、items:3（RSS 前 3 条标题）。
优先使用稳定的免 Key 公开 API：加密货币用 https://api.coingecko.com/api/v3/simple/price，汇率用 https://open.er-api.com/v6/latest/USD，网站更新用它的 RSS。refresh_minutes 不小于 60。字段不超过 4 个。无法实现时输出 {"error":"原因"}。"""
}
