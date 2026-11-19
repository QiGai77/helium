package tech.qigai.helium.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConversationEntity::class, EventEntity::class, MemoryEntity::class, ForgetRuleEntity::class,
        GrantEntity::class, AuditEntity::class, ApprovalEntity::class, ScheduledTaskEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class HeliumDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun events(): EventDao
    abstract fun memories(): MemoryDao
    abstract fun grants(): GrantDao
    abstract fun audit(): AuditDao
    abstract fun approvals(): ApprovalDao
    abstract fun tasks(): TaskDao

    companion object {
        fun open(context: Context): HeliumDatabase =
            Room.databaseBuilder(context, HeliumDatabase::class.java, "helium.db").build()
    }
}
