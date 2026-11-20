package tech.qigai.helium.agent

import org.json.JSONObject
import tech.qigai.helium.agent.tools.Tool

/** How much Helium may do without asking. */
enum class TrustMode(val label: String) { CAUTIOUS("谨慎"), STANDARD("标准"), RELAXED("放手") }

data class Grant(
    val tool: String,
    val target: String,
    val scope: GrantScope,
    val conversationId: String?,
    val expiresAt: Long?,
    val id: Long = 0,
    val createdAt: Long = now(),
)

interface GrantStore {
    suspend fun active(tool: String, target: String, conversationId: String, now: Long): Grant?
    suspend fun add(grant: Grant)
}

sealed interface Verdict {
    data class Allow(val reason: String) : Verdict
    data class Ask(val risk: Risk, val scopes: List<GrantScope>, val warning: String?, val tainted: Boolean = false) : Verdict
    data class Deny(val reason: String) : Verdict
}

/**
 * Risk floor by tool name. A tool declares its own risk per call, but the categories the product promises to always
 * gate (send SMS / email, write or delete calendar, delete memory, change skills) are pinned to HIGH here, so a new
 * tool in one of those families can't ship as LOW by mistake (OpenHands' ConfirmRisky: HIGH always confirms).
 */
object RiskPolicy {
    private val highPrefixes = listOf("sms_", "email_", "mail_", "skill_create", "skill_update", "skill_edit", "skill_delete", "skill_save")
    private val highExact = setOf(
        "sms_send", "email_send", "calendar_create", "calendar_write", "calendar_update", "calendar_delete",
        "memory_forget", "memory_delete", "memory_clear",
    )

    fun floor(tool: String): Risk = when {
        tool in highExact -> Risk.HIGH
        highPrefixes.any { tool.startsWith(it) } -> Risk.HIGH
        tool.startsWith("calendar_") && tool != "calendar_read" -> Risk.HIGH
        else -> Risk.LOW
    }

    fun effective(tool: Tool, args: JSONObject): Risk = maxOf(tool.risk(args), floor(tool.name))

    /** Scopes a card may offer. HIGH: only "仅这一次". */
    fun scopesFor(risk: Risk, mode: TrustMode): List<GrantScope> = when {
        risk == Risk.HIGH -> listOf(GrantScope.ONCE)
        mode == TrustMode.CAUTIOUS -> listOf(GrantScope.ONCE, GrantScope.CONVERSATION)
        else -> GrantScope.entries.toList()
    }

    /** The scope actually honoured: anything not offered on the card (or any scope for HIGH) falls back to ONCE. */
    fun clamp(offered: List<GrantScope>, chosen: GrantScope?): GrantScope =
        if (chosen != null && chosen in offered) chosen else GrantScope.ONCE
}

/**
 * Prompt-injection taint (clean-room, after nanoMuse's design notes): once a run has read untrusted content
 * (web_fetch / web_search), anything that could carry data out — a HIGH action, a tool that sends to a third
 * party, or any call whose arguments contain text read from the user's memory — must be approved on a card,
 * whatever standing grants say.
 */
object Taint {
    const val WARNING = "这次对话读过网页等外部内容，接下来这一步会把数据发出去或做高风险操作。请确认这是你要的，而不是网页里的文字在指挥 Helium。"

    /** Pieces of private text (memory, calendar) read in this run; used to spot them in outgoing arguments. */
    fun fragments(output: String): List<String> {
        val texts = mutableListOf<String>()
        runCatching {
            val arr = org.json.JSONArray(output)
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.optString("text")?.let { texts += it }
        }
        if (texts.isEmpty()) texts += output.lines()
        return texts.flatMap { it.split(Regex("[\\n，。、；;,.!！?？]+")) }
            .map { it.trim() }
            .filter { it.length >= 4 }
            .distinct()
    }

    fun carries(args: JSONObject, sensitive: Collection<String>): Boolean {
        if (sensitive.isEmpty()) return false
        val raw = args.toString()
        val decoded = runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        return sensitive.any { s -> raw.contains(s) || decoded.contains(s) }
    }
}

/**
 * The gate every tool call passes before it runs. Decision order (opencode evaluates deny first, then rules,
 * default ask; OpenHands confirms HIGH regardless of mode):
 * deny list → HIGH (always ask, once only) → tainted exfiltration (always ask, once only) →
 * standing grant → risk × trust mode.
 */
class Sentinel(
    private val grants: GrantStore,
    private val mode: () -> TrustMode = { TrustMode.STANDARD },
    private val denied: Set<String> = emptySet(),
    private val clock: () -> Long = ::now,
) {
    suspend fun guard(
        tool: Tool,
        args: JSONObject,
        conversationId: String,
        tainted: Boolean,
        sensitive: Collection<String> = emptyList(),
    ): Verdict {
        if (tool.name in denied) return Verdict.Deny("「${tool.name}」已被禁用")
        val risk = RiskPolicy.effective(tool, args)
        if (risk == Risk.HIGH) return Verdict.Ask(risk, listOf(GrantScope.ONCE), if (tainted) Taint.WARNING else null, tainted)
        if (tainted && (tool.exfiltrates(args) || Taint.carries(args, sensitive))) {
            return Verdict.Ask(risk, listOf(GrantScope.ONCE), Taint.WARNING, tainted = true)
        }
        grants.active(tool.name, tool.target(args), conversationId, clock())?.let { return Verdict.Allow("已有授权：${it.scope.label}") }
        val m = mode()
        return when {
            risk == Risk.LOW -> Verdict.Allow("低风险")
            m == TrustMode.RELAXED -> Verdict.Allow("放手模式")
            else -> Verdict.Ask(risk, RiskPolicy.scopesFor(risk, m), null)
        }
    }

    /** Stores the grant the user chose on an approval card (nothing for "仅这一次", never anything for HIGH). */
    suspend fun remember(tool: Tool, args: JSONObject, scope: GrantScope, conversationId: String) {
        if (RiskPolicy.effective(tool, args) == Risk.HIGH) return
        grantFor(tool, args, scope, conversationId)?.let { grants.add(it) }
    }

    fun grantFor(tool: Tool, args: JSONObject, scope: GrantScope, conversationId: String): Grant? {
        val t = clock()
        return when (scope) {
            GrantScope.ONCE -> null
            GrantScope.CONVERSATION -> Grant(tool.name, tool.target(args), scope, conversationId, null, createdAt = t)
            GrantScope.DAY -> Grant(tool.name, tool.target(args), scope, null, t + DAY_MS, createdAt = t)
            GrantScope.FOREVER -> Grant(tool.name, tool.target(args), scope, null, null, createdAt = t)
        }
    }

    companion object {
        const val DAY_MS = 24 * 3600_000L
    }
}

/**
 * Matches a stored grant against a call. Targets are bound exactly; "*" covers everything for that tool and a
 * trailing "*" is a prefix pattern ("host:*" style, as opencode's wildcard rules).
 */
fun Grant.covers(tool: String, target: String, conversationId: String, now: Long): Boolean =
    this.tool == tool && targetMatches(this.target, target) &&
        (expiresAt == null || expiresAt > now) &&
        (scope != GrantScope.ONCE) &&
        (scope != GrantScope.CONVERSATION || this.conversationId == conversationId)

fun targetMatches(pattern: String, target: String): Boolean = when {
    pattern == "*" -> true
    pattern.endsWith("*") -> target.startsWith(pattern.dropLast(1))
    else -> pattern == target
}
