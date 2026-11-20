@file:OptIn(ExperimentalLayoutApi::class)

package tech.qigai.helium.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.qigai.helium.data.db.ForgetRuleEntity
import tech.qigai.helium.data.db.MemoryCandidateEntity
import tech.qigai.helium.data.db.MemoryEntity
import tech.qigai.helium.memory.MemoryDomains
import tech.qigai.helium.memory.MemoryExport
import tech.qigai.helium.skills.SkillDoc
import tech.qigai.helium.ui.components.ButtonKind
import tech.qigai.helium.ui.components.GlassIcon
import tech.qigai.helium.ui.components.GlassShape
import tech.qigai.helium.ui.components.HePillButton
import tech.qigai.helium.ui.components.HeToggle
import tech.qigai.helium.ui.components.WhiteCard
import tech.qigai.helium.ui.components.heCardShadow
import tech.qigai.helium.ui.icons.HeIcons
import tech.qigai.helium.ui.theme.HeButton
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeShapes

enum class MemTab(val label: String) { ALL("全部"), MEMORY("记忆"), SKILLS("专长") }

/** Everything the 记忆与专长 page can do; wired to RoomMemoryStore / RoomSkillStore in the nav host. */
class MemoryActions(
    val add: (text: String, domain: String) -> Unit,
    val edit: (id: Long, text: String, domain: String, inferred: Boolean) -> Unit,
    val forget: (id: Long) -> Unit,
    val accept: (Long) -> Unit,
    val dismiss: (Long) -> Unit,
    val never: (Long) -> Unit,
    val deleteRule: (Long) -> Unit,
    val clearAll: () -> Unit,
    val exportMarkdown: suspend () -> String,
    val exportJson: suspend () -> String,
    val saveSkill: (SkillDoc) -> Unit,
    val deleteSkill: (String) -> Unit,
    val toggleSkill: (String, Boolean) -> Unit,
)

/** 05 记忆与专长 — memories grouped by domain (你说的 / 推断), pending candidates, management, and 专长. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MemorySkillsScreen(
    memories: List<MemoryEntity>,
    candidates: List<MemoryCandidateEntity>,
    rules: List<ForgetRuleEntity>,
    skills: List<SkillDoc>,
    actions: MemoryActions,
    onBack: () -> Unit,
    toast: (String) -> Unit,
    startTab: MemTab = MemTab.ALL,
) {
    var tab by remember { mutableStateOf(startTab) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MemoryEntity?>(null) }
    var forgetting by remember { mutableStateOf<MemoryEntity?>(null) }
    var managing by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var editingSkill by remember { mutableStateOf<SkillDoc?>(null) }
    var creatingSkill by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun write(uri: android.net.Uri?, content: suspend () -> String) {
        if (uri == null) return
        scope.launch {
            val ok = runCatching {
                val text = content()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: error("无法写入") }
            }.isSuccess
            toast(if (ok) "已导出" else "导出失败")
        }
    }
    val exportMd = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { write(it, actions.exportMarkdown) }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { write(it, actions.exportJson) }

    Column(Modifier.fillMaxSize()) {
        SubTopBar(title = null, onBack = onBack, trailing = { Spacer(Modifier.width(40.dp)) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Segmented(tab) { tab = it }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Helium\n记得的事", fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink, modifier = Modifier.weight(1f))
                HePillButton("管理", { managing = true }, kind = ButtonKind.TONAL, height = 44.dp)
            }
            if (tab != MemTab.SKILLS) {
                if (candidates.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    MemoryCandidateCard(candidates, actions.accept, actions.dismiss, actions.never)
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    if (memories.isEmpty()) "还没有记忆：在对话里告诉 Helium 你的偏好，确认后会出现在这里；也可以点「+ 添加」" else "点一条可编辑，长按可遗忘",
                    fontSize = 12.sp, color = HeColors.Ink3,
                )
                val groups = memories.groupBy { MemoryDomains.normalize(it.domain) ?: MemoryDomains.OTHER }
                MemoryDomains.all.filter { it in groups }.forEach { d ->
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("关于你·$d", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink, modifier = Modifier.weight(1f))
                        Text("${groups[d]!!.size} 条", fontSize = 12.sp, color = HeColors.Ink3)
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        groups[d]!!.forEach { m -> MemoryPill(m, onClick = { editing = m }, onLongClick = { forgetting = m }) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                AddPill("+ 添加") { adding = true }
            }
            if (tab != MemTab.MEMORY) {
                Spacer(Modifier.height(28.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("专长", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink, modifier = Modifier.weight(1f))
                    Text("全部 ${skills.size} 个", fontSize = 14.sp, color = HeColors.Ink2)
                }
                Spacer(Modifier.height(16.dp))
                if (skills.isEmpty()) {
                    EmptyState("还没有专长", "专长是写给 Helium 的说明书（例如“写周报的格式”）。说到它的名字时，Helium 会按它来做。", action = "新建专长", onAction = { creatingSkill = true })
                } else {
                    skills.chunked(2).forEachIndexed { r, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            row.forEachIndexed { i, s ->
                                SkillCard(s, r * 2 + i, onToggle = { on -> actions.toggleSkill(s.id, on) }, onClick = { editingSkill = s }, modifier = Modifier.weight(1f))
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    AddPill("+ 新建专长") { creatingSkill = true }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (adding) {
        MemoryEditDialog(
            title = "让 Helium 记住", initialText = "", initialDomain = null, initialInferred = false,
            onSave = { t, d, _ -> actions.add(t, d); adding = false; toast("已记住") },
            onForget = null, onDismiss = { adding = false },
        )
    }
    editing?.let { m ->
        MemoryEditDialog(
            title = "编辑记忆", initialText = m.text, initialDomain = MemoryDomains.normalize(m.domain), initialInferred = m.inferred,
            onSave = { t, d, inf -> actions.edit(m.id, t, d, inf); editing = null },
            onForget = { editing = null; forgetting = m },
            onDismiss = { editing = null },
        )
    }
    forgetting?.let { m ->
        AlertDialog(
            onDismissRequest = { forgetting = null },
            containerColor = Color.White,
            title = { Text("遗忘这条记忆？") },
            text = { Text("「${m.text}」会被彻底删除，并生成一条「不再保存」规则：以后同样的内容不会再被自动提议。") },
            confirmButton = { TextButton(onClick = { actions.forget(m.id); forgetting = null; toast("已遗忘") }) { Text("遗忘", color = HeColors.CoralInk) } },
            dismissButton = { TextButton(onClick = { forgetting = null }) { Text("取消") } },
        )
    }
    if (managing) {
        val now = System.currentTimeMillis()
        AlertDialog(
            onDismissRequest = { managing = false },
            containerColor = Color.White,
            title = { Text("管理记忆") },
            text = {
                Column {
                    ManageRow("导出 Markdown", "可读的文档，按领域分组") { managing = false; exportMd.launch(MemoryExport.fileName("md", now)) }
                    ManageRow("导出 JSON", "完整数据，含专长和规则") { managing = false; exportJson.launch(MemoryExport.fileName("json", now)) }
                    ManageRow("「不再保存」规则", if (rules.isEmpty()) "还没有" else "${rules.size} 条") { managing = false; showRules = true }
                    ManageRow("清空全部记忆", "${memories.size} 条记忆和待确认的候选", danger = true) { managing = false; confirmClear = true }
                }
            },
            confirmButton = { TextButton(onClick = { managing = false }) { Text("完成") } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Color.White,
            title = { Text("清空全部记忆？") },
            text = { Text("${memories.size} 条记忆和所有待确认的候选会被彻底删除，不可恢复。建议先导出。「不再保存」规则和专长会保留。") },
            confirmButton = { TextButton(onClick = { actions.clearAll(); confirmClear = false; toast("已清空") }) { Text("清空", color = HeColors.CoralInk) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
    if (showRules) {
        AlertDialog(
            onDismissRequest = { showRules = false },
            containerColor = Color.White,
            title = { Text("不再保存") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text("遗忘过的内容。和这些意思相同的事实不会再被提议或保存；删除一条规则即可恢复。", fontSize = 12.sp, color = HeColors.Ink3)
                    Spacer(Modifier.height(8.dp))
                    if (rules.isEmpty()) Text("还没有规则", fontSize = 14.sp, color = HeColors.Ink2)
                    rules.forEach { r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(r.text, fontSize = 14.sp, color = HeColors.Ink, modifier = Modifier.weight(1f))
                            TextButton(onClick = { actions.deleteRule(r.id) }) { Text("删除", color = HeColors.CoralInk, fontSize = 13.sp) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRules = false }) { Text("完成") } },
        )
    }
    if (creatingSkill) {
        SkillEditorDialog(null, onSave = { actions.saveSkill(it); creatingSkill = false; toast("已保存专长") }, onDelete = null, onDismiss = { creatingSkill = false })
    }
    editingSkill?.let { s ->
        SkillEditorDialog(
            s,
            onSave = { actions.saveSkill(it); editingSkill = null; toast("已保存") },
            onDelete = { actions.deleteSkill(s.id); editingSkill = null; toast("已删除") },
            onDismiss = { editingSkill = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoryPill(m: MemoryEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .widthIn(max = 320.dp)
            .heCardShadow(HeShapes.Pill, 6.dp)
            .clip(HeShapes.Pill)
            .background(Color.White)
            .border(1.dp, HeColors.Hair, HeShapes.Pill)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(m.text, fontSize = 16.sp, color = HeColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(8.dp))
        OriginTag(m.inferred)
    }
}

@Composable
private fun AddPill(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(48.dp)
            .clip(HeShapes.Pill)
            .drawBehind {
                drawRoundRect(
                    HeColors.Ink3,
                    cornerRadius = CornerRadius(size.height / 2),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 16.sp, color = HeColors.Ink2)
    }
}

@Composable
private fun ManageRow(title: String, meta: String, danger: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp)) {
        Text(title, fontSize = 16.sp, color = if (danger) HeColors.CoralInk else HeColors.Ink, fontWeight = FontWeight.Medium)
        Text(meta, fontSize = 12.sp, color = HeColors.Ink3)
    }
}

@Composable
private fun Segmented(tab: MemTab, onSelect: (MemTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(HeShapes.Pill).background(HeColors.Mist).padding(6.dp),
    ) {
        MemTab.entries.forEach { t ->
            val active = t == tab
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .then(if (active) Modifier.heCardShadow(HeShapes.Pill, 4.dp) else Modifier)
                    .clip(HeShapes.Pill)
                    .background(if (active) Color.White else Color.Transparent)
                    .clickable { onSelect(t) },
                contentAlignment = Alignment.Center,
            ) {
                Text(t.label, fontSize = 16.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, color = if (active) HeColors.Ink else HeColors.Ink2)
            }
        }
    }
}

private val SHAPES = listOf(GlassShape.SCALLOP, GlassShape.FLOWER, GlassShape.PILL, GlassShape.SQUARE)

@Composable
private fun SkillCard(s: SkillDoc, index: Int, onToggle: (Boolean) -> Unit, onClick: () -> Unit, modifier: Modifier) {
    WhiteCard(modifier.height(168.dp), onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            GlassIcon(HeIcons.Sparkle, size = 44.dp, shape = SHAPES[index % SHAPES.size])
            Spacer(Modifier.weight(1f))
            Text(s.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (s.enabled) s.description else "已关闭", fontSize = 12.sp, lineHeight = 16.sp, color = HeColors.Ink2,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                HeToggle(s.enabled, onToggle)
            }
        }
    }
}
