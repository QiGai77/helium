package tech.qigai.helium.memory

import org.json.JSONArray
import org.json.JSONObject
import tech.qigai.helium.agent.tools.MemoryRecord
import tech.qigai.helium.chat.SearchText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * Pure memory logic (no Android, no Room) so it is unit-tested on the JVM:
 * domains, the extraction prompt + its JSON parser, "不再保存" rule matching, relevance ranking and export.
 */

/** The groups on the 记忆 page ("关于你·饮食"). Order is the display order. */
object MemoryDomains {
    const val FOOD = "饮食"
    const val HEALTH = "健康"
    const val TRAVEL = "出行"
    const val WORK = "工作"
    const val FAMILY = "家庭"
    const val SHOPPING = "购物"
    const val PREFS = "偏好"
    const val OTHER = "其他"

    val all = listOf(FOOD, HEALTH, TRAVEL, WORK, FAMILY, SHOPPING, PREFS, OTHER)

    /** v4 used "人" and "家与健康"; MIGRATION_4_5 maps them the same way. */
    private val legacy = mapOf("人" to FAMILY, "家与健康" to HEALTH, "家" to FAMILY, "饮食偏好" to FOOD, "旅行" to TRAVEL)

    private val keywords: List<Pair<String, List<String>>> = listOf(
        FOOD to listOf(
            "吃", "喝", "饭", "菜", "餐", "食", "零食", "口味", "辣", "甜", "咸", "吃素", "素食", "荤", "忌口", "花生", "海鲜", "坚果", "牛奶", "乳糖",
            "麸质", "咖啡", "奶茶", "茶", "酒", "水果", "外卖", "早餐", "午餐", "晚餐", "夜宵", "饮料", "清真", "鸡蛋", "香菜", "葱", "蒜",
        ),
        HEALTH to listOf("过敏", "病", "药", "医", "健身", "运动", "睡眠", "失眠", "血压", "血糖", "体重", "减肥", "跑步", "孕", "牙", "视力", "哮喘"),
        TRAVEL to listOf("航班", "飞机", "机票", "高铁", "火车", "动车", "酒店", "靠窗", "过道", "出差", "旅行", "旅游", "打车", "地铁", "开车", "签证", "里程", "护照", "座位"),
        WORK to listOf("工作", "公司", "同事", "老板", "领导", "会议", "周报", "日报", "项目", "职位", "上班", "下班", "加班", "客户", "简历", "工位"),
        FAMILY to listOf(
            "妈", "爸", "父母", "老婆", "老公", "妻子", "丈夫", "孩子", "儿子", "女儿", "家人", "宠物", "狗", "猫", "生日", "住在", "家住",
            "地址", "朋友", "女朋友", "男朋友", "爷爷", "奶奶", "外婆", "外公", "哥", "姐", "弟", "妹",
        ),
        SHOPPING to listOf("买", "品牌", "尺码", "衣服", "鞋", "淘宝", "京东", "拼多多", "预算", "会员", "快递"),
        PREFS to listOf("喜欢", "讨厌", "偏好", "习惯", "不喜欢", "爱看", "爱听", "风格", "称呼", "叫我"),
    )

    fun normalize(domain: String?): String? {
        val d = domain?.trim().orEmpty()
        if (d.isEmpty()) return null
        if (d in all) return d
        legacy[d]?.let { return it }
        return all.firstOrNull { d.contains(it) }
    }

    /** Domains whose keywords appear in [text], best first. Food wins over health for "对花生过敏". */
    fun matching(text: String): List<String> {
        val t = text.lowercase()
        return keywords.map { (d, words) -> d to words.count { t.contains(it) } }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { all.indexOf(it.first) })
            .map { it.first }
    }

    fun guess(text: String): String {
        val m = matching(text)
        // An allergy to something edible belongs with food, where the 饮食 group is checked before suggesting meals.
        if (HEALTH in m && FOOD in m) return FOOD
        return m.firstOrNull() ?: OTHER
    }
}

/** A fact the extractor proposes; nothing is stored until the user confirms it. */
data class CandidateFact(val text: String, val domain: String, val inferred: Boolean)

/**
 * The cheap post-turn extraction call. Shape follows mem0's USER_MEMORY_EXTRACTION_PROMPT (facts only from the
 * user's own messages, few-shot, JSON `{"facts": [...]}`, same language as the user), narrowed to long-lived
 * facts and preferences, with domain + "inferred" per fact and existing memories passed in for de-duplication.
 */
object ExtractionPrompt {
    const val MAX_INPUT = 1200
    const val MAX_FACT = 120

    val SYSTEM = """
        你是 Helium 的记忆整理器。从对话中**只根据用户自己说的话**，找出以后还用得上的长期事实和偏好，整理成独立、简短的一句话。
        不要从助手或系统的话里提取。不要提取一次性的请求、闲聊、问题本身或临时状态。

        值得记的类型：饮食禁忌和口味、过敏和健康情况、出行偏好、工作信息、家人和重要的人、常用地址、购物尺码和品牌、长期习惯和喜好。

        每条事实：
        - 一句话、用用户的语言（通常是简体中文）、不要以“用户”开头，保留条件（例如“出差时住高楼层”）。
        - domain 取其一：饮食 / 健康 / 出行 / 工作 / 家庭 / 购物 / 偏好 / 其他。食物过敏归“饮食”。
        - inferred：用户明确说出的为 false；是你从话里推断出来的为 true（尽量少推断）。
        - 已经在「已有记忆」里的不要重复。

        只输出 JSON，不要任何解释：{"facts":[{"text":"…","domain":"…","inferred":false}]}
        没有可记的就输出 {"facts":[]}

        示例：
        用户：你好
        输出：{"facts":[]}
        用户：我对花生过敏，推荐个零食
        输出：{"facts":[{"text":"对花生过敏","domain":"饮食","inferred":false}]}
        用户：帮我订周五去上海的高铁，我一般坐靠窗
        输出：{"facts":[{"text":"坐高铁一般选靠窗座位","domain":"出行","inferred":false}]}
        用户：我女儿下周三过生日，她喜欢恐龙
        输出：{"facts":[{"text":"有个女儿，喜欢恐龙","domain":"家庭","inferred":false}]}
        用户：今天好累，帮我查下天气
        输出：{"facts":[]}
    """.trimIndent()

    fun build(user: String, assistant: String, existing: List<String>, today: String): String = buildString {
        append("今天是 ").append(today).append("。\n\n")
        append("已有记忆：\n")
        if (existing.isEmpty()) append("（无）\n") else existing.take(40).forEach { append("- ").append(it.take(MAX_FACT)).append('\n') }
        append("\n<conversation>\n")
        append("用户：").append(user.trim().take(MAX_INPUT)).append('\n')
        if (assistant.isNotBlank()) append("助手：").append(assistant.trim().take(400)).append('\n')
        append("</conversation>\n\n输出：")
    }

    private val firstPerson = Regex("我|俺|本人|咱|\\bmy\\b|\\bi'm\\b|\\bi am\\b|\\bi\\b|记住|记一下|别忘", RegexOption.IGNORE_CASE)
    private val smallTalk = Regex("^(你好|您好|嗨|哈喽|hi|hello|hey|谢谢|多谢|好的|好|嗯|ok|收到|在吗)[!！。.~～\\s]*$", RegexOption.IGNORE_CASE)

    /** Skip the call for turns that can't carry a personal fact (saves one model call on most questions). */
    fun worthTrying(user: String): Boolean {
        val t = user.trim()
        if (t.length < 4 || smallTalk.matches(t)) return false
        return firstPerson.containsMatchIn(t)
    }

    /** Parses whatever the model answered. Tolerates code fences, <think>, prose around the JSON, bare arrays and plain strings. */
    fun parse(raw: String): List<CandidateFact> {
        var s = raw.replace(Regex("(?s)<think>.*?</think>"), "").trim()
        s = s.replace(Regex("(?s)```(?:json)?"), "").trim()
        val arr: JSONArray = runCatching {
            val o = s.indexOf('{')
            val a = s.indexOf('[')
            if (o >= 0 && (a < 0 || o < a)) {
                val obj = JSONObject(s.substring(o, s.lastIndexOf('}') + 1))
                obj.optJSONArray("facts") ?: obj.optJSONArray("memory") ?: obj.optJSONArray("memories") ?: JSONArray()
            } else if (a >= 0) {
                JSONArray(s.substring(a, s.lastIndexOf(']') + 1))
            } else {
                JSONArray()
            }
        }.getOrElse { return emptyList() }
        val out = ArrayList<CandidateFact>()
        val seen = HashSet<String>()
        for (i in 0 until arr.length()) {
            val item = arr.opt(i)
            val (text, domain, inferred) = when (item) {
                is String -> Triple(item, null, false)
                is JSONObject -> Triple(
                    item.optString("text").ifBlank { item.optString("fact") }.ifBlank { item.optString("memory") },
                    item.optString("domain").ifBlank { item.optString("category") }.ifBlank { null },
                    item.optBoolean("inferred", false) || item.optString("source") == "推断",
                )
                else -> continue
            }
            val clean = cleanFact(text) ?: continue
            if (!seen.add(ForgetRules.normalize(clean))) continue
            out += CandidateFact(clean, MemoryDomains.normalize(domain) ?: MemoryDomains.guess(clean), inferred)
        }
        return out.take(5)
    }

    /** True when the model answered with an explicit empty list (`{"facts":[]}`) rather than garbage. */
    fun isExplicitEmpty(raw: String): Boolean {
        val s = raw.replace(Regex("(?s)<think>.*?</think>"), "").replace(Regex("(?s)```(?:json)?"), "").trim()
        return Regex("\"(?:facts|memory|memories)\"\\s*:\\s*\\[\\s*\\]").containsMatchIn(s)
    }

    fun cleanFact(text: String): String? {
        var t = text.trim().trim('"', '“', '”', '-', '•', ' ')
        t = t.replace(Regex("^(用户|该用户|user)\\s*", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("\\s+"), " ").trimEnd('。', '.')
        if (t.isBlank() || t.length < 2) return null
        return if (t.length <= MAX_FACT) t else t.take(MAX_FACT)
    }
}

/** "不再保存": a forgotten fact, and any rewording of it, is never proposed or saved again. */
object ForgetRules {
    private val punct = Regex("[\\s\\p{Punct}，。、！？；：“”‘’「」『』（）《》【】…·—~～]+")
    private val subject = Regex("^(用户|我|本人|俺)+")

    fun normalize(s: String): String = subject.replace(punct.replace(s.lowercase(), ""), "")

    private fun bigrams(s: String): Set<String> = if (s.length < 2) setOf(s) else (0 until s.length - 1).map { s.substring(it, it + 2) }.toSet()

    fun similarity(a: String, b: String): Double {
        val x = bigrams(a)
        val y = bigrams(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return 2.0 * x.intersect(y).size / (x.size + y.size)
    }

    /** True when [text] states the same thing as the rule: containment either way, or near-identical wording. */
    fun matches(rule: String, text: String): Boolean {
        val r = normalize(rule)
        val t = normalize(text)
        if (r.length < 2 || t.length < 2) return r.isNotEmpty() && r == t
        if (t.contains(r) || r.contains(t)) return true
        return similarity(r, t) >= 0.7
    }

    fun blocked(rules: List<String>, text: String) = rules.any { matches(it, text) }

    /** Same test for "already saved / already proposed". */
    fun duplicate(existing: List<String>, text: String) = existing.any { matches(it, text) }
}

/** Ranks memories for one turn's context. Small libraries go in whole; safety facts (allergies, diet) always do. */
object MemoryRanker {
    private val critical = Regex("过敏|忌口|不能吃|不吃|素食|吃素|清真|乳糖|麸质|孕|哮喘|禁")

    fun isCritical(text: String) = critical.containsMatchIn(text)

    /** CJK bigrams (single chars for 1-char runs) and lowercased ASCII words. */
    fun terms(text: String): Set<String> {
        val out = HashSet<String>()
        val cjk = StringBuilder()
        val word = StringBuilder()
        fun flushCjk() {
            val s = cjk.toString()
            if (s.length == 1) out += s else for (i in 0 until s.length - 1) out += s.substring(i, i + 2)
            cjk.clear()
        }
        fun flushWord() {
            if (word.length >= 2) out += word.toString()
            word.clear()
        }
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            when {
                SearchText.isCjk(cp) -> { flushWord(); cjk.appendCodePoint(cp) }
                Character.isLetterOrDigit(cp) -> { flushCjk(); word.appendCodePoint(Character.toLowerCase(cp)) }
                else -> { flushCjk(); flushWord() }
            }
            i += Character.charCount(cp)
        }
        flushCjk()
        flushWord()
        out.remove("")
        return out
    }

    fun score(query: String, queryTerms: Set<String>, queryDomains: List<String>, m: MemoryRecord): Double {
        val overlap = terms(m.text).intersect(queryTerms).size
        var s = overlap * 2.0
        if (m.domain in queryDomains) s += 3.0
        if (isCritical(m.text) && (m.domain in queryDomains || overlap > 0)) s += 4.0
        if (!m.inferred) s += 0.25
        return s
    }

    /**
     * Picks what to inject for [query]: anything relevant (term overlap or the query's domain), every critical
     * fact, and when the library is small ([wholeBelow] or fewer) everything. Best first, at most [limit].
     */
    fun rank(query: String, memories: List<MemoryRecord>, limit: Int = 10, wholeBelow: Int = 12): List<MemoryRecord> {
        val qt = terms(query)
        val qd = MemoryDomains.matching(query)
        val scored = memories.map { it to score(query, qt, qd, it) }
        val picked = scored.filter { (m, s) -> s >= 2.0 || isCritical(m.text) || memories.size <= wholeBelow }
        return picked.sortedWith(compareByDescending<Pair<MemoryRecord, Double>> { it.second }.thenByDescending { it.first.createdAt })
            .take(limit).map { it.first }
    }

    /** Words for an FTS4 OR query over the memory index: CJK bigram phrases + ASCII prefixes. */
    fun ftsAnyOf(query: String, max: Int = 24): String? {
        val parts = terms(query).filter { it.isNotBlank() }.take(max).map { t ->
            if (t.codePoints().anyMatch { SearchText.isCjk(it) }) t.codePoints().toArray().joinToString(" ", "\"", "\"") { String(Character.toChars(it)) }
            else "$t*"
        }
        return if (parts.isEmpty()) null else parts.joinToString(" OR ")
    }

    /** The block in the system prompt. Phrased so the model uses the facts without narrating where they came from. */
    fun contextBlock(memories: List<MemoryRecord>): String {
        if (memories.isEmpty()) return ""
        return buildString {
            append("关于用户（背景资料，直接按它办事；不要说“根据你的记忆/我记得你说过”，也不要复述这些条目，除非用户问起）：\n")
            memories.forEach { append("- ").append(it.text).append("（").append(it.domain).append(if (it.inferred) "，推断" else "").append("）\n") }
            append("如果用户的请求和上面的禁忌或偏好有关（例如推荐吃的要避开过敏食物），默默遵守，不用解释原因。")
        }
    }
}

data class RuleRecord(val id: Long, val text: String, val createdAt: Long)

/** Skill as exported (kept here so the export stays one pure function). */
data class ExportSkill(val id: String, val name: String, val description: String, val triggers: List<String>, val enabled: Boolean, val markdown: String)

/** 全部导出: readable Markdown and machine-readable JSON with the same content. */
object MemoryExport {
    const val VERSION = 1
    private val day = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private fun fmt(f: DateTimeFormatter, at: Long, zone: ZoneId) = f.format(Instant.ofEpochMilli(at).atZone(zone))

    fun origin(inferred: Boolean) = if (inferred) "推断" else "你说的"

    fun markdown(memories: List<MemoryRecord>, rules: List<RuleRecord>, skills: List<ExportSkill>, now: Long, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        append("# Helium 记忆导出\n\n")
        append("导出时间：").append(fmt(stamp, now, zone)).append("　·　记忆 ").append(memories.size).append(" 条　·　专长 ").append(skills.size).append(" 个\n")
        val groups = memories.groupBy { it.domain }
        val order = MemoryDomains.all + groups.keys.filter { it !in MemoryDomains.all }
        for (d in order) {
            val list = groups[d] ?: continue
            append("\n## 关于你·").append(d).append("\n\n")
            list.sortedBy { it.createdAt }.forEach { append("- ").append(it.text).append("（").append(origin(it.inferred)).append(" · ").append(fmt(day, it.createdAt, zone)).append("）\n") }
        }
        if (memories.isEmpty()) append("\n（没有记忆）\n")
        if (rules.isNotEmpty()) {
            append("\n## 不再保存\n\n")
            rules.forEach { append("- ").append(it.text).append("\n") }
        }
        if (skills.isNotEmpty()) {
            append("\n## 专长\n")
            skills.forEach { s ->
                append("\n### ").append(s.name).append(if (s.enabled) "" else "（已关闭）").append("\n\n")
                append("````markdown\n").append(s.markdown.trimEnd()).append("\n````\n")
            }
        }
    }

    fun json(memories: List<MemoryRecord>, rules: List<RuleRecord>, skills: List<ExportSkill>, now: Long): String = JSONObject()
        .put("format", "helium-memory")
        .put("version", VERSION)
        .put("exportedAt", Instant.ofEpochMilli(now).toString())
        .put("memories", JSONArray(memories.map {
            JSONObject().put("id", it.id).put("text", it.text).put("domain", it.domain).put("inferred", it.inferred)
                .put("origin", origin(it.inferred)).put("createdAt", Instant.ofEpochMilli(it.createdAt).toString())
        }))
        .put("forgetRules", JSONArray(rules.map { JSONObject().put("text", it.text).put("createdAt", Instant.ofEpochMilli(it.createdAt).toString()) }))
        .put("skills", JSONArray(skills.map {
            JSONObject().put("id", it.id).put("name", it.name).put("description", it.description).put("triggers", JSONArray(it.triggers))
                .put("enabled", it.enabled).put("markdown", it.markdown)
        }))
        .toString(2)

    fun fileName(ext: String, now: Long, zone: ZoneId = ZoneId.systemDefault()) = "helium-memory-${fmt(day, now, zone)}.$ext"
}
