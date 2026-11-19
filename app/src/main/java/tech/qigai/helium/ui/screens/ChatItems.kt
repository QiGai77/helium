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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import tech.qigai.helium.data.db.ConversationEntity
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

    data class User(override val key: String, val text: String) : ChatItem
    data class Assistant(override val key: String, val message: ChatMessage) : ChatItem
    data class Step(override val key: String, val call: ToolCall?, val observation: AgentEvent.ToolObservation) : ChatItem
    data class Approval(override val key: String, val request: AgentEvent.ApprovalRequest, val decision: AgentEvent.ApprovalDecision?) : ChatItem
}

fun chatItems(events: List<AgentEvent>): List<ChatItem> {
    val calls = HashMap<String, ToolCall>()
    val decisions = events.filterIsInstance<AgentEvent.ApprovalDecision>().associateBy { it.requestId }
    val out = ArrayList<ChatItem>(events.size)
    events.forEachIndexed { i, e ->
        when (e) {
            is AgentEvent.UserMessage -> out += ChatItem.User("u$i", e.text)
            is AgentEvent.AssistantMessage -> {
                e.toolCalls.forEach { calls[it.id] = it }
                if (e.text.isNotBlank() || e.reasoning.isNotBlank() || e.error != null || e.stopped) {
                    out += ChatItem.Assistant(
                        "a$i",
                        ChatMessage(i.toLong(), Role.ASSISTANT, e.text, e.reasoning, false, e.error, e.stopped, e.model, ChatViewModel.localTime(e.at)),
                    )
                }
            }
            is AgentEvent.ToolObservation -> out += ChatItem.Step("o$i", calls[e.callId], e)
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

@Composable
fun HistoryDialog(
    conversations: List<ConversationEntity>,
    currentId: String,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var confirm by remember { mutableStateOf<ConversationEntity?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text("历史对话") },
        text = {
            if (conversations.isEmpty()) {
                Text("还没有对话。", color = HeColors.Ink2)
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(conversations, key = { it.id }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(c.id) }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    c.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (c.id == currentId) FontWeight.Bold else FontWeight.Normal, color = HeColors.Ink,
                                )
                                Text(ChatViewModel.localTime(c.updatedAt).format(LIST_TIME), fontSize = 12.sp, color = HeColors.Ink3)
                            }
                            TextButton(onClick = { confirm = c }) { Text("删除", color = HeColors.CoralInk) }
                        }
                    }
                }
            }
        },
    )
    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            confirmButton = { TextButton(onClick = { onDelete(c.id); confirm = null }) { Text("删除", color = HeColors.CoralInk) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("取消") } },
            title = { Text("删除这个对话？") },
            text = { Text("「${c.title}」和其中的所有步骤会被永久删除。") },
        )
    }
}
