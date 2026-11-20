package tech.qigai.helium.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.qigai.helium.agent.tools.Tool
import tech.qigai.helium.agent.tools.ToolContext
import tech.qigai.helium.agent.tools.ToolOutput
import tech.qigai.helium.agent.tools.ToolRegistry
import tech.qigai.helium.agent.tools.ToolSpec
import tech.qigai.helium.agent.tools.params
import tech.qigai.helium.data.llm.LlmClient
import tech.qigai.helium.data.llm.LlmEndpoint
import tech.qigai.helium.data.llm.ProviderId
import tech.qigai.helium.data.llm.Role
import tech.qigai.helium.data.llm.ChatTurn
import tech.qigai.helium.data.llm.WireApi
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * M2 acceptance 4: "执行中点停止，1 秒内停下，已完成的步骤保留在时间线。"
 * These tests prove the stop chain with injectable fakes and a stalled server — no real device,
 * no real network timing: cancellation is cooperative and must land well inside 1 second.
 * The bound is asserted at 1000 ms; the fakes cancel immediately, so a healthy run lands in tens of ms.
 */
class StopLatencyTest {
    private class FakeGrants : GrantStore {
        override suspend fun active(tool: String, target: String, conversationId: String, now: Long) = null
        override suspend fun add(grant: Grant) {}
    }

    private class ListSink : EventSink {
        val e = mutableListOf<AgentEvent>()
        override suspend fun append(conversationId: String, event: AgentEvent) { e += event }
    }

    private class RecordingListener : AgentListener {
        val states = mutableListOf<AgentState>()
        override fun onState(state: AgentState) { states += state }
    }

    private fun controller(model: ModelRunner, tools: List<Tool>, sink: ListSink): AgentController =
        AgentController(model, ToolRegistry(tools), Sentinel(FakeGrants()), { _, r -> AgentEvent.ApprovalDecision(r.requestId, true) }, sink)

    private class SlowTool : Tool {
        override val spec = ToolSpec("slow", "", params())
        override fun risk(args: JSONObject) = Risk.LOW
        override suspend fun run(args: JSONObject, ctx: ToolContext): ToolOutput {
            delay(60_000)
            return ToolOutput("never")
        }
    }

    @Test fun stopDuringModelCallLandsWithinOneSecond() = runBlocking {
        val sink = ListSink()
        val listener = RecordingListener()
        var entered = false
        val blocking = object : ModelRunner {
            override suspend fun run(system: String, history: List<ModelMessage>, tools: List<ToolSpec>, onDelta: (String, String) -> Unit): ModelTurn {
                entered = true
                delay(60_000)
                return ModelTurn("never", "", emptyList(), "m")
            }
        }
        val events = mutableListOf<AgentEvent>(AgentEvent.UserMessage("开始"))
        val job = launch {
            try {
                controller(blocking, emptyList(), sink).run("c1", "", events, listener)
            } catch (e: CancellationException) {
                // Expected: this is the stop.
            }
        }
        var waited = 0
        while (!entered && waited < 2000) { delay(10); waited += 10 }
        assertTrue(entered)
        val elapsed = measureTimeMillis { job.cancel(); job.join() }
        assertTrue("stop during model call took ${elapsed}ms, must be < 1000ms", elapsed < 1000)
        assertTrue(sink.e.any { it is AgentEvent.AssistantMessage && it.stopped })
        assertTrue(listener.states.contains(AgentState.STOPPED))
    }

    @Test fun stopDuringToolExecutionLandsWithinOneSecond() = runBlocking {
        val sink = ListSink()
        val listener = RecordingListener()
        var entered = false
        val oneShot = object : ModelRunner {
            override suspend fun run(system: String, history: List<ModelMessage>, tools: List<ToolSpec>, onDelta: (String, String) -> Unit): ModelTurn {
                entered = true
                return ModelTurn("", "", listOf(ToolCall("t1", "slow", "{}")), "m")
            }
        }
        val events = mutableListOf<AgentEvent>(AgentEvent.UserMessage("跑个慢工具"))
        val job = launch {
            try {
                controller(oneShot, listOf(SlowTool()), sink).run("c1", "", events, listener)
            } catch (e: CancellationException) {
                // Expected: this is the stop.
            }
        }
        var waited = 0
        while (!entered && waited < 2000) { delay(10); waited += 10 }
        assertTrue(entered)
        delay(100) // let the tool call start executing
        val elapsed = measureTimeMillis { job.cancel(); job.join() }
        assertTrue("stop during tool execution took ${elapsed}ms, must be < 1000ms", elapsed < 1000)
        assertTrue(sink.e.any { it is AgentEvent.ToolObservation && it.output.contains("已被用户停止") })
        assertTrue(listener.states.contains(AgentState.STOPPED))
    }

    @Test fun cancellingJobCancelsTheOkHttpCallWithinOneSecond() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            // The server never answers: without cancellation the stream would hang until read timeout.
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val client = LlmClient(OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build())
            val ep = LlmEndpoint(ProviderId.OPENCODE_GO, WireApi.CHAT_COMPLETIONS, server.url("/v1").toString(), "k", "m")
            var thrown: Throwable? = null
            val job = launch {
                try {
                    client.stream(ep, "sys", listOf(ChatTurn(Role.USER, "hi")), "s").collect {}
                } catch (e: CancellationException) {
                    thrown = e // Expected: the job's cancellation must cancel the OkHttp call.
                } catch (e: Exception) {
                    thrown = e // Recorded, not rethrown: the assertion below decides.
                }
            }
            delay(300) // let the request reach the server
            assertTrue(server.requestCount >= 1)
            val elapsed = measureTimeMillis { job.cancel(); job.join() }
            assertTrue("cancelling the stream took ${elapsed}ms, must be < 1000ms", elapsed < 1000)
            assertTrue(job.isCancelled)
            assertTrue("expected CancellationException, got $thrown", thrown is CancellationException)
        } finally {
            server.shutdown()
        }
    }
}
