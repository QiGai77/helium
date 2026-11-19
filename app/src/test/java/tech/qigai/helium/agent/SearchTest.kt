package tech.qigai.helium.agent

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.qigai.helium.agent.search.DeepSeekSearch
import tech.qigai.helium.agent.search.ExaMcpSearch
import tech.qigai.helium.agent.search.SearchFormat
import tech.qigai.helium.agent.search.SearchHit
import tech.qigai.helium.agent.search.SearchProvider
import tech.qigai.helium.agent.search.WebSearchTool
import tech.qigai.helium.agent.tools.ToolContext

class SearchTest {
    private val http = OkHttpClient()

    private val exaText = "Title: deepseek-ai/chat-demo\nURL: https://github.com/deepseek-ai/chat-demo/\nPublished: N/A\nAuthor: N/A\n" +
        "Highlights:\n# chat-demo\n\nA static chat demo.\n\n---\n\n" +
        "Title: Web Tools\nURL: https://api-docs.deepseek.com/guides/web_tools/\nPublished: 2026-11-13T00:00:00.000Z\nText: Two server tools."

    private fun exaRpc(text: String) = JSONObject().put("jsonrpc", "2.0").put("id", 1)
        .put("result", JSONObject().put("content", org.json.JSONArray().put(JSONObject().put("type", "text").put("text", text)))).toString()

    @Test fun exaParsesSseFrame() {
        val hits = ExaMcpSearch.parseResponse("event: message\ndata: ${exaRpc(exaText)}\n\n")
        assertEquals(2, hits.size)
        assertEquals("deepseek-ai/chat-demo", hits[0].title)
        assertNull(hits[0].published)
        assertTrue(hits[0].summary.contains("static chat demo"))
        assertEquals("2026-11-13T00:00:00.000Z", hits[1].published)
        assertEquals("Two server tools.", hits[1].summary)
    }

    @Test fun exaParsesPlainJsonAndErrors() {
        assertEquals(2, ExaMcpSearch.parseResponse(exaRpc(exaText)).size)
        val err = runCatching { ExaMcpSearch.parseResponse("""{"jsonrpc":"2.0","id":1,"error":{"code":-32000,"message":"rate"}}""") }
        assertTrue(err.exceptionOrNull()!!.message!!.contains("rate"))
        assertTrue(runCatching { ExaMcpSearch.parseResponse("<html>") }.isFailure)
    }

    @Test fun exaSendsJsonRpcAndBearerThroughWorker() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: ${exaRpc(exaText)}\n\n"))
            server.start()
            val exa = ExaMcpSearch(http, endpoint = { server.url("/mcp/exa").toString() }, bearer = { "dev-token" })
            val hits = exa.search("chat demo", 5)
            assertEquals(2, hits.size)
            val req = server.takeRequest()
            assertEquals("Bearer dev-token", req.getHeader("Authorization"))
            val body = JSONObject(req.body.readUtf8())
            assertEquals("tools/call", body.getString("method"))
            assertEquals("web_search_exa", body.getJSONObject("params").getString("name"))
            assertEquals(5, body.getJSONObject("params").getJSONObject("arguments").getInt("numResults"))
        }
    }

    @Test fun deepSeekSearchRequestAndErrors() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"results":[{"title":"V4","url":"https://e.com/v4","snippet":"s","summary":"long","site_name":"E","date":"2026-06-01T00:00:00+00:00"}]}"""))
            server.enqueue(MockResponse().setResponseCode(402).setBody("""{"error":{"message":"Insufficient Balance"}}"""))
            server.start()
            val ds = DeepSeekSearch(http, baseUrl = { server.url("/").toString() }, bearer = { "sk" })
            val hits = ds.search("x".repeat(200), 99)
            assertEquals("long", hits[0].summary)
            assertEquals("E", hits[0].siteName)
            val req = server.takeRequest()
            assertEquals("/v1/tools/search", req.path)
            assertEquals("Bearer sk", req.getHeader("Authorization"))
            val body = JSONObject(req.body.readUtf8())
            assertEquals(128, body.getString("query").length)
            assertEquals(50, body.getInt("count"))
            val e = runCatching { ds.search("q", 3) }.exceptionOrNull()
            assertEquals("DeepSeek 账户余额不足", e?.message)
        }
        val unset = DeepSeekSearch(http, { "https://api.deepseek.com" }, { "" })
        assertTrue(runCatching { runBlocking { unset.search("q", 1) } }.isFailure)
    }

    @Test fun renderNumbersSourcesAndRoundTrips() {
        val out = SearchFormat.render("q", "Exa", listOf(SearchHit("A", "https://a.com", summary = "aa\nbb"), SearchHit("B", "https://b.com", siteName = "B站")))
        assertTrue(out.contains("[1] A\n    https://a.com"))
        assertTrue(out.contains("    aa\n    bb"))
        assertEquals(listOf("A" to "https://a.com", "B" to "https://b.com"), SearchFormat.sources(out))
        assertTrue(SearchFormat.render("q", "Exa", emptyList()).contains("没有找到"))
    }

    private class Fake(override val id: String, val result: () -> List<SearchHit>) : SearchProvider {
        override val label = id
        var calls = 0
        override suspend fun search(query: String, count: Int): List<SearchHit> { calls++; return result() }
    }

    @Test fun toolFallsBackToExaAndMarksUntrusted() = runBlocking {
        val exa = Fake("exa") { listOf(SearchHit("A", "https://a.com")) }
        val ds = Fake("deepseek") { throw IllegalStateException("402") }
        val tool = WebSearchTool(primary = { ds }, fallback = exa)
        val out = tool.run(JSONObject().put("query", "q"), ToolContext("c"))
        assertFalse(out.isError)
        assertTrue(out.text.startsWith("（deepseek 不可用：402，已改用 exa）"))
        assertEquals(1, exa.calls)
        assertTrue(tool.readsUntrusted)
        assertEquals(Risk.LOW, tool.risk(JSONObject()))
        assertTrue(tool.run(JSONObject().put("query", " "), ToolContext("c")).isError)
        val exaOnly = WebSearchTool(primary = { exa }, fallback = exa)
        assertFalse(exaOnly.run(JSONObject().put("query", "q"), ToolContext("c")).isError)
    }
}
