package tech.qigai.helium.memory

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.qigai.helium.data.db.ForgetRuleEntity
import tech.qigai.helium.data.db.RoomMemoryStore

class MemoryExtractorTest {
    private fun extractor(dao: FakeMemoryDao, calls: MutableList<String>, raw: String?): MemoryExtractor {
        val store = RoomMemoryStore(dao, clock = { 1_000L })
        return MemoryExtractor(store) { _, prompt ->
            calls += prompt
            raw
        }
    }

    @Test fun `model failure is Failed, not zero candidates`() = runBlocking {
        val calls = mutableListOf<String>()
        assertEquals(ExtractionOutcome.Failed, extractor(FakeMemoryDao(), calls, null).afterTurn("c1", "我对花生过敏", "好的"))
        assertEquals(1, calls.size)
    }

    @Test fun `garbage output is Failed, explicit empty list is NoFacts`() = runBlocking {
        val dao = FakeMemoryDao()
        assertEquals(ExtractionOutcome.Failed, extractor(dao, mutableListOf(), "not json at all").afterTurn("c1", "我对花生过敏", "好的"))
        assertEquals(ExtractionOutcome.Failed, extractor(dao, mutableListOf(), "").afterTurn("c1", "我对花生过敏", "好的"))
        assertEquals(ExtractionOutcome.Failed, extractor(dao, mutableListOf(), "嗯，我想想……").afterTurn("c1", "我对花生过敏", "好的"))
        assertEquals(ExtractionOutcome.NoFacts, extractor(dao, mutableListOf(), """{"facts":[]}""").afterTurn("c1", "我对花生过敏", "好的"))
        assertEquals(ExtractionOutcome.NoFacts, extractor(dao, mutableListOf(), "```json\n{\"facts\": []}\n```").afterTurn("c1", "我对花生过敏", "好的"))
        assertTrue("no candidates were stored on any of these", dao.pendingCandidates().isEmpty())
    }

    @Test fun `a real fact becomes a pending candidate`() = runBlocking {
        val dao = FakeMemoryDao()
        val out = extractor(dao, mutableListOf(), """{"facts":[{"text":"对花生过敏","domain":"饮食","inferred":false}]}""")
            .afterTurn("c1", "我对花生过敏", "好的")
        assertEquals(ExtractionOutcome.Extracted(1), out)
        assertEquals(listOf("对花生过敏"), dao.pendingCandidates().map { it.text })
    }

    @Test fun `a fact blocked by a forget rule extracts zero, not a failure`() = runBlocking {
        val dao = FakeMemoryDao()
        dao.insertRule(ForgetRuleEntity(text = "对花生过敏", createdAt = 1L))
        val out = extractor(dao, mutableListOf(), """{"facts":[{"text":"对花生过敏","domain":"饮食","inferred":false}]}""")
            .afterTurn("c1", "我对花生过敏", "好的")
        assertEquals(ExtractionOutcome.Extracted(0), out)
        assertTrue(dao.pendingCandidates().isEmpty())
    }

    @Test fun `turns that cannot hold a fact skip the model call`() = runBlocking {
        val calls = mutableListOf<String>()
        assertEquals(ExtractionOutcome.NoFacts, extractor(FakeMemoryDao(), calls, null).afterTurn("c1", "推荐个零食", "好的"))
        assertTrue(calls.isEmpty())
    }

    @Test fun `isExplicitEmpty distinguishes an empty answer from garbage`() {
        assertTrue(ExtractionPrompt.isExplicitEmpty("""{"facts":[]}"""))
        assertTrue(ExtractionPrompt.isExplicitEmpty("""{"facts": []}"""))
        assertTrue(ExtractionPrompt.isExplicitEmpty("```json\n{\"facts\":[]}\n```"))
        assertTrue(ExtractionPrompt.isExplicitEmpty("""{"memory":[]}"""))
        assertTrue(ExtractionPrompt.isExplicitEmpty("<think>没有</think>{\"facts\":[]}"))
        assertTrue(!ExtractionPrompt.isExplicitEmpty(""))
        assertTrue(!ExtractionPrompt.isExplicitEmpty("没有可以记的"))
        assertTrue(!ExtractionPrompt.isExplicitEmpty("""{"facts":[{"text":"对花生过敏"}]}"""))
    }
}
