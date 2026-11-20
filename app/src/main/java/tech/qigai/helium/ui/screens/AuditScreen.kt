package tech.qigai.helium.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import tech.qigai.helium.agent.Risk
import tech.qigai.helium.data.db.AuditEntity
import tech.qigai.helium.data.db.AuditOutcome
import tech.qigai.helium.data.db.AuditPeriod
import tech.qigai.helium.ui.components.HeTag
import tech.qigai.helium.ui.components.TagKind
import tech.qigai.helium.ui.components.WhiteCard
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeShapes
import java.time.format.DateTimeFormatter

private val AT = DateTimeFormatter.ofPattern("M月d日 HH:mm:ss")

fun auditKindLabel(k: String) = when (k) {
    "ask" -> "请求确认"
    "approve" -> "已批准"
    "reject" -> "已拒绝"
    "deny" -> "规则拒绝"
    "skip" -> "已跳过"
    "run" -> "执行"
    "error" -> "失败"
    "taint" -> "读到外部内容"
    else -> k
}

/** 审计日志 — every call, approval and result; filter by period, outcome and tool; export the filtered view to JSON. */
@Composable
fun AuditScreen(
    rows: List<AuditEntity>,
    tools: List<String>,
    period: AuditPeriod,
    outcome: AuditOutcome?,
    tool: String?,
    onPeriod: (AuditPeriod) -> Unit,
    onOutcome: (AuditOutcome?) -> Unit,
    onTool: (String?) -> Unit,
    onExport: () -> Unit,
    onOpenPermissions: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SubTopBar("审计日志", onBack, trailing = { TextButton(onClick = onExport, enabled = rows.isNotEmpty()) { Text("导出 JSON") } })
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipRow {
                AuditPeriod.entries.forEach { p -> FilterPill(p.label, period == p) { onPeriod(p) } }
            }
            ChipRow {
                FilterPill("全部结果", outcome == null) { onOutcome(null) }
                AuditOutcome.entries.forEach { o -> FilterPill(o.label, outcome == o) { onOutcome(if (outcome == o) null else o) } }
            }
            if (tools.isNotEmpty()) ChipRow {
                FilterPill("全部工具", tool == null) { onTool(null) }
                tools.forEach { t -> FilterPill(toolDisplayName(t), tool == t) { onTool(if (tool == t) null else t) } }
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${rows.size} 条记录", fontSize = 13.sp, color = HeColors.Ink2, modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenPermissions) { Text("管理授权") }
                }
            }
            if (rows.isEmpty()) {
                item { EmptyState("没有符合条件的记录", "Helium 的每一次工具调用、审批和结果都会记在这里。换个时间范围或筛选条件看看。") }
            }
            items(rows, key = { "a${it.id}" }) { a -> AuditRow(a) }
        }
    }
}

@Composable
private fun AuditRow(a: AuditEntity) {
    var open by remember { mutableStateOf(false) }
    WhiteCard(Modifier.fillMaxWidth(), shape = HeShapes.Row) {
        Column(Modifier.fillMaxWidth().clickable { open = !open }.animateContentSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeTag(
                    auditKindLabel(a.kind),
                    when (a.kind) { "approve", "run" -> TagKind.MINT; "ask", "taint" -> TagKind.AMBER; else -> TagKind.NEUTRAL },
                    dot = false,
                )
                if (a.tainted && a.kind != "taint") {
                    Spacer(Modifier.width(6.dp))
                    HeTag("taint", TagKind.AMBER, dot = false)
                }
                if (a.risk == Risk.HIGH.name) {
                    Spacer(Modifier.width(6.dp))
                    HeTag("高风险", TagKind.NEUTRAL, dot = false)
                }
                Spacer(Modifier.width(8.dp))
                Text(toolDisplayName(a.tool), fontSize = 14.sp, color = HeColors.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ChatViewModel.localTime(a.at).format(AT), fontSize = 12.sp, color = HeColors.Ink3)
            }
            if (a.target.isNotBlank() && a.target != "*") {
                Spacer(Modifier.height(4.dp))
                Text("目标：${a.target}", fontSize = 12.sp, color = HeColors.Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (a.detail.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    a.detail, fontSize = 12.sp, lineHeight = 17.sp, color = HeColors.Ink2,
                    maxLines = if (open) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun FilterPill(label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(32.dp)
            .clip(HeShapes.Pill)
            .background(if (active) HeColors.AccentTint else Color.White)
            .then(if (active) Modifier else Modifier.border(1.dp, HeColors.Hair, HeShapes.Pill))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, maxLines = 1, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, color = if (active) HeColors.Accent else HeColors.Ink)
    }
}
