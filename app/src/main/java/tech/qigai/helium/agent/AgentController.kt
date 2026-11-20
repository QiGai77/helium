package tech.qigai.helium.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import tech.qigai.helium.agent.tools.ToolContext
import tech.qigai.helium.agent.tools.ToolRegistry
import tech.qigai.helium.agent.tools.ToolSpec
import java.util.UUID

/** Result of one streamed model call. */
data class ModelTurn(val text: String, val reasoning: String, val toolCalls: List<ToolCall>, val model: String?)

/** One model call with tools; implemented on top of LlmClient for all three wire protocols. */
fun interface ModelRunner {
    suspend fun run(system: String, history: List<ModelMessage>, tools: List<ToolSpec>, onDelta: (text: String, reasoning: String) -> Unit): ModelTurn
}

/** Suspends until the user decides on an approval card. */
fun interface ApprovalGate {
    suspend fun await(conversationId: String, request: AgentEvent.ApprovalRequest): AgentEvent.ApprovalDecision
}

/** Where events go: Room in the app, a list in tests. */
interface EventSink {
    suspend fun append(conversationId: String, event: AgentEvent)
}

/**
 * One audit row. [kind] is the outcome: ask / approve / reject / deny / run / error / taint / skip
 * ([AuditKinds]). [tainted] marks anything that happened after the run read untrusted content.
 */
data class AuditRecord(
    val conversationId: String,
    val kind: String,
    val tool: String,
    val target: String,
    val detail: String,
    val risk: Risk? = null,
    val tainted: Boolean = false,
)

object AuditKinds {
    const val ASK = "ask"
    const val APPROVE = "approve"
    const val REJECT = "reject"
    const val DENY = "deny"
    const val RUN = "run"
    const val ERROR = "error"
    const val TAINT = "taint"
    const val SKIP = "skip"
}

fun interface AuditSink {
    suspend fun record(entry: AuditRecord)
}

interface AgentListener {
    fun onEvent(event: AgentEvent) {}
    fun onStreaming(text: String, reasoning: String) {}
    fun onState(state: AgentState) {}
}

/**
 * The agent loop (OpenHands AgentController shape): call the model, run each tool call through the [Sentinel],
 * pause for approval when it says ask, append every action and observation to the event stream, and stop on a
 * plain answer, a loop, the step budget, or cancellation.
 */
class AgentController(
    private val model: ModelRunner,
    private val tools: ToolRegistry,
    private val sentinel: Sentinel,
    private val gate: ApprovalGate,
    private val sink: EventSink,
    private val audit: AuditSink = AuditSink { },
    private val maxSteps: Int = 12,
    private val toolTimeoutMs: Long = 60_000,
    /**
     * Messages the user sent while this run is in flight. Each step folds whatever is queued into
     * this round's context instead of cancelling the in-flight tool call and restarting; anything
     * still queued when the run ends (finished, stopped, error) is emitted too, so it is never lost.
     * Null in tests / callers without a live composer.
     */
    private val pending: (() -> AgentEvent.UserMessage?)? = null,
) {
    /** Runs until the agent finishes. [events] is the conversation so far and is appended to in place. */
    suspend fun run(conversationId: String, system: String, events: MutableList<AgentEvent>, listener: AgentListener): AgentState {
        suspend fun emit(e: AgentEvent) {
            events += e
            sink.append(conversationId, e)
            listener.onEvent(e)
        }
        /** Pulls queued user messages into this run (persisted + shown via [emit]); returns how many. */
        suspend fun drainPending(): Int {
            var n = 0
            while (true) {
                emit(pending?.invoke() ?: break)
                n++
            }
            return n
        }
        // Untrusted content already in the context (a page read earlier in this conversation) still taints the run.
        var tainted = events.any { it is AgentEvent.ToolObservation && it.tainted }
        val sensitive = LinkedHashSet<String>()
        events.forEach { e -> if (e is AgentEvent.ToolObservation && !e.isError && tools[e.tool]?.readsPrivate == true) sensitive += Taint.fragments(e.output) }
        listener.onState(AgentState.RUNNING)
        try {
        for (step in 1..maxSteps) {
            drainPending()
            var partialText = ""
            var partialReasoning = ""
            val turn = try {
                model.run(system, History.toModel(events), tools.specs) { t, r ->
                    partialText = t
                    partialReasoning = r
                    listener.onStreaming(t, r)
                }
            } catch (e: CancellationException) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    emit(AgentEvent.AssistantMessage(partialText, partialReasoning, stopped = true))
                    listener.onState(AgentState.STOPPED)
                }
                throw e
            } catch (e: Exception) {
                emit(AgentEvent.AssistantMessage(partialText, partialReasoning, error = e.message ?: e.javaClass.simpleName))
                listener.onState(AgentState.ERROR)
                return AgentState.ERROR
            }
            emit(AgentEvent.AssistantMessage(turn.text, turn.reasoning, turn.model, turn.toolCalls))
            if (turn.toolCalls.isEmpty()) {
                // A message queued during this turn still gets answered in this run, not dropped.
                if (drainPending() > 0) continue
                listener.onState(AgentState.FINISHED)
                return AgentState.FINISHED
            }
            var rejected = false
            for (call in turn.toolCalls) {
                if (rejected) {
                    // A rejection ends the whole batch (opencode rejects the rest of the message's pending asks).
                    audit.record(AuditRecord(conversationId, AuditKinds.SKIP, call.name, "", "上一步被拒绝，跳过", tainted = tainted))
                    emit(AgentEvent.ToolObservation(call.id, call.name, "用户拒绝了这一轮的上一个操作，这一步没有执行。", isError = true))
                    continue
                }
                val obs = try {
                    execute(conversationId, call, tainted, sensitive, ::emit, listener)
                } catch (e: CancellationException) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        emit(AgentEvent.ToolObservation(call.id, call.name, "已被用户停止", isError = true))
                        listener.onState(AgentState.STOPPED)
                    }
                    throw e
                }
                if (obs.tainted && !tainted) {
                    audit.record(AuditRecord(conversationId, AuditKinds.TAINT, call.name, tools[call.name]?.let { t -> runCatching { t.target(JSONObject(call.arguments.ifBlank { "{}" })) }.getOrNull() } ?: "", "读取了外部内容，之后发出数据或高风险操作都要先确认", tainted = true))
                }
                tainted = tainted || obs.tainted
                if (!obs.isError && tools[call.name]?.readsPrivate == true) sensitive += Taint.fragments(obs.output)
                if (obs.isError && obs.output.startsWith(REJECTED)) rejected = true
                emit(obs)
            }
            if (StuckDetector.isStuck(events)) {
                emit(AgentEvent.AssistantMessage("我在重复同一步却没有进展，先停下来。你可以换个说法，或告诉我缺什么信息。"))
                listener.onState(AgentState.STOPPED)
                return AgentState.STOPPED
            }
        }
        emit(AgentEvent.AssistantMessage("已经走了 $maxSteps 步还没完成，先停在这里。说「继续」我会接着做。"))
        listener.onState(AgentState.FINISHED)
        return AgentState.FINISHED
        } finally {
            // Queued messages must survive a stop / error / step budget: persist + show them so the
            // next run picks them up. Runs under NonCancellable because this also covers cancellation.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { drainPending() }
        }
    }

    private suspend fun execute(
        conversationId: String,
        call: ToolCall,
        tainted: Boolean,
        sensitive: Collection<String>,
        emit: suspend (AgentEvent) -> Unit,
        listener: AgentListener,
    ): AgentEvent.ToolObservation {
        val tool = tools[call.name] ?: run {
            audit.record(AuditRecord(conversationId, AuditKinds.ERROR, call.name, "", "没有这个工具：${call.name}", tainted = tainted))
            return AgentEvent.ToolObservation(call.id, call.name, "没有这个工具：${call.name}", isError = true)
        }
        val args = try {
            if (call.arguments.isBlank()) JSONObject() else JSONObject(call.arguments)
        } catch (e: Exception) {
            audit.record(AuditRecord(conversationId, AuditKinds.ERROR, call.name, "", "参数不是合法 JSON：${call.arguments.take(200)}", tainted = tainted))
            return AgentEvent.ToolObservation(call.id, call.name, "参数不是合法 JSON：${call.arguments.take(200)}", isError = true)
        }
        val target = tool.target(args)
        val risk = RiskPolicy.effective(tool, args)
        var allowedBy = ""
        when (val v = sentinel.guard(tool, args, conversationId, tainted, sensitive)) {
            is Verdict.Deny -> {
                audit.record(AuditRecord(conversationId, AuditKinds.DENY, tool.name, target, v.reason, risk, tainted))
                return AgentEvent.ToolObservation(call.id, call.name, "被安全规则拒绝：${v.reason}", isError = true)
            }
            is Verdict.Ask -> {
                val req = AgentEvent.ApprovalRequest(
                    UUID.randomUUID().toString(), call.id, tool.name, tool.title(args), tool.detail(args), target, v.risk, v.scopes, v.warning, tainted = v.tainted,
                )
                emit(req)
                audit.record(AuditRecord(conversationId, AuditKinds.ASK, tool.name, target, req.title + "\n" + req.detail, v.risk, v.tainted))
                listener.onState(AgentState.AWAITING_APPROVAL)
                val raw = gate.await(conversationId, req)
                // Never trust the scope coming back: only what the card offered, and HIGH stays "仅这一次".
                val decision = if (raw.approved) raw.copy(scope = RiskPolicy.clamp(v.scopes, raw.scope)) else raw.copy(scope = null)
                emit(decision)
                listener.onState(AgentState.RUNNING)
                audit.record(
                    AuditRecord(conversationId, if (decision.approved) AuditKinds.APPROVE else AuditKinds.REJECT, tool.name, target, decision.scope?.label ?: "", v.risk, v.tainted),
                )
                if (!decision.approved) return AgentEvent.ToolObservation(call.id, call.name, REJECTED, isError = true)
                if (!v.tainted) decision.scope?.let { s -> sentinel.remember(tool, args, s, conversationId) }
                allowedBy = "已批准（${decision.scope?.label ?: GrantScope.ONCE.label}）"
            }
            is Verdict.Allow -> allowedBy = v.reason
        }
        val started = now()
        val out = try {
            withTimeout(toolTimeoutMs) { tool.run(args, ToolContext(conversationId)) }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            tech.qigai.helium.agent.tools.ToolOutput("超时（${toolTimeoutMs / 1000} 秒）", isError = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            tech.qigai.helium.agent.tools.ToolOutput("执行失败：${e.message ?: e.javaClass.simpleName}", isError = true)
        }
        val ms = now() - started
        audit.record(
            AuditRecord(conversationId, if (out.isError) AuditKinds.ERROR else AuditKinds.RUN, tool.name, target, "[$allowedBy] " + out.text.take(300), risk, tainted),
        )
        return AgentEvent.ToolObservation(call.id, call.name, out.text, out.isError, ms, tool.readsUntrusted && !out.isError)
    }

    companion object {
        const val REJECTED = "用户拒绝了这个操作。不要再尝试同样的操作，除非用户改变主意。"
    }

}
