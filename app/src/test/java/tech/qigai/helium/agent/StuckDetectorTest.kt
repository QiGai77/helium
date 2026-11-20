package tech.qigai.helium.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M2 stuck rule: the same tool with the same arguments three times in a row is stuck,
 * even when every output is different. Identical output is corroborating, never required.
 */
class StuckDetectorTest {
    private fun events(vararg calls: Triple<String, String, String>): List<AgentEvent> {
        // Each call is one assistant turn (one tool call) plus its observation.
        val out = ArrayList<AgentEvent>()
        out += AgentEvent.UserMessage("x", at = 0)
        calls.forEachIndexed { i, (name, args, output) ->
            val id = "c$i"
            out += AgentEvent.AssistantMessage("", at = (i * 2 + 1).toLong(), toolCalls = listOf(ToolCall(id, name, args)))
            out += AgentEvent.ToolObservation(id, name, output, at = (i * 2 + 2).toLong())
        }
        return out
    }

    @Test fun sameCallWithDifferentOutputsIsStuck() {
        // e.g. get_current_time returns a new time on every call: still no progress.
        val e = events(
            Triple("get_current_time", "{}", "10:00"),
            Triple("get_current_time", "{}", "10:01"),
            Triple("get_current_time", "{}", "10:02"),
        )
        assertTrue(StuckDetector.isStuck(e))
    }

    @Test fun sameCallWithIdenticalOutputIsStillStuck() {
        val e = events(
            Triple("web_search", """{"q":"a"}""", "same"),
            Triple("web_search", """{"q":"a"}""", "same"),
            Triple("web_search", """{"q":"a"}""", "same"),
        )
        assertTrue(StuckDetector.isStuck(e))
    }

    @Test fun sameFailingCallThreeTimesIsStuck() {
        val e = events(
            Triple("web_fetch", """{"url":"https://x.test/1"}""", "ERR: timeout"),
            Triple("web_fetch", """{"url":"https://x.test/1"}""", "ERR: 404"),
            Triple("web_fetch", """{"url":"https://x.test/1"}""", "ERR: refused"),
        )
        assertTrue(StuckDetector.isStuck(e))
    }

    @Test fun differentArgumentsAreNotStuck() {
        val e = events(
            Triple("web_search", """{"q":"a"}""", "r1"),
            Triple("web_search", """{"q":"b"}""", "r2"),
            Triple("web_search", """{"q":"c"}""", "r3"),
        )
        assertFalse(StuckDetector.isStuck(e))
    }

    @Test fun differentToolsAreNotStuck() {
        val e = events(
            Triple("web_search", """{"q":"a"}""", "r1"),
            Triple("web_fetch", """{"q":"a"}""", "r2"),
            Triple("web_search", """{"q":"a"}""", "r3"),
        )
        assertFalse(StuckDetector.isStuck(e))
    }

    @Test fun onlyTwoRepeatsAreNotStuck() {
        val e = events(
            Triple("get_current_time", "{}", "10:00"),
            Triple("get_current_time", "{}", "10:01"),
        )
        assertFalse(StuckDetector.isStuck(e))
    }

    @Test fun newUserMessageResetsTheCount() {
        val e = events(
            Triple("get_current_time", "{}", "10:00"),
            Triple("get_current_time", "{}", "10:01"),
        ).toMutableList()
        e += AgentEvent.UserMessage("换个问法", at = 99)
        e += AgentEvent.AssistantMessage("", at = 100, toolCalls = listOf(ToolCall("c9", "get_current_time", "{}")))
        e += AgentEvent.ToolObservation("c9", "get_current_time", "10:03", at = 101)
        assertFalse(StuckDetector.isStuck(e))
    }

    @Test fun whitespaceOnlyArgumentDifferenceDoesNotCount() {
        val e = events(
            Triple("web_search", """{"q": "a"}""", "r1"),
            Triple("web_search", """{"q":"a"}""", "r2"),
            Triple("web_search", """{ "q" : "a" }""", "r3"),
        )
        assertTrue(StuckDetector.isStuck(e))
    }
}
