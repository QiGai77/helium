package tech.qigai.helium.data.llm

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import tech.qigai.helium.agent.*
import tech.qigai.helium.agent.tools.*
import tech.qigai.helium.data.llm.*

private fun sse(vararg l: String) = MockResponse().setHeader("Content-Type", "text/event-stream").setBody(l.joinToString("\n\n", postfix = "\n\n"))
private fun ok(c: Boolean, m: String) { if (!c) error("FAIL $m") else println("ok  $m") }
class LlmToolCallTest {
@org.junit.Test fun toolCalls() = runBlocking {
    val s = MockWebServer(); s.start()
    val client = LlmClient()
    val tools = listOf(TimeTool().spec, MemorySaveTool(object : MemoryStore {
        override suspend fun search(query: String, limit: Int) = emptyList<MemoryRecord>()
        override suspend fun add(text: String, domain: String, inferred: Boolean) = 1L
        override suspend fun forget(id: Long) = null
        override suspend fun blocked(text: String) = false }).spec)
    val hist = listOf(ModelMessage(ModelMessage.Role.USER, "几点"), ModelMessage(ModelMessage.Role.ASSISTANT, "", "想", listOf(ToolCall("x1", "get_current_time", "{}"))), ModelMessage(ModelMessage.Role.TOOL, "10:00", toolCallId = "x1", toolName = "get_current_time"))
    // OpenAI
    s.enqueue(sse("""data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"memory_save","arguments":""}}]}}]}""",
        """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"text\":\"a\","}}]}}]}""",
        """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"domain\":\"饮食\"}"}}]},"finish_reason":"tool_calls"}]}""", "data: [DONE]"))
    var ev = client.stream(LlmEndpoint(ProviderId.OPENCODE_GO, WireApi.CHAT_COMPLETIONS, s.url("/v1").toString(), "k", "m"), "sys", hist, "s", tools).toList()
    val c = ev.filterIsInstance<StreamEvent.ToolCall>().single().call
    ok(c.id == "call_1" && c.name == "memory_save" && JSONObject(c.arguments).getString("domain") == "饮食", "openai tool call assembled")
    var body = JSONObject(s.takeRequest().body.readUtf8())
    ok(body.getJSONArray("tools").length() == 2 && body.getJSONArray("messages").getJSONObject(2).getJSONArray("tool_calls").length() == 1 && body.getJSONArray("messages").getJSONObject(3).getString("role") == "tool" && body.getJSONArray("messages").getJSONObject(2).getString("reasoning_content") == "想", "openai request shape")
    // Anthropic
    s.enqueue(sse("event: content_block_start\ndata: {\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"tool_use\",\"id\":\"tu_1\",\"name\":\"get_current_time\",\"input\":{}}}",
        "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{}\"}}",
        "event: message_delta\ndata: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"}}", "event: message_stop\ndata: {\"type\":\"message_stop\"}"))
    ev = client.stream(LlmEndpoint(ProviderId.OPENCODE_GO, WireApi.ANTHROPIC_MESSAGES, s.url("/v1").toString(), "k", "m"), "sys", hist, "s", tools).toList()
    ok(ev.filterIsInstance<StreamEvent.ToolCall>().single().call.id == "tu_1", "anthropic tool call")
    body = JSONObject(s.takeRequest().body.readUtf8())
    val msgs = body.getJSONArray("messages")
    ok(msgs.length() == 3 && msgs.getJSONObject(2).getJSONArray("content").getJSONObject(0).getString("type") == "tool_result" && body.getJSONArray("tools").getJSONObject(0).has("input_schema"), "anthropic request shape")
    // Gemini
    s.enqueue(sse("""data: {"candidates":[{"content":{"parts":[{"functionCall":{"name":"get_current_time","args":{}},"thoughtSignature":"SIG"}]},"finishReason":"STOP"}]}"""))
    ev = client.stream(LlmEndpoint(ProviderId.GEMINI, WireApi.GEMINI, s.url("/v1beta").toString(), "k", "gemini-x"), "sys", hist, "s", tools).toList()
    ok(ev.filterIsInstance<StreamEvent.ToolCall>().single().call.signature == "SIG", "gemini function call + signature")
    body = JSONObject(s.takeRequest().body.readUtf8())
    val decl = body.getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations")
    ok(!decl.getJSONObject(0).has("parameters") && decl.getJSONObject(1).has("parameters") && body.getJSONArray("contents").getJSONObject(2).getJSONArray("parts").getJSONObject(0).has("functionResponse"), "gemini request shape")
    s.shutdown(); println("ALL PASS")
}
}
