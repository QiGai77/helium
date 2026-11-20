package tech.qigai.helium.data.backup

import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * M7 备份载荷：纯 JSON（org.json，Android 内置，无新依赖），与加密层分离，
 * 所以本文件可在普通 JVM 单元测试里完整覆盖往返与损坏拒绝。
 *
 * 结构：
 * ```
 * {"format":"helium-backup","version":1,"appVersion":"0.3.0","exportedAt":<ms>,
 *  "settings":{...仅非敏感偏好，如 provider / 模型名 / Worker 地址...},
 *  "tables":{"conversations":[{列名:值,...}],"events":[...],...}}
 * ```
 * 值类型：null / Boolean / Number / String；BLOB 列写成 {"$blob":"<base64>"}。
 * API Key、云端 token、DeepSeek Key 永远不进备份（它们在 Keystore 保险箱
 * helium_vault 里，卸载即丢），恢复后必须重填，界面上有明确提示。
 *
 * 恢复策略是“整库替换”：每张表先 DELETE 再 INSERT OR REPLACE。FTS 外部
 * 内容表（event_fts / memory_fts）不备份——Room 的触发器会在 INSERT 时重建。
 */
object BackupPayload {
    const val FORMAT = "helium-backup"
    const val VERSION = 1
    const val BLOB_KEY = "\$blob"

    /** 备份里的表。顺序即恢复顺序；外键不存在，顺序不影响正确性。 */
    val TABLES: List<String> = listOf(
        "conversations", "events", "pending_replies",
        "memories", "memory_candidates", "forget_rules", "skills",
        "grants", "audit", "approvals",
        "scheduled_tasks", "task_runs",
        "feed_sources", "feed_items", "glance_cards", "weather_cache",
    )

    data class Parsed(
        val appVersion: String,
        val exportedAt: Long,
        val settings: Map<String, String>,
        val tables: Map<String, List<Map<String, Any?>>>,
    ) {
        /** 每张表的行数，供恢复成功页展示。 */
        val counts: Map<String, Int> get() = tables.mapValues { it.value.size }
        val totalRows: Int get() = tables.values.sumOf { it.size }
    }

    fun build(
        tables: Map<String, List<Map<String, Any?>>>,
        settings: Map<String, String>,
        exportedAt: Long,
        appVersion: String,
    ): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("appVersion", appVersion)
        root.put("exportedAt", exportedAt)
        val settingsJson = JSONObject()
        settings.forEach { (k, v) -> settingsJson.put(k, v) }
        root.put("settings", settingsJson)
        val tablesJson = JSONObject()
        tables.forEach { (name, rows) ->
            val arr = JSONArray()
            rows.forEach { row ->
                val obj = JSONObject()
                row.forEach { (col, value) -> obj.put(col, encodeValue(value)) }
                arr.put(obj)
            }
            tablesJson.put(name, arr)
        }
        root.put("tables", tablesJson)
        return root.toString()
    }

    /** @throws BackupException 版本不符、结构不对或表缺失时抛出。 */
    fun parse(json: String): Parsed {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw BackupException("备份内容损坏（不是有效的 JSON）", e)
        }
        if (root.optString("format") != FORMAT) throw BackupException("不是 Helium 备份文件")
        if (root.optInt("version", -1) != VERSION) {
            throw BackupException("不支持的备份版本 ${root.optInt("version", -1)}，请用新版 Helium 导出后再恢复")
        }
        val tablesJson = root.optJSONObject("tables")
            ?: throw BackupException("备份内容损坏（缺少数据表）")
        val tables = LinkedHashMap<String, List<Map<String, Any?>>>()
        for (name in TABLES) {
            val arr = tablesJson.optJSONArray(name)
                ?: throw BackupException("备份内容损坏（缺少 $name 表）")
            val rows = ArrayList<Map<String, Any?>>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                    ?: throw BackupException("备份内容损坏（$name 第 ${i + 1} 行不是对象）")
                val row = LinkedHashMap<String, Any?>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val col = keys.next()
                    row[col] = decodeValue(obj.get(col))
                }
                rows += row
            }
            tables[name] = rows
        }
        val settingsJson = root.optJSONObject("settings")
        val settings = LinkedHashMap<String, String>()
        settingsJson?.keys()?.forEach { k -> settings[k] = settingsJson.optString(k, "") }
        return Parsed(
            appVersion = root.optString("appVersion", "未知"),
            exportedAt = root.optLong("exportedAt", 0L),
            settings = settings,
            tables = tables,
        )
    }

    /** BLOB 列在 JSON 里是 {"$blob": base64}；其余按 org.json 原样。 */
    fun encodeValue(value: Any?): Any? = when (value) {
        null -> JSONObject.NULL
        is ByteArray -> JSONObject().put(BLOB_KEY, Base64.getEncoder().encodeToString(value))
        else -> value
    }

    fun decodeValue(jsonValue: Any?): Any? = when {
        jsonValue == null || jsonValue === JSONObject.NULL -> null
        jsonValue is JSONObject && jsonValue.has(BLOB_KEY) ->
            runCatching { Base64.getDecoder().decode(jsonValue.getString(BLOB_KEY)) }
                .getOrElse { throw BackupException("备份内容损坏（BLOB 列无法解码）") }
        else -> jsonValue
    }
}
