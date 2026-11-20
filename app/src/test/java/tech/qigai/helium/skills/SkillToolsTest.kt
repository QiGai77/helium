package tech.qigai.helium.skills

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.qigai.helium.agent.GrantScope
import tech.qigai.helium.agent.Risk
import tech.qigai.helium.agent.Sentinel
import tech.qigai.helium.agent.TrustMode
import tech.qigai.helium.agent.Verdict
import tech.qigai.helium.agent.Grant
import tech.qigai.helium.agent.GrantStore
import tech.qigai.helium.agent.tools.LoadSkillTool
import tech.qigai.helium.agent.tools.SkillDeleteTool
import tech.qigai.helium.agent.tools.SkillSaveTool
import tech.qigai.helium.agent.tools.SkillStore
import tech.qigai.helium.agent.tools.ToolContext

private class MemSkills : SkillStore {
    val docs = mutableListOf<SkillDoc>()
    override suspend fun list() = docs.toList()
    override suspend fun save(doc: SkillDoc): SkillDoc {
        val id = doc.id.ifBlank { SkillIds.forName(doc.name, docs.map { it.id }.toSet()) }
        val d = doc.copy(id = id)
        docs.removeAll { it.id == id }
        docs += d
        return d
    }
    override suspend fun delete(id: String) = docs.removeAll { it.id == id }
}

class SkillToolsTest {
    private val ctx = ToolContext("c1")

    @Test fun `load_skill returns the body by id or name`() = runBlocking {
        val store = MemSkills().apply { docs += SkillDoc("weekly", "写周报", "写周报时用", emptyList(), "## 本周完成\n## 下周计划") }
        val out = LoadSkillTool(store).run(JSONObject().put("id", "写周报"), ctx)
        assertTrue(out.text.contains("## 下周计划"))
        assertTrue(LoadSkillTool(store).run(JSONObject().put("id", "nope"), ctx).isError)
    }

    @Test fun `writing skills is high risk and always asks, even in relaxed mode with a grant`() = runBlocking {
        val save = SkillSaveTool(MemSkills())
        val args = JSONObject().put("name", "写周报").put("description", "写周报时用").put("body", "三段式")
        assertEquals(Risk.HIGH, save.risk(args))
        assertEquals(Risk.HIGH, SkillDeleteTool(MemSkills()).risk(JSONObject().put("id", "x")))
        val grants = object : GrantStore {
            override suspend fun active(tool: String, target: String, conversationId: String, now: Long) = Grant(tool, "*", GrantScope.FOREVER, null, null)
            override suspend fun add(grant: Grant) {}
        }
        val v = Sentinel(grants, mode = { TrustMode.RELAXED }).guard(save, args, "c1", tainted = false)
        assertTrue(v is Verdict.Ask && v.scopes == listOf(GrantScope.ONCE))
        assertTrue(save.detail(args).startsWith("---\nname: 写周报"))
    }

    @Test fun `skill_save accepts a full SKILL_md and validates`() = runBlocking {
        val store = MemSkills()
        val save = SkillSaveTool(store)
        val md = "---\nname: 写周报\ndescription: 写周报时用\ntriggers: [周报]\n---\n\n## 本周完成"
        val out = save.run(JSONObject().put("name", "x").put("description", "y").put("body", "z").put("markdown", md), ctx)
        assertTrue(out.text, !out.isError)
        assertEquals("写周报", store.docs.single().name)
        assertEquals(listOf("周报"), store.docs.single().triggers)
        assertTrue(save.run(JSONObject().put("name", "a").put("description", "").put("body", "b"), ctx).isError)
        SkillDeleteTool(store).run(JSONObject().put("id", store.docs.single().id), ctx)
        assertTrue(store.docs.isEmpty())
    }
}
