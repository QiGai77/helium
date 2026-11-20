package tech.qigai.helium.data.db

/**
 * SQL for the memory / skill tables added in DB version 5 (M4). Written exactly as Room generates it for the
 * entities in Entities.kt; `MemorySchemaTest` checks every statement against the exported `schemas/.../5.json`.
 */
object MemorySchema {
    const val FTS_TABLE = "memory_fts"

    val alterColumns = listOf(
        "ALTER TABLE `memories` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `memories` ADD COLUMN `searchText` TEXT NOT NULL DEFAULT ''",
    )

    /** v4 domains → v5 groups (same mapping as MemoryDomains.normalize). */
    val remapDomains = listOf(
        "UPDATE `memories` SET `domain` = '家庭' WHERE `domain` = '人'",
        "UPDATE `memories` SET `domain` = '健康' WHERE `domain` = '家与健康'",
        "UPDATE `memories` SET `updatedAt` = `createdAt`",
    )

    const val CREATE_FTS =
        "CREATE VIRTUAL TABLE IF NOT EXISTS `memory_fts` USING FTS4(`searchText` TEXT NOT NULL, content=`memories`)"

    val ftsTriggers = listOf(
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memory_fts_BEFORE_UPDATE BEFORE UPDATE ON `memories` BEGIN DELETE FROM `memory_fts` WHERE `docid`=OLD.`rowid`; END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memory_fts_BEFORE_DELETE BEFORE DELETE ON `memories` BEGIN DELETE FROM `memory_fts` WHERE `docid`=OLD.`rowid`; END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memory_fts_AFTER_UPDATE AFTER UPDATE ON `memories` BEGIN INSERT INTO `memory_fts`(`docid`, `searchText`) VALUES (NEW.`rowid`, NEW.`searchText`); END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memory_fts_AFTER_INSERT AFTER INSERT ON `memories` BEGIN INSERT INTO `memory_fts`(`docid`, `searchText`) VALUES (NEW.`rowid`, NEW.`searchText`); END",
    )

    const val CREATE_CANDIDATES =
        "CREATE TABLE IF NOT EXISTS `memory_candidates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `conversationId` TEXT NOT NULL, `text` TEXT NOT NULL, `domain` TEXT NOT NULL, `inferred` INTEGER NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"

    val candidateIndices = listOf(
        "CREATE INDEX IF NOT EXISTS `index_memory_candidates_conversationId` ON `memory_candidates` (`conversationId`)",
        "CREATE INDEX IF NOT EXISTS `index_memory_candidates_status` ON `memory_candidates` (`status`)",
    )

    const val CREATE_SKILLS =
        "CREATE TABLE IF NOT EXISTS `skills` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `triggers` TEXT NOT NULL, `body` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"

    /** Statements for 4 → 5, in order. The searchText backfill (which fills the index via the update trigger) runs after them. */
    val migration4to5: List<String> = alterColumns + remapDomains + CREATE_FTS + ftsTriggers + CREATE_CANDIDATES + candidateIndices + CREATE_SKILLS
}
