package tech.qigai.helium.data.db

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies that [HeliumDatabase.MIGRATIONS] forms a contiguous, gap-free chain from v1 to v6
 * and that every exported Room schema (2.json..6.json) matches the declared migration versions.
 */
class FullMigrationChainTest {

    private fun schemaDir(): File =
        listOf("schemas", "app/schemas")
            .map { File(it, "tech.qigai.helium.data.db.HeliumDatabase") }
            .first { it.exists() }

    @Test
    fun migrationsFormContiguousChainFromV1ToV6() {
        val migrations = HeliumDatabase.MIGRATIONS.sortedBy { it.startVersion }
        assertEquals(5, migrations.size)
        for (i in migrations.indices) {
            val expectedStart = i + 1
            val expectedEnd = i + 2
            assertEquals("startVersion at index $i", expectedStart, migrations[i].startVersion)
            assertEquals("endVersion at index $i", expectedEnd, migrations[i].endVersion)
        }
    }

    @Test
    fun exportedSchemasExistAndMatchVersions() {
        val dir = schemaDir()
        for (v in 2..6) {
            val file = File(dir, "$v.json")
            assertTrue("Missing exported schema ${file.path}", file.exists())
            val db = JSONObject(file.readText()).getJSONObject("database")
            assertEquals(v, db.getInt("version"))
        }
    }
}
