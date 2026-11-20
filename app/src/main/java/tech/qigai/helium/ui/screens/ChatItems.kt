package tech.qigai.helium.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import tech.qigai.helium.agent.AgentEvent
import tech.qigai.helium.agent.GrantScope
import tech.qigai.helium.agent.Risk
import tech.qigai.helium.agent.ToolCall
import tech.qigai.helium.agent.search.SearchFormat
import tech.qigai.helium.data.db.ConversationEntity
import tech.qigai.helium.data.db.MessageHit
import tech.qigai.helium.data.llm.Role
import tech.qigai.helium.ui.components.ButtonKind
import tech.qigai.helium.ui.components.HePillButton
import tech.qigai.helium.ui.components.HeTag
import tech.qigai.helium.ui.components.TagKind
import tech.qigai.helium.ui.icons.HeIcons
import tech.qigai.helium.ui.theme.HeButton
import tech.qigai.helium.ui.theme.HeColors
import java.time.format.DateTimeFormatter

/** What the chat list renders, projected from the event stream. */
sealed interface ChatItem {
    val key: String

    data class User(override val key: String, val event: AgentEvent.UserMessage) : ChatItem {
        val text: String get() = event.text
    }
    data class Assistant(
        override val key: String,
        val message: ChatMessage,
        val sources: List<Pair<String, String>> = emptyList(),
        /** `at` of the underlying event (search focus). */
        val at: Long = 0,
    ) : ChatItem
    data class Step(override val key: String, val call: ToolCall?, val observation: AgentEvent.ToolObservation) : ChatItem
    data class Approval(override val key: String, val request: AgentEvent.ApprovalRequest, val decision: AgentEvent.ApprovalDecision?) : ChatItem
}

fun chatItems(events: List<AgentEvent>): List<ChatItem> {
    val calls = HashMap<String, ToolCall>()
    val decisions = events.filterIsInstance<AgentEvent.ApprovalDecision>().associateBy { it.requestId }
    val out = ArrayList<ChatItem>(events.size)
    // Sources from this turn's web_search calls, attached to the turn's final answer as citation chips.
    val turnSources = LinkedHashMap<String, String>()
    events.forEachIndexed { i, e ->
        when (e) {
            is AgentEvent.UserMessage -> { turnSources.clear(); out += ChatItem.User("u$i", e) }
            is AgentEvent.AssistantMessage -> {
                e.toolCalls.forEach { calls[it.id] = it }
                if (e.text.isNotBlank() || e.reasoning.isNotBlank() || e.error != null || e.stopped || e.interrupted) {
                    out += ChatItem.Assistant(
                        "a$i",
                        ChatMessage(i.toLong(), Role.ASSISTANT, e.text, e.reasoning, false, e.error, e.stopped, e.model, ChatViewModel.localTime(e.at), e.interrupted),
                        sources = if (e.toolCalls.isEmpty() && e.text.isNotBlank()) turnSources.map { (url, title) -> title to url }.take(8) else emptyList(),
                        at = e.at,
                    )
                }
            }
            is AgentEvent.ToolObservation -> {
                if (e.tool == "web_search" && !e.isError) SearchFormat.sources(e.output).forEach { (title, url) -> turnSources.putIfAbsent(url, title) }
                out += ChatItem.Step("o$i", calls[e.callId], e)
            }
            is AgentEvent.ApprovalRequest -> out += ChatItem.Approval("r$i", e, decisions[e.requestId])
            is AgentEvent.ApprovalDecision -> Unit
        }
    }
    return out
}

/** Human label for a tool call, shown on step rows and in the timeline. */
fun toolLabel(name: String, arguments: String?): String {
    val args = runCatching { JSONObject(arguments ?: "{}") }.getOrDefault(JSONObject())
    return when (name) {
        "get_current_time" -> "查看当前时间"
        "web_search" -> "搜索「${args.optString("query").take(20)}」"
        "web_fetch" -> "读取网页 " + runCatching { java.net.URI(args.optString("url")).host }.getOrNull().orEmpty()
        "memory_search" -> "查找记忆「${args.optString("query").take(16)}」"
        "memory_save" -> "保存记忆「${args.optString("text").take(16)}」"
        "memory_forget" -> "遗忘一条记忆"
        else -> name
    }
}

@Composable
fun ToolStepRow(item: ChatItem.Step) {
    var open by remember { mutableStateOf(false) }
    val obs = item.observation
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 36.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(HeColors.Mist3)
            .border(1.dp, HeColors.Hair, RoundedCornerShape(16.dp))
            .clickable { open = !open }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(20.dp).clip(CircleShape).background(if (obs.isError) HeColors.CoralTint else HeColors.MintTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (obs.isError) HeIcons.Close else HeIcons.Check, null, tint = if (obs.isError) HeColors.CoralInk else HeColors.MintInk, modifier = Modifier.size(12.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                toolLabel(obs.tool, item.call?.arguments), fontSize = 14.sp, color = HeColors.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (obs.durationMs > 0) Text(formatMs(obs.durationMs), fontSize = 12.sp, color = HeColors.Ink3)
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            Text(obs.output.take(1200), fontSize = 12.sp, lineHeight = 18.sp, color = HeColors.Ink2)
        }
    }
}

private fun formatMs(ms: Long) = if (ms < 1000) "${ms}ms" else "%.1fs".format(ms / 1000.0)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InlineApproval(item: ChatItem.Approval, awaiting: Boolean, onDecide: (Boolean, GrantScope?) -> Unit) {
    val r = item.request
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 36.dp)
            .clip(shape)
            .background(Color.White)
            .border(1.5.dp, if (awaiting) HeColors.Amber else HeColors.Hair, shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeTag("需要你确认", TagKind.AMBER)
            Spacer(Modifier.width(8.dp))
            HeTag("风险 ${r.risk.label}", if (r.risk == Risk.HIGH) TagKind.AMBER else TagKind.NEUTRAL, dot = false)
        }
        Spacer(Modifier.height(10.dp))
        Text(r.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink)
        Spacer(Modifier.height(6.dp))
        Text(r.detail, fontSize = 13.sp, lineHeight = 19.sp, color = HeColors.Ink2)
        if (r.warning != null) {
            Spacer(Modifier.height(8.dp))
            Text(r.warning, fontSize = 13.sp, lineHeight = 19.sp, color = HeColors.CoralInk)
        }
        Spacer(Modifier.height(12.dp))
        val d = item.decision
        when {
            d != null -> Text(
                if (d.approved) "已批准" + (d.scope?.let { "（${it.label}）" } ?: "") else "已拒绝",
                fontSize = 13.sp, color = if (d.approved) HeColors.MintInk else HeColors.Ink3, fontWeight = FontWeight.SemiBold,
            )
            !awaiting -> Text("已失效（任务已停止）", fontSize = 13.sp, color = HeColors.Ink3)
            else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                r.scopes.forEachIndexed { i, s ->
                    HePillButton(
                        if (i == 0) "批准（${s.label}）" else s.label,
                        { onDecide(true, s) },
                        kind = if (i == 0) ButtonKind.PRIMARY else ButtonKind.TONAL,
                        height = HeButton.Small,
                    )
                }
                HePillButton("拒绝", { onDecide(false, null) }, kind = ButtonKind.OUTLINE, height = HeButton.Small)
            }
        }
    }
}

private val LIST_TIME = DateTimeFormatter.ofPattern("M月d日 HH:mm")

/**
 * 对话列表: new, rename, delete, and full-text search across every conversation. A search hit opens its
 * conversation scrolled to the message.
 */
@Composable
fun HistoryDialog(
    conversations: List<ConversationEntity>,
    currentId: String,
    hits: List<MessageHit>,
    searching: Boolean,
    onSearch: (String) -> Unit,
    onOpen: (String) -> Unit,
    onOpenHit: (MessageHit) -> Unit,
    onNew: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var confirm by remember { mutableStateOf<ConversationEntity?>(null) }
    var renaming by remember { mutableStateOf<ConversationEntity?>(null) }
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onSearch(""); onDismiss() },
        confirmButton = { TextButton(onClick = { onSearch(""); onDismiss() }) { Text("关闭") } },
        dismissButton = { TextButton(onClick = onNew) { Text("＋ 新对话", color = HeColors.Accent) } },
        title = { Text("对话") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; onSearch(it) },
                    placeholder = { Text("搜索所有对话里的消息") },
                    singleLine = true,
                    leadingIcon = { Icon(HeIcons.Search, null, tint = HeColors.Ink3, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            Icon(HeIcons.Close, "清除", tint = HeColors.Ink3, modifier = Modifier.size(18.dp).clip(CircleShape).clickable { query = ""; onSearch("") })
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                when {
                    query.isNotBlank() -> SearchHits(hits, searching, onOpenHit)
                    conversations.isEmpty() -> Text("还没有对话。", color = HeColors.Ink2, modifier = Modifier.padding(vertical = 12.dp))
                    else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(conversations, key = { it.id }) { c ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(c.id) }.padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        c.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (c.id == currentId) FontWeight.Bold else FontWeight.Normal, color = HeColors.Ink,
                                    )
                                    Text(ChatViewModel.localTime(c.updatedAt).format(LIST_TIME), fontSize = 12.sp, color = HeColors.Ink3)
                                }
                                TextButton(onClick = { renaming = c }) { Text("重命名", color = HeColors.Ink2, fontSize = 13.sp) }
                                TextButton(onClick = { confirm = c }) { Text("删除", color = HeColors.CoralInk, fontSize = 13.sp) }
                            }
                        }
                    }
                }
            }
        },
    )
    renaming?.let { c ->
        var title by remember(c.id) { mutableStateOf(c.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            confirmButton = {
                TextButton(onClick = { onRename(c.id, title); renaming = null }, enabled = title.isNotBlank()) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("取消") } },
            title = { Text("重命名对话") },
            text = { OutlinedTextField(value = title, onValueChange = { title = it.take(40) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        )
    }
    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            confirmButton = { TextButton(onClick = { onDelete(c.id); confirm = null }) { Text("删除", color = HeColors.CoralInk) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("取消") } },
            title = { Text("删除这个对话？") },
            text = { Text("「${c.title}」和其中的所有消息、图片和步骤会被永久删除。") },
        )
    }
}

@Composable
private fun SearchHits(hits: List<MessageHit>, searching: Boolean, onOpenHit: (MessageHit) -> Unit) {
    if (hits.isEmpty()) {
        Text(if (searching) "搜索中…" else "没有找到包含这个关键词的消息。", color = HeColors.Ink2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
        return
    }
    LazyColumn(Modifier.heightIn(max = 420.dp)) {
        items(hits, key = { it.eventId }) { hit ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpenHit(hit) }.padding(vertical = 8.dp, horizontal = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        hit.conversationTitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Text(ChatViewModel.localTime(hit.at).format(LIST_TIME), fontSize = 11.sp, color = HeColors.Ink3)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    (if (hit.fromUser) "你：" else "Helium：") + hit.snippet,
                    fontSize = 13.sp, lineHeight = 18.sp, color = HeColors.Ink2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Citation chips under an answer that used web_search, in the spirit of chat-demo's source list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourceChips(sources: List<Pair<String, String>>) {
    val uri = LocalUriHandler.current
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        sources.forEachIndexed { i, (title, url) ->
            val host = runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull().orEmpty()
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(HeColors.Mist2)
                    .clickable { runCatching { uri.openUri(url) } }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Accent)
                Spacer(Modifier.width(6.dp))
                Text(
                    host.ifBlank { title },
                    fontSize = 12.sp,
                    color = HeColors.Ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 160.dp),
                )
            }
        }
    }
}
