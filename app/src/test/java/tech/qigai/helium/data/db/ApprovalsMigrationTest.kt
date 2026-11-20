package tech.qigai.helium.data.db

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * MIGRATION_5_6 only adds columns and an index. The test checks those against Room's exported 6.json (KSP writes
 * it while compiling, before unit tests run): column types, nullability, defaults and the index SQL.
 */
class ApprovalsMigrationTest {
    private val schema = listOf("schemas", "app/schemas")
        .map { File(it, "tech.qigai.helium.data.db.HeliumDatabase/6.json") }
        .firstOrNull { it.exists() }

    private fun entity(db: JSONObject, table: String): JSONObject {
        val es = db.getJSONArray("entities")
        for (i in 0 until es.length()) if (es.getJSONObject(i).getString("tableName") == table) return es.getJSONObject(i)
        error("no $table")
    }

    private fun field(e: JSONObject, column: String): JSONObject {
        val fs = e.getJSONArray("fields")
        for (i in 0 until fs.length()) if (fs.getJSONObject(i).getString("columnName") == column) return fs.getJSONObject(i)
        error("no $column")
    }

    @Test fun `migration 5 to 6 matches the exported room schema`() {
        assumeTrue("schema 6.json not exported yet", schema != null)
        val db = JSONObject(schema!!.readText()).getJSONObject("database")
        assertEquals(6, db.getInt("version"))
        val audit = entity(db, "audit")
        field(audit, "risk").let { assertEquals("TEXT", it.getString("affinity")); assertFalse(it.getBoolean("notNull")); assertFalse(it.has("defaultValue")) }
        field(audit, "tainted").let { assertEquals("INTEGER", it.getString("affinity")); assertTrue(it.getBoolean("notNull")); assertEquals("0", it.getString("defaultValue")) }
        val ix = audit.getJSONArray("indices")
        val idxSql = (0 until ix.length()).map { ix.getJSONObject(it) }.first { it.getString("name") == "index_audit_tool" }
            .getString("createSql").replace("\${TABLE_NAME}", "audit")
        assertTrue(HeliumDatabase.APPROVALS_V6_SQL.contains(idxSql))
        field(entity(db, "approvals"), "tainted").let { assertTrue(it.getBoolean("notNull")); assertEquals("0", it.getString("defaultValue")) }
        // createSql of the new tables equals the old one plus the added columns, so ALTER TABLE yields the same table.
        assertTrue(audit.getString("createSql").endsWith("`risk` TEXT, `tainted` INTEGER NOT NULL DEFAULT 0)"))
    }

    @Test fun `statements touch only audit and approvals`() {
        val sql = HeliumDatabase.APPROVALS_V6_SQL
        assertEquals(4, sql.size)
        assertTrue(sql.all { it.contains("`audit`") || it.contains("`approvals`") })
        assertEquals(2, sql.count { it.contains("ADD COLUMN `tainted` INTEGER NOT NULL DEFAULT 0") })
    }
}
