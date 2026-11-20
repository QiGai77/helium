package tech.qigai.helium.chat

import org.json.JSONObject
import tech.qigai.helium.agent.AgentEvent

/*
 * Pure chat logic (no Android): search-text normalisation and FTS query building, auto-title prompt and cleanup,
 * interrupted-turn recovery and image downscale maths. Everything here is unit-tested on the JVM.
 */

/**
 * FTS4's `simple` tokenizer keeps a run of non-ASCII characters as one token, so "今天天气不错" would only match
 * the whole sentence. We index CJK one character per token ("今 天 天 气 不 错") and search with a phrase of the
 * query's characters ("天 气"), which matches adjacent characters — the same idea as RikkaHub's per-character
 * index, done with FTS4 phrases instead of FTS5 NEAR.
 */
object SearchText {
    fun isCjk(cp: Int): Boolean = when (Character.UnicodeScript.of(cp)) {
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL, Character.UnicodeScript.BOPOMOFO -> true
        else -> false
    }

    /** Text as stored in `events.searchText`: CJK split per character, other letters/digits lowercased words. */
    fun normalize(text: String): String {
        val sb = StringBuilder(text.length * 2)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            when {
                isCjk(cp) -> { sb.append(' '); sb.appendCodePoint(cp); sb.append(' ') }
                Character.isLetterOrDigit(cp) -> sb.appendCodePoint(Character.toLowerCase(cp))
                else -> sb.append(' ')
            }
            i += Character.charCount(cp)
        }
        return sb.toString().trim().replace(Regex(" +"), " ")
    }

    /** Searchable text for one stored event row; only user and final assistant text is indexed. */
    fun forEvent(kind: String, json: String): String {
        if (kind != "user" && kind != "assistant") return ""
        val text = runCatching { JSONObject(json).optString("text") }.getOrDefault("")
        return normalize(text)
    }

    fun forEvent(e: AgentEvent): String = when (e) {
        is AgentEvent.UserMessage -> normalize(e.text)
        is AgentEvent.AssistantMessage -> normalize(e.text)
        else -> ""
    }
}

object FtsQuery {
    private const val MAX_TERMS = 8
    private const val MAX_PHRASE = 16

    /**
     * Turns whatever the user typed into a safe FTS4 MATCH expression, or null when nothing searchable is left.
     * CJK runs become quoted per-character phrases, other words become prefix terms; every operator, quote and
     * bracket the user typed is dropped, so the expression can never be a syntax error. Terms are ANDed.
     */
    fun build(input: String): String? {
        val terms = ArrayList<String>()
        val cjk = StringBuilder()
        val word = StringBuilder()
        fun flushCjk() {
            if (cjk.isEmpty()) return
            val cps = cjk.codePoints().toArray().take(MAX_PHRASE)
            terms += cps.joinToString(" ", prefix = "\"", postfix = "\"") { String(Character.toChars(it)) }
            cjk.clear()
        }
        fun flushWord() {
            if (word.isEmpty()) return
            terms += "$word*"
            word.clear()
        }
        var i = 0
        while (i < input.length) {
            val cp = input.codePointAt(i)
            when {
                SearchText.isCjk(cp) -> { flushWord(); cjk.appendCodePoint(cp) }
                Character.isLetterOrDigit(cp) -> { flushCjk(); word.appendCodePoint(Character.toLowerCase(cp)) }
                else -> { flushCjk(); flushWord() }
            }
            i += Character.charCount(cp)
        }
        flushCjk()
        flushWord()
        // FTS4 treats bare AND / OR / NOT / NEAR as operators: those words are already suffixed with '*', so safe.
        val kept = terms.distinct().take(MAX_TERMS)
        return if (kept.isEmpty()) null else kept.joinToString(" ")
    }

    /** The raw keywords used for snippet highlighting: CJK runs and words, as typed. */
    fun keywords(input: String): List<String> =
        input.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
}

object Snippet {
    /** A one-line window of [text] around the first keyword hit, with ellipses where it was cut. */
    fun around(text: String, query: String, radius: Int = 24): String {
        val flat = text.replace(Regex("\\s+"), " ").trim()
        val lower = flat.lowercase()
        val hit = FtsQuery.keywords(query).map { lower.indexOf(it.lowercase()) }.filter { it >= 0 }.minOrNull()
            ?: return flat.take(radius * 2).let { if (flat.length > it.length) "$it…" else it }
        val start = (hit - radius).coerceAtLeast(0)
        val end = (hit + radius * 2).coerceAtMost(flat.length)
        return (if (start > 0) "…" else "") + flat.substring(start, end) + (if (end < flat.length) "…" else "")
    }
}

/** Auto title after the first exchange (prompt shape borrowed from RikkaHub's TitleSummary, rewritten for zh). */
object TitlePrompt {
    const val MAX_CHARS = 16
    private const val MAX_INPUT = 600

    fun build(user: String, assistant: String): String = """
        下面 <content> 里是用户和助手的第一轮对话。请为这段对话起一个简短的标题。
        要求：
        1. 使用用户主要使用的语言（通常是简体中文）
        2. 不超过 12 个字，不要标点、引号、表情或"标题："之类的前缀
        3. 只输出标题本身

        <content>
        用户：${user.trim().take(MAX_INPUT)}
        助手：${assistant.trim().take(MAX_INPUT)}
        </content>
    """.trimIndent()

    /** Cleans whatever the model answered into a title, or null when nothing usable came back. */
    fun clean(raw: String): String? {
        var t = raw.replace(Regex("(?s)<think>.*?</think>"), "")
        t = t.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        t = t.replace(Regex("^(标题|题目|title)\\s*[:：]\\s*", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("[\\p{Punct}“”‘’「」『』《》【】（）、，。！？；：…·—~`*#\\s]+"), " ").trim()
        t = t.replace(Regex(" +"), " ")
        if (t.isEmpty()) return null
        val cps = t.codePoints().toArray()
        return if (cps.size <= MAX_CHARS) t else String(cps, 0, MAX_CHARS).trim()
    }

    /** Provisional title shown until the model's title arrives: first line of the first message. */
    fun provisional(text: String, hasImages: Boolean = false): String =
        text.lineSequence().firstOrNull()?.trim()?.take(24)?.ifBlank { null } ?: if (hasImages) "图片" else "新对话"
}

/** Streamed text saved while a reply is in flight, so a killed process can show what it had. */
data class PendingReply(val text: String, val reasoning: String)

object Recovery {
    /**
     * When a conversation is opened and nothing is running, an unfinished tail means the process died mid-turn.
     * Returns the "已中断" assistant event to append (carrying any streamed text), or null when the tail is a
     * finished, stopped or failed answer.
     */
    fun interruptedTail(events: List<AgentEvent>, pending: PendingReply?): AgentEvent.AssistantMessage? {
        val last = events.lastOrNull() ?: return null
        val unfinished = when (last) {
            is AgentEvent.UserMessage -> true
            is AgentEvent.ToolObservation -> true
            is AgentEvent.ApprovalRequest -> true
            is AgentEvent.ApprovalDecision -> true
            is AgentEvent.AssistantMessage -> last.toolCalls.isNotEmpty() && !last.stopped && last.error == null && !last.interrupted
        }
        if (!unfinished) return null
        return AgentEvent.AssistantMessage(
            text = pending?.text.orEmpty(),
            reasoning = pending?.reasoning.orEmpty(),
            interrupted = true,
            at = maxOf(System.currentTimeMillis(), last.at + 1),
        )
    }
}

object ImageScale {
    const val MAX_EDGE = 1568

    /** Target size so the longest edge is at most [max], preserving aspect ratio. */
    fun target(width: Int, height: Int, max: Int = MAX_EDGE): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 0 to 0
        val longest = maxOf(width, height)
        if (longest <= max) return width to height
        val s = max.toDouble() / longest
        return maxOf(1, Math.round(width * s).toInt()) to maxOf(1, Math.round(height * s).toInt())
    }

    /** Largest power-of-two BitmapFactory sample size that still decodes at least [max] on the longest edge. */
    fun sampleSize(width: Int, height: Int, max: Int = MAX_EDGE): Int {
        var s = 1
        val longest = maxOf(width, height)
        while (longest / (s * 2) >= max) s *= 2
        return s
    }
}
