package tech.qigai.helium.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.qigai.helium.agent.AgentEvent
import tech.qigai.helium.agent.AgentState

class RunOutcomeTest {
    @Test fun success() {
        val o = RunAnalysis.of(listOf(AgentEvent.UserMessage("x"), AgentEvent.AssistantMessage("晴，18°C")), AgentState.FINISHED)
        assertEquals(RunOutcome.Success("晴，18°C"), o)
    }

    @Test fun offlineIsNetworkFailure() {
        val o = RunAnalysis.of(listOf(AgentEvent.AssistantMessage("", error = "Unable to resolve host \"api.deepseek.com\"")), AgentState.ERROR)
        assertTrue(o is RunOutcome.Failed && o.reason.startsWith("网络不可用"))
    }

    @Test fun stuckIsFailure() {
        val o = RunAnalysis.of(listOf(AgentEvent.AssistantMessage("我在重复同一步")), AgentState.STOPPED)
        assertTrue(o is RunOutcome.Failed)
    }

    @Test fun nothingIsFailure() {
        assertTrue(RunAnalysis.of(emptyList(), AgentState.FINISHED) is RunOutcome.Failed)
    }

    @Test fun preview() {
        assertEquals("今天晴 。", RunAnalysis.preview("**今天晴** [1]。"))
        assertEquals(10, RunAnalysis.preview("a".repeat(50), 10).length)
    }
}
