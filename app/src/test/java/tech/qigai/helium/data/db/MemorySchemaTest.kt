package tech.qigai.helium.data.db

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** MIGRATION_4_5 must create exactly what Room expects for version 5 (checked against the exported 5.json). */
class MemorySchemaTest {
    private val schema: JSONObject? by lazy {
        listOf("schemas", "app/schemas").map { File(it, "tech.qigai.helium.data.db.HeliumDatabase/5.json") }
            .firstOrNull { it.isFile }?.let { JSONObject(it.readText()) }
    }

    private fun entity(name: String): JSONObject {
        val arr = schema!!.getJSONObject("database").getJSONArray("entities")
        return (0 until arr.length()).map { arr.getJSONObject(it) }.first { it.getString("tableName") == name }
    }

    private fun sql(e: JSONObject) = e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName"))

    @Test fun `new tables and indices match room`() {
        assumeTrue("schema 5.json not exported yet", schema != null)
        assertEquals(5, schema!!.getJSONObject("database").getInt("version"))
        assertEquals(sql(entity("memory_candidates")), MemorySchema.CREATE_CANDIDATES)
        assertEquals(sql(entity("skills")), MemorySchema.CREATE_SKILLS)
        val idx = entity("memory_candidates").getJSONArray("indices")
        val expected = (0 until idx.length()).map { idx.getJSONObject(it) }.map {
            it.getString("createSql").replace("\${TABLE_NAME}", "memory_candidates").replace("\${INDEX_NAME}", it.getString("name"))
        }
        assertEquals(expected.sorted(), MemorySchema.candidateIndices.sorted())
    }

    @Test fun `fts table and triggers match room`() {
        assumeTrue("schema 5.json not exported yet", schema != null)
        val fts = entity(MemorySchema.FTS_TABLE)
        assertEquals(sql(fts), MemorySchema.CREATE_FTS)
        val triggers = fts.getJSONArray("contentSyncTriggers").let { a -> (0 until a.length()).map { a.getString(it) } }
        assertEquals(triggers.sorted(), MemorySchema.ftsTriggers.sorted())
    }

    @Test fun `added memory columns match a fresh install`() {
        assumeTrue("schema 5.json not exported yet", schema != null)
        val create = sql(entity("memories"))
        assertTrue(create, create.contains("`updatedAt` INTEGER NOT NULL DEFAULT 0"))
        assertTrue(create, create.contains("`searchText` TEXT NOT NULL DEFAULT ''"))
        MemorySchema.alterColumns.forEach { a -> assertTrue(a, create.contains(a.substringAfter("ADD COLUMN "))) }
    }

    @Test fun `migration statements are complete and ordered`() {
        val all = MemorySchema.migration4to5
        assertTrue(all.indexOf(MemorySchema.CREATE_FTS) > all.indexOf(MemorySchema.alterColumns[1]))
        assertTrue(all.containsAll(MemorySchema.ftsTriggers))
        assertTrue(all.any { it.contains("'家与健康'") })
        assertEquals(4, HeliumDatabase.MIGRATION_4_5.startVersion)
        assertEquals(5, HeliumDatabase.MIGRATION_4_5.endVersion)
    }
}
