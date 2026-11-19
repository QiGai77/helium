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
    data class Ask(val risk: Risk, val scopes: List<GrantScope>, val warning: String?) : Verdict
    data class Deny(val reason: String) : Verdict
}

/**
 * The gate every tool call passes before it runs. Decision order:
 * deny list → high risk (always ask, once only) → tainted exfiltration (always ask, once only) →
 * standing grant → risk × trust mode.
 */
class Sentinel(
    private val grants: GrantStore,
    private val mode: () -> TrustMode = { TrustMode.STANDARD },
    private val denied: Set<String> = emptySet(),
    private val clock: () -> Long = ::now,
) {
    suspend fun guard(tool: Tool, args: JSONObject, conversationId: String, tainted: Boolean): Verdict {
        if (tool.name in denied) return Verdict.Deny("「${tool.name}」已被禁用")
        val risk = tool.risk(args)
        if (risk == Risk.HIGH) return Verdict.Ask(risk, listOf(GrantScope.ONCE), null)
        if (tainted && tool.exfiltrates) {
            return Verdict.Ask(risk, listOf(GrantScope.ONCE), "这次任务读过网页等外部内容，之后要把数据发出去，请确认不是网页里的指令在操纵它。")
        }
        grants.active(tool.name, tool.target(args), conversationId, clock())?.let { return Verdict.Allow("已有授权：${it.scope.label}") }
        val m = mode()
        return when {
            risk == Risk.LOW -> Verdict.Allow("低风险")
            m == TrustMode.RELAXED -> Verdict.Allow("放手模式")
            m == TrustMode.CAUTIOUS -> Verdict.Ask(risk, listOf(GrantScope.ONCE, GrantScope.CONVERSATION), null)
            else -> Verdict.Ask(risk, GrantScope.entries.toList(), null)
        }
    }

    /** Stores the grant the user chose on an approval card (nothing for "仅这一次"). */
    suspend fun remember(tool: Tool, args: JSONObject, scope: GrantScope, conversationId: String) {
        grantFor(tool, args, scope, conversationId)?.let { grants.add(it) }
    }

    fun grantFor(tool: Tool, args: JSONObject, scope: GrantScope, conversationId: String): Grant? {
        val t = clock()
        return when (scope) {
            GrantScope.ONCE -> null
            GrantScope.CONVERSATION -> Grant(tool.name, tool.target(args), scope, conversationId, null, createdAt = t)
            GrantScope.DAY -> Grant(tool.name, tool.target(args), scope, null, t + 24 * 3600_000L, createdAt = t)
            GrantScope.FOREVER -> Grant(tool.name, tool.target(args), scope, null, null, createdAt = t)
        }
    }
}

/** Matches a stored grant against a call; "*" targets cover everything for that tool. */
fun Grant.covers(tool: String, target: String, conversationId: String, now: Long): Boolean =
    this.tool == tool && (this.target == "*" || this.target == target) &&
        (expiresAt == null || expiresAt > now) &&
        (scope != GrantScope.CONVERSATION || this.conversationId == conversationId)
