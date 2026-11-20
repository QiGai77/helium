package tech.qigai.helium.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConversationEntity::class, EventEntity::class, MemoryEntity::class, ForgetRuleEntity::class,
        GrantEntity::class, AuditEntity::class, ApprovalEntity::class, ScheduledTaskEntity::class,
        FeedSourceEntity::class, FeedItemEntity::class, GlanceCardEntity::class, WeatherCacheEntity::class,
    ],
    version = 4,
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
    abstract fun glance(): GlanceDao

    companion object {
        /** M1 (chat) owns version 2. Empty placeholder on this branch — replaced on merge. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }

        /** M5 (tasks) owns version 3. Empty placeholder on this branch — replaced on merge. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
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

        fun open(context: Context): HeliumDatabase =
            Room.databaseBuilder(context, HeliumDatabase::class.java, "helium.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
