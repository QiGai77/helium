package tech.qigai.helium.memory

import tech.qigai.helium.agent.tools.ProposeResult
import tech.qigai.helium.data.db.RoomMemoryStore
import java.time.LocalDate

/**
 * After a finished turn: one cheap model call over the user's message (mem0-style extraction), then every fact
 * goes through the "不再保存" rules and de-duplication before it becomes a PENDING candidate card. Nothing here
 * writes a memory; the user does, by tapping 记住.
 */
class MemoryExtractor(
    private val store: RoomMemoryStore,
    /** (system, user prompt) → raw model text, or null on failure / timeout. */
    private val complete: suspend (String, String) -> String?,
) {
    suspend fun afterTurn(conversationId: String, user: String, assistant: String): Int {
        if (!ExtractionPrompt.worthTrying(user)) return 0
        val existing = store.recent(40).map { it.text }
        val raw = complete(ExtractionPrompt.SYSTEM, ExtractionPrompt.build(user, assistant, existing, LocalDate.now().toString())) ?: return 0
        val facts = ExtractionPrompt.parse(raw)
        if (facts.isEmpty()) return 0
        return store.proposeAll(conversationId, facts).count { it == ProposeResult.PROPOSED }
    }
}
