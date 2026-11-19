package tech.qigai.helium.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** One row per [tech.qigai.helium.agent.AgentEvent]; `json` is [tech.qigai.helium.agent.EventCodec] output. */
@Entity(tableName = "events", indices = [Index("conversationId"), Index("kind")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val kind: String,
    val json: String,
    val at: Long,
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val domain: String,
    val inferred: Boolean,
    /** "chat" when the agent saved it, "manual" when the user typed it. */
    val source: String,
    val createdAt: Long,
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

/** Append-only audit trail: every ask, approval, rejection, denial, run and error. */
@Entity(tableName = "audit", indices = [Index("at")])
data class AuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val conversationId: String,
    val kind: String,
    val tool: String,
    val target: String,
    val detail: String,
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
)

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val prompt: String,
    /** ONCE / DAILY / WEEKLY */
    val kind: String,
    /** "HH:mm" local time. */
    val timeOfDay: String,
    /** Bitmask Mon=1 … Sun=64, for WEEKLY. */
    val weekdays: Int,
    val enabled: Boolean,
    val nextRunAt: Long?,
    val lastStatus: String?,
    val createdAt: Long,
)
