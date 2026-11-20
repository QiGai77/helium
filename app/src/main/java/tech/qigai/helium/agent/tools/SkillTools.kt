package tech.qigai.helium.agent.tools

import org.json.JSONObject
import tech.qigai.helium.agent.Risk
import tech.qigai.helium.skills.SkillDoc
import tech.qigai.helium.skills.SkillMarkdown
import tech.qigai.helium.skills.SkillMatcher

/** Backed by Room (`skills`) in the app. */
interface SkillStore {
    suspend fun list(): List<SkillDoc>
    /** Creates (blank / unknown id) or replaces a skill; returns what was stored. */
    suspend fun save(doc: SkillDoc): SkillDoc
    suspend fun delete(id: String): Boolean
}

/** opencode's `skill` tool: the system prompt lists descriptions, the body comes in only when the model asks. */
class LoadSkillTool(private val store: SkillStore) : Tool {
    override val spec = ToolSpec(
        "load_skill",
        "读取一个专长（用户写的做事说明书）的完整内容。任务和系统提示里某个专长的描述相符时先调用，然后严格按它做。",
        params(Triple("id", "string", "专长 id（或名字）")),
    )
    override fun risk(args: JSONObject) = Risk.LOW
    override fun target(args: JSONObject) = "skill:" + args.optString("id")
    override fun title(args: JSONObject) = "读取专长 ${args.optString("id")}"
    override suspend fun run(args: JSONObject, ctx: ToolContext): ToolOutput {
        val all = store.list().filter { it.enabled }
        val s = SkillMatcher.find(all, args.optString("id"))
            ?: return ToolOutput("没有这个专长。可用的：" + all.joinToString("、") { "${it.id}（${it.name}）" }.ifEmpty { "（无）" }, isError = true)
        return ToolOutput("<skill id=\"${s.id}\" name=\"${s.name}\">\n${s.body.trim()}\n</skill>\n按上面的说明完成用户的请求。")
    }
}

/** Writing or rewriting a skill changes how Helium behaves later, so it is HIGH risk: the user always sees the full sheet. */
class SkillSaveTool(private val store: SkillStore) : Tool {
    override val spec = ToolSpec(
        "skill_save",
        "新建或修改一个专长（用户要求“把这个做成专长/以后都按这个格式”时）。需要用户审批。可以直接给完整 Markdown（带 --- 前言），或分别给 name/description/body。",
        params(
            Triple("id", "string", "要修改的专长 id；新建时留空"),
            Triple("name", "string", "名字，例如“写周报”"),
            Triple("description", "string", "一句话：什么时候用它"),
            Triple("body", "string", "Markdown 正文：格式、步骤、注意事项"),
            Triple("triggers", "string", "可选，触发关键词，用逗号分隔"),
            Triple("markdown", "string", "可选，完整 SKILL.md（--- name/description/triggers --- 加正文），给了就以它为准"),
            required = listOf("name", "description", "body"),
        ),
    )
    override fun risk(args: JSONObject) = Risk.HIGH
    override fun target(args: JSONObject) = "skill:" + args.optString("id").ifBlank { "new" }
    override fun title(args: JSONObject) = (if (args.optString("id").isBlank()) "新建专长「" else "修改专长「") + doc(args).name + "」"
    override fun detail(args: JSONObject) = SkillMarkdown.render(doc(args)).take(3000)

    fun doc(args: JSONObject): SkillDoc {
        val md = args.optString("markdown")
        val p = if (md.isNotBlank()) SkillMarkdown.parse(md) else null
        val triggers = p?.triggers?.takeIf { it.isNotEmpty() }
            ?: args.optString("triggers").split(',', '，', '、', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        return SkillDoc(
            id = args.optString("id").trim(),
            name = (p?.name ?: args.optString("name")).trim().take(30),
            description = (p?.description ?: args.optString("description")).trim(),
            triggers = triggers,
            body = (p?.body?.takeIf { it.isNotBlank() } ?: args.optString("body")).trim(),
        )
    }

    override suspend fun run(args: JSONObject, ctx: ToolContext): ToolOutput {
        val d = doc(args)
        SkillMarkdown.problem(d.name, d.description, d.body)?.let { return ToolOutput(it, isError = true) }
        val saved = store.save(d)
        return ToolOutput("已保存专长「${saved.name}」（id=${saved.id}）。")
    }
}

class SkillDeleteTool(private val store: SkillStore) : Tool {
    override val spec = ToolSpec("skill_delete", "删除一个专长。需要用户审批。", params(Triple("id", "string", "专长 id")))
    override fun risk(args: JSONObject) = Risk.HIGH
    override fun target(args: JSONObject) = "skill:" + args.optString("id")
    override fun title(args: JSONObject) = "删除专长 ${args.optString("id")}"
    override fun detail(args: JSONObject) = "专长 id：${args.optString("id")}\n删除后不可恢复。"
    override suspend fun run(args: JSONObject, ctx: ToolContext): ToolOutput {
        val s = SkillMatcher.find(store.list(), args.optString("id")) ?: return ToolOutput("没有这个专长", isError = true)
        store.delete(s.id)
        return ToolOutput("已删除专长「${s.name}」。")
    }
}
