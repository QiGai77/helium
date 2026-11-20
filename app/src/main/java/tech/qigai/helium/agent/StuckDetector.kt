package tech.qigai.helium.agent

/**
 * Detects loops the way OpenHands' StuckDetector does, with one deliberate change of rule (M2):
 * the same tool with the same arguments three times in a row is stuck, **even if the outputs differ**
 * (a clock that returns a new time each call is still no progress). Identical output is only
 * corroborating evidence, never a precondition. A new user message resets the count.
 */
object StuckDetector {
    fun isStuck(events: List<AgentEvent>, repeats: Int = 3): Boolean {
        val keys = ArrayList<String>()
        var pendingCalls: Map<String, ToolCall> = emptyMap()
        for (e in events) {
            when (e) {
                is AgentEvent.UserMessage -> { keys.clear(); pendingCalls = emptyMap() }
                is AgentEvent.AssistantMessage -> pendingCalls = e.toolCalls.associateBy { it.id }
                is AgentEvent.ToolObservation -> {
                    val call = pendingCalls[e.callId] ?: continue
                    keys += call.name + normalize(call.arguments)
                }
                else -> Unit
            }
        }
        if (keys.size < repeats) return false
        val tail = keys.takeLast(repeats)
        return tail.all { it == tail.first() }
    }

    private fun normalize(args: String) = args.filterNot { it.isWhitespace() }
}
