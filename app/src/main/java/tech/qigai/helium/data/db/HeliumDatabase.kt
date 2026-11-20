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
        FeedSourceEntity::class, FeedItemEntity::class, GlanceCardEntity::class, WeatherCacheEntity::class,
    ],
    version = 4,
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
    abstract fun glance(): GlanceDao

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

        /** M6 速览: feed sources/items, custom cards and the weather cache, exactly as Room generates them. */
        val GLANCE_V4_SQL = listOf(
            "CREATE TABLE IF NOT EXISTS `feed_sources` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, `input` TEXT NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `siteUrl` TEXT, `enabled` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, `lastFetchedAt` INTEGER, `lastError` TEXT)",
            "CREATE TABLE IF NOT EXISTS `feed_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourceId` INTEGER NOT NULL, `guid` TEXT NOT NULL, `title` TEXT NOT NULL, `link` TEXT NOT NULL, `summary` TEXT NOT NULL, `publishedAt` INTEGER NOT NULL, `fetchedAt` INTEGER NOT NULL, `reason` TEXT)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_feed_items_sourceId_guid` ON `feed_items` (`sourceId`, `guid`)",
            "CREATE INDEX IF NOT EXISTS `index_feed_items_publishedAt` ON `feed_items` (`publishedAt`)",
            "CREATE TABLE IF NOT EXISTS `glance_cards` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `prompt` TEXT NOT NULL, `definition` TEXT NOT NULL, `intervalMinutes` INTEGER NOT NULL, `size` TEXT NOT NULL, `valuesJson` TEXT, `lastRefreshAt` INTEGER, `nextRefreshAt` INTEGER NOT NULL, `lastError` TEXT, `position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `weather_cache` (`id` INTEGER NOT NULL, `place` TEXT NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, `placeSource` TEXT NOT NULL, `json` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                GLANCE_V4_SQL.forEach { db.execSQL(it) }
            }
        }

        val MIGRATIONS = arrayOf<Migration>(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

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
