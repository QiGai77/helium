package tech.qigai.helium.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** [TitleState]: 0 provisional (first line of the first message), 1 model-generated, 2 set by the user. */
    @ColumnInfo(defaultValue = "0") val titleState: Int = TitleState.PROVISIONAL,
)

object TitleState {
    const val PROVISIONAL = 0
    const val AUTO = 1
    const val USER = 2
}

/** One row per [tech.qigai.helium.agent.AgentEvent]; `json` is [tech.qigai.helium.agent.EventCodec] output. */
@Entity(tableName = "events", indices = [Index("conversationId"), Index("kind")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val kind: String,
    val json: String,
    val at: Long,
    /** [tech.qigai.helium.chat.SearchText] form of the message text (CJK split per character); indexed by [EventFts]. */
    @ColumnInfo(defaultValue = "''") val searchText: String = "",
)

/** External-content FTS4 index over `events.searchText`; Room keeps it in sync with triggers. */
@Fts4(contentEntity = EventEntity::class)
@Entity(tableName = "event_fts")
data class EventFts(val searchText: String)

/** Text streamed so far for the reply in flight; survives a killed process so the turn can show "已中断". */
@Entity(tableName = "pending_replies")
data class PendingReplyEntity(
    @PrimaryKey val conversationId: String,
    val text: String,
    val reasoning: String,
    val updatedAt: Long,
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val domain: String,
    val inferred: Boolean,
    /** "chat" when confirmed from a candidate, "manual" when the user typed it. */
    val source: String,
    val createdAt: Long,
    // ---- v5 (M4) ----
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    /** [tech.qigai.helium.chat.SearchText] form of `text`; indexed by [MemoryFts]. */
    @ColumnInfo(defaultValue = "''") val searchText: String = "",
)

/** External-content FTS4 index over `memories.searchText` (v5); Room keeps it in sync with triggers. */
@Fts4(contentEntity = MemoryEntity::class)
@Entity(tableName = "memory_fts")
data class MemoryFts(val searchText: String)

/** A fact extracted after a turn (or proposed by `memory_save`); only becomes a memory when the user confirms. */
@Entity(tableName = "memory_candidates", indices = [Index("conversationId"), Index("status")])
data class MemoryCandidateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val text: String,
    val domain: String,
    val inferred: Boolean,
    /** PENDING / ACCEPTED / DISMISSED ([CandidateStatus]). */
    val status: String,
    val createdAt: Long,
)

object CandidateStatus {
    const val PENDING = "PENDING"
    const val ACCEPTED = "ACCEPTED"
    const val DISMISSED = "DISMISSED"
}

/** 专长: a Markdown instruction sheet (SKILL.md shape: name + description + optional triggers, then the body). */
@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    /** Newline-separated keywords; empty = the name itself. */
    val triggers: String,
    val body: String,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

/** "不再保存" rule created when a memory is forgotten. */
@Entity(tableName = "forget_rules")
data class ForgetRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long,
)

@Entity(tableName = "grants", indices = [Index("tool")])
data class GrantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tool: String,
    val target: String,
    val scope: String,
    val conversationId: String?,
    val expiresAt: Long?,
    val createdAt: Long,
)

/** Append-only audit trail: every ask, approval, rejection, denial, run, error and taint mark. */
@Entity(tableName = "audit", indices = [Index("at"), Index("tool")])
data class AuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val conversationId: String,
    val kind: String,
    val tool: String,
    val target: String,
    val detail: String,
    // ---- v6 (M3) ----
    /** LOW / MEDIUM / HIGH as assessed for this call; null for rows written before v6. */
    val risk: String? = null,
    /** The run had read untrusted content (web_fetch / web_search) when this happened. */
    @ColumnInfo(defaultValue = "0") val tainted: Boolean = false,
)

/** Stored action review (OpenFuse pattern): an approval is a durable record, not a transient dialog. */
@Entity(tableName = "approvals", indices = [Index("status")])
data class ApprovalEntity(
    @PrimaryKey val requestId: String,
    val conversationId: String,
    val tool: String,
    val title: String,
    val detail: String,
    val target: String,
    val risk: String,
    val warning: String?,
    /** Comma-separated GrantScope names offered on the card. */
    val scopes: String,
    /** PENDING / APPROVED / REJECTED / EXPIRED */
    val status: String,
    val createdAt: Long,
    val decidedAt: Long?,
    // ---- v6 (M3) ----
    @ColumnInfo(defaultValue = "0") val tainted: Boolean = false,
)

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** What the agent is asked to do each run (AGENT), or the reminder text (NOTIFY). */
    val prompt: String,
    /** ONCE / DAILY / WEEKLY ([tech.qigai.helium.tasks.TaskKind]). */
    val kind: String,
    /** "HH:mm" local time. */
    val timeOfDay: String,
    /** Bitmask Mon=1 … Sun=64, for WEEKLY. */
    val weekdays: Int,
    val enabled: Boolean,
    /** The pending fire time; claimed (compare-and-set) by whichever trigger runs it first. Null = never again. */
    val nextRunAt: Long?,
    val lastStatus: String?,
    val createdAt: Long,
    // ---- v3 (M5) ----
    /** AGENT / NOTIFY ([tech.qigai.helium.tasks.TaskAction]). */
    @ColumnInfo(defaultValue = "AGENT") val action: String = "AGENT",
    /** ONCE only: the absolute fire time. */
    val onceAt: Long? = null,
    /** The task's own conversation; every run appends to it. */
    val conversationId: String? = null,
    /** Consecutive failures, drives the exponential backoff; reset on success. */
    @ColumnInfo(defaultValue = "0") val failCount: Int = 0,
    val lastRunAt: Long? = null,
)

/** One execution of a scheduled task. Its events live in the task's conversation between startedAt and finishedAt. */
@Entity(tableName = "task_runs", indices = [Index("taskId")])
data class TaskRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    val conversationId: String,
    /** SCHEDULED / RETRY / MANUAL / CATCH_UP / MISSED ([tech.qigai.helium.tasks.RunCause]). */
    val cause: String,
    /** 1 for the first try of a fire, 2+ for retries. */
    val attempt: Int,
    val scheduledFor: Long?,
    val startedAt: Long,
    val finishedAt: Long?,
    /** RUNNING / SUCCESS / FAILED / SKIPPED ([tech.qigai.helium.tasks.RunStatus]). */
    val status: String,
    val error: String?,
    val summary: String?,
    val nextRetryAt: Long?,
)
