package tech.qigai.helium.data.db

/**
 * SQL for the chat tables added in DB version 2 (M1). Written exactly as Room generates it for the entities in
 * Entities.kt; `ChatSchemaTest` checks every statement against the exported `schemas/.../2.json` in CI.
 */
object ChatSchema {
    const val FTS_TABLE = "event_fts"

    val alterColumns = listOf(
        "ALTER TABLE `conversations` ADD COLUMN `titleState` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `events` ADD COLUMN `searchText` TEXT NOT NULL DEFAULT ''",
    )

    const val CREATE_PENDING =
        "CREATE TABLE IF NOT EXISTS `pending_replies` (`conversationId` TEXT NOT NULL, `text` TEXT NOT NULL, `reasoning` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`conversationId`))"

    const val CREATE_FTS =
        "CREATE VIRTUAL TABLE IF NOT EXISTS `event_fts` USING FTS4(`searchText` TEXT NOT NULL, content=`events`)"

    val ftsTriggers = listOf(
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_event_fts_BEFORE_UPDATE BEFORE UPDATE ON `events` BEGIN DELETE FROM `event_fts` WHERE `docid`=OLD.`rowid`; END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_event_fts_BEFORE_DELETE BEFORE DELETE ON `events` BEGIN DELETE FROM `event_fts` WHERE `docid`=OLD.`rowid`; END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_event_fts_AFTER_UPDATE AFTER UPDATE ON `events` BEGIN INSERT INTO `event_fts`(`docid`, `searchText`) VALUES (NEW.`rowid`, NEW.`searchText`); END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_event_fts_AFTER_INSERT AFTER INSERT ON `events` BEGIN INSERT INTO `event_fts`(`docid`, `searchText`) VALUES (NEW.`rowid`, NEW.`searchText`); END",
    )

    /** Statements for 1 → 2, in order. The searchText backfill of existing rows runs after them. */
    val migration1to2: List<String> = alterColumns + CREATE_PENDING + CREATE_FTS + ftsTriggers
}
