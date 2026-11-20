package tech.qigai.helium.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLogTest {
    @Test fun `format keeps the exception and stack trace`() {
        val cause = IllegalStateException("boom")
        val text = CrashLog.format("main", cause, "0.3.0", at = 1_700_000_000_000L, device = "Test Phone", sdk = 35)
        assertTrue(text.contains("IllegalStateException: boom"))
        assertTrue(text.contains("0.3.0"))
        assertTrue(text.contains("Test Phone"))
        assertTrue(text.contains("main"))
        assertTrue(text.contains("at tech.qigai.helium.crash.CrashLogTest"))
    }

    @Test fun `format includes the cause chain`() {
        val root = RuntimeException("root cause")
        val wrapped = IllegalArgumentException("outer", root)
        val text = CrashLog.format("worker", wrapped, "0.3.0", device = "Test Phone", sdk = 35)
        assertTrue(text.contains("Caused by: java.lang.RuntimeException: root cause"))
    }

    @Test fun `title extracts the exception line`() {
        val text = CrashLog.format("main", NullPointerException("npe here"), "0.3.0", device = "Test", sdk = 35)
        assertEquals("java.lang.NullPointerException: npe here", CrashLog.title(text))
        assertEquals("未知崩溃", CrashLog.title("no exception line here"))
        assertEquals("未知崩溃", CrashLog.title(""))
    }

    @Test fun `file names sort chronologically`() {
        val a = CrashLog.fileName(1_700_000_000_000L)
        val b = CrashLog.fileName(1_700_000_060_000L)
        assertTrue(a < b)
        assertTrue(a.startsWith("crash-") && a.endsWith(".txt"))
    }

    @Test fun `prune keeps only the newest ten`() {
        val names = (1..12).map { "crash-20261120-0000%02d.txt".format(it) }
        assertEquals(listOf("crash-20261120-000001.txt", "crash-20261120-000002.txt"), CrashLog.prune(names))
        assertEquals(emptyList<String>(), CrashLog.prune(names.take(10)))
        assertEquals(emptyList<String>(), CrashLog.prune(emptyList()))
    }

    @Test fun `prune keeps newest when input is unsorted`() {
        val names = listOf("crash-20261120-000003.txt", "crash-20261120-000001.txt", "crash-20261120-000002.txt")
        assertEquals(emptyList<String>(), CrashLog.prune(names, keep = 3))
        assertEquals(listOf("crash-20261120-000001.txt"), CrashLog.prune(names, keep = 2))
    }
}
