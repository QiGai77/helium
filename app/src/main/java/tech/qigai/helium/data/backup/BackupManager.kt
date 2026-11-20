package tech.qigai.helium.data.backup

import android.content.Context
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.qigai.helium.data.db.HeliumDatabase
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * M7 整库备份 / 恢复（Android 层：读 Room 的 SQLite 表 + SAF 流）。
 *
 * 恢复什么：会话、事件（含全文索引由触发器重建）、记忆、候选记忆、
 * “不再保存”规则、专长、授权、审计、审批、定时任务与运行历史、速览订阅/
 * 条目/自定义卡/天气缓存，以及非敏感偏好（提供方、模型名、Worker 地址、
 * 搜索提供方）。恢复后这些全回来。
 *
 * 不恢复什么：API Key 与云端 token（它们在 Keystore 保险箱里，卸载即丢，
 * 备份文件里也绝不写）。恢复成功后界面会明确提示去「模型与推理」重填。
 *
 * 注意：恢复是整库替换（先 DELETE 再 INSERT），会丢弃本机现有数据；
 * 恢复后 Room 进程内缓存已过期，调用方必须提示用户重启 App（见
 * [BackupResult.restartRequired] 与 BackupScreen 的文案）。
 */
class BackupManager(
    private val context: Context,
    private val db: HeliumDatabase,
    private val appVersion: String,
) {
    data class BackupResult(
        /** 每张表恢复的行数。 */
        val counts: Map<String, Int>,
        /** 备份来自哪个版本。 */
        val fromVersion: String,
        val exportedAt: Long,
        /** 恢复后必须重启 App，Room 缓存才与新数据一致。 */
        val restartRequired: Boolean = true,
    ) {
        val totalRows: Int get() = counts.values.sum()
    }

    /** 默认导出文件名，如 helium-backup-20261120-1630.helium。 */
    fun exportFileName(now: Long = System.currentTimeMillis()): String {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
            .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(now))
        return "helium-backup-$stamp.helium"
    }

    /** 把整库加密后写入 SAF 提供的流（调用方负责打开/关闭流）。 */
    suspend fun exportTo(out: OutputStream, passphrase: CharArray) {
        val json = withContext(Dispatchers.IO) { buildJson() }
        val encrypted = BackupCrypto.encrypt(json.toByteArray(Charsets.UTF_8), passphrase)
        withContext(Dispatchers.IO) { out.write(encrypted) }
    }

    /** 从 SAF 读入的流恢复整库；口令错或文件损坏时抛 [BackupException]，本机数据不动。 */
    suspend fun importFrom(input: InputStream, passphrase: CharArray): BackupResult {
        val bytes = withContext(Dispatchers.IO) { input.readBytes() }
        val plain = BackupCrypto.decrypt(bytes, passphrase)
        val parsed = BackupPayload.parse(String(plain, Charsets.UTF_8))
        withContext(Dispatchers.IO) { restore(parsed) }
        return BackupResult(parsed.counts, parsed.appVersion, parsed.exportedAt)
    }

    private fun buildJson(): String {
        val readable = db.openHelper.readableDatabase
        val tables = LinkedHashMap<String, List<Map<String, Any?>>>()
        for (table in BackupPayload.TABLES) tables[table] = dumpTable(readable, table)
        return BackupPayload.build(tables, readSettings(), System.currentTimeMillis(), appVersion)
    }

    private fun dumpTable(sqlite: SupportSQLiteDatabase, table: String): List<Map<String, Any?>> {
        val rows = ArrayList<Map<String, Any?>>()
        sqlite.query("SELECT * FROM `$table`").use { c ->
            val names = (0 until c.columnCount).map { c.getColumnName(it) }
            while (c.moveToNext()) {
                val row = LinkedHashMap<String, Any?>()
                names.forEachIndexed { i, name ->
                    row[name] = when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                        Cursor.FIELD_TYPE_BLOB -> c.getBlob(i)
                        else -> c.getString(i)
                    }
                }
                rows += row
            }
        }
        return rows
    }

    private fun restore(parsed: BackupPayload.Parsed) {
        val writable = db.openHelper.writableDatabase
        writable.beginTransaction()
        try {
            for ((table, rows) in parsed.tables) {
                writable.execSQL("DELETE FROM `$table`")
                for (row in rows) insertRow(writable, table, row)
            }
            writable.setTransactionSuccessful()
        } finally {
            writable.endTransaction()
        }
        // 非敏感偏好写回 helium_settings；保险箱（helium_vault）绝不在此触碰。
        applySettings(parsed.settings)
    }

    private fun insertRow(sqlite: SupportSQLiteDatabase, table: String, row: Map<String, Any?>) {
        if (row.isEmpty()) return
        val cols = row.keys.toList()
        val sql = "INSERT OR REPLACE INTO `$table` (${cols.joinToString(", ") { "`$it`" }}) " +
            "VALUES (${cols.joinToString(", ") { "?" }})"
        sqlite.execSQL(sql, row.values.map { bindValue(it) }.toTypedArray())
    }

    /** Room 只用 Long / Double / String / ByteArray 四种列类型；Boolean 在备份里是 0/1 的 Long。 */
    private fun bindValue(value: Any?): Any? = when (value) {
        null -> null
        is Boolean -> if (value) 1L else 0L
        is Number -> if (value is Double || value is Float) value.toDouble() else value.toLong()
        is ByteArray -> value
        is String -> value
        else -> value.toString()
    }

    /** 只读本应用自己的 helium_settings；值全是字符串（提供方、模型名、地址等）。 */
    private fun readSettings(): Map<String, String> {
        val prefs = context.getSharedPreferences("helium_settings", Context.MODE_PRIVATE)
        val out = LinkedHashMap<String, String>()
        prefs.all.forEach { (k, v) -> if (v is String) out[k] = v }
        return out
    }

    private fun applySettings(settings: Map<String, String>) {
        if (settings.isEmpty()) return
        val prefs = context.getSharedPreferences("helium_settings", Context.MODE_PRIVATE)
        prefs.edit().apply { settings.forEach { (k, v) -> putString(k, v) } }.apply()
    }
}
