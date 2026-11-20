package tech.qigai.helium.agent

/**
 * Detects loops the way OpenHands' StuckDetector does: the same tool call with the same result three times in a
 * row, or the same failing call three times in a row.
 */
object StuckDetector {
    fun isStuck(events: List<AgentEvent>, repeats: Int = 3): Boolean {
        val pairs = ArrayList<Pair<String, String>>()
        var pendingCalls: Map<String, ToolCall> = emptyMap()
        for (e in events) {
            when (e) {
                is AgentEvent.UserMessage -> { pairs.clear(); pendingCalls = emptyMap() }
                is AgentEvent.AssistantMessage -> pendingCalls = e.toolCalls.associateBy { it.id }
                is AgentEvent.ToolObservation -> {
                    val call = pendingCalls[e.callId] ?: continue
                    pairs += (call.name + normalize(call.arguments)) to (if (e.isError) "ERR:" else "") + e.output.take(2000)
                }
                else -> Unit
            }
        }
        if (pairs.size < repeats) return false
        val tail = pairs.takeLast(repeats)
        return tail.all { it == tail.first() }
    }

    private fun normalize(args: String) = args.filterNot { it.isWhitespace() }
}
