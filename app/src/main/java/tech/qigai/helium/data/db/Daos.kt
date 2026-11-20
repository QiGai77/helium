package tech.qigai.helium.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latest(): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: String): ConversationEntity?

    @Upsert
    suspend fun upsert(c: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, titleState = :state WHERE id = :id")
    suspend fun setTitle(id: String, title: String, state: Int)

    @Query("UPDATE conversations SET updatedAt = :at WHERE id = :id")
    suspend fun touch(id: String, at: Long)

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun count(): Int

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface EventDao {
    @Insert
    suspend fun insert(e: EventEntity): Long

    @Query("SELECT * FROM events WHERE conversationId = :conversationId ORDER BY id")
    suspend fun forConversation(conversationId: String): List<EventEntity>

    @Query("SELECT * FROM events WHERE conversationId = :conversationId ORDER BY id")
    fun observe(conversationId: String): Flow<List<EventEntity>>

    @Query("DELETE FROM events WHERE conversationId = :conversationId AND at > :after")
    suspend fun deleteAfter(conversationId: String, after: Long)

    @Query("DELETE FROM events WHERE conversationId = :conversationId AND at >= :from")
    suspend fun deleteFrom(conversationId: String, from: Long)

    @Query("DELETE FROM events WHERE conversationId = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    /** Full-text hits over user and assistant messages, newest first. [match] comes from FtsQuery.build. */
    @Query(
        """
        SELECT e.id AS eventId, e.conversationId AS conversationId, e.kind AS kind, e.json AS json, e.at AS at, c.title AS title
        FROM event_fts
        JOIN events AS e ON e.id = event_fts.rowid
        JOIN conversations AS c ON c.id = e.conversationId
        WHERE event_fts MATCH :match
        ORDER BY e.at DESC
        LIMIT :limit
        """,
    )
    suspend fun search(match: String, limit: Int): List<MessageHitRow>
}

data class MessageHitRow(
    val eventId: Long,
    val conversationId: String,
    val kind: String,
    val json: String,
    val at: Long,
    val title: String,
)

@Dao
interface PendingReplyDao {
    @Upsert
    suspend fun upsert(p: PendingReplyEntity)

    @Query("SELECT * FROM pending_replies WHERE conversationId = :conversationId")
    suspend fun get(conversationId: String): PendingReplyEntity?

    @Query("DELETE FROM pending_replies WHERE conversationId = :conversationId")
    suspend fun delete(conversationId: String)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE text LIKE '%' || :q || '%' OR domain = :q ORDER BY createdAt DESC LIMIT :limit")
    suspend fun search(q: String, limit: Int): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun get(id: Long): MemoryEntity?

    @Insert
    suspend fun insert(m: MemoryEntity): Long

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM memories")
    suspend fun clear()

    @Insert
    suspend fun insertRule(r: ForgetRuleEntity): Long

    @Query("SELECT * FROM forget_rules ORDER BY createdAt DESC")
    suspend fun rules(): List<ForgetRuleEntity>

    @Query("SELECT * FROM forget_rules ORDER BY createdAt DESC")
    fun observeRules(): Flow<List<ForgetRuleEntity>>

    @Query("DELETE FROM forget_rules WHERE id = :id")
    suspend fun deleteRule(id: Long)
}

@Dao
interface GrantDao {
    @Query("SELECT * FROM grants ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GrantEntity>>

    @Query("SELECT * FROM grants WHERE tool = :tool")
    suspend fun forTool(tool: String): List<GrantEntity>

    @Insert
    suspend fun insert(g: GrantEntity): Long

    @Query("DELETE FROM grants WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM grants WHERE expiresAt IS NOT NULL AND expiresAt < :now")
    suspend fun purgeExpired(now: Long)
}

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(a: AuditEntity): Long

    @Query("SELECT * FROM audit ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditEntity>>

    @Query("SELECT * FROM audit ORDER BY id")
    suspend fun all(): List<AuditEntity>

    @Query("SELECT COUNT(*) FROM audit WHERE at >= :since")
    fun observeCountSince(since: Long): Flow<Int>
}

@Dao
interface ApprovalDao {
    @Insert
    suspend fun insert(a: ApprovalEntity)

    @Query("SELECT * FROM approvals WHERE status = 'PENDING' ORDER BY createdAt")
    fun observePending(): Flow<List<ApprovalEntity>>

    @Query("UPDATE approvals SET status = :status, decidedAt = :at WHERE requestId = :id AND status = 'PENDING'")
    suspend fun decide(id: String, status: String, at: Long)

    @Query("UPDATE approvals SET status = 'EXPIRED', decidedAt = :at WHERE status = 'PENDING'")
    suspend fun expireAll(at: Long)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM scheduled_tasks ORDER BY createdAt")
    fun observeAll(): Flow<List<ScheduledTaskEntity>>

    @Upsert
    suspend fun upsert(t: ScheduledTaskEntity)

    @Query("UPDATE scheduled_tasks SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    suspend fun delete(id: String)
}
