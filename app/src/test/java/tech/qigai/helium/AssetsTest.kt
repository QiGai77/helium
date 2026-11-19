package tech.qigai.helium

import androidx.compose.ui.graphics.vector.ImageVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.qigai.helium.ui.components.inlineMarkdown
import tech.qigai.helium.ui.icons.HeIcons
import tech.qigai.helium.ui.illo.HeIllustrations
import tech.qigai.helium.ui.illo.IlloKind
import tech.qigai.helium.ui.illo.IlloTone

/** Builds every generated vector so a bad path string fails CI instead of crashing at runtime. */
class AssetsTest {
    @Test fun allIconsBuild() {
        val getters = HeIcons::class.java.declaredMethods.filter { it.name.startsWith("get") && it.returnType == ImageVector::class.java }
        assertTrue("expected generated icons", getters.size >= 50)
        getters.forEach { m ->
            val v = m.invoke(HeIcons) as ImageVector
            assertEquals(24f, v.viewportWidth)
            assertTrue("${m.name} has no paths", v.root.size > 0)
        }
    }

    @Test fun allIllustrationsBuild() {
        for (k in IlloKind.entries) for (t in IlloTone.entries) {
            val v = HeIllustrations.get(k, t)
            assertEquals(120f, v.viewportWidth)
            assertTrue("$k/$t has no paths", v.root.size > 3)
        }
    }

    @Test fun inlineMarkdownStripsMarkers() {
        assertEquals("粗体 和 code 与 链接", inlineMarkdown("**粗体** 和 `code` 与 [链接](https://x.y)").text)
    }
}
