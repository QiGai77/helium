package tech.qigai.helium.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

/**
 * MIGRATION_2_3 must produce exactly the schema Room exported for version 3 (app/schemas/…/3.json), otherwise
 * Room's validation crashes on upgrade. Runs the migration against a recording fake database.
 */
class MigrationTest {
    private fun record(): Pair<SupportSQLiteDatabase, MutableList<String>> {
        val sql = mutableListOf<String>()
        val db = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SupportSQLiteDatabase::class.java)) { _, m, args ->
            if (m.name == "execSQL") sql += args[0] as String
            null
        } as SupportSQLiteDatabase
        return db to sql
    }

    private fun schema(): JSONObject {
        val f = listOf("schemas", "app/schemas").map { File(it, "tech.qigai.helium.data.db.HeliumDatabase/3.json") }.first { it.exists() }
        return JSONObject(f.readText()).getJSONObject("database")
    }

    private fun entity(name: String): JSONObject {
        val arr = schema().getJSONArray("entities")
        return (0 until arr.length()).map { arr.getJSONObject(it) }.first { it.getString("tableName") == name }
    }

    @Test fun taskRunsTableMatchesExport() {
        val (db, sql) = record()
        HeliumDatabase.MIGRATION_2_3.migrate(db)
        val runs = entity("task_runs")
        assertTrue(sql.contains(runs.getString("createSql").replace("\${TABLE_NAME}", "task_runs")))
        val idx = runs.getJSONArray("indices").getJSONObject(0).getString("createSql").replace("\${TABLE_NAME}", "task_runs")
        assertTrue(sql.contains(idx))
    }

    @Test fun addedColumnsMatchExport() {
        val (db, sql) = record()
        HeliumDatabase.MIGRATION_2_3.migrate(db)
        val create = entity("scheduled_tasks").getString("createSql")
        val alters = sql.filter { it.startsWith("ALTER TABLE `scheduled_tasks` ADD COLUMN ") }
        assertEquals(5, alters.size)
        alters.forEach { a ->
            val def = a.removePrefix("ALTER TABLE `scheduled_tasks` ADD COLUMN ").removeSuffix(" DEFAULT NULL")
            assertTrue("$def not in $create", create.contains(def))
        }
    }

    @Test fun versionIsThree() {
        assertEquals(3, schema().getInt("version"))
        assertEquals(3, HeliumDatabase.MIGRATION_2_3.endVersion)
        assertEquals(2, HeliumDatabase.MIGRATION_1_2.endVersion)
    }
}
