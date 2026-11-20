package tech.qigai.helium.memory

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.qigai.helium.agent.tools.ProposeResult
import tech.qigai.helium.data.db.CandidateStatus
import tech.qigai.helium.data.db.ForgetRuleEntity
import tech.qigai.helium.data.db.MemoryCandidateEntity
import tech.qigai.helium.data.db.MemoryDao
import tech.qigai.helium.data.db.MemoryEntity
import tech.qigai.helium.data.db.RoomMemoryStore

/** In-memory MemoryDao for store-level tests (no Room on the JVM test classpath). */
internal class FakeMemoryDao : MemoryDao {
    val memories = mutableListOf<MemoryEntity>()
    val rules = mutableListOf<ForgetRuleEntity>()
    val candidates = mutableListOf<MemoryCandidateEntity>()
    private var nextId = 1L
    private var nextRule = 1L
    private var nextCandidate = 1L

    override fun observeAll(): Flow<List<MemoryEntity>> = flowOf(memories.toList())
    override suspend fun recent(limit: Int) = memories.sortedByDescending { it.createdAt }.take(limit)
    override suspend fun search(q: String, limit: Int) = memories.filter { it.text.contains(q) || it.domain == q }.take(limit)
    override suspend fun get(id: Long) = memories.firstOrNull { it.id == id }
    override suspend fun insert(m: MemoryEntity): Long {
        val row = m.copy(id = nextId++)
        memories += row
        return row.id
    }

    override suspend fun delete(id: Long) {
        memories.removeAll { it.id == id }
    }

    override suspend fun clear() = memories.clear()
    override suspend fun insertRule(r: ForgetRuleEntity): Long {
        val row = r.copy(id = nextRule++)
        rules += row
        return row.id
    }

    override suspend fun rules() = rules.toList()
    override fun observeRules(): Flow<List<ForgetRuleEntity>> = flowOf(rules.toList())
    override suspend fun deleteRule(id: Long) {
        rules.removeAll { it.id == id }
    }

    override suspend fun all() = memories.sortedByDescending { it.createdAt }
    override suspend fun count() = memories.size
    override suspend fun ftsSearch(match: String, limit: Int) = emptyList<MemoryEntity>()
    override suspend fun byDomains(domains: List<String>, limit: Int) = memories.filter { it.domain in domains }.take(limit)
    override suspend fun update(m: MemoryEntity) {
        val i = memories.indexOfFirst { it.id == m.id }
        if (i >= 0) memories[i] = m
    }

    override suspend fun insertCandidate(c: MemoryCandidateEntity): Long {
        val row = c.copy(id = nextCandidate++)
        candidates += row
        return row.id
    }

    override suspend fun candidate(id: Long) = candidates.firstOrNull { it.id == id }
    override suspend fun pendingCandidates() = candidates.filter { it.status == CandidateStatus.PENDING }
    override fun observePendingCandidates(): Flow<List<MemoryCandidateEntity>> = flowOf(pendingCandidates())
    override fun observeCandidates(conversationId: String): Flow<List<MemoryCandidateEntity>> =
        flowOf(candidates.filter { it.conversationId == conversationId && it.status == CandidateStatus.PENDING })

    override suspend fun setCandidateStatus(id: Long, status: String) {
        val i = candidates.indexOfFirst { it.id == id }
        if (i >= 0) candidates[i] = candidates[i].copy(status = status)
    }

    override suspend fun clearCandidates() = candidates.clear()
    override suspend fun purgeDecided(before: Long) {
        candidates.removeAll { it.status != CandidateStatus.PENDING && it.createdAt < before }
    }
}

class MemoryStoreTest {
    private val dao = FakeMemoryDao()
    private val store = RoomMemoryStore(dao, clock = { 1_000L })

    private suspend fun forgetRule(text: String) {
        dao.insertRule(ForgetRuleEntity(text = text, createdAt = 1L))
    }

    @Test fun `manual add blocked by a forget rule stores nothing`() = runBlocking {
        forgetRule("对花生过敏")
        assertEquals(-1L, store.addManual("我对花生过敏", "饮食"))
        assertEquals(-1L, store.addManual("对花生过敏，不能吃花生酱", "饮食"))
        assertTrue(dao.all().isEmpty())
        assertTrue("the rule itself is untouched", dao.rules().size == 1)
    }

    @Test fun `manual add of unrelated text stores it`() = runBlocking {
        forgetRule("对花生过敏")
        val id = store.addManual("喜欢跑步", "健康")
        assertTrue(id > 0)
        assertEquals(listOf("喜欢跑步"), dao.all().map { it.text })
    }

    @Test fun `add through the store entry point is blocked too`() = runBlocking {
        forgetRule("对花生过敏")
        assertEquals(-1L, store.add("对花生过敏", "饮食", false))
        assertTrue(dao.all().isEmpty())
    }

    @Test fun `edit to a ruled-out text is refused and keeps the old text`() = runBlocking {
        forgetRule("对花生过敏")
        val id = store.addManual("喜欢跑步", "健康")
        assertFalse(store.update(id, "我对花生过敏", "饮食", false))
        assertEquals("喜欢跑步", dao.get(id)?.text)
        assertTrue("an unrelated edit still works", store.update(id, "每天跑步", "健康", false))
        assertEquals("每天跑步", dao.get(id)?.text)
    }

    @Test fun `proposeAll still blocks ruled-out facts`() = runBlocking {
        forgetRule("坐高铁一般选靠窗座位")
        val r = store.proposeAll("c1", listOf(CandidateFact("坐高铁一般选靠窗的座位", "出行", false)))
        assertEquals(listOf(ProposeResult.BLOCKED), r)
        assertTrue(dao.pendingCandidates().isEmpty())
    }
}
