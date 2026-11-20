package tech.qigai.helium.crash

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * M7 崩溃日志：只保存在本机，不上传到任何地方。
 *
 * [CrashHandler.install] 应在 Application.onCreate 尽早调用（见汇报里的接入
 * 片段——HeliumApplication.kt 是 B 路的注册点，本路不改它）。它接管未捕获
 * 异常：先把日志写进 files/crash-logs/，再交给原来的 handler（系统照常
 * 弹“应用已停止”，进程照常退出；这里绝不吞掉崩溃）。
 *
 * 纯逻辑（文件名、格式化、保留策略）抽在 [CrashLog] 里，可在 JVM 单元测试
 * 覆盖；[CrashLogStore] 只是薄薄的 Android 文件层。
 */
object CrashLog {
    const val DIR = "crash-logs"
    const val MAX_FILES = 10

    fun fileName(at: Long): String =
        "crash-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(at)) + ".txt"

    /** 日志正文：只含异常本身 + 设备型号 / 系统版本 / App 版本，不含对话与记忆内容。 */
    fun format(
        threadName: String,
        throwable: Throwable,
        appVersion: String,
        at: Long = System.currentTimeMillis(),
        device: String = "${Build.MANUFACTURER} ${Build.MODEL}",
        sdk: Int = Build.VERSION.SDK_INT,
    ): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return buildString {
            appendLine("Helium 崩溃日志")
            appendLine("时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(at))}")
            appendLine("版本：$appVersion")
            appendLine("设备：$device（Android $sdk）")
            appendLine("线程：$threadName")
            appendLine("异常：${throwable.javaClass.name}: ${throwable.message}")
            appendLine()
            append(sw.toString())
        }
    }

    /** 崩溃列表里显示的一行摘要：异常类名 + 首行 message。 */
    fun title(text: String): String =
        text.lineSequence().firstOrNull { it.startsWith("异常：") }
            ?.removePrefix("异常：")?.takeIf { it.isNotBlank() } ?: "未知崩溃"

    /** 超过 [MAX_FILES] 时删最旧的，返回要删的文件名（调用方执行删除）。 */
    fun prune(names: List<String>, keep: Int = MAX_FILES): List<String> =
        if (names.size <= keep) emptyList() else names.sorted().dropLast(keep)
}

data class CrashEntry(val fileName: String, val at: Long, val title: String)

class CrashLogStore(context: Context) {
    private val dir: File = File(context.filesDir, CrashLog.DIR).apply { mkdirs() }

    fun write(text: String, at: Long = System.currentTimeMillis()): File {
        val file = File(dir, CrashLog.fileName(at))
        file.writeText(text)
        CrashLog.prune(dir.listFiles()?.map { it.name } ?: emptyList())
            .forEach { runCatching { File(dir, it).delete() } }
        return file
    }

    fun list(): List<CrashEntry> =
        (dir.listFiles() ?: emptyArray())
            .sortedByDescending { it.name }
            .mapNotNull { f ->
                val at = runCatching {
                    SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).parse(f.name.removePrefix("crash-").removeSuffix(".txt"))?.time
                }.getOrNull() ?: f.lastModified()
                val title = runCatching { CrashLog.title(f.readText()) }.getOrDefault("未知崩溃")
                CrashEntry(f.name, at, title)
            }

    fun read(fileName: String): String? =
        runCatching { File(dir, fileName).takeIf { it.isFile }?.readText() }.getOrNull()

    fun file(fileName: String): File = File(dir, fileName)

    fun clear() {
        dir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    fun count(): Int = dir.listFiles()?.size ?: 0
}

/** 在 Application.onCreate 调用一次。写日志失败时静默放弃，绝不影响崩溃上报链。 */
object CrashHandler {
    private val installed = AtomicBoolean(false)

    fun install(context: Context, appVersion: String) {
        if (!installed.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val store = CrashLogStore(appContext)
                store.write(CrashLog.format(thread.name, throwable, appVersion))
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
