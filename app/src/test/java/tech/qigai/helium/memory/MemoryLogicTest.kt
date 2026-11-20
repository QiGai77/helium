package tech.qigai.helium.memory

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.qigai.helium.agent.tools.MemoryRecord
import java.time.ZoneId

class ExtractionParsingTest {
    @Test fun `parses the facts object`() {
        val f = ExtractionPrompt.parse("""{"facts":[{"text":"对花生过敏","domain":"饮食","inferred":false}]}""")
        assertEquals(listOf(CandidateFact("对花生过敏", "饮食", false)), f)
    }

    @Test fun `tolerates fences, think blocks and prose`() {
        val raw = "<think>用户说了过敏</think>好的，结果如下：\n```json\n{\"facts\": [{\"text\": \"用户出差住高楼层。\", \"domain\": \"出行\", \"inferred\": true}]}\n```"
        assertEquals(listOf(CandidateFact("出差住高楼层", "出行", true)), ExtractionPrompt.parse(raw))
    }

    @Test fun `accepts mem0 style string facts and guesses the domain`() {
        val f = ExtractionPrompt.parse("""{"facts":["女儿喜欢恐龙","坐高铁选靠窗"]}""")
        assertEquals(listOf("家庭", "出行"), f.map { it.domain })
        assertTrue(f.none { it.inferred })
    }

    @Test fun `bare array, legacy domains and duplicates`() {
        val f = ExtractionPrompt.parse("""[{"text":"对花生过敏","domain":"家与健康"},{"text":"我对花生过敏！"},{"text":"x"}]""")
        assertEquals(1, f.size)
        assertEquals("健康", f[0].domain)
    }

    @Test fun `garbage and empty answers give nothing`() {
        assertTrue(ExtractionPrompt.parse("").isEmpty())
        assertTrue(ExtractionPrompt.parse("没有可以记的").isEmpty())
        assertTrue(ExtractionPrompt.parse("{\"facts\": [}").isEmpty())
        assertTrue(ExtractionPrompt.parse("{\"facts\":[]}").isEmpty())
    }

    @Test fun `gate skips turns that cannot hold a personal fact`() {
        assertTrue(ExtractionPrompt.worthTrying("我对花生过敏"))
        assertTrue(ExtractionPrompt.worthTrying("记住：周报每周五交"))
        assertFalse(ExtractionPrompt.worthTrying("你好"))
        assertFalse(ExtractionPrompt.worthTrying("推荐个零食"))
        assertFalse(ExtractionPrompt.worthTrying("几点"))
    }

    @Test fun `prompt carries existing memories and the user turn`() {
        val p = ExtractionPrompt.build("我对花生过敏", "好的", listOf("坐高铁选靠窗"), "2026-11-20")
        assertTrue(p.contains("- 坐高铁选靠窗"))
        assertTrue(p.contains("用户：我对花生过敏"))
        assertTrue(ExtractionPrompt.SYSTEM.contains("\"facts\""))
    }

    @Test fun `domain guess puts food allergies under food`() {
        assertEquals("饮食", MemoryDomains.guess("对花生过敏"))
        assertEquals("健康", MemoryDomains.guess("对青霉素过敏"))
        assertEquals("出行", MemoryDomains.guess("出差住高楼层的酒店"))
        assertEquals("其他", MemoryDomains.guess("xyz"))
        assertEquals("家庭", MemoryDomains.normalize("人"))
    }
}

class ForgetRuleTest {
    @Test fun `rule blocks the same fact however it is worded`() {
        val rules = listOf("对花生过敏")
        assertTrue(ForgetRules.blocked(rules, "我对花生过敏"))
        assertTrue(ForgetRules.blocked(rules, "用户对花生过敏。"))
        assertTrue(ForgetRules.blocked(rules, "对花生过敏，不能吃花生酱"))
        assertTrue(ForgetRules.blocked(listOf("坐高铁一般选靠窗座位"), "坐高铁一般选靠窗的座位"))
    }

    @Test fun `rule does not block unrelated facts`() {
        val rules = listOf("对花生过敏")
        assertFalse(ForgetRules.blocked(rules, "喜欢靠窗"))
        assertFalse(ForgetRules.blocked(rules, "对海鲜过敏"))
        assertFalse(ForgetRules.blocked(emptyList(), "对花生过敏"))
        assertFalse(ForgetRules.matches("我", "我喜欢猫"))
    }
}

class RelevanceRankingTest {
    private fun m(id: Long, text: String, domain: String, inferred: Boolean = false) = MemoryRecord(id, text, domain, inferred, id)

    private val library = listOf(
        m(1, "对花生过敏", "饮食"), m(2, "坐高铁选靠窗", "出行"), m(3, "女儿喜欢恐龙", "家庭"), m(4, "周报每周五交给王经理", "工作"),
        m(5, "出差住高楼层", "出行"), m(6, "喜欢深色模式", "偏好"), m(7, "常用地址是南京鼓楼区", "家庭"), m(8, "穿 42 码的鞋", "购物"),
        m(9, "不喝冰的饮料", "饮食"), m(10, "每天晚上跑步", "健康"), m(11, "猫叫团子", "家庭"), m(12, "喜欢科幻小说", "偏好"),
        m(13, "老板姓王", "工作"), m(14, "爱吃辣", "饮食"),
    )

    @Test fun `snack question surfaces the peanut allergy first`() {
        val r = MemoryRanker.rank("推荐个零食", library, limit = 5)
        assertEquals(1L, r.first().id)
        assertTrue(r.any { it.id == 14L })
        assertFalse(r.any { it.id == 2L || it.id == 4L })
    }

    @Test fun `term overlap ranks the matching memory`() {
        val r = MemoryRanker.rank("帮我写周报", library, limit = 3)
        assertEquals(4L, r.first().id)
    }

    @Test fun `small libraries are injected whole`() {
        val small = library.take(3)
        assertEquals(3, MemoryRanker.rank("你好", small).size)
        // Otherwise only the safety facts ride along.
        assertEquals(listOf(1L), MemoryRanker.rank("你好", library).map { it.id })
    }

    @Test fun `fts or-query is safe and uses bigram phrases`() {
        val q = MemoryRanker.ftsAnyOf("推荐\"零食\" OR")!!
        assertTrue(q.contains("\"零 食\""))
        assertTrue(q.contains("or*"))
        assertEquals(null, MemoryRanker.ftsAnyOf("！？"))
    }

    @Test fun `context block never asks the model to cite memory`() {
        val b = MemoryRanker.contextBlock(listOf(m(1, "对花生过敏", "饮食")))
        assertTrue(b.contains("- 对花生过敏（饮食）"))
        assertTrue(b.contains("不要说“根据你的记忆"))
        assertEquals("", MemoryRanker.contextBlock(emptyList()))
    }
}

class ExportFormatTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = 1_763_600_000_000L
    private val mems = listOf(MemoryRecord(1, "对花生过敏", "饮食", false, now), MemoryRecord(2, "可能喜欢早起", "偏好", true, now))
    private val rules = listOf(RuleRecord(1, "坐高铁选靠窗", now))
    private val skills = listOf(ExportSkill("weekly-report", "写周报", "写周报时用", listOf("周报"), true, "---\nname: 写周报\n---\n\n## 本周完成\n"))

    @Test fun `markdown groups by domain and marks the origin`() {
        val md = MemoryExport.markdown(mems, rules, skills, now, zone)
        assertTrue(md.startsWith("# Helium 记忆导出"))
        assertTrue(md.contains("## 关于你·饮食\n\n- 对花生过敏（你说的 · 2025-11-20）"))
        assertTrue(md.contains("- 可能喜欢早起（推断"))
        assertTrue(md.indexOf("关于你·饮食") < md.indexOf("关于你·偏好"))
        assertTrue(md.contains("## 不再保存\n\n- 坐高铁选靠窗"))
        assertTrue(md.contains("### 写周报"))
        assertTrue(md.contains("````markdown\n---\nname: 写周报"))
    }

    @Test fun `json round-trips every field`() {
        val o = JSONObject(MemoryExport.json(mems, rules, skills, now))
        assertEquals("helium-memory", o.getString("format"))
        assertEquals(1, o.getInt("version"))
        val m0 = o.getJSONArray("memories").getJSONObject(0)
        assertEquals("对花生过敏", m0.getString("text"))
        assertEquals("饮食", m0.getString("domain"))
        assertEquals("你说的", m0.getString("origin"))
        assertEquals("推断", o.getJSONArray("memories").getJSONObject(1).getString("origin"))
        assertEquals("坐高铁选靠窗", o.getJSONArray("forgetRules").getJSONObject(0).getString("text"))
        assertEquals("周报", o.getJSONArray("skills").getJSONObject(0).getJSONArray("triggers").getString(0))
    }

    @Test fun `empty export is still readable`() {
        val md = MemoryExport.markdown(emptyList(), emptyList(), emptyList(), now, zone)
        assertTrue(md.contains("（没有记忆）"))
        assertEquals(0, JSONObject(MemoryExport.json(emptyList(), emptyList(), emptyList(), now)).getJSONArray("memories").length())
        assertEquals("helium-memory-2025-11-20.md", MemoryExport.fileName("md", now, zone))
    }
}
