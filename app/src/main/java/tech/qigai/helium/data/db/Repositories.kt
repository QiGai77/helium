package tech.qigai.helium.data.db

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tech.qigai.helium.agent.AgentEvent
import tech.qigai.helium.agent.ApprovalGate
import tech.qigai.helium.agent.AuditSink
import tech.qigai.helium.agent.EventCodec
import tech.qigai.helium.agent.EventSink
import tech.qigai.helium.agent.Grant
import tech.qigai.helium.agent.GrantScope
import tech.qigai.helium.agent.GrantStore
import tech.qigai.helium.agent.covers
import tech.qigai.helium.agent.tools.MemoryRecord
import tech.qigai.helium.agent.tools.MemoryStore
import java.util.concurrent.ConcurrentHashMap

class ConversationRepository(private val db: HeliumDatabase) : EventSink {
    fun observeAll(): Flow<List<ConversationEntity>> = db.conversations().observeAll()
    suspend fun latest() = db.conversations().latest()

    suspend fun load(conversationId: String): List<AgentEvent> =
        db.events().forConversation(conversationId).mapNotNull { EventCodec.decode(it.kind, it.json) }

    fun observeEvents(conversationId: String): Flow<List<AgentEvent>> =
        db.events().observe(conversationId).map { rows -> rows.mapNotNull { EventCodec.decode(it.kind, it.json) } }

    override suspend fun append(conversationId: String, event: AgentEvent) {
        db.events().insert(EventEntity(conversationId = conversationId, kind = EventCodec.kind(event), json = EventCodec.encode(event), at = event.at))
        val existing = db.conversations().get(conversationId)
        val title = existing?.title ?: (event as? AgentEvent.UserMessage)?.text?.let(::titleFrom) ?: "新对话"
        db.conversations().upsert(ConversationEntity(conversationId, title, existing?.createdAt ?: event.at, event.at))
    }

    suspend fun deleteAfter(conversationId: String, at: Long) = db.events().deleteAfter(conversationId, at)

    suspend fun delete(conversationId: String) {
        db.events().deleteConversation(conversationId)
        db.conversations().delete(conversationId)
    }

    suspend fun rename(conversationId: String, title: String) = db.conversations().rename(conversationId, title.trim().take(40))

    suspend fun search(q: String) = db.events().searchConversations(q)

    private fun titleFrom(text: String) = text.lineSequence().first().trim().take(24).ifBlank { "新对话" }
}

class RoomGrantStore(private val db: HeliumDatabase) : GrantStore {
    override suspend fun active(tool: String, target: String, conversationId: String, now: Long): Grant? =
        db.grants().forTool(tool).map { it.toGrant() }.firstOrNull { it.covers(tool, target, conversationId, now) }

    override suspend fun add(grant: Grant) {
        db.grants().insert(GrantEntity(0, grant.tool, grant.target, grant.scope.name, grant.conversationId, grant.expiresAt, grant.createdAt))
    }

    fun observeAll(): Flow<List<GrantEntity>> = db.grants().observeAll()
    suspend fun revoke(id: Long) = db.grants().delete(id)
    suspend fun purgeExpired() = db.grants().purgeExpired(System.currentTimeMillis())

    private fun GrantEntity.toGrant() = Grant(tool, target, runCatching { GrantScope.valueOf(scope) }.getOrDefault(GrantScope.ONCE), conversationId, expiresAt, id, createdAt)
}

class RoomMemoryStore(private val db: HeliumDatabase) : MemoryStore {
    fun observeAll(): Flow<List<MemoryEntity>> = db.memories().observeAll()
    fun observeRules(): Flow<List<ForgetRuleEntity>> = db.memories().observeRules()
    suspend fun recent(limit: Int) = db.memories().recent(limit)

    override suspend fun search(query: String, limit: Int): List<MemoryRecord> {
        val terms = query.split(Regex("[\\s,，。、]+")).filter { it.isNotBlank() }.ifEmpty { listOf(query) }
        return terms.flatMap { db.memories().search(it, limit) }.distinctBy { it.id }.take(limit).map { it.toRecord() }
    }

    override suspend fun add(text: String, domain: String, inferred: Boolean): Long =
        db.memories().insert(MemoryEntity(0, text.trim(), domain, inferred, "chat", System.currentTimeMillis()))

    suspend fun addManual(text: String, domain: String): Long =
        db.memories().insert(MemoryEntity(0, text.trim(), domain, false, "manual", System.currentTimeMillis()))

    /** Forgetting deletes the memory and records a "不再保存" rule for it. */
    override suspend fun forget(id: Long): MemoryRecord? {
        val m = db.memories().get(id) ?: return null
        db.memories().delete(id)
        db.memories().insertRule(ForgetRuleEntity(0, m.text, System.currentTimeMillis()))
        return m.toRecord()
    }

    override suspend fun blocked(text: String): Boolean {
        val t = normalize(text)
        return db.memories().rules().any { r -> val n = normalize(r.text); n.isNotEmpty() && (t.contains(n) || n.contains(t)) }
    }

    suspend fun deleteRule(id: Long) = db.memories().deleteRule(id)
    suspend fun clear() = db.memories().clear()

    private fun normalize(s: String) = s.filterNot { it.isWhitespace() || it in "，。,.!！?？" }.lowercase()
    private fun MemoryEntity.toRecord() = MemoryRecord(id, text, domain, inferred, createdAt)
}

class AuditRepository(private val db: HeliumDatabase) : AuditSink {
    override suspend fun record(conversationId: String, kind: String, tool: String, target: String, detail: String) {
        db.audit().insert(AuditEntity(0, System.currentTimeMillis(), conversationId, kind, tool, target, detail.take(500)))
    }

    fun observeRecent(limit: Int = 200) = db.audit().observeRecent(limit)
    fun observeCountSince(since: Long) = db.audit().observeCountSince(since)
    suspend fun all() = db.audit().all()
}

/**
 * Bridges the suspended agent loop and the approval UI. Each request is stored as a PENDING row (so Home and the
 * 审批 page can show it); the loop resumes when [decide] completes its deferred. Rows still pending when the
 * process dies are expired on the next start.
 */
class ApprovalBroker(private val db: HeliumDatabase) : ApprovalGate {
    private val waiting = ConcurrentHashMap<String, CompletableDeferred<AgentEvent.ApprovalDecision>>()

    fun observePending(): Flow<List<ApprovalEntity>> = db.approvals().observePending()

    fun isWaiting(requestId: String) = waiting.containsKey(requestId)

    suspend fun expireStale() = db.approvals().expireAll(System.currentTimeMillis())

    override suspend fun await(conversationId: String, request: AgentEvent.ApprovalRequest): AgentEvent.ApprovalDecision {
        val d = CompletableDeferred<AgentEvent.ApprovalDecision>()
        waiting[request.requestId] = d
        db.approvals().insert(
            ApprovalEntity(
                request.requestId, conversationId, request.tool, request.title, request.detail, request.target,
                request.risk.name, request.warning, request.scopes.joinToString(",") { it.name }, "PENDING", request.at, null,
            ),
        )
        try {
            return d.await()
        } finally {
            waiting.remove(request.requestId)
            db.approvals().decide(request.requestId, "EXPIRED", System.currentTimeMillis())
        }
    }

    /** Returns false when nothing is waiting for this request any more (e.g. the run was stopped). */
    suspend fun decide(requestId: String, approved: Boolean, scope: GrantScope?): Boolean {
        val d = waiting[requestId]
        db.approvals().decide(requestId, if (d == null) "EXPIRED" else if (approved) "APPROVED" else "REJECTED", System.currentTimeMillis())
        return d?.complete(AgentEvent.ApprovalDecision(requestId, approved, if (approved) scope ?: GrantScope.ONCE else null)) ?: false
    }
}

class TaskRepository(private val db: HeliumDatabase) {
    fun observeAll(): Flow<List<ScheduledTaskEntity>> = db.tasks().observeAll()
    suspend fun setEnabled(id: String, enabled: Boolean) = db.tasks().setEnabled(id, enabled)
    suspend fun delete(id: String) = db.tasks().delete(id)
}
