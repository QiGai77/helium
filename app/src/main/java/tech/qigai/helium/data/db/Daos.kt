package tech.qigai.helium.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
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

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: String, title: String)

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

    @Query("DELETE FROM events WHERE conversationId = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("SELECT DISTINCT conversationId FROM events WHERE kind = 'user' AND json LIKE '%' || :q || '%' ORDER BY id DESC LIMIT 50")
    suspend fun searchConversations(q: String): List<String>
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

    @Query("SELECT * FROM scheduled_tasks ORDER BY createdAt")
    suspend fun all(): List<ScheduledTaskEntity>

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id")
    suspend fun get(id: String): ScheduledTaskEntity?

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id")
    fun observe(id: String): Flow<ScheduledTaskEntity?>

    @Upsert
    suspend fun upsert(t: ScheduledTaskEntity)

    @Query("UPDATE scheduled_tasks SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    suspend fun delete(id: String)

    /** Compare-and-set on the pending fire time: exactly one trigger (alarm, backup work, boot catch-up) wins. */
    @Query("UPDATE scheduled_tasks SET nextRunAt = :next WHERE id = :id AND enabled = 1 AND nextRunAt = :expected")
    suspend fun claim(id: String, expected: Long, next: Long?): Int

    @Query("UPDATE scheduled_tasks SET nextRunAt = :next WHERE id = :id")
    suspend fun setNext(id: String, next: Long?)

    @Query("UPDATE scheduled_tasks SET lastStatus = :status, failCount = :failCount, lastRunAt = :at WHERE id = :id")
    suspend fun setOutcome(id: String, status: String, failCount: Int, at: Long)

    @Insert
    suspend fun insertRun(r: TaskRunEntity): Long

    @Update
    suspend fun updateRun(r: TaskRunEntity)

    @Query("SELECT * FROM task_runs WHERE id = :id")
    suspend fun run(id: Long): TaskRunEntity?

    @Query("SELECT * FROM task_runs WHERE taskId = :taskId ORDER BY startedAt DESC LIMIT :limit")
    fun observeRuns(taskId: String, limit: Int): Flow<List<TaskRunEntity>>

    @Query("SELECT * FROM task_runs WHERE id = :id")
    fun observeRun(id: Long): Flow<TaskRunEntity?>

    @Query("DELETE FROM task_runs WHERE taskId = :taskId")
    suspend fun deleteRuns(taskId: String)

    /** Runs left RUNNING by a process that died. */
    @Query("UPDATE task_runs SET status = 'FAILED', error = :reason, finishedAt = :at WHERE status = 'RUNNING' AND startedAt < :before")
    suspend fun failStale(before: Long, at: Long, reason: String): Int

    /** Keep the history bounded. */
    @Query("DELETE FROM task_runs WHERE taskId = :taskId AND id NOT IN (SELECT id FROM task_runs WHERE taskId = :taskId ORDER BY startedAt DESC LIMIT :keep)")
    suspend fun trimRuns(taskId: String, keep: Int)
}
