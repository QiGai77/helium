package tech.qigai.helium.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.qigai.helium.agent.GrantScope
import tech.qigai.helium.agent.Risk
import tech.qigai.helium.data.db.GrantEntity
import tech.qigai.helium.ui.components.HeTag
import tech.qigai.helium.ui.components.TagKind
import tech.qigai.helium.ui.components.WhiteCard
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeShapes
import java.time.format.DateTimeFormatter

private val EXP = DateTimeFormatter.ofPattern("M月d日 HH:mm")

/** One registered tool and the risk it declares (with the policy floor applied). */
data class ToolRiskRow(val name: String, val risk: Risk)

/** Chinese name for a tool id, used on the 权限 and 审计 pages. */
fun toolDisplayName(name: String): String = when (name) {
    "get_current_time" -> "查看时间"
    "web_search" -> "网页搜索"
    "web_fetch" -> "读取网页"
    "memory_search" -> "查找记忆"
    "memory_save" -> "保存记忆"
    "memory_forget" -> "遗忘记忆"
    "calendar_read" -> "读取日历"
    "notify" -> "发通知"
    "sms_send" -> "发短信"
    "reminder_create" -> "创建提醒"
    "task_create" -> "创建定时任务"
    "task_list" -> "查看定时任务"
    "task_update" -> "修改定时任务"
    "" -> "—"
    else -> name
}

/**
 * 权限 — every standing grant (bound to tool + target) with per-item revoke. Revoking deletes the row; the sentinel
 * reads grants on every call, so the next call asks again. Below: what each tool's risk is.
 */
@Composable
fun PermissionsScreen(
    grants: List<GrantEntity>,
    conversationTitles: Map<String, String>,
    tools: List<ToolRiskRow>,
    now: Long,
    onRevoke: (GrantEntity) -> Unit,
    onRevokeAll: () -> Unit,
    onOpenAudit: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmAll by remember { mutableStateOf(false) }
    val live = grants.filter { it.expiresAt == null || it.expiresAt > now }
    Column(Modifier.fillMaxSize()) {
        SubTopBar("权限", onBack, trailing = { TextButton(onClick = onOpenAudit) { Text("审计日志") } })
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("已授予的授权", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink, modifier = Modifier.weight(1f))
                    if (live.isNotEmpty()) TextButton(onClick = { confirmAll = true }) { Text("全部撤销", color = HeColors.CoralInk) }
                }
                Text(
                    "授权按「工具 + 目标」生效。高风险操作每次都会问你，不能长期授权；读过网页之后要发出数据也会再问。",
                    fontSize = 13.sp, lineHeight = 19.sp, color = HeColors.Ink2,
                )
            }
            if (live.isEmpty()) {
                item { EmptyState("还没有授权", "审批时选「本对话 / 24 小时 / 永久」会出现在这里，可以随时撤销。") }
            }
            items(live, key = { "g${it.id}" }) { g ->
                WhiteCard(Modifier.fillMaxWidth(), shape = HeShapes.Row) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(toolDisplayName(g.tool), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink)
                            Text("目标：" + if (g.target == "*") "全部" else g.target, fontSize = 13.sp, color = HeColors.Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val scope = runCatching { GrantScope.valueOf(g.scope) }.getOrNull()
                            val where = when (scope) {
                                GrantScope.CONVERSATION -> "本对话「${conversationTitles[g.conversationId] ?: "已删除的对话"}」"
                                GrantScope.DAY -> "24 小时 · 到 " + ChatViewModel.localTime(g.expiresAt ?: now).format(EXP)
                                GrantScope.FOREVER -> "永久"
                                else -> g.scope
                            }
                            Text(where, fontSize = 12.sp, color = HeColors.Ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = { onRevoke(g) }) { Text("撤销", color = HeColors.CoralInk) }
                    }
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                Text("工具与风险等级", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink)
                Text("低风险直接执行；中风险按授权或询问；高风险每次都要你批准。", fontSize = 13.sp, color = HeColors.Ink2)
            }
            items(tools, key = { "t${it.name}" }) { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(toolDisplayName(t.name), fontSize = 15.sp, color = HeColors.Ink, modifier = Modifier.weight(1f))
                    Text(t.name, fontSize = 12.sp, color = HeColors.Ink3)
                    Spacer(Modifier.width(8.dp))
                    HeTag(
                        "风险 ${t.risk.label}",
                        when (t.risk) { Risk.HIGH -> TagKind.AMBER; Risk.MEDIUM -> TagKind.ACCENT; Risk.LOW -> TagKind.MINT },
                        dot = false,
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            confirmButton = { TextButton(onClick = { confirmAll = false; onRevokeAll() }) { Text("全部撤销", color = HeColors.CoralInk) } },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("取消") } },
            title = { Text("撤销全部 ${live.size} 项授权？") },
            text = { Text("之后每个需要授权的操作都会重新询问你。") },
            containerColor = Color.White,
        )
    }
}
