package tech.qigai.helium.data.db

import androidx.room.withTransaction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tech.qigai.helium.agent.AgentEvent
import tech.qigai.helium.agent.ApprovalGate
import tech.qigai.helium.agent.AuditRecord
import tech.qigai.helium.agent.AuditSink
import tech.qigai.helium.agent.RiskPolicy
import tech.qigai.helium.agent.EventCodec
import tech.qigai.helium.agent.EventSink
import tech.qigai.helium.agent.Grant
import tech.qigai.helium.agent.GrantScope
import tech.qigai.helium.agent.GrantStore
import tech.qigai.helium.agent.covers
import tech.qigai.helium.chat.FtsQuery
import tech.qigai.helium.chat.PendingReply
import tech.qigai.helium.chat.SearchText
import tech.qigai.helium.chat.Snippet
import tech.qigai.helium.chat.TitlePrompt
import java.util.concurrent.ConcurrentHashMap

/** One search hit: the message, where it lives, and a one-line snippet around the keyword. */
data class MessageHit(
    val conversationId: String,
    val conversationTitle: String,
    val eventId: Long,
    val at: Long,
    val fromUser: Boolean,
    val snippet: String,
)

class ConversationRepository(
    private val db: HeliumDatabase,
    /** Called with the attachment refs of a deleted conversation so their files can be removed. */
    private val onImagesDropped: (List<String>) -> Unit = {},
) : EventSink {
    fun observeAll(): Flow<List<ConversationEntity>> = db.conversations().observeAll()
    suspend fun latest() = db.conversations().latest()
    suspend fun get(id: String) = db.conversations().get(id)

    suspend fun load(conversationId: String): List<AgentEvent> =
        db.events().forConversation(conversationId).mapNotNull { EventCodec.decode(it.kind, it.json) }

    fun observeEvents(conversationId: String): Flow<List<AgentEvent>> =
        db.events().observe(conversationId).map { rows -> rows.mapNotNull { EventCodec.decode(it.kind, it.json) } }

    override suspend fun append(conversationId: String, event: AgentEvent) {
        db.withTransaction {
            db.events().insert(
                EventEntity(
                    conversationId = conversationId, kind = EventCodec.kind(event), json = EventCodec.encode(event),
                    at = event.at, searchText = SearchText.forEvent(event),
                ),
            )
            val existing = db.conversations().get(conversationId)
            if (existing == null) {
                val user = event as? AgentEvent.UserMessage
                val title = user?.let { TitlePrompt.provisional(it.text, it.images.isNotEmpty()) } ?: "新对话"
                db.conversations().upsert(ConversationEntity(conversationId, title, event.at, event.at, TitleState.PROVISIONAL))
            } else {
                db.conversations().touch(conversationId, event.at)
            }
        }
    }

    suspend fun deleteAfter(conversationId: String, at: Long) = db.events().deleteAfter(conversationId, at)

    /** Drops [at] and everything after it (edit-and-resend replaces that user message). */
    suspend fun deleteFrom(conversationId: String, at: Long) = db.events().deleteFrom(conversationId, at)

    suspend fun delete(conversationId: String) {
        val images = load(conversationId).filterIsInstance<AgentEvent.UserMessage>().flatMap { it.images }
        db.withTransaction {
            db.events().deleteConversation(conversationId)
            db.pending().delete(conversationId)
            db.conversations().delete(conversationId)
        }
        if (images.isNotEmpty()) onImagesDropped(images)
    }

    /** A title the user typed; auto-titling never overrides it. */
    suspend fun rename(conversationId: String, title: String) {
        val t = title.trim().take(40)
        if (t.isNotEmpty()) db.conversations().setTitle(conversationId, t, TitleState.USER)
    }

    /** Model-generated title; only replaces a provisional one. */
    suspend fun setAutoTitle(conversationId: String, title: String) {
        val c = db.conversations().get(conversationId) ?: return
        if (c.titleState == TitleState.PROVISIONAL) db.conversations().setTitle(conversationId, title, TitleState.AUTO)
    }

    suspend fun search(query: String, limit: Int = 60): List<MessageHit> {
        val match = FtsQuery.build(query) ?: return emptyList()
        val rows = try {
            db.events().search(match, limit)
        } catch (e: Exception) {
            emptyList() // A malformed MATCH can't come out of FtsQuery, but never crash the search box.
        }
        return rows.map { r ->
            val text = (EventCodec.decode(r.kind, r.json) as? AgentEvent.UserMessage)?.text
                ?: (EventCodec.decode(r.kind, r.json) as? AgentEvent.AssistantMessage)?.text.orEmpty()
            MessageHit(r.conversationId, r.title, r.eventId, r.at, r.kind == "user", Snippet.around(text, query))
        }
    }

    suspend fun savePending(conversationId: String, text: String, reasoning: String) =
        db.pending().upsert(PendingReplyEntity(conversationId, text, reasoning, System.currentTimeMillis()))

    suspend fun pending(conversationId: String): PendingReply? =
        db.pending().get(conversationId)?.let { PendingReply(it.text, it.reasoning) }

    suspend fun clearPending(conversationId: String) = db.pending().delete(conversationId)
}

class RoomGrantStore(private val db: HeliumDatabase) : GrantStore {
    override suspend fun active(tool: String, target: String, conversationId: String, now: Long): Grant? =
        db.grants().forTool(tool).map { it.toGrant() }.firstOrNull { it.covers(tool, target, conversationId, now) }

    override suspend fun add(grant: Grant) {
        db.grants().insert(GrantEntity(0, grant.tool, grant.target, grant.scope.name, grant.conversationId, grant.expiresAt, grant.createdAt))
    }

    fun observeAll(): Flow<List<GrantEntity>> = db.grants().observeAll()

    /** Takes effect on the very next call: [active] always reads the table, nothing is cached. */
    suspend fun revoke(id: Long) = db.grants().delete(id)
    suspend fun revokeAll() = db.grants().deleteAll()
    suspend fun purgeExpired() = db.grants().purgeExpired(System.currentTimeMillis())

    private fun GrantEntity.toGrant() = Grant(tool, target, runCatching { GrantScope.valueOf(scope) }.getOrDefault(GrantScope.ONCE), conversationId, expiresAt, id, createdAt)
}

class AuditRepository(private val db: HeliumDatabase) : AuditSink {
    override suspend fun record(entry: AuditRecord) {
        db.audit().insert(
            AuditEntity(
                0, System.currentTimeMillis(), entry.conversationId, entry.kind, entry.tool, entry.target, entry.detail.take(2000),
                entry.risk?.name, entry.tainted,
            ),
        )
    }

    fun observeRecent(limit: Int = 200) = db.audit().observeRecent(limit)
    fun observeCountSince(since: Long) = db.audit().observeCountSince(since)
    fun observeTools() = db.audit().observeTools()

    /** Date range in SQL, tool / outcome in [AuditFilter] (the same code the export uses). */
    fun observe(filter: AuditFilter, limit: Int = 500): Flow<List<AuditEntity>> =
        db.audit().observeBetween(filter.from ?: 0L, filter.to ?: Long.MAX_VALUE, 20_000).map { filter.apply(it).take(limit) }

    suspend fun all() = db.audit().all()

    suspend fun export(filter: AuditFilter, now: Long, zone: java.time.ZoneId): String =
        AuditExport.toJson(filter.apply(db.audit().between(filter.from ?: 0L, filter.to ?: Long.MAX_VALUE)), filter, now, zone)
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
                request.tainted,
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
        val offered = db.approvals().get(requestId)?.scopes?.split(',')?.mapNotNull { runCatching { GrantScope.valueOf(it) }.getOrNull() }.orEmpty()
        db.approvals().decide(requestId, if (d == null) "EXPIRED" else if (approved) "APPROVED" else "REJECTED", System.currentTimeMillis())
        return d?.complete(AgentEvent.ApprovalDecision(requestId, approved, if (approved) RiskPolicy.clamp(offered, scope) else null)) ?: false
    }
}

class TaskRepository(private val db: HeliumDatabase) {
    fun observeAll(): Flow<List<ScheduledTaskEntity>> = db.tasks().observeAll()
    suspend fun setEnabled(id: String, enabled: Boolean) = db.tasks().setEnabled(id, enabled)
    suspend fun delete(id: String) = db.tasks().delete(id)
}
