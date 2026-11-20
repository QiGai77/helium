package tech.qigai.helium.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.qigai.helium.data.backup.BackupCrypto
import tech.qigai.helium.data.backup.BackupException
import tech.qigai.helium.data.backup.BackupPayload

class BackupPayloadTest {
    private fun sampleTables(): Map<String, List<Map<String, Any?>>> {
        val tables = LinkedHashMap<String, List<Map<String, Any?>>>()
        for (t in BackupPayload.TABLES) tables[t] = emptyList()
        tables["conversations"] = listOf(
            mapOf("id" to "c1", "title" to "早报", "createdAt" to 1L, "updatedAt" to 2L, "titleState" to 1L),
        )
        tables["memories"] = listOf(
            mapOf("id" to 1L, "text" to "对花生过敏", "domain" to "饮食", "inferred" to 0L, "source" to "chat", "createdAt" to 3L, "updatedAt" to 0L, "searchText" to "对 花 生 过 敏"),
            mapOf("id" to 2L, "text" to null, "domain" to "其他", "inferred" to 1L, "source" to "manual", "createdAt" to 4L, "updatedAt" to 0L, "searchText" to ""),
        )
        tables["grants"] = listOf(
            mapOf("id" to 1L, "tool" to "sms_send", "target" to "张三", "scope" to "ONCE", "conversationId" to "c1", "expiresAt" to null, "createdAt" to 5L),
        )
        tables["events"] = listOf(
            mapOf("id" to 1L, "conversationId" to "c1", "kind" to "user", "json" to """{"text":"你好"}""", "at" to 6L, "searchText" to "你 好"),
        )
        return tables
    }

    @Test fun `build then parse round-trips every table`() {
        val json = BackupPayload.build(sampleTables(), mapOf("provider" to "GEMINI"), 1_700_000_000_000L, "0.3.0")
        val parsed = BackupPayload.parse(json)
        assertEquals("0.3.0", parsed.appVersion)
        assertEquals(1_700_000_000_000L, parsed.exportedAt)
        assertEquals(mapOf("provider" to "GEMINI"), parsed.settings)
        assertEquals(5, parsed.totalRows)
        assertEquals("早报", parsed.tables.getValue("conversations")[0]["title"])
        assertEquals("对花生过敏", parsed.tables.getValue("memories")[0]["text"])
        assertNull(parsed.tables.getValue("memories")[1]["text"])
        assertNull(parsed.tables.getValue("grants")[0]["expiresAt"])
        assertEquals("张三", parsed.tables.getValue("grants")[0]["target"])
        assertEquals(mapOf("conversations" to 1, "memories" to 2, "grants" to 1, "events" to 1), parsed.counts.filterValues { it > 0 })
    }

    @Test fun `blob columns survive as base64`() {
        val tables = LinkedHashMap<String, List<Map<String, Any?>>>()
        for (t in BackupPayload.TABLES) tables[t] = emptyList()
        val blob = byteArrayOf(0, 1, 2, 127, -1, -128)
        tables["weather_cache"] = listOf(mapOf("id" to 1L, "json" to blob))
        val parsed = BackupPayload.parse(BackupPayload.build(tables, emptyMap(), 0L, "0.3.0"))
        val restored = parsed.tables.getValue("weather_cache")[0]["json"] as ByteArray
        assertEquals(blob.toList(), restored.toList())
    }

    @Test fun `empty database round-trips`() {
        val tables = BackupPayload.TABLES.associateWith { emptyList<Map<String, Any?>>() }
        val parsed = BackupPayload.parse(BackupPayload.build(tables, emptyMap(), 0L, "0.3.0"))
        assertEquals(0, parsed.totalRows)
        assertEquals(BackupPayload.TABLES.size, parsed.tables.size)
    }

    @Test fun `full crypto plus payload round-trip`() {
        val json = BackupPayload.build(sampleTables(), emptyMap(), 0L, "0.3.0")
        val enc = BackupCrypto.encrypt(json.toByteArray(Charsets.UTF_8), "backup-pass".toCharArray())
        val parsed = BackupPayload.parse(String(BackupCrypto.decrypt(enc, "backup-pass".toCharArray()), Charsets.UTF_8))
        assertEquals(5, parsed.totalRows)
    }

    @Test fun `wrong format is rejected`() {
        assertThrows(BackupException::class.java) {
            BackupPayload.parse("""{"format":"something-else","version":1,"tables":{}}""")
        }
    }

    @Test fun `future version is rejected`() {
        val json = BackupPayload.build(sampleTables(), emptyMap(), 0L, "0.3.0")
            .replace("\"version\":1", "\"version\":99")
        assertThrows(BackupException::class.java) { BackupPayload.parse(json) }
    }

    @Test fun `missing table is rejected`() {
        val tables = sampleTables().toMutableMap().apply { remove("memories") }
        val json = BackupPayload.build(tables, emptyMap(), 0L, "0.3.0")
        assertThrows(BackupException::class.java) { BackupPayload.parse(json) }
    }

    @Test fun `garbage json is rejected`() {
        assertThrows(BackupException::class.java) { BackupPayload.parse("not json at all") }
        assertThrows(BackupException::class.java) { BackupPayload.parse("") }
        assertThrows(BackupException::class.java) { BackupPayload.parse("{}") }
    }

    @Test fun `payload never contains a key field`() {
        val json = BackupPayload.build(sampleTables(), mapOf("provider" to "GEMINI"), 0L, "0.3.0")
        assertTrue(!json.contains("apiKey") && !json.contains("key.") && !json.contains("token"))
    }
}
