package tech.qigai.helium.agent.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** One search result, normalised across providers. [summary] is the long, model-facing text. */
data class SearchHit(
    val title: String,
    val url: String,
    val snippet: String = "",
    val summary: String = "",
    val published: String? = null,
    val siteName: String? = null,
)

class SearchException(message: String) : Exception(message)

/** A web search backend. Implementations do blocking IO on [Dispatchers.IO] and never throw for "no results". */
interface SearchProvider {
    val id: String
    val label: String
    suspend fun search(query: String, count: Int): List<SearchHit>
}

private val JSON = "application/json".toMediaType()

/**
 * Exa's public MCP endpoint, the same keyless backend opencode's `websearch` uses: one JSON-RPC `tools/call` to
 * `web_search_exa`, answered as JSON or as an SSE `data:` frame. The text result is blocks of `Title:/URL:/Published:`
 * headers followed by `Highlights:` or `Text:`, separated by `\n\n---\n\n`.
 */
class ExaMcpSearch(
    private val http: OkHttpClient,
    /** Exa's own endpoint, or the Worker's `/mcp/exa` when Helium Cloud is configured (reachable where Exa isn't). */
    private val endpoint: () -> String = { ENDPOINT },
    private val bearer: () -> String? = { null },
) : SearchProvider {
    override val id = "exa"
    override val label = "Exa（免费）"

    override suspend fun search(query: String, count: Int): List<SearchHit> = withContext(Dispatchers.IO) {
        val url = endpoint()
        val body = JSONObject()
            .put("jsonrpc", "2.0").put("id", 1).put("method", "tools/call")
            .put("params", JSONObject().put("name", "web_search_exa")
                .put("arguments", JSONObject().put("query", query).put("numResults", count.coerceIn(1, 20))))
        val req = Request.Builder().url(url)
            .header("Accept", "application/json, text/event-stream")
            .apply { bearer()?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") } }
            .post(body.toString().toRequestBody(JSON)).build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw SearchException("Exa 搜索失败：HTTP ${resp.code}")
            parseResponse(text)
        }
    }

    companion object {
        const val ENDPOINT = "https://mcp.exa.ai/mcp"

        /** Accepts a plain JSON-RPC body or an SSE stream; returns the hits of the first `result` found. */
        fun parseResponse(body: String): List<SearchHit> {
            val payloads = buildList {
                val t = body.trim()
                if (t.startsWith("{")) add(t)
                body.lineSequence().filter { it.startsWith("data:") }.forEach { add(it.removePrefix("data:").trim()) }
            }
            for (p in payloads) {
                val o = runCatching { JSONObject(p) }.getOrNull() ?: continue
                o.optJSONObject("error")?.let { throw SearchException("Exa 搜索失败：${it.optString("message")}") }
                val result = o.optJSONObject("result") ?: continue
                if (result.optBoolean("isError")) throw SearchException("Exa 搜索失败：${firstText(result).take(200)}")
                return parseResults(firstText(result))
            }
            throw SearchException("Exa 返回了无法识别的响应")
        }

        private fun firstText(result: JSONObject): String {
            val content = result.optJSONArray("content") ?: return ""
            for (i in 0 until content.length()) {
                val c = content.optJSONObject(i) ?: continue
                if (c.optString("type") == "text" && c.optString("text").isNotBlank()) return c.optString("text")
            }
            return ""
        }

        private val urlRe = Regex("(?m)^URL:\\s*(.+)$")
        private val titleRe = Regex("(?m)^Title:\\s*(.+)$")
        private val pubRe = Regex("(?m)^Published:\\s*(.+)$")
        private val bodyRe = Regex("(?m)^(?:Highlights|Text):\\s*\\n?([\\s\\S]*)$")

        fun parseResults(text: String): List<SearchHit> = text.split("\n\n---\n\n").mapNotNull { block ->
            val url = urlRe.find(block)?.groupValues?.get(1)?.trim() ?: return@mapNotNull null
            val title = titleRe.find(block)?.groupValues?.get(1)?.trim()?.takeIf { it != "N/A" }
            val pub = pubRe.find(block)?.groupValues?.get(1)?.trim()?.takeIf { it != "N/A" }
            val content = bodyRe.find(block)?.groupValues?.get(1).orEmpty()
                .lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
            SearchHit(
                title = title ?: url,
                url = url,
                snippet = content.replace(Regex("\\s+"), " ").take(200),
                summary = content.take(1_500),
                published = pub,
            )
        }
    }
}

/**
 * DeepSeek Search API (`POST /v1/tools/search`, $0.75 per 1,000 successful requests). [baseUrl] is either
 * `https://api.deepseek.com` with the user's own key, or the Helium Worker, which serves the same path and
 * injects the key server-side so it never sits on the phone.
 */
class DeepSeekSearch(
    private val http: OkHttpClient,
    private val baseUrl: () -> String,
    private val bearer: () -> String,
) : SearchProvider {
    override val id = "deepseek"
    override val label = "DeepSeek"

    override suspend fun search(query: String, count: Int): List<SearchHit> = withContext(Dispatchers.IO) {
        val token = bearer()
        if (token.isBlank()) throw SearchException("DeepSeek 搜索还没配置密钥或 Helium Cloud")
        val body = JSONObject().put("query", query.take(128)).put("count", count.coerceIn(1, 50))
        val req = Request.Builder().url(baseUrl().trimEnd('/') + "/v1/tools/search")
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON)).build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrDefault(text.take(200))
                throw SearchException(when (resp.code) {
                    401 -> "DeepSeek 密钥无效"
                    402 -> "DeepSeek 账户余额不足"
                    429 -> "DeepSeek 搜索请求太频繁，稍后再试"
                    else -> "DeepSeek 搜索失败：HTTP ${resp.code} $msg"
                })
            }
            parseResponse(text)
        }
    }

    companion object {
        fun parseResponse(body: String): List<SearchHit> {
            val arr: JSONArray = JSONObject(body).optJSONArray("results") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val url = o.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                SearchHit(
                    title = o.optString("title").ifBlank { url },
                    url = url,
                    snippet = o.optString("snippet"),
                    summary = o.optString("summary").ifBlank { o.optString("snippet") },
                    published = o.optString("date").takeIf { it.isNotBlank() && it != "null" },
                    siteName = o.optString("site_name").takeIf { it.isNotBlank() && it != "null" },
                )
            }
        }
    }
}

/** Numbered, model-facing rendering. The numbers are what the model cites as [1], [2]. */
object SearchFormat {
    fun render(query: String, provider: String, hits: List<SearchHit>): String {
        if (hits.isEmpty()) return "没有找到“$query”的结果（$provider）。换个说法或更具体的关键词再试。"
        return buildString {
            append("搜索“").append(query).append("”（").append(provider).append("），").append(hits.size).append(" 条结果。")
            append("回答时用 [编号] 标注来源；需要细节时用 web_fetch 读原文。\n")
            hits.forEachIndexed { i, h ->
                append("\n[").append(i + 1).append("] ").append(h.title).append('\n')
                append("    ").append(h.url).append('\n')
                val meta = listOfNotNull(h.siteName, h.published).joinToString(" · ")
                if (meta.isNotBlank()) append("    ").append(meta).append('\n')
                val text = h.summary.ifBlank { h.snippet }
                if (text.isNotBlank()) append("    ").append(text.replace("\n", "\n    ")).append('\n')
            }
        }.trimEnd()
    }

    /** Pulls the sources back out of a rendered observation, for citation chips in the chat. */
    private val itemRe = Regex("(?m)^\\[(\\d+)] (.+)\\n {4}(https?://\\S+)")
    fun sources(rendered: String): List<Pair<String, String>> =
        itemRe.findAll(rendered).map { it.groupValues[2].trim() to it.groupValues[3].trim() }.toList()
}
