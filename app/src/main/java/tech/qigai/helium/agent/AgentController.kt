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

fun interface AuditSink {
    suspend fun record(conversationId: String, kind: String, tool: String, target: String, detail: String)
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
    private val audit: AuditSink = AuditSink { _, _, _, _, _ -> },
    private val maxSteps: Int = 12,
    private val toolTimeoutMs: Long = 60_000,
) {
    /** Runs until the agent finishes. [events] is the conversation so far and is appended to in place. */
    suspend fun run(conversationId: String, system: String, events: MutableList<AgentEvent>, listener: AgentListener): AgentState {
        suspend fun emit(e: AgentEvent) {
            events += e
            sink.append(conversationId, e)
            listener.onEvent(e)
        }
        var tainted = false
        listener.onState(AgentState.RUNNING)
        for (step in 1..maxSteps) {
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
                listener.onState(AgentState.FINISHED)
                return AgentState.FINISHED
            }
            for (call in turn.toolCalls) {
                val obs = try {
                    execute(conversationId, call, tainted, ::emit, listener)
                } catch (e: CancellationException) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        emit(AgentEvent.ToolObservation(call.id, call.name, "已被用户停止", isError = true))
                        listener.onState(AgentState.STOPPED)
                    }
                    throw e
                }
                tainted = tainted || obs.tainted
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
    }

    private suspend fun execute(
        conversationId: String,
        call: ToolCall,
        tainted: Boolean,
        emit: suspend (AgentEvent) -> Unit,
        listener: AgentListener,
    ): AgentEvent.ToolObservation {
        val tool = tools[call.name] ?: return AgentEvent.ToolObservation(call.id, call.name, "没有这个工具：${call.name}", isError = true)
        val args = try {
            if (call.arguments.isBlank()) JSONObject() else JSONObject(call.arguments)
        } catch (e: Exception) {
            return AgentEvent.ToolObservation(call.id, call.name, "参数不是合法 JSON：${call.arguments.take(200)}", isError = true)
        }
        val target = tool.target(args)
        when (val v = sentinel.guard(tool, args, conversationId, tainted)) {
            is Verdict.Deny -> {
                audit.record(conversationId, "deny", tool.name, target, v.reason)
                return AgentEvent.ToolObservation(call.id, call.name, "被安全规则拒绝：${v.reason}", isError = true)
            }
            is Verdict.Ask -> {
                val req = AgentEvent.ApprovalRequest(
                    UUID.randomUUID().toString(), call.id, tool.name, tool.title(args), tool.detail(args), target, v.risk, v.scopes, v.warning,
                )
                emit(req)
                audit.record(conversationId, "ask", tool.name, target, req.title)
                listener.onState(AgentState.AWAITING_APPROVAL)
                val decision = gate.await(conversationId, req)
                emit(decision)
                listener.onState(AgentState.RUNNING)
                audit.record(conversationId, if (decision.approved) "approve" else "reject", tool.name, target, decision.scope?.label ?: "")
                if (!decision.approved) return AgentEvent.ToolObservation(call.id, call.name, "用户拒绝了这个操作。不要再尝试同样的操作，除非用户改变主意。", isError = true)
                decision.scope?.let { s -> sentinel.remember(tool, args, s, conversationId) }
            }
            is Verdict.Allow -> Unit
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
        audit.record(conversationId, if (out.isError) "error" else "run", tool.name, target, out.text.take(200))
        return AgentEvent.ToolObservation(call.id, call.name, out.text, out.isError, ms, tool.readsUntrusted && !out.isError)
    }

}
