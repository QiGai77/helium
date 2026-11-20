package tech.qigai.helium.memory

import tech.qigai.helium.agent.tools.ProposeResult
import tech.qigai.helium.data.db.RoomMemoryStore
import java.time.LocalDate

/** What the post-turn extraction produced. [Failed] is distinct from [NoFacts] on purpose: a model call that timed out or returned garbage must be visible to the user, not silently swallowed as "nothing to remember". */
sealed interface ExtractionOutcome {
    data class Extracted(val count: Int) : ExtractionOutcome
    data object NoFacts : ExtractionOutcome
    data object Failed : ExtractionOutcome
}

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
    suspend fun afterTurn(conversationId: String, user: String, assistant: String): ExtractionOutcome {
        if (!ExtractionPrompt.worthTrying(user)) return ExtractionOutcome.NoFacts
        val existing = store.recent(40).map { it.text }
        val raw = complete(ExtractionPrompt.SYSTEM, ExtractionPrompt.build(user, assistant, existing, LocalDate.now().toString()))
            ?: return ExtractionOutcome.Failed
        val facts = ExtractionPrompt.parse(raw)
        if (facts.isEmpty()) {
            // An explicit {"facts":[]} means "nothing worth remembering"; anything else unparseable is a failure.
            return if (ExtractionPrompt.isExplicitEmpty(raw)) ExtractionOutcome.NoFacts else ExtractionOutcome.Failed
        }
        val n = store.proposeAll(conversationId, facts).count { it == ProposeResult.PROPOSED }
        return ExtractionOutcome.Extracted(n)
    }
}
