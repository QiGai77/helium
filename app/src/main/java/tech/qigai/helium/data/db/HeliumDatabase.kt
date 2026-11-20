package tech.qigai.helium.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import tech.qigai.helium.chat.SearchText

@Database(
    entities = [
        ConversationEntity::class, EventEntity::class, MemoryEntity::class, ForgetRuleEntity::class,
        GrantEntity::class, AuditEntity::class, ApprovalEntity::class, ScheduledTaskEntity::class,
        EventFts::class, PendingReplyEntity::class, TaskRunEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class HeliumDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun events(): EventDao
    abstract fun pending(): PendingReplyDao
    abstract fun memories(): MemoryDao
    abstract fun grants(): GrantDao
    abstract fun audit(): AuditDao
    abstract fun approvals(): ApprovalDao
    abstract fun tasks(): TaskDao

    companion object {
        /**
         * Version plan: 2 = M1 chat, 3 = M5 tasks, 4 = M6 glance. Each milestone branch owns one step; the others
         * are added as placeholders on their own branches and replaced on merge.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ChatSchema.migration1to2.forEach(db::execSQL)
                backfillSearchText(db)
            }
        }

        /** M5: scheduled task runner columns + run history. SQL exactly as Room generates it for the entities. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `scheduled_tasks` ADD COLUMN `action` TEXT NOT NULL DEFAULT 'AGENT'")
                db.execSQL("ALTER TABLE `scheduled_tasks` ADD COLUMN `onceAt` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `scheduled_tasks` ADD COLUMN `conversationId` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `scheduled_tasks` ADD COLUMN `failCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `scheduled_tasks` ADD COLUMN `lastRunAt` INTEGER DEFAULT NULL")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `task_runs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `taskId` TEXT NOT NULL, " +
                        "`conversationId` TEXT NOT NULL, `cause` TEXT NOT NULL, `attempt` INTEGER NOT NULL, `scheduledFor` INTEGER, " +
                        "`startedAt` INTEGER NOT NULL, `finishedAt` INTEGER, `status` TEXT NOT NULL, `error` TEXT, `summary` TEXT, `nextRetryAt` INTEGER)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_runs_taskId` ON `task_runs` (`taskId`)")
            }
        }

        val MIGRATIONS = arrayOf<Migration>(MIGRATION_1_2, MIGRATION_2_3)

        /** Fills `events.searchText` for messages written before v2; the AFTER UPDATE trigger indexes them. */
        private fun backfillSearchText(db: SupportSQLiteDatabase) {
            val rows = ArrayList<Pair<Long, String>>()
            db.query("SELECT `id`, `kind`, `json` FROM `events` WHERE `kind` IN ('user', 'assistant')").use { c ->
                while (c.moveToNext()) {
                    val text = SearchText.forEvent(c.getString(1), c.getString(2))
                    if (text.isNotEmpty()) rows += c.getLong(0) to text
                }
            }
            rows.forEach { (id, text) -> db.execSQL("UPDATE `events` SET `searchText` = ? WHERE `id` = ?", arrayOf<Any>(text, id)) }
        }

        fun open(context: Context): HeliumDatabase =
            Room.databaseBuilder(context, HeliumDatabase::class.java, "helium.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
