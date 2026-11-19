package tech.qigai.helium.data.llm

import tech.qigai.helium.agent.ModelMessage
import tech.qigai.helium.agent.ModelRunner
import tech.qigai.helium.agent.ModelTurn
import tech.qigai.helium.agent.ToolCall
import tech.qigai.helium.agent.tools.ToolSpec

/** Bridges the agent loop to [LlmClient] using whatever provider is active in settings. */
class AgentModelRunner(
    private val client: LlmClient,
    private val endpoint: suspend () -> LlmEndpoint,
    private val sessionId: () -> String,
) : ModelRunner {
    override suspend fun run(system: String, history: List<ModelMessage>, tools: List<ToolSpec>, onDelta: (String, String) -> Unit): ModelTurn {
        val ep = endpoint()
        val text = StringBuilder()
        val reasoning = StringBuilder()
        val calls = ArrayList<ToolCall>()
        client.stream(ep, system, history, sessionId(), tools).collect { ev ->
            when (ev) {
                is StreamEvent.Text -> { text.append(ev.delta); onDelta(text.toString(), reasoning.toString()) }
                is StreamEvent.Reasoning -> { reasoning.append(ev.delta); onDelta(text.toString(), reasoning.toString()) }
                is StreamEvent.ToolCall -> calls += ev.call
                is StreamEvent.Done -> Unit
            }
        }
        return ModelTurn(text.toString(), reasoning.toString(), calls, ep.model)
    }
}
