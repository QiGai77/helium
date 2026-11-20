package tech.qigai.helium.data.llm

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import tech.qigai.helium.agent.ModelMessage
import tech.qigai.helium.agent.tools.ToolSpec

/** Image bytes ready for the wire: base64 without a data-URI prefix. */
data class ImageData(val mimeType: String, val base64: String)

/** Resolves an attachment ref (see [ModelMessage.images]) to inline image data; null skips the image. */
fun interface ImageLoader {
    fun load(ref: String): ImageData?

    companion object {
        val NONE = ImageLoader { null }
    }
}

/** Builds the request body for each wire protocol from provider-neutral [ModelMessage]s. */
internal object WireFormat {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * What a non-vision model is told instead of the pixels: the user must know the pictures never
     * reached the model, and the model must know to ask for a text description (or a vision model).
     */
    fun noVisionNote(imageCount: Int) =
        "（你发了 $imageCount 张图片，但当前模型不支持看图，我看不到图片内容。请用文字描述图片里有什么，或换一个支持视觉的模型，例如 Gemini。）"

    /** OpenAI-compatible user content: a plain string, or text + `image_url` data-URI parts. */
    fun openAiUserContent(m: ModelMessage, images: ImageLoader, supportsVision: Boolean = false): Any {
        if (!supportsVision && m.images.isNotEmpty()) {
            val note = noVisionNote(m.images.size)
            return if (m.content.isBlank()) note else "$note\n${m.content}"
        }
        val loaded = m.images.mapNotNull(images::load)
        if (loaded.isEmpty()) return m.content
        val parts = JSONArray()
        if (m.content.isNotBlank()) parts.put(JSONObject().put("type", "text").put("text", m.content))
        loaded.forEach { img ->
            parts.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:${img.mimeType};base64,${img.base64}")))
        }
        return parts
    }

    /** Anthropic user content: images first (as Anthropic recommends), then the text block. */
    fun anthropicUserContent(m: ModelMessage, images: ImageLoader, supportsVision: Boolean = false): Any {
        if (!supportsVision && m.images.isNotEmpty()) {
            val note = noVisionNote(m.images.size)
            return if (m.content.isBlank()) note else "$note\n${m.content}"
        }
        val loaded = m.images.mapNotNull(images::load)
        if (loaded.isEmpty()) return m.content
        val blocks = JSONArray()
        loaded.forEach { img ->
            blocks.put(
                JSONObject().put("type", "image").put(
                    "source", JSONObject().put("type", "base64").put("media_type", img.mimeType).put("data", img.base64),
                ),
            )
        }
        blocks.put(JSONObject().put("type", "text").put("text", m.content.ifBlank { "（见图片）" }))
        return blocks
    }

    /** Gemini user parts: `inlineData` images then the text (Gemini rejects an empty text part). */
    fun geminiUserParts(m: ModelMessage, images: ImageLoader, supportsVision: Boolean = false): JSONArray {
        val parts = JSONArray()
        if (!supportsVision && m.images.isNotEmpty()) {
            val note = noVisionNote(m.images.size)
            parts.put(JSONObject().put("text", if (m.content.isBlank()) note else "$note\n${m.content}"))
            return parts
        }
        m.images.mapNotNull(images::load).forEach { img ->
            parts.put(JSONObject().put("inlineData", JSONObject().put("mimeType", img.mimeType).put("data", img.base64)))
        }
        if (m.content.isNotBlank() || parts.length() == 0) parts.put(JSONObject().put("text", m.content.ifBlank { " " }))
        return parts
    }

    fun chatCompletions(e: LlmEndpoint, system: String?, messages: List<ModelMessage>, tools: List<ToolSpec>, images: ImageLoader = ImageLoader.NONE, supportsVision: Boolean = e.supportsVision): Request {
        val arr = JSONArray()
        if (!system.isNullOrBlank()) arr.put(JSONObject().put("role", "system").put("content", system))
        messages.forEach { m ->
            when (m.role) {
                ModelMessage.Role.USER -> arr.put(JSONObject().put("role", "user").put("content", openAiUserContent(m, images, supportsVision)))
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

    fun anthropic(e: LlmEndpoint, system: String?, messages: List<ModelMessage>, tools: List<ToolSpec>, images: ImageLoader = ImageLoader.NONE, supportsVision: Boolean = e.supportsVision): Request {
        val arr = JSONArray()
        var pendingResults: JSONArray? = null
        fun flushResults() {
            pendingResults?.let { arr.put(JSONObject().put("role", "user").put("content", it)) }
            pendingResults = null
        }
        messages.forEach { m ->
            when (m.role) {
                ModelMessage.Role.USER -> { flushResults(); arr.put(JSONObject().put("role", "user").put("content", anthropicUserContent(m, images, supportsVision))) }
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

    fun gemini(e: LlmEndpoint, system: String?, messages: List<ModelMessage>, tools: List<ToolSpec>, images: ImageLoader = ImageLoader.NONE, supportsVision: Boolean = e.supportsVision): Request {
        val contents = JSONArray()
        var pending: JSONArray? = null
        fun flush() {
            pending?.let { contents.put(JSONObject().put("role", "user").put("parts", it)) }
            pending = null
        }
        messages.forEach { m ->
            when (m.role) {
                ModelMessage.Role.USER -> { flush(); contents.put(JSONObject().put("role", "user").put("parts", geminiUserParts(m, images, supportsVision))) }
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
