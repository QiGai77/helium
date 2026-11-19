package tech.qigai.helium.data.llm

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import tech.qigai.helium.agent.ModelMessage
import tech.qigai.helium.agent.tools.ToolSpec

/** Builds the request body for each wire protocol from provider-neutral [ModelMessage]s. */
internal object WireFormat {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun chatCompletions(e: LlmEndpoint, system: String?, messages: List<ModelMessage>, tools: List<ToolSpec>): Request {
        val arr = JSONArray()
        if (!system.isNullOrBlank()) arr.put(JSONObject().put("role", "system").put("content", system))
        messages.forEach { m ->
            when (m.role) {
                ModelMessage.Role.USER -> arr.put(JSONObject().put("role", "user").put("content", m.content))
                ModelMessage.Role.ASSISTANT -> {
                    val o = JSONObject().put("role", "assistant")
                    if (m.toolCalls.isEmpty()) o.put("content", m.content) else {
                        o.put("content", if (m.content.isBlank()) JSONObject.NULL else m.content)
                        o.put("tool_calls", JSONArray(m.toolCalls.map { c ->
                            JSONObject().put("id", c.id).put("type", "function")
                                .put("function", JSONObject().put("name", c.name).put("arguments", c.arguments))
                        }))
                        // Thinking models behind OpenCode Go (DeepSeek / Kimi / GLM) want their reasoning echoed on tool turns.
                        if (e.provider == ProviderId.OPENCODE_GO && m.reasoning.isNotBlank()) o.put("reasoning_content", m.reasoning)
                    }
                    arr.put(o)
                }
                ModelMessage.Role.TOOL -> arr.put(JSONObject().put("role", "tool").put("tool_call_id", m.toolCallId).put("content", m.content))
            }
        }
        val body = JSONObject().put("model", e.model).put("stream", true).put("messages", arr)
        if (tools.isNotEmpty()) {
            body.put("tools", JSONArray(tools.map { t ->
                JSONObject().put("type", "function").put("function", JSONObject().put("name", t.name).put("description", t.description).put("parameters", t.parameters))
            }))
        }
        return Request.Builder()
            .url(LlmClient.joinUrl(e.baseUrl, "chat/completions"))
            .header("Authorization", "Bearer ${e.apiKey}")
            .header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody(JSON))
            .build()
    }

    fun anthropic(e: LlmEndpoint, system: String?, messages: List<ModelMessage>, tools: List<ToolSpec>): Request {
        val arr = JSONArray()
        var pendingResults: JSONArray? = null
        fun flushResults() {
            pendingResults?.let { arr.put(JSONObject().put("role", "user").put("content", it)) }
            pendingResults = null
        }
        messages.forEach { m ->
            when (m.role) {
                ModelMessage.Role.USER -> { flushResults(); arr.put(JSONObject().put("role", "user").put("content", m.content)) }
                ModelMessage.Role.ASSISTANT -> {
                    flushResults()
                    if (m.toolCalls.isEmpty()) arr.put(JSONObject().put("role", "assistant").put("content", m.content)) else {
                        val blocks = JSONArray()
                        if (m.content.isNotBlank()) blocks.put(JSONObject().put("type", "text").put("text", m.content))
                        m.toolCalls.forEach { c ->
                            val input = runCatching { JSONObject(c.arguments) }.getOrDefault(JSONObject())
                            blocks.put(JSONObject().put("type", "tool_use").put("id", c.id).put("name", c.name).put("input", input))
                        }
                        arr.put(JSONObject().put("role", "assistant").put("content", blocks))
                    }
                }
                ModelMessage.Role.TOOL -> {
                    val r = pendingResults ?: JSONArray().also { pendingResults = it }
                    r.put(JSONObject().put("type", "tool_result").put("tool_use_id", m.toolCallId).put("content", m.content))
                }
            }
        }
        flushResults()
        val body = JSONObject().put("model", e.model).put("max_tokens", 8192).put("stream", true).put("messages", arr)
        if (!system.isNullOrBlank()) body.put("system", system)
        if (tools.isNotEmpty()) {
            body.put("tools", JSONArray(tools.map { t -> JSONObject().put("name", t.name).put("description", t.description).put("input_schema", t.parameters) }))
        }
        return Request.Builder()
            .url(LlmClient.joinUrl(e.baseUrl, "messages"))
            .header("x-api-key", e.apiKey)
            .header("Authorization", "Bearer ${e.apiKey}")
            .header("anthropic-version", "2023-06-01")
            .header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody(JSON))
            .build()
    }

    fun gemini(e: LlmEndpoint, system: String?, messages: List<ModelMessage>, tools: List<ToolSpec>): Request {
        val contents = JSONArray()
        var pending: JSONArray? = null
        fun flush() {
            pending?.let { contents.put(JSONObject().put("role", "user").put("parts", it)) }
            pending = null
        }
        messages.forEach { m ->
            when (m.role) {
                ModelMessage.Role.USER -> { flush(); contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", m.content)))) }
                ModelMessage.Role.ASSISTANT -> {
                    flush()
                    val parts = JSONArray()
                    if (m.content.isNotBlank()) parts.put(JSONObject().put("text", m.content))
                    m.toolCalls.forEach { c ->
                        val fc = JSONObject().put("name", c.name).put("args", runCatching { JSONObject(c.arguments) }.getOrDefault(JSONObject()))
                        val part = JSONObject().put("functionCall", fc)
                        c.signature?.let { part.put("thoughtSignature", it) }
                        parts.put(part)
                    }
                    if (parts.length() > 0) contents.put(JSONObject().put("role", "model").put("parts", parts))
                }
                ModelMessage.Role.TOOL -> {
                    val p = pending ?: JSONArray().also { pending = it }
                    p.put(JSONObject().put("functionResponse", JSONObject().put("name", m.toolName ?: "tool").put("response", JSONObject().put("content", m.content))))
                }
            }
        }
        flush()
        val body = JSONObject().put("contents", contents)
        if (!system.isNullOrBlank()) {
            body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        }
        if (tools.isNotEmpty()) {
            body.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", JSONArray(tools.map { t ->
                JSONObject().put("name", t.name).put("description", t.description).apply {
                    if ((t.parameters.optJSONObject("properties")?.length() ?: 0) > 0) put("parameters", t.parameters)
                }
            }))))
        }
        val model = e.model.removePrefix("models/")
        return Request.Builder()
            .url(LlmClient.joinUrl(e.baseUrl, "models/$model:streamGenerateContent?alt=sse"))
            .header("x-goog-api-key", e.apiKey)
            .header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody(JSON))
            .build()
    }
}
