package tech.qigai.helium.agent

import org.json.JSONArray
import org.json.JSONObject

/*
 * Append-only event stream for one conversation, modelled on OpenHands' EventStream: every user message,
 * model turn, tool observation and approval is one immutable event. The model's context, the chat UI, the task
 * timeline and the audit log are all projections of this stream, so there is exactly one source of truth.
 */

enum class Risk(val label: String) { LOW("低"), MEDIUM("中"), HIGH("高") }

enum class AgentState { IDLE, RUNNING, AWAITING_APPROVAL, FINISHED, STOPPED, ERROR }

/** Scope a user grants when approving (nanoMuse-style scoped grants, re-designed clean-room). */
enum class GrantScope(val label: String) {
    ONCE("仅这一次"),
    CONVERSATION("本对话"),
    DAY("24 小时"),
    FOREVER("永久"),
}

data class ToolCall(
    val id: String,
    val name: String,
    /** Raw JSON arguments exactly as the model produced them. */
    val arguments: String,
    /** Opaque provider signature that must be echoed back (Gemini thoughtSignature). */
    val signature: String? = null,
)

sealed interface AgentEvent {
    val at: Long

    data class UserMessage(val text: String, override val at: Long = now()) : AgentEvent

    data class AssistantMessage(
        val text: String,
        val reasoning: String = "",
        val model: String? = null,
        val toolCalls: List<ToolCall> = emptyList(),
        val stopped: Boolean = false,
        val error: String? = null,
        override val at: Long = now(),
    ) : AgentEvent

    data class ToolObservation(
        val callId: String,
        val tool: String,
        val output: String,
        val isError: Boolean = false,
        val durationMs: Long = 0,
        /** Output came from an untrusted source (web page, mail…): evidence, never instructions. */
        val tainted: Boolean = false,
        override val at: Long = now(),
    ) : AgentEvent

    data class ApprovalRequest(
        val requestId: String,
        val callId: String,
        val tool: String,
        val title: String,
        val detail: String,
        val target: String,
        val risk: Risk,
        val scopes: List<GrantScope>,
        val warning: String? = null,
        override val at: Long = now(),
    ) : AgentEvent

    data class ApprovalDecision(
        val requestId: String,
        val approved: Boolean,
        val scope: GrantScope? = null,
        override val at: Long = now(),
    ) : AgentEvent
}

internal fun now() = System.currentTimeMillis()

/** JSON codec used by the Room event table. Kind strings are part of the on-disk format: never rename. */
object EventCodec {
    fun kind(e: AgentEvent): String = when (e) {
        is AgentEvent.UserMessage -> "user"
        is AgentEvent.AssistantMessage -> "assistant"
        is AgentEvent.ToolObservation -> "observation"
        is AgentEvent.ApprovalRequest -> "approval_request"
        is AgentEvent.ApprovalDecision -> "approval_decision"
    }

    fun encode(e: AgentEvent): String {
        val o = JSONObject().put("at", e.at)
        when (e) {
            is AgentEvent.UserMessage -> o.put("text", e.text)
            is AgentEvent.AssistantMessage -> {
                o.put("text", e.text).put("reasoning", e.reasoning).put("stopped", e.stopped)
                e.model?.let { o.put("model", it) }
                e.error?.let { o.put("error", it) }
                val calls = JSONArray()
                e.toolCalls.forEach { c ->
                    calls.put(JSONObject().put("id", c.id).put("name", c.name).put("arguments", c.arguments).apply { c.signature?.let { put("signature", it) } })
                }
                o.put("toolCalls", calls)
            }
            is AgentEvent.ToolObservation -> o.put("callId", e.callId).put("tool", e.tool).put("output", e.output)
                .put("isError", e.isError).put("durationMs", e.durationMs).put("tainted", e.tainted)
            is AgentEvent.ApprovalRequest -> {
                o.put("requestId", e.requestId).put("callId", e.callId).put("tool", e.tool).put("title", e.title)
                    .put("detail", e.detail).put("target", e.target).put("risk", e.risk.name)
                    .put("scopes", JSONArray(e.scopes.map { it.name }))
                e.warning?.let { o.put("warning", it) }
            }
            is AgentEvent.ApprovalDecision -> {
                o.put("requestId", e.requestId).put("approved", e.approved)
                e.scope?.let { o.put("scope", it.name) }
            }
        }
        return o.toString()
    }

    fun decode(kind: String, json: String): AgentEvent? {
        val o = try { JSONObject(json) } catch (e: Exception) { return null }
        val at = o.optLong("at", 0L)
        return when (kind) {
            "user" -> AgentEvent.UserMessage(o.optString("text"), at)
            "assistant" -> {
                val arr = o.optJSONArray("toolCalls") ?: JSONArray()
                val calls = (0 until arr.length()).mapNotNull { i ->
                    val c = arr.optJSONObject(i) ?: return@mapNotNull null
                    ToolCall(c.optString("id"), c.optString("name"), c.optString("arguments", "{}"), c.optStr("signature"))
                }
                AgentEvent.AssistantMessage(
                    o.optString("text"), o.optString("reasoning"), o.optStr("model"), calls,
                    o.optBoolean("stopped"), o.optStr("error"), at,
                )
            }
            "observation" -> AgentEvent.ToolObservation(
                o.optString("callId"), o.optString("tool"), o.optString("output"), o.optBoolean("isError"),
                o.optLong("durationMs"), o.optBoolean("tainted"), at,
            )
            "approval_request" -> {
                val sc = o.optJSONArray("scopes") ?: JSONArray()
                AgentEvent.ApprovalRequest(
                    o.optString("requestId"), o.optString("callId"), o.optString("tool"), o.optString("title"),
                    o.optString("detail"), o.optString("target"),
                    runCatching { Risk.valueOf(o.optString("risk")) }.getOrDefault(Risk.HIGH),
                    (0 until sc.length()).mapNotNull { runCatching { GrantScope.valueOf(sc.getString(it)) }.getOrNull() },
                    o.optStr("warning"), at,
                )
            }
            "approval_decision" -> AgentEvent.ApprovalDecision(
                o.optString("requestId"), o.optBoolean("approved"),
                o.optStr("scope")?.let { runCatching { GrantScope.valueOf(it) }.getOrNull() }, at,
            )
            else -> null
        }
    }

    private fun JSONObject.optStr(k: String): String? = if (!has(k) || isNull(k)) null else optString(k)
}
