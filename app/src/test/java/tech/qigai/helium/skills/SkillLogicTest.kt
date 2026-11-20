package tech.qigai.helium.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillFrontmatterTest {
    @Test fun `parses name, description and a trigger list`() {
        val p = SkillMarkdown.parse(
            """
            ---
            name: 写周报
            description: "用户要写周报时：按固定三段格式输出"
            triggers:
              - 周报
              - weekly report
            ---

            ## 本周完成
            - …
            """.trimIndent(),
        )
        assertTrue(p.hadFrontmatter)
        assertEquals("写周报", p.name)
        assertEquals("用户要写周报时：按固定三段格式输出", p.description)
        assertEquals(listOf("周报", "weekly report"), p.triggers)
        assertTrue(p.body.startsWith("## 本周完成"))
    }

    @Test fun `inline lists, comma lists and unknown keys`() {
        assertEquals(listOf("周报", "总结"), SkillMarkdown.parse("---\nname: a\ntriggers: [周报, \"总结\"]\nlicense: MIT\n---\nbody").triggers)
        assertEquals(listOf("周报", "总结"), SkillMarkdown.parse("---\nname: a\ntriggers: 周报，总结\n---\nbody").triggers)
    }

    @Test fun `no or broken frontmatter is all body, name from heading`() {
        val p = SkillMarkdown.parse("# 写周报\n\n三段式")
        assertFalse(p.hadFrontmatter)
        assertEquals("写周报", p.name)
        assertNull(p.description)
        val broken = SkillMarkdown.parse("---\nname: x\nno end")
        assertFalse(broken.hadFrontmatter)
        assertTrue(broken.body.contains("no end"))
    }

    @Test fun `render then parse round-trips`() {
        val doc = SkillDoc("weekly", "写周报", "写周报: 三段式", listOf("周报", "- 总结"), "## 本周完成\n- a")
        val p = SkillMarkdown.parse(SkillMarkdown.render(doc))
        assertEquals(doc.name, p.name)
        assertEquals(doc.description, p.description)
        assertEquals(doc.triggers, p.triggers)
        assertEquals(doc.body, p.body)
    }

    @Test fun `validation`() {
        assertEquals(null, SkillMarkdown.problem("写周报", "写周报时用", "正文"))
        assertTrue(SkillMarkdown.problem("", "d", "b") != null)
        assertTrue(SkillMarkdown.problem("n", "", "b") != null)
        assertTrue(SkillMarkdown.problem("n", "d", " ") != null)
    }
}

class SkillMatchingTest {
    private val weekly = SkillDoc("skill-1", "写周报", "写周报时用", emptyList(), "## 本周完成")
    private val trip = SkillDoc("trip-plan", "行程规划", "规划出行", listOf("行程", "旅行计划"), "…")
    private val off = SkillDoc("off", "翻译", "翻译", emptyList(), "…", enabled = false)

    @Test fun `name is the default trigger`() {
        assertEquals(listOf(weekly), SkillMatcher.triggered(listOf(weekly, trip), "帮我写周报"))
        assertEquals(listOf(trip), SkillMatcher.triggered(listOf(weekly, trip), "帮我排一下下周的行程"))
        assertTrue(SkillMatcher.triggered(listOf(weekly, trip, off), "翻译这句话").isEmpty())
    }

    @Test fun `explicit pick comes first`() {
        val t = SkillMatcher.marker("行程规划") + "写周报也顺便"
        assertEquals(listOf(trip, weekly), SkillMatcher.triggered(listOf(weekly, trip), t))
        assertEquals(listOf(trip), SkillMatcher.triggered(listOf(weekly, trip), "用 @trip-plan 看看"))
    }

    @Test fun `ids are kebab or hashed and unique`() {
        assertEquals("weekly-report", SkillIds.forName("Weekly Report!", emptySet()))
        assertEquals("weekly-report-2", SkillIds.forName("weekly report", setOf("weekly-report")))
        val cn = SkillIds.forName("写周报", emptySet())
        assertTrue(cn.matches(Regex("skill-[0-9a-f]{6}")))
        assertEquals(cn, SkillIds.forName("写周报", emptySet()))
    }

    @Test fun `listing shows descriptions only and find works by id or name`() {
        val l = SkillPrompt.listing(listOf(weekly, off))
        assertTrue(l.contains("skill-1「写周报」：写周报时用"))
        assertFalse(l.contains("## 本周完成"))
        assertFalse(l.contains("翻译"))
        assertEquals(weekly, SkillMatcher.find(listOf(weekly), "写周报"))
        assertEquals(weekly, SkillMatcher.find(listOf(weekly), "@skill-1"))
        assertTrue(SkillPrompt.injected(listOf(weekly)).contains("<skill name=\"写周报\">"))
    }
}
