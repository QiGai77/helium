package tech.qigai.helium

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import tech.qigai.helium.data.llm.ChatTurn
import tech.qigai.helium.data.llm.LlmClient
import tech.qigai.helium.data.llm.LlmEndpoint
import tech.qigai.helium.data.llm.LlmException
import tech.qigai.helium.data.llm.ProviderId
import tech.qigai.helium.data.llm.Role
import tech.qigai.helium.data.llm.StreamEvent
import tech.qigai.helium.data.llm.WireApi

class LlmClientTest {
    private lateinit var server: MockWebServer
    private val client = LlmClient()

    @Before fun setUp() { server = MockWebServer().apply { start() } }

    @After fun tearDown() { server.shutdown() }

    private fun sse(vararg lines: String) = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(lines.joinToString("\n\n", postfix = "\n\n"))

    private fun ep(api: WireApi, provider: ProviderId = ProviderId.OPENCODE_GO) =
        LlmEndpoint(provider, api, server.url("/v1").toString(), "test-key", "glm-5.3")

    private val turns = listOf(ChatTurn(Role.USER, "你好"))

    @Test fun chatCompletionsStreamsTextAndReasoning() = runTest {
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"reasoning_content":"想一想"}}]}""",
                """data: {"choices":[{"delta":{"content":"你"}}]}""",
                """data: {"choices":[{"delta":{"content":"好"},"finish_reason":null}]}""",
                """data: {"choices":[{"delta":{},"finish_reason":"stop"}]}""",
                "data: [DONE]",
            ),
        )
        val events = client.stream(ep(WireApi.CHAT_COMPLETIONS), "sys", turns, "session-1").toList()
        val text = events.filterIsInstance<StreamEvent.Text>().joinToString("") { it.delta }
        val reasoning = events.filterIsInstance<StreamEvent.Reasoning>().joinToString("") { it.delta }
        assertEquals("你好", text)
        assertEquals("想一想", reasoning)
        assertEquals("stop", (events.last() as StreamEvent.Done).finishReason)

        val req = server.takeRequest()
        assertEquals("/v1/chat/completions", req.path)
        assertEquals("Bearer test-key", req.getHeader("Authorization"))
        assertEquals("session-1", req.getHeader("x-opencode-session"))
        assertTrue(req.getHeader("User-Agent")!!.startsWith("Helium/"))
        val body = JSONObject(req.body.readUtf8())
        assertEquals("glm-5.3", body.getString("model"))
        assertTrue(body.getBoolean("stream"))
        assertEquals("system", body.getJSONArray("messages").getJSONObject(0).getString("role"))
    }

    @Test fun anthropicMessagesStream() = runTest {
        server.enqueue(
            sse(
                "event: message_start\ndata: {\"type\":\"message_start\",\"message\":{}}",
                "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"嗯\"}}",
                "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}",
                "event: message_delta\ndata: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}",
                "event: message_stop\ndata: {\"type\":\"message_stop\"}",
            ),
        )
        val events = client.stream(ep(WireApi.ANTHROPIC_MESSAGES), "sys", turns, "s").toList()
        assertEquals("Hi", events.filterIsInstance<StreamEvent.Text>().joinToString("") { it.delta })
        assertEquals("嗯", events.filterIsInstance<StreamEvent.Reasoning>().joinToString("") { it.delta })
        assertEquals("end_turn", (events.last() as StreamEvent.Done).finishReason)
        val req = server.takeRequest()
        assertEquals("/v1/messages", req.path)
        assertEquals("test-key", req.getHeader("x-api-key"))
        assertEquals("sys", JSONObject(req.body.readUtf8()).getString("system"))
    }

    @Test fun geminiStream() = runTest {
        server.enqueue(
            sse(
                """data: {"candidates":[{"content":{"parts":[{"text":"晴"}],"role":"model"}}]}""",
                """data: {"candidates":[{"content":{"parts":[{"text":"天"}],"role":"model"},"finishReason":"STOP"}]}""",
            ),
        )
        val e = LlmEndpoint(ProviderId.GEMINI, WireApi.GEMINI, server.url("/v1beta").toString(), "g-key", "gemini-flash-latest")
        val events = client.stream(e, "sys", turns, "s").toList()
        assertEquals("晴天", events.filterIsInstance<StreamEvent.Text>().joinToString("") { it.delta })
        val req = server.takeRequest()
        assertEquals("/v1beta/models/gemini-flash-latest:streamGenerateContent?alt=sse", req.path)
        assertEquals("g-key", req.getHeader("x-goog-api-key"))
        assertEquals(null, req.getHeader("x-opencode-session"))
    }

    @Test fun httpErrorIsReadable() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"type":"authentication_error","message":"Invalid credential"}}"""))
        try {
            client.stream(ep(WireApi.CHAT_COMPLETIONS), null, turns, "s").toList()
            fail("expected LlmException")
        } catch (e: LlmException) {
            assertEquals(401, e.httpCode)
            assertTrue(e.message!!.contains("Invalid credential"))
        }
    }

    @Test fun missingKeyFailsFast() = runTest {
        val e = LlmEndpoint(ProviderId.OPENCODE_GO, WireApi.CHAT_COMPLETIONS, "https://example.invalid/v1", "", "glm-5.3")
        try {
            client.stream(e, null, turns, "s").toList()
            fail("expected LlmException")
        } catch (ex: LlmException) {
            assertTrue(ex.message!!.contains("API Key"))
        }
    }

    @Test fun joinUrlToleratesFullEndpoint() {
        assertEquals("https://a.b/v1/chat/completions", LlmClient.joinUrl("https://a.b/v1/", "chat/completions"))
        assertEquals("https://a.b/v1/chat/completions", LlmClient.joinUrl("https://a.b/v1/chat/completions", "chat/completions"))
        assertEquals("https://g/v1beta/models/x:streamGenerateContent?alt=sse", LlmClient.joinUrl("https://g/v1beta", "models/x:streamGenerateContent?alt=sse"))
    }
}
