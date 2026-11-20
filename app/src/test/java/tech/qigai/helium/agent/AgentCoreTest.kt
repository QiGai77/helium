package tech.qigai.helium.agent

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import tech.qigai.helium.agent.*
import tech.qigai.helium.agent.tools.*

private class FakeGrants : GrantStore {
    val list = mutableListOf<Grant>()
    override suspend fun active(tool: String, target: String, conversationId: String, now: Long) = list.firstOrNull { it.covers(tool, target, conversationId, now) }
    override suspend fun add(grant: Grant) { list += grant }
}
private class FakeMem : MemoryStore {
    val items = mutableListOf<MemoryRecord>()
    val proposed = mutableListOf<Pair<String, String>>()
    override suspend fun propose(text: String, domain: String, inferred: Boolean, conversationId: String): ProposeResult { proposed += conversationId to text; return ProposeResult.PROPOSED }
    override suspend fun search(query: String, limit: Int) = items.filter { r -> query.split(" ").any { it.isNotBlank() && r.text.contains(it) } }
    override suspend fun add(text: String, domain: String, inferred: Boolean): Long { items += MemoryRecord(items.size + 1L, text, domain, inferred, 0); return items.size.toLong() }
    override suspend fun forget(id: Long) = items.firstOrNull { it.id == id }?.also { items.remove(it) }
    override suspend fun blocked(text: String) = false
}
/** A medium-risk tool that writes straight to the store (memory_save only proposes since M4). */
private class NoteTool(private val mem: FakeMem) : Tool {
    override val spec = ToolSpec("note_save", "", params(Triple("text", "string", ""), Triple("domain", "string", "")))
    override fun risk(args: JSONObject) = Risk.MEDIUM
    override fun target(args: JSONObject) = "memory"
    override suspend fun run(args: JSONObject, ctx: ToolContext): ToolOutput { mem.add(args.optString("text"), args.optString("domain"), false); return ToolOutput("ok") }
}
private class ListSink : EventSink { val e = mutableListOf<AgentEvent>(); override suspend fun append(conversationId: String, event: AgentEvent) { e += event } }

private fun scripted(vararg turns: ModelTurn) = object : ModelRunner {
    var i = 0
    override suspend fun run(system: String, history: List<ModelMessage>, tools: List<ToolSpec>, onDelta: (String, String) -> Unit): ModelTurn {
        val t = turns[minOf(i, turns.size - 1)]; i++; onDelta(t.text, ""); return t
    }
}
private fun check(c: Boolean, msg: String) { if (!c) error("FAIL: $msg") else println("ok  $msg") }

class AgentCoreTest {
@org.junit.Test fun scenarios() = runBlocking {
    val mem = FakeMem(); val grants = FakeGrants()
    val reg = ToolRegistry(listOf(TimeTool(), NoteTool(mem), MemorySaveTool(mem), MemorySearchTool(mem), MemoryForgetTool(mem)))
    val sentinel = Sentinel(grants)

    // 1. plain answer
    run {
        val sink = ListSink(); val ev = mutableListOf<AgentEvent>(AgentEvent.UserMessage("hi"))
        val st = AgentController(scripted(ModelTurn("你好", "", emptyList(), "m")), reg, sentinel, { _, r -> AgentEvent.ApprovalDecision(r.requestId, true) }, sink).run("c1", "", ev, object : AgentListener {})
        check(st == AgentState.FINISHED && sink.e.size == 1, "plain answer finishes")
    }
    // 2. low risk tool runs without approval, then answer
    run {
        val sink = ListSink(); val ev = mutableListOf<AgentEvent>(AgentEvent.UserMessage("几点"))
        var asked = 0
        AgentController(scripted(ModelTurn("", "", listOf(ToolCall("a", "get_current_time", "{}")), "m"), ModelTurn("现在…", "", emptyList(), "m")), reg, sentinel,
            { _, r -> asked++; AgentEvent.ApprovalDecision(r.requestId, true) }, sink).run("c1", "", ev, object : AgentListener {})
        check(asked == 0 && sink.e.any { it is AgentEvent.ToolObservation && !it.isError }, "low risk runs without asking")
        val h = History.toModel(ev)
        check(h.map { it.role } == listOf(ModelMessage.Role.USER, ModelMessage.Role.ASSISTANT, ModelMessage.Role.TOOL, ModelMessage.Role.ASSISTANT), "history projection pairs tool result")
    }
    // 3. medium risk asks; rejection blocks; conversation grant persists
    run {
        val sink = ListSink(); val ev = mutableListOf<AgentEvent>(AgentEvent.UserMessage("记住我花生过敏"))
        AgentController(scripted(ModelTurn("", "", listOf(ToolCall("b", "note_save", """{"text":"对花生过敏","domain":"饮食"}""")), "m"), ModelTurn("好", "", emptyList(), "m")), reg, sentinel,
            { _, r -> AgentEvent.ApprovalDecision(r.requestId, false) }, sink).run("c1", "", ev, object : AgentListener {})
        check(mem.items.isEmpty() && sink.e.any { it is AgentEvent.ApprovalRequest }, "rejected save does not write")
        val ev2 = mutableListOf<AgentEvent>(AgentEvent.UserMessage("记住"))
        AgentController(scripted(ModelTurn("", "", listOf(ToolCall("c", "note_save", """{"text":"对花生过敏","domain":"饮食"}""")), "m"), ModelTurn("好", "", emptyList(), "m")), reg, sentinel,
            { _, r -> AgentEvent.ApprovalDecision(r.requestId, true, GrantScope.CONVERSATION) }, ListSink()).run("c1", "", ev2, object : AgentListener {})
        check(mem.items.size == 1 && grants.list.size == 1, "approved save writes and stores grant")
        var asked = 0
        AgentController(scripted(ModelTurn("", "", listOf(ToolCall("d", "note_save", """{"text":"喜欢靠窗","domain":"出行"}""")), "m"), ModelTurn("好", "", emptyList(), "m")), reg, sentinel,
            { _, r -> asked++; AgentEvent.ApprovalDecision(r.requestId, true) }, ListSink()).run("c1", "", mutableListOf(AgentEvent.UserMessage("x")), object : AgentListener {})
        check(asked == 0 && mem.items.size == 2, "conversation grant skips asking in same conversation")
        AgentController(scripted(ModelTurn("", "", listOf(ToolCall("e", "note_save", """{"text":"x","domain":"出行"}""")), "m"), ModelTurn("好", "", emptyList(), "m")), reg, sentinel,
            { _, r -> asked++; AgentEvent.ApprovalDecision(r.requestId, false) }, ListSink()).run("c2", "", mutableListOf(AgentEvent.UserMessage("x")), object : AgentListener {})
        check(asked == 1, "new conversation asks again")
    }
    // 3b. memory_save never writes or asks: it proposes a candidate for the user to confirm
    run {
        var asked = 0
        val before = mem.items.size
        val sink = ListSink()
        AgentController(scripted(ModelTurn("", "", listOf(ToolCall("p", "memory_save", """{"text":"对花生过敏","domain":"饮食"}""")), "m"), ModelTurn("好", "", emptyList(), "m")), reg, sentinel,
            { _, r -> asked++; AgentEvent.ApprovalDecision(r.requestId, true) }, sink).run("c9", "", mutableListOf(AgentEvent.UserMessage("记住我花生过敏")), object : AgentListener {})
        check(asked == 0 && mem.proposed == listOf("c9" to "对花生过敏") && mem.items.size == before, "memory_save proposes a candidate without approval")
        check(sink.e.any { it is AgentEvent.ToolObservation && it.output.contains("候选") }, "memory_save tells the model it is only a candidate")
    }
    // 4. high risk always asks, only ONCE offered
    run {
        val sink = ListSink(); var scopes: List<GrantScope> = emptyList()
        grants.list += Grant("memory_forget", "*", GrantScope.FOREVER, null, null)
        AgentController(scripted(ModelTurn("", "", listOf(ToolCall("f", "memory_forget", """{"id":1}""")), "m"), ModelTurn("好", "", emptyList(), "m")), reg, sentinel,
            { _, r -> scopes = r.scopes; AgentEvent.ApprovalDecision(r.requestId, true) }, sink).run("c1", "", mutableListOf(AgentEvent.UserMessage("忘掉")), object : AgentListener {})
        check(scopes == listOf(GrantScope.ONCE), "high risk ignores grants and offers once only")
    }
    // 5. stuck detection
    run {
        val st = AgentController(scripted(ModelTurn("", "", listOf(ToolCall("g", "get_current_time", "{}")), "m")), ToolRegistry(listOf(object : Tool {
            override val spec = ToolSpec("get_current_time", "", JSONObject()); override fun risk(args: JSONObject) = Risk.LOW
            override suspend fun run(args: JSONObject, ctx: ToolContext) = ToolOutput("same")
        })), sentinel, { _, r -> AgentEvent.ApprovalDecision(r.requestId, true) }, ListSink()).run("c1", "", mutableListOf(AgentEvent.UserMessage("x")), object : AgentListener {})
        check(st == AgentState.STOPPED, "repeated identical call is stopped")
    }
    // 6. codec roundtrip
    run {
        val e = AgentEvent.ApprovalRequest("r", "c", "t", "title", "d", "x", Risk.MEDIUM, listOf(GrantScope.ONCE, GrantScope.DAY), "w")
        check(EventCodec.decode(EventCodec.kind(e), EventCodec.encode(e)) == e, "codec roundtrip")
        val a = AgentEvent.AssistantMessage("t", "r", "m", listOf(ToolCall("1", "n", "{}", "sig")))
        check(EventCodec.decode("assistant", EventCodec.encode(a)) == a, "assistant codec roundtrip")
    }
    check(HtmlText.extract("<html><head><title>T</title></head><body><script>x()</script><p>你好&amp;世界</p><div>二</div></body></html>") == "你好&世界\n二", "html extract")
    check(WebFetchTool.isPrivateHost("127.0.0.1") && WebFetchTool.isPrivateHost("192.168.1.2") && !WebFetchTool.isPrivateHost("8.8.8.8"), "private host check")
    println("ALL PASS")
}
}
