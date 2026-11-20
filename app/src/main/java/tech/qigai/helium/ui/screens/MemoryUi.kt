@file:OptIn(ExperimentalLayoutApi::class)

package tech.qigai.helium.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tech.qigai.helium.data.db.MemoryCandidateEntity
import tech.qigai.helium.memory.MemoryDomains
import tech.qigai.helium.skills.SkillDoc
import tech.qigai.helium.skills.SkillMarkdown
import tech.qigai.helium.ui.components.ButtonKind
import tech.qigai.helium.ui.components.FlowerAvatar
import tech.qigai.helium.ui.components.HePillButton
import tech.qigai.helium.ui.components.HeTag
import tech.qigai.helium.ui.components.HeToggle
import tech.qigai.helium.ui.components.TagKind
import tech.qigai.helium.ui.components.WhiteCard
import tech.qigai.helium.ui.icons.HeIcons
import tech.qigai.helium.ui.theme.HeButton
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeShapes

/** "你说的" / "推断" tag on every memory. */
@Composable
fun OriginTag(inferred: Boolean) {
    HeTag(if (inferred) "推断" else "你说的", if (inferred) TagKind.AMBER else TagKind.NEUTRAL, dot = false, height = 22.dp, fontSize = 11)
}

/** Candidate memories after a turn: nothing is saved until 记住. */
@Composable
fun MemoryCandidateCard(
    candidates: List<MemoryCandidateEntity>,
    onAccept: (Long) -> Unit,
    onDismiss: (Long) -> Unit,
    onNever: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (candidates.isEmpty()) return
    WhiteCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowerAvatar(size = 20.dp)
                Spacer(Modifier.width(8.dp))
                Text("要我记住吗？", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink, modifier = Modifier.weight(1f))
                Text("确认后才会保存", fontSize = 12.sp, color = HeColors.Ink3)
            }
            candidates.forEach { c ->
                Spacer(Modifier.height(12.dp))
                CandidateRow(c, onAccept, onDismiss, onNever)
            }
        }
    }
}

@Composable
fun CandidateRow(c: MemoryCandidateEntity, onAccept: (Long) -> Unit, onDismiss: (Long) -> Unit, onNever: (Long) -> Unit) {
    var more by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(HeColors.Mist3).padding(12.dp)) {
        Text(c.text, fontSize = 15.sp, color = HeColors.Ink, lineHeight = 21.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeTag("关于你·${c.domain}", TagKind.ACCENT, dot = false, height = 22.dp, fontSize = 11)
            Spacer(Modifier.width(6.dp))
            OriginTag(c.inferred)
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            HePillButton("记住", { onAccept(c.id) }, kind = ButtonKind.PRIMARY, height = HeButton.Small)
            HePillButton("不用", { onDismiss(c.id) }, kind = ButtonKind.WHITE, height = HeButton.Small)
            Spacer(Modifier.weight(1f))
            Text(
                "不再提这类", fontSize = 12.sp, color = HeColors.Ink3,
                modifier = Modifier.clip(HeShapes.Pill).clickable { more = true }.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
    if (more) {
        AlertDialog(
            onDismissRequest = { more = false },
            containerColor = Color.White,
            title = { Text("不再保存这条？") },
            text = { Text("「${c.text}」以及意思相同的内容以后都不会再被提议或保存。可以在「记忆 → 管理」里撤销。") },
            confirmButton = { TextButton(onClick = { more = false; onNever(c.id) }) { Text("不再保存", color = HeColors.CoralInk) } },
            dismissButton = { TextButton(onClick = { more = false }) { Text("取消") } },
        )
    }
}

@Composable
fun DomainChips(selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MemoryDomains.all.forEach { d ->
            val on = d == selected
            Box(
                Modifier.height(32.dp).clip(HeShapes.Pill).background(if (on) HeColors.AccentTint else HeColors.Mist2)
                    .then(if (on) Modifier.border(1.dp, HeColors.Accent, HeShapes.Pill) else Modifier)
                    .clickable { onSelect(d) }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(d, fontSize = 13.sp, color = if (on) HeColors.Accent else HeColors.Ink2, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

/** Add (id == null) or edit one memory: text, group, and whether it's something the user said or an inference. */
@Composable
fun MemoryEditDialog(
    title: String,
    initialText: String,
    initialDomain: String?,
    initialInferred: Boolean,
    onSave: (text: String, domain: String, inferred: Boolean) -> Unit,
    onForget: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    var domain by remember { mutableStateOf(initialDomain) }
    var inferred by remember { mutableStateOf(initialInferred) }
    val shownDomain = domain ?: MemoryDomains.guess(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(text, { text = it }, placeholder = { Text("例如：出差住高楼层") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
                Spacer(Modifier.height(14.dp))
                Text("分组", fontSize = 13.sp, color = HeColors.Ink2)
                Spacer(Modifier.height(8.dp))
                DomainChips(shownDomain) { domain = it }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (inferred) "推断" else "你说的", fontSize = 15.sp, color = HeColors.Ink)
                        Text("推断的内容 Helium 会更谨慎地使用", fontSize = 12.sp, color = HeColors.Ink3)
                    }
                    HeToggle(inferred, { inferred = it })
                }
                if (onForget != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "遗忘这条", color = HeColors.CoralInk, fontSize = 14.sp,
                        modifier = Modifier.clip(HeShapes.Pill).clickable(onClick = onForget).padding(vertical = 6.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim(), shownDomain, inferred) }, enabled = text.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** Composer 技能 button: pick a 专长 for this message, or jump to managing them. */
@Composable
fun SkillPickerDialog(skills: List<SkillDoc>, onPick: (SkillDoc) -> Unit, onManage: () -> Unit, onDismiss: () -> Unit) {
    val on = skills.filter { it.enabled }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("专长") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (on.isEmpty()) {
                    Text("还没有专长。专长是写给 Helium 的说明书，例如“写周报：本周完成 / 下周计划 / 风险，三段式”。", fontSize = 14.sp, color = HeColors.Ink2, lineHeight = 21.sp)
                } else {
                    Text("选一个，这条消息就按它来做。提到专长的名字或触发词时也会自动使用。", fontSize = 12.sp, color = HeColors.Ink3)
                    Spacer(Modifier.height(8.dp))
                    on.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(s) }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(HeIcons.Sparkle, null, tint = HeColors.Accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink)
                                Text(s.description, fontSize = 13.sp, color = HeColors.Ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onManage) { Text(if (on.isEmpty()) "新建专长" else "管理专长") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

/** Full-screen 专长 editor: name, when to use it, trigger words and the Markdown body (frontmatter pasted in is split out). */
@Composable
fun SkillEditorDialog(
    initial: SkillDoc?,
    onSave: (SkillDoc) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var triggers by remember { mutableStateOf(initial?.triggers?.joinToString("，").orEmpty()) }
    var body by remember { mutableStateOf(initial?.body ?: TEMPLATE) }
    var confirmDelete by remember { mutableStateOf(false) }
    val problem = SkillMarkdown.problem(name, description, body)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(HeColors.Canvas)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("取消", color = HeColors.Ink2) }
                Text(if (initial == null) "新建专长" else "编辑专长", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(
                    onClick = {
                        onSave(
                            SkillDoc(
                                id = initial?.id.orEmpty(), name = name.trim(), description = description.trim(),
                                triggers = triggers.split(',', '，', '、', '\n').map { it.trim() }.filter { it.isNotEmpty() },
                                body = body.trim(), enabled = initial?.enabled ?: true,
                            ),
                        )
                    },
                    enabled = problem == null,
                ) { Text("保存") }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                OutlinedTextField(name, { name = it.take(30) }, label = { Text("名字") }, placeholder = { Text("写周报") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(description, { description = it.take(200) }, label = { Text("什么时候用") }, placeholder = { Text("用户要写周报时，按三段式输出") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(triggers, { triggers = it }, label = { Text("触发词（可选，逗号分隔）") }, placeholder = { Text("默认就是名字，例如：周报，weekly") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    body,
                    { v ->
                        // Pasting a whole SKILL.md fills the fields from its frontmatter.
                        val p = SkillMarkdown.parse(v)
                        if (p.hadFrontmatter) {
                            p.name?.let { name = it.take(30) }
                            p.description?.let { description = it.take(200) }
                            if (p.triggers.isNotEmpty()) triggers = p.triggers.joinToString("，")
                            body = p.body
                        } else {
                            body = v
                        }
                    },
                    label = { Text("说明书（Markdown）") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(problem ?: "保存后，说到「${name.ifBlank { "名字" }}」或触发词时 Helium 会按它来做。", fontSize = 12.sp, color = if (problem != null) HeColors.CoralInk else HeColors.Ink3)
                if (onDelete != null) {
                    Spacer(Modifier.height(20.dp))
                    HePillButton("删除这个专长", { confirmDelete = true }, kind = ButtonKind.WHITE, height = HeButton.Medium)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Color.White,
            title = { Text("删除专长「$name」？") },
            text = { Text("删除后不可恢复。") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("删除", color = HeColors.CoralInk) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

private val TEMPLATE = """
    ## 格式
    1. …
    2. …

    ## 注意
    - …
""".trimIndent()
