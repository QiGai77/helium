package tech.qigai.helium.data.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import tech.qigai.helium.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import tech.qigai.helium.agent.ModelMessage
import tech.qigai.helium.agent.ToolCall
import tech.qigai.helium.agent.tools.ToolSpec
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Streaming chat client for the three wire protocols Helium supports.
 * Everything is plain OkHttp + org.json so there is no codegen / reflection to configure.
 */
class LlmClient(
    private val http: OkHttpClient = defaultHttp,
) {
    /** Plain chat without tools (kept for simple callers and the protocol tests). */
    fun stream(endpoint: LlmEndpoint, system: String?, turns: List<ChatTurn>, sessionId: String): Flow<StreamEvent> =
        stream(endpoint, system, turns.filter { it.role != Role.SYSTEM }.map {
            ModelMessage(if (it.role == Role.ASSISTANT) ModelMessage.Role.ASSISTANT else ModelMessage.Role.USER, it.content)
        }, sessionId, emptyList())

    /**
     * Streams one model turn, with optional tools. Tool calls are assembled from the stream and emitted as
     * [StreamEvent.ToolCall] right before [StreamEvent.Done].
     * [sessionId] must stay stable for a whole conversation: OpenCode Go routes and caches prompts by the
     * `x-opencode-session` header and rejects requests without it (HTTP 400 MissingSessionID).
     */
    fun stream(
        endpoint: LlmEndpoint,
        system: String?,
        messages: List<ModelMessage>,
        sessionId: String,
        tools: List<ToolSpec>,
    ): Flow<StreamEvent> = flow {
        if (endpoint.apiKey.isBlank()) throw LlmException("还没有填写 ${endpoint.provider.title} 的 API Key。去「我的 → 模型与推理」填写后再试。")
        if (endpoint.model.isBlank()) throw LlmException("还没有选择模型。")
        val built = when (endpoint.api) {
            WireApi.CHAT_COMPLETIONS -> WireFormat.chatCompletions(endpoint, system, messages, tools)
            WireApi.ANTHROPIC_MESSAGES -> WireFormat.anthropic(endpoint, system, messages, tools)
            WireApi.GEMINI -> WireFormat.gemini(endpoint, system, messages, tools)
        }
        val request = built.newBuilder()
            .header("User-Agent", USER_AGENT)
            .apply { if (endpoint.provider == ProviderId.OPENCODE_GO) header("x-opencode-session", sessionId) }
            .build()
        val call = http.newCall(request)
        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        val acc = ToolCallAccumulator()
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw httpError(response)
                val source = response.body?.source() ?: throw LlmException("空响应")
                var finish: String? = null
                var event: String? = null
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty()) {
                        event = null
                        continue
                    }
                    if (line.startsWith(":")) continue
                    if (line.startsWith("event:")) {
                        event = line.removePrefix("event:").trim()
                        continue
                    }
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val obj = try {
                        JSONObject(data)
                    } catch (e: Exception) {
                        continue
                    }
                    val parsed = when (endpoint.api) {
                        WireApi.CHAT_COMPLETIONS -> parseChatChunk(obj, acc)
                        WireApi.ANTHROPIC_MESSAGES -> parseAnthropicEvent(event ?: obj.optString("type"), obj, acc)
                        WireApi.GEMINI -> parseGeminiChunk(obj, acc)
                    }
                    for (ev in parsed) {
                        if (ev is StreamEvent.Done) finish = ev.finishReason else emit(ev)
                    }
                    if (endpoint.api == WireApi.ANTHROPIC_MESSAGES && obj.optString("type") == "message_stop") break
                }
                acc.finish().forEach { emit(it) }
                emit(StreamEvent.Done(finish))
            }
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            throw LlmException("网络错误：${e.message ?: e.javaClass.simpleName}")
        } finally {
            handle?.dispose()
        }
    }.flowOn(Dispatchers.IO)

    /** GET the public OpenCode Go model list; keeps only models reachable with the protocols above. */
    suspend fun fetchOpenCodeModels(): List<ModelOption> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(ModelCatalog.OPENCODE_MODELS_URL).header("User-Agent", USER_AGENT).get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw httpError(resp)
            val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("data") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    val id = m.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val api = when (m.optString("recommended_api")) {
                        "chat-completions" -> WireApi.CHAT_COMPLETIONS
                        "messages" -> WireApi.ANTHROPIC_MESSAGES
                        else -> null
                    } ?: continue
                    add(ModelOption(id, m.optString("name").ifBlank { id }, api))
                }
            }
        }
    }

    // ---------------------------------------------------------------- parsers

    private fun parseChatChunk(obj: JSONObject, acc: ToolCallAccumulator): List<StreamEvent> {
        obj.optJSONObject("error")?.let { throw LlmException(it.optString("message").ifBlank { it.toString() }) }
        val out = ArrayList<StreamEvent>(2)
        val choices = obj.optJSONArray("choices") ?: return out
        if (choices.length() == 0) return out
        val choice = choices.optJSONObject(0) ?: return out
        val delta = choice.optJSONObject("delta")
        if (delta != null) {
            val reasoning = delta.optStringOrNull("reasoning_content") ?: delta.optStringOrNull("reasoning")
            if (!reasoning.isNullOrEmpty()) out += StreamEvent.Reasoning(reasoning)
            val content = delta.optStringOrNull("content")
            if (!content.isNullOrEmpty()) out += StreamEvent.Text(content)
            val calls = delta.optJSONArray("tool_calls")
            if (calls != null) {
                for (i in 0 until calls.length()) {
                    val c = calls.optJSONObject(i) ?: continue
                    val fn = c.optJSONObject("function")
                    acc.chunk(
                        key = c.optInt("index", i).toString(),
                        id = c.optStringOrNull("id"),
                        name = fn?.optStringOrNull("name"),
                        argsDelta = fn?.optStringOrNull("arguments"),
                    )
                }
            }
        }
        val finish = choice.optStringOrNull("finish_reason")
        if (!finish.isNullOrEmpty()) out += StreamEvent.Done(finish)
        return out
    }

    private fun parseAnthropicEvent(type: String, obj: JSONObject, acc: ToolCallAccumulator): List<StreamEvent> {
        return when (type) {
            "content_block_start" -> {
                val b = obj.optJSONObject("content_block")
                if (b?.optString("type") == "tool_use") {
                    acc.chunk(obj.optInt("index").toString(), b.optStringOrNull("id"), b.optStringOrNull("name"), null)
                }
                emptyList()
            }
            "content_block_delta" -> {
                val d = obj.optJSONObject("delta") ?: return emptyList()
                when (d.optString("type")) {
                    "text_delta" -> listOf(StreamEvent.Text(d.optString("text")))
                    "thinking_delta" -> listOf(StreamEvent.Reasoning(d.optString("thinking")))
                    "input_json_delta" -> {
                        acc.chunk(obj.optInt("index").toString(), null, null, d.optString("partial_json"))
                        emptyList()
                    }
                    else -> emptyList()
                }
            }
            "message_delta" -> {
                val stop = obj.optJSONObject("delta")?.optStringOrNull("stop_reason")
                if (stop != null) listOf(StreamEvent.Done(stop)) else emptyList()
            }
            "error" -> {
                val err = obj.optJSONObject("error")
                throw LlmException(err?.optString("message")?.ifBlank { null } ?: obj.toString())
            }
            else -> emptyList()
        }
    }

    private fun parseGeminiChunk(obj: JSONObject, acc: ToolCallAccumulator): List<StreamEvent> {
        obj.optJSONObject("error")?.let { throw LlmException(it.optString("message").ifBlank { it.toString() }) }
        val out = ArrayList<StreamEvent>(2)
        val cands = obj.optJSONArray("candidates") ?: return out
        val c = cands.optJSONObject(0) ?: return out
        val parts = c.optJSONObject("content")?.optJSONArray("parts")
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i) ?: continue
                val fc = p.optJSONObject("functionCall")
                if (fc != null) {
                    acc.complete(
                        id = fc.optStringOrNull("id"),
                        name = fc.optString("name"),
                        arguments = (fc.optJSONObject("args") ?: JSONObject()).toString(),
                        signature = p.optStringOrNull("thoughtSignature"),
                    )
                    continue
                }
                val text = p.optStringOrNull("text") ?: continue
                if (p.optBoolean("thought", false)) out += StreamEvent.Reasoning(text) else out += StreamEvent.Text(text)
            }
        }
        c.optStringOrNull("finishReason")?.let { out += StreamEvent.Done(it) }
        return out
    }

    private fun httpError(resp: Response): LlmException {
        val raw = try {
            resp.body?.string().orEmpty()
        } catch (e: Exception) {
            ""
        }
        val msg = try {
            val o = JSONObject(raw)
            o.optJSONObject("error")?.optString("message")?.ifBlank { null }
                ?: o.optString("message").ifBlank { null }
                ?: raw
        } catch (e: Exception) {
            raw
        }
        val hint = when (resp.code) {
            401, 403 -> "（API Key 无效或没有权限）"
            404 -> "（地址或模型名不存在）"
            429 -> "（请求太频繁或额度已用完）"
            else -> ""
        }
        return LlmException("HTTP ${resp.code}$hint ${msg.take(300)}".trim(), resp.code)
    }

    companion object {
        /** OpenCode Go asks clients to identify themselves with their own user agent. */
        val USER_AGENT = "Helium/${BuildConfig.VERSION_NAME} (Android; tech.qigai.helium)"

        val defaultHttp: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        /** Joins base + path; tolerates a base that already ends with the path (e.g. a full `/chat/completions` URL). */
        fun joinUrl(base: String, path: String): String {
            val b = base.trim().trimEnd('/')
            val p = path.trimStart('/')
            val pathOnly = p.substringBefore('?')
            return if (b.endsWith("/$pathOnly")) b + p.removePrefix(pathOnly) else "$b/$p"
        }
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key)

/** Assembles streamed tool-call fragments (OpenAI `tool_calls[i]`, Anthropic `input_json_delta`) into whole calls. */
internal class ToolCallAccumulator {
    private class Part(var id: String?, var name: String?, val args: StringBuilder = StringBuilder(), var signature: String? = null)
    private val parts = LinkedHashMap<String, Part>()
    private var seq = 0

    fun chunk(key: String, id: String?, name: String?, argsDelta: String?) {
        val p = parts.getOrPut(key) { Part(null, null) }
        if (!id.isNullOrEmpty()) p.id = id
        if (!name.isNullOrEmpty()) p.name = name
        if (!argsDelta.isNullOrEmpty()) p.args.append(argsDelta)
    }

    fun complete(id: String?, name: String, arguments: String, signature: String?) {
        parts["g${seq++}"] = Part(id, name, StringBuilder(arguments), signature)
    }

    fun finish(): List<StreamEvent.ToolCall> = parts.values.filter { !it.name.isNullOrBlank() }.mapIndexed { i, p ->
        StreamEvent.ToolCall(ToolCall(p.id?.takeIf { it.isNotBlank() } ?: "call_${System.nanoTime()}_$i", p.name!!, p.args.toString().ifBlank { "{}" }, p.signature))
    }
}
