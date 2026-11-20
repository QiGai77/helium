package tech.qigai.helium.data.db

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * MIGRATION_3_4 must create exactly what Room expects for version 4. KSP exports the schema JSON while
 * compiling, before unit tests run, so the test compares the hand-written SQL with Room's own createSql.
 */
class GlanceMigrationTest {
    private val schema = listOf("schemas", "app/schemas")
        .map { File(it, "tech.qigai.helium.data.db.HeliumDatabase/4.json") }
        .firstOrNull { it.exists() }

    @Test fun `migration 3 to 4 matches the exported room schema`() {
        assumeTrue("schema 4.json not exported yet", schema != null)
        val db = JSONObject(schema!!.readText()).getJSONObject("database")
        assertEquals(4, db.getInt("version"))
        val entities = db.getJSONArray("entities")
        val expected = mutableListOf<String>()
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val table = e.getString("tableName")
            if (table !in setOf("feed_sources", "feed_items", "glance_cards", "weather_cache")) continue
            expected += e.getString("createSql").replace("\${TABLE_NAME}", table)
            val idx = e.optJSONArray("indices")
            if (idx != null) for (j in 0 until idx.length()) {
                val ix = idx.getJSONObject(j)
                expected += ix.getString("createSql").replace("\${TABLE_NAME}", table).replace("\${INDEX_NAME}", ix.getString("name"))
            }
        }
        assertEquals(6, expected.size)
        assertEquals(expected.sorted(), HeliumDatabase.GLANCE_V4_SQL.sorted())
    }

    @Test fun `migration statements cover all four tables`() {
        val sql = HeliumDatabase.GLANCE_V4_SQL.joinToString("\n")
        listOf("feed_sources", "feed_items", "glance_cards", "weather_cache").forEach { assertTrue(it, sql.contains("`$it`")) }
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_feed_items_sourceId_guid`"))
    }
}
