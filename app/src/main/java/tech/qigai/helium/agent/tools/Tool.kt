package tech.qigai.helium.agent.tools

import org.json.JSONObject
import tech.qigai.helium.agent.Risk

/** What the model sees for one tool (name, description, JSON-schema parameters). */
data class ToolSpec(val name: String, val description: String, val parameters: JSONObject)

data class ToolOutput(val text: String, val isError: Boolean = false)

class ToolContext(val conversationId: String)

/**
 * One capability the agent can call. Risk is assessed per call from the actual arguments (so `web_fetch` of a
 * public page is LOW while the same tool posting a form would be HIGH), the way nanoMuse's `assess()` escalates.
 */
interface Tool {
    val spec: ToolSpec
    val name: String get() = spec.name

    fun risk(args: JSONObject): Risk

    /** What the call acts on (a host, a recipient, a memory id). Grants bind to tool + target. */
    fun target(args: JSONObject): String = "*"

    /** Short verb phrase for the approval card ("保存一条记忆"). */
    fun title(args: JSONObject): String = spec.name

    /** Full content the user approves: recipients, body, what gets deleted. */
    fun detail(args: JSONObject): String = args.toString(2)

    /** Output is untrusted external content; marks the run as tainted. */
    val readsUntrusted: Boolean get() = false

    /** Sends data off the device to a third party; asks again when the run is tainted. */
    val exfiltrates: Boolean get() = false

    /** Per-call form of [exfiltrates] (a GET carrying a payload in its query string counts). */
    fun exfiltrates(args: JSONObject): Boolean = exfiltrates

    /** Output is the user's private data (memory, calendar); in a tainted run, calls that echo it ask first. */
    val readsPrivate: Boolean get() = false

    suspend fun run(args: JSONObject, ctx: ToolContext): ToolOutput
}

class ToolRegistry(tools: List<Tool>) {
    private val byName = tools.associateBy { it.name }
    val all: List<Tool> get() = byName.values.toList()
    operator fun get(name: String): Tool? = byName[name]
    val specs: List<ToolSpec> get() = byName.values.map { it.spec }
}

/** Tiny DSL for JSON-schema parameter objects. */
fun params(vararg props: Triple<String, String, String>, required: List<String> = props.map { it.first }): JSONObject {
    val p = JSONObject()
    props.forEach { (name, type, desc) -> p.put(name, JSONObject().put("type", type).put("description", desc)) }
    return JSONObject().put("type", "object").put("properties", p).put("required", org.json.JSONArray(required))
}
