package tech.qigai.helium.data.db

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The hand-written 1 → 2 migration must produce exactly the schema Room expects. Room exports that schema to
 * app/schemas while compiling (KSP runs before unit tests in CI), so the migration SQL is checked against it here.
 */
class ChatSchemaTest {
    private val schema: JSONObject? by lazy {
        listOf("schemas", "app/schemas").map { File(it, "tech.qigai.helium.data.db.HeliumDatabase/2.json") }
            .firstOrNull { it.isFile }?.let { JSONObject(it.readText()) }
    }

    private fun entity(name: String): JSONObject {
        val arr = schema!!.getJSONObject("database").getJSONArray("entities")
        return (0 until arr.length()).map { arr.getJSONObject(it) }.first { it.getString("tableName") == name }
    }

    private fun sql(e: JSONObject) = e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName"))

    @Test fun pendingRepliesTableMatchesRoom() {
        assumeTrue("schema not exported (no KSP run)", schema != null)
        assertEquals(sql(entity("pending_replies")), ChatSchema.CREATE_PENDING)
    }

    @Test fun ftsTableAndTriggersMatchRoom() {
        assumeTrue("schema not exported (no KSP run)", schema != null)
        val fts = entity(ChatSchema.FTS_TABLE)
        assertEquals(sql(fts), ChatSchema.CREATE_FTS)
        val triggers = fts.getJSONArray("contentSyncTriggers").let { a -> (0 until a.length()).map { a.getString(it) } }
        assertEquals(triggers.sorted(), ChatSchema.ftsTriggers.sorted())
    }

    @Test fun addedColumnsMatchRoomDefinitions() {
        assumeTrue("schema not exported (no KSP run)", schema != null)
        fun field(table: String, col: String): JSONObject {
            val f = entity(table).getJSONArray("fields")
            return (0 until f.length()).map { f.getJSONObject(it) }.first { it.getString("columnName") == col }
        }
        val title = field("conversations", "titleState")
        assertEquals("INTEGER", title.getString("affinity"))
        assertTrue(title.getBoolean("notNull"))
        assertEquals("0", title.getString("defaultValue"))
        val search = field("events", "searchText")
        assertEquals("TEXT", search.getString("affinity"))
        assertTrue(search.getBoolean("notNull"))
        assertEquals("''", search.getString("defaultValue"))
        // The ALTER statements must declare the same type / NOT NULL / default.
        assertTrue(ChatSchema.alterColumns[0].endsWith("`titleState` INTEGER NOT NULL DEFAULT 0"))
        assertTrue(ChatSchema.alterColumns[1].endsWith("`searchText` TEXT NOT NULL DEFAULT ''"))
        // Fresh installs create the column the same way.
        assertTrue(sql(entity("events")).contains("`searchText` TEXT NOT NULL DEFAULT ''"))
        assertTrue(sql(entity("conversations")).contains("`titleState` INTEGER NOT NULL DEFAULT 0"))
    }
}
