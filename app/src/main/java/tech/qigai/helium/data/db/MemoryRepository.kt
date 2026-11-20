package tech.qigai.helium.data.db

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import tech.qigai.helium.agent.tools.MemoryRecord
import tech.qigai.helium.agent.tools.MemoryStore
import tech.qigai.helium.agent.tools.ProposeResult
import tech.qigai.helium.agent.tools.SkillStore
import tech.qigai.helium.chat.FtsQuery
import tech.qigai.helium.chat.SearchText
import tech.qigai.helium.memory.CandidateFact
import tech.qigai.helium.memory.ExportSkill
import tech.qigai.helium.memory.ForgetRules
import tech.qigai.helium.memory.MemoryDomains
import tech.qigai.helium.memory.MemoryExport
import tech.qigai.helium.memory.MemoryRanker
import tech.qigai.helium.memory.RuleRecord
import tech.qigai.helium.skills.SkillDoc
import tech.qigai.helium.skills.SkillIds
import tech.qigai.helium.skills.SkillMarkdown

/**
 * 关于你: the local memory library. Memories are only written when the user confirms a candidate (or types one);
 * forgetting deletes the row (and its FTS entry, via Room's triggers) and records a "不再保存" rule that blocks the
 * same fact from being proposed or saved again.
 */
class RoomMemoryStore(private val db: HeliumDatabase, private val clock: () -> Long = System::currentTimeMillis) : MemoryStore {
    private val dao get() = db.memories()

    fun observeAll(): Flow<List<MemoryEntity>> = dao.observeAll()
    fun observeRules(): Flow<List<ForgetRuleEntity>> = dao.observeRules()
    fun observePendingCandidates(): Flow<List<MemoryCandidateEntity>> = dao.observePendingCandidates()
    fun observeCandidates(conversationId: String): Flow<List<MemoryCandidateEntity>> = dao.observeCandidates(conversationId)
    suspend fun recent(limit: Int) = dao.recent(limit)

    override suspend fun search(query: String, limit: Int): List<MemoryRecord> {
        val strict = FtsQuery.build(query)?.let { runCatching { dao.ftsSearch(it, limit) }.getOrNull() }.orEmpty()
        val loose = if (strict.size >= limit) emptyList() else MemoryRanker.ftsAnyOf(query)?.let { runCatching { dao.ftsSearch(it, 40) }.getOrNull() }.orEmpty()
        val ranked = MemoryRanker.rank(query, loose.map { it.toRecord() }, limit, wholeBelow = 0)
        return (strict.map { it.toRecord() } + ranked).distinctBy { it.id }.take(limit)
    }

    /** What goes into this turn's system prompt: FTS hits, the query's domains and safety facts, ranked. */
    suspend fun relevant(query: String, limit: Int = 10): List<MemoryRecord> {
        val total = dao.count()
        if (total == 0) return emptyList()
        val pool = if (total <= 60) {
            dao.all()
        } else {
            val fts = MemoryRanker.ftsAnyOf(query)?.let { runCatching { dao.ftsSearch(it, 40) }.getOrNull() }.orEmpty()
            val domains = (MemoryDomains.matching(query) + MemoryDomains.FOOD + MemoryDomains.HEALTH).distinct()
            fts + dao.byDomains(domains, 60) + dao.recent(10)
        }
        return MemoryRanker.rank(query, pool.distinctBy { it.id }.map { it.toRecord() }, limit)
    }

    override suspend fun add(text: String, domain: String, inferred: Boolean): Long = insert(text, domain, inferred, "chat")

    suspend fun addManual(text: String, domain: String): Long = insert(text, domain, false, "manual")

    private suspend fun insert(text: String, domain: String, inferred: Boolean, source: String): Long {
        val t = text.trim()
        val now = clock()
        return dao.insert(MemoryEntity(0, t, MemoryDomains.normalize(domain) ?: MemoryDomains.guess(t), inferred, source, now, now, SearchText.normalize(t)))
    }

    suspend fun update(id: Long, text: String, domain: String, inferred: Boolean) {
        val m = dao.get(id) ?: return
        val t = text.trim().ifEmpty { return }
        dao.update(m.copy(text = t, domain = MemoryDomains.normalize(domain) ?: m.domain, inferred = inferred, updatedAt = clock(), searchText = SearchText.normalize(t)))
    }

    /** Forgetting deletes the memory and records a "不再保存" rule for it; matching pending candidates go too. */
    override suspend fun forget(id: Long): MemoryRecord? {
        val m = dao.get(id) ?: return null
        db.withTransaction {
            dao.delete(id)
            addRule(m.text)
            dao.pendingCandidates().filter { ForgetRules.matches(m.text, it.text) }.forEach { dao.setCandidateStatus(it.id, CandidateStatus.DISMISSED) }
        }
        return m.toRecord()
    }

    private suspend fun addRule(text: String) {
        if (dao.rules().none { ForgetRules.normalize(it.text) == ForgetRules.normalize(text) }) dao.insertRule(ForgetRuleEntity(0, text.trim(), clock()))
    }

    override suspend fun blocked(text: String): Boolean = ForgetRules.blocked(dao.rules().map { it.text }, text)

    override suspend fun propose(text: String, domain: String, inferred: Boolean, conversationId: String): ProposeResult {
        val fact = CandidateFact(text.trim(), MemoryDomains.normalize(domain) ?: MemoryDomains.guess(text), inferred)
        return proposeAll(conversationId, listOf(fact)).firstOrNull() ?: ProposeResult.DUPLICATE
    }

    /** Filters [facts] through the rules, saved memories and pending candidates, and stores the rest as PENDING. */
    suspend fun proposeAll(conversationId: String, facts: List<CandidateFact>): List<ProposeResult> {
        val rules = dao.rules().map { it.text }
        val known = (dao.all().map { it.text } + dao.pendingCandidates().map { it.text }).toMutableList()
        return facts.map { f ->
            when {
                ForgetRules.blocked(rules, f.text) -> ProposeResult.BLOCKED
                ForgetRules.duplicate(known, f.text) -> ProposeResult.DUPLICATE
                else -> {
                    dao.insertCandidate(MemoryCandidateEntity(0, conversationId, f.text, f.domain, f.inferred, CandidateStatus.PENDING, clock()))
                    known += f.text
                    ProposeResult.PROPOSED
                }
            }
        }
    }

    /** 记住: the candidate (optionally edited) becomes a memory. */
    suspend fun accept(candidateId: Long, text: String? = null, domain: String? = null): Long? {
        val c = dao.candidate(candidateId) ?: return null
        if (c.status != CandidateStatus.PENDING) return null
        val t = text?.trim()?.ifEmpty { null } ?: c.text
        val id = db.withTransaction {
            dao.setCandidateStatus(c.id, CandidateStatus.ACCEPTED)
            insert(t, domain ?: c.domain, c.inferred && text == null, "chat")
        }
        dao.purgeDecided(clock() - 7L * 24 * 3600_000)
        return id
    }

    /** 不用: dismiss this time; the same fact may be proposed again later. */
    suspend fun dismiss(candidateId: Long) = dao.setCandidateStatus(candidateId, CandidateStatus.DISMISSED)

    /** 不再提: dismiss and add a "不再保存" rule right away. */
    suspend fun never(candidateId: Long) {
        val c = dao.candidate(candidateId) ?: return
        db.withTransaction {
            dao.setCandidateStatus(c.id, CandidateStatus.DISMISSED)
            addRule(c.text)
        }
    }

    suspend fun deleteRule(id: Long) = dao.deleteRule(id)

    /** 清空: every memory and pending candidate. "不再保存" rules stay, so cleared facts aren't re-learned silently. */
    suspend fun clear() = db.withTransaction {
        dao.clear()
        dao.clearCandidates()
    }

    suspend fun exportMarkdown(skills: List<SkillDoc>): String =
        MemoryExport.markdown(dao.all().map { it.toRecord() }, dao.rules().map { RuleRecord(it.id, it.text, it.createdAt) }, skills.map { it.toExport() }, clock())

    suspend fun exportJson(skills: List<SkillDoc>): String =
        MemoryExport.json(dao.all().map { it.toRecord() }, dao.rules().map { RuleRecord(it.id, it.text, it.createdAt) }, skills.map { it.toExport() }, clock())

    private fun SkillDoc.toExport() = ExportSkill(id, name, description, triggers, enabled, SkillMarkdown.render(this))
}

fun MemoryEntity.toRecord() = MemoryRecord(id, text, domain, inferred, createdAt)

fun SkillEntity.toDoc() = SkillDoc(id, name, description, triggers.lines().map { it.trim() }.filter { it.isNotEmpty() }, body, enabled)

class RoomSkillStore(private val db: HeliumDatabase, private val clock: () -> Long = System::currentTimeMillis) : SkillStore {
    fun observeAll(): Flow<List<SkillEntity>> = db.skills().observeAll()

    override suspend fun list(): List<SkillDoc> = db.skills().all().map { it.toDoc() }

    override suspend fun save(doc: SkillDoc): SkillDoc {
        val all = db.skills().all()
        val existing = all.firstOrNull { it.id == doc.id && doc.id.isNotBlank() }
        val id = existing?.id ?: SkillIds.forName(doc.name, all.map { it.id }.toSet())
        val now = clock()
        val row = SkillEntity(
            id, doc.name.trim(), doc.description.trim(), doc.triggers.joinToString("\n"), doc.body.trim(),
            existing?.enabled ?: doc.enabled, existing?.createdAt ?: now, now,
        )
        db.skills().upsert(row)
        return row.toDoc()
    }

    override suspend fun delete(id: String): Boolean {
        val had = db.skills().get(id) != null
        db.skills().delete(id)
        return had
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = db.skills().setEnabled(id, enabled, clock())
}
