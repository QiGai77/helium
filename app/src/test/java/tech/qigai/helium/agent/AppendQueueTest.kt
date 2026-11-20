package tech.qigai.helium.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.qigai.helium.agent.tools.Tool
import tech.qigai.helium.agent.tools.ToolContext
import tech.qigai.helium.agent.tools.ToolOutput
import tech.qigai.helium.agent.tools.ToolRegistry
import tech.qigai.helium.agent.tools.ToolSpec
import tech.qigai.helium.agent.tools.params

/**
 * M2 append-while-running: a message sent mid-run is queued into this run's context at the next
 * step boundary. The in-flight tool call is never cancelled or discarded (the old behaviour was
 * `cancelAndJoin` + restart, which threw the running call away).
 */
class AppendQueueTest {
    private class FakeGrants : GrantStore {
        val list = mutableListOf<Grant>()
        override suspend fun active(tool: String, target: String, conversationId: String, now: Long) =
            list.firstOrNull { it.covers(tool, target, conversationId, now) }
        override suspend fun add(grant: Grant) { list += grant }
    }

    private class EchoTool : Tool {
        var runs = 0
        override val spec = ToolSpec("echo", "", params(Triple("text", "string", "")))
        override fun risk(args: JSONObject) = Risk.LOW
        override suspend fun run(args: JSONObject, ctx: ToolContext): ToolOutput {
            runs++
            return ToolOutput("echo-ok")
        }
    }

    private class ListSink : EventSink {
        val e = mutableListOf<AgentEvent>()
        override suspend fun append(conversationId: String, event: AgentEvent) { e += event }
    }

    private class RecordingListener : AgentListener {
        val states = mutableListOf<AgentState>()
        override fun onState(state: AgentState) { states += state }
    }

    private class ScriptedRunner(private val turns: List<ModelTurn>) : ModelRunner {
        var calls = 0
        val histories = mutableListOf<List<ModelMessage>>()
        var entered = false
        override suspend fun run(system: String, history: List<ModelMessage>, tools: List<ToolSpec>, onDelta: (String, String) -> Unit): ModelTurn {
            entered = true
            histories += history
            return turns[minOf(calls++, turns.size - 1)]
        }
    }

    private fun controller(runner: ModelRunner, tool: EchoTool, pending: (() -> AgentEvent.UserMessage?)?, sink: ListSink): AgentController {
        val reg = ToolRegistry(listOf(tool))
        return AgentController(runner, reg, Sentinel(FakeGrants()), { _, r -> AgentEvent.ApprovalDecision(r.requestId, true) }, sink, pending = pending)
    }

    @Test fun appendDuringRunDoesNotDiscardInFlightToolCall() = runBlocking {
        val tool = EchoTool()
        val sink = ListSink()
        val runner = ScriptedRunner(
            listOf(
                ModelTurn("", "", listOf(ToolCall("a", "echo", """{"text":"hi"}""")), "m"),
                ModelTurn("收到，明天也查了。", "", emptyList(), "m"),
            ),
        )
        // The queued message only exists from the second drain onwards (i.e. after the tool ran).
        var polls = 0
        val queued = AgentEvent.UserMessage("还有，明天呢？")
        val pending: () -> AgentEvent.UserMessage? = { polls++; if (polls == 2) queued else null }
        val listener = RecordingListener()
        val events = mutableListOf<AgentEvent>(AgentEvent.UserMessage("今天天气怎么样"))
        val state = controller(runner, tool, pending, sink).run("c1", "", events, listener)

        assertEquals(AgentState.FINISHED, state)
        assertEquals(1, tool.runs) // the in-flight call ran to completion, exactly once
        assertEquals(2, runner.calls) // no restart: the same run answered the queued message
        assertTrue(sink.e.any { it is AgentEvent.ToolObservation && it.tool == "echo" && !it.isError })
        assertTrue(sink.e.any { it is AgentEvent.UserMessage && it.text == "还有，明天呢？" })
        // The second model call saw the queued message in its context.
        val secondHistory = runner.histories[1]
        assertTrue(secondHistory.any { it.role == ModelMessage.Role.USER && it.content.contains("还有，明天呢？") })
        // And the tool observation survived into that context too.
        assertTrue(secondHistory.any { it.role == ModelMessage.Role.TOOL })
    }

    @Test fun plainAnswerThenQueuedMessageContinuesSameRun() = runBlocking {
        val tool = EchoTool()
        val sink = ListSink()
        val runner = ScriptedRunner(
            listOf(
                ModelTurn("第一条答完了。", "", emptyList(), "m"),
                ModelTurn("第二条也答了。", "", emptyList(), "m"),
            ),
        )
        var polls = 0
        val queued = AgentEvent.UserMessage("再问一个")
        val pending: () -> AgentEvent.UserMessage? = { polls++; if (polls == 2) queued else null }
        val events = mutableListOf<AgentEvent>(AgentEvent.UserMessage("你好"))
        val state = controller(runner, tool, pending, sink).run("c1", "", events, RecordingListener())

        assertEquals(AgentState.FINISHED, state)
        assertEquals(2, runner.calls)
        assertTrue(runner.histories[1].any { it.role == ModelMessage.Role.USER && it.content.contains("再问一个") })
        val queuedIdx = sink.e.indexOfFirst { it is AgentEvent.UserMessage && it.text == "再问一个" }
        val lastAnswerIdx = sink.e.indexOfLast { it is AgentEvent.AssistantMessage && it.text == "第二条也答了。" }
        assertTrue(queuedIdx >= 0 && queuedIdx < lastAnswerIdx)
    }

    @Test fun queuedMessageIsNotLostWhenRunIsStopped() = runBlocking {
        val tool = EchoTool()
        val sink = ListSink()
        var entered = false
        val blocking = object : ModelRunner {
            override suspend fun run(system: String, history: List<ModelMessage>, tools: List<ToolSpec>, onDelta: (String, String) -> Unit): ModelTurn {
                entered = true
                delay(60_000)
                return ModelTurn("never", "", emptyList(), "m")
            }
        }
        val queue = ArrayDeque<AgentEvent.UserMessage>()
        val listener = RecordingListener()
        val events = mutableListOf<AgentEvent>(AgentEvent.UserMessage("开始"))
        val job = launch {
            try {
                controller(blocking, tool, { queue.removeFirstOrNull() }, sink).run("c1", "", events, listener)
            } catch (e: CancellationException) {
                // Expected: stop() cancels the run.
            }
        }
        // Wait until the model call is actually in flight, then queue a message and stop.
        var waited = 0
        while (!entered && waited < 2000) { delay(10); waited += 10 }
        assertTrue(entered)
        queue.addLast(AgentEvent.UserMessage("等下，先别停，我还有一句"))
        delay(50)
        job.cancel()
        job.join()
        assertTrue(sink.e.any { it is AgentEvent.UserMessage && it.text == "等下，先别停，我还有一句" })
        assertTrue(sink.e.any { it is AgentEvent.AssistantMessage && it.stopped })
        assertTrue(listener.states.contains(AgentState.STOPPED))
    }
}
