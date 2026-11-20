package tech.qigai.helium.skills

/*
 * 专长 = a Markdown instruction sheet with a small YAML frontmatter, the same file shape as opencode / Claude
 * SKILL.md (`name`, `description`) plus OpenHands' keyword `triggers`. Only id + name + description are listed in
 * the system prompt; the body is loaded on demand with `load_skill`, or injected directly when the user picks the
 * skill in the composer or one of its triggers appears in the message (OpenHands keyword-triggered skills).
 */

data class SkillDoc(
    val id: String,
    val name: String,
    val description: String,
    val triggers: List<String> = emptyList(),
    val body: String,
    val enabled: Boolean = true,
)

object SkillMarkdown {
    const val MAX_BODY = 8000

    data class Parsed(val name: String?, val description: String?, val triggers: List<String>, val body: String, val hadFrontmatter: Boolean)

    /** Splits `---` frontmatter from the body. Unknown keys are ignored; malformed frontmatter is treated as body. */
    fun parse(markdown: String): Parsed {
        val text = markdown.replace("\r\n", "\n").trimStart('\uFEFF')
        val lines = text.lines()
        if (lines.firstOrNull()?.trim() != "---") return fromBody(text)
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" || it.trim() == "..." }
        if (end < 0) return fromBody(text)
        val front = lines.subList(1, end + 1)
        val body = lines.drop(end + 2).joinToString("\n").trim()
        val map = LinkedHashMap<String, MutableList<String>>()
        var listKey: String? = null
        for (raw in front) {
            if (raw.isBlank() || raw.trimStart().startsWith("#")) continue
            val item = Regex("^\\s*-\\s+(.*)$").find(raw)
            if (item != null && listKey != null) {
                map.getOrPut(listKey) { mutableListOf() } += unquote(item.groupValues[1])
                continue
            }
            val kv = Regex("^([A-Za-z_][\\w./-]*)\\s*:\\s*(.*)$").find(raw.trim()) ?: continue
            val key = kv.groupValues[1].lowercase()
            val value = kv.groupValues[2].trim()
            if (value.isEmpty() || value == "|" || value == ">") {
                listKey = key
                map[key] = mutableListOf()
            } else {
                listKey = null
                map[key] = if (value.startsWith("[") && value.endsWith("]")) splitList(value.substring(1, value.length - 1)) else mutableListOf(unquote(value))
            }
        }
        fun one(k: String) = map[k]?.joinToString(" ")?.trim()?.ifEmpty { null }
        val triggers = (map["triggers"] ?: map["trigger"] ?: map["keywords"]).orEmpty()
            .flatMap { if (map["triggers"]?.size == 1) splitList(it) else listOf(it) }
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return Parsed(one("name") ?: heading(body), one("description"), triggers, body, true)
    }

    private fun fromBody(text: String) = Parsed(heading(text), null, emptyList(), text.trim(), false)

    private fun heading(body: String) = body.lines().firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim()?.ifEmpty { null }

    private fun splitList(s: String): MutableList<String> = s.split(',', '，', '、').map { unquote(it.trim()) }.filter { it.isNotEmpty() }.toMutableList()

    private fun unquote(s: String): String {
        val t = s.trim()
        return if (t.length >= 2 && ((t.startsWith('"') && t.endsWith('"')) || (t.startsWith('\'') && t.endsWith('\'')))) t.substring(1, t.length - 1) else t
    }

    private fun yamlValue(s: String): String {
        val t = s.replace('\n', ' ').trim()
        val needsQuote = t.isEmpty() || t.contains(": ") || t.startsWith("[") || t.startsWith("-") || t.startsWith("#") || t.contains('"')
        return if (needsQuote) "\"" + t.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" else t
    }

    /** SKILL.md text for export and for the approval card. */
    fun render(doc: SkillDoc): String = buildString {
        append("---\n")
        append("name: ").append(yamlValue(doc.name)).append('\n')
        append("description: ").append(yamlValue(doc.description)).append('\n')
        if (doc.triggers.isNotEmpty()) {
            append("triggers:\n")
            doc.triggers.forEach { append("  - ").append(yamlValue(it)).append('\n') }
        }
        append("---\n\n")
        append(doc.body.trim()).append('\n')
    }

    /** Validation shared by the editor and the `skill_save` tool. Null = ok. */
    fun problem(name: String, description: String, body: String): String? = when {
        name.isBlank() -> "专长需要一个名字"
        name.length > 30 -> "名字最多 30 个字"
        description.isBlank() -> "写一句什么时候用它（描述），Helium 靠它判断要不要加载"
        description.length > 200 -> "描述最多 200 个字"
        body.isBlank() -> "说明书正文不能为空"
        body.length > MAX_BODY -> "正文最多 $MAX_BODY 个字"
        else -> null
    }
}

object SkillIds {
    private val kebab = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

    /** opencode-style kebab id from an ASCII name, else "skill-" + a stable hash of the name. Unique against [taken]. */
    fun forName(name: String, taken: Set<String>): String {
        val ascii = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).trim('-')
        val base = if (ascii.length >= 2 && kebab.matches(ascii)) ascii else "skill-" + Integer.toHexString(name.trim().hashCode()).padStart(8, '0').take(6)
        if (base !in taken) return base
        var i = 2
        while ("$base-$i" in taken) i++
        return "$base-$i"
    }
}

object SkillMatcher {
    /** What the composer's 专长 picker puts in front of the message. */
    fun marker(name: String) = "【专长：$name】"

    private val explicit = Regex("【专长[:：]\\s*([^】]+)】")

    fun explicitNames(text: String): List<String> = explicit.findAll(text).map { it.groupValues[1].trim() }.toList()

    private fun norm(s: String) = s.lowercase().replace(Regex("\\s+"), "")

    /**
     * Skills to inject for this message: the ones picked explicitly (marker or `@id`) first, then those whose
     * triggers appear in it. A skill with no triggers is triggered by its own name. At most [max].
     */
    fun triggered(skills: List<SkillDoc>, text: String, max: Int = 2): List<SkillDoc> {
        val on = skills.filter { it.enabled }
        val t = norm(text)
        val names = explicitNames(text).map(::norm)
        val picked = on.filter { s -> norm(s.name) in names || Regex("(^|\\s)@" + Regex.escape(s.id) + "\\b").containsMatchIn(text) }
        val byTrigger = on.filter { s ->
            s !in picked && (s.triggers.ifEmpty { listOf(s.name) }).any { tr -> norm(tr).length >= 2 && t.contains(norm(tr)) }
        }
        return (picked + byTrigger).take(max)
    }

    fun find(skills: List<SkillDoc>, idOrName: String): SkillDoc? {
        val q = idOrName.trim().removePrefix("@")
        return skills.firstOrNull { it.id == q } ?: skills.firstOrNull { norm(it.name) == norm(q) }
    }
}

object SkillPrompt {
    /** The list in the system prompt: ids, names and descriptions only. */
    fun listing(skills: List<SkillDoc>): String {
        val on = skills.filter { it.enabled }
        if (on.isEmpty()) return ""
        return buildString {
            append("可用专长（用户写给你的做事说明书）。任务和某个专长的描述相符时，先调用 load_skill 读出它，再严格按里面的格式和步骤做：\n")
            on.forEach { append("- ").append(it.id).append("「").append(it.name).append("」：").append(it.description.replace('\n', ' ')).append('\n') }
        }.trimEnd()
    }

    fun injected(skills: List<SkillDoc>): String = skills.joinToString("\n\n") { s ->
        "本轮已加载专长「${s.name}」（${s.id}），严格按它的格式和步骤完成用户的请求，不必再调用 load_skill：\n<skill name=\"${s.name}\">\n${s.body.trim().take(SkillMarkdown.MAX_BODY)}\n</skill>"
    }
}
