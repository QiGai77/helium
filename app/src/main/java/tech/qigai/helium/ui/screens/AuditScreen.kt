package tech.qigai.helium.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.qigai.helium.agent.GrantScope
import tech.qigai.helium.data.db.AuditEntity
import tech.qigai.helium.data.db.GrantEntity
import tech.qigai.helium.ui.components.HeTag
import tech.qigai.helium.ui.components.TagKind
import tech.qigai.helium.ui.components.WhiteCard
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeShapes
import java.time.format.DateTimeFormatter

private val AT = DateTimeFormatter.ofPattern("M月d日 HH:mm:ss")

private fun kindLabel(k: String) = when (k) {
    "ask" -> "请求确认"
    "approve" -> "已批准"
    "reject" -> "已拒绝"
    "deny" -> "规则拒绝"
    "run" -> "执行"
    "error" -> "失败"
    else -> k
}

/** S8 权限与审计 — standing grants (revocable one by one) and the append-only audit log. */
@Composable
fun AuditScreen(
    grants: List<GrantEntity>,
    audit: List<AuditEntity>,
    onRevoke: (Long) -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SubTopBar("权限与审计", onBack, trailing = { TextButton(onClick = onExport, enabled = audit.isNotEmpty()) { Text("导出") } })
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text("已授予的授权", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink) }
            if (grants.isEmpty()) {
                item { Text("还没有。审批时选「本对话 / 24 小时 / 永久」会出现在这里，可随时撤销。", fontSize = 13.sp, lineHeight = 19.sp, color = HeColors.Ink2) }
            }
            items(grants, key = { "g${it.id}" }) { g ->
                WhiteCard(Modifier.fillMaxWidth(), shape = HeShapes.Row) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${toolLabel(g.tool, null)} · ${g.target}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val scope = runCatching { GrantScope.valueOf(g.scope) }.getOrNull()
                            Text(
                                (scope?.label ?: g.scope) + (g.expiresAt?.let { " · 到期 " + ChatViewModel.localTime(it).format(AT) } ?: ""),
                                fontSize = 12.sp, color = HeColors.Ink2,
                            )
                        }
                        TextButton(onClick = { onRevoke(g.id) }) { Text("撤销", color = HeColors.CoralInk) }
                    }
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                Text("审计日志", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink)
            }
            if (audit.isEmpty()) {
                item { Text("Helium 的每一次工具调用、审批和结果都会记在这里。", fontSize = 13.sp, color = HeColors.Ink2) }
            }
            items(audit, key = { "a${it.id}" }) { a ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HeTag(
                            kindLabel(a.kind),
                            when (a.kind) { "approve", "run" -> TagKind.MINT; "ask" -> TagKind.AMBER; else -> TagKind.NEUTRAL },
                            dot = false,
                        )
                        Spacer(Modifier.padding(start = 8.dp))
                        Text(toolLabel(a.tool, null), fontSize = 14.sp, color = HeColors.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(ChatViewModel.localTime(a.at).format(AT), fontSize = 12.sp, color = HeColors.Ink3)
                    }
                    if (a.detail.isNotBlank()) Text(a.detail, fontSize = 12.sp, color = HeColors.Ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
