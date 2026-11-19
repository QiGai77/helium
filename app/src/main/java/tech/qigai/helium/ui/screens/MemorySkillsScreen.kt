@file:OptIn(ExperimentalLayoutApi::class)

package tech.qigai.helium.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.qigai.helium.data.model.MemoryItem
import tech.qigai.helium.data.model.Skill
import tech.qigai.helium.data.model.SkillIcon
import tech.qigai.helium.ui.components.ButtonKind
import tech.qigai.helium.ui.components.GlassIcon
import tech.qigai.helium.ui.components.GlassShape
import tech.qigai.helium.ui.components.HePillButton
import tech.qigai.helium.ui.components.HeTag
import tech.qigai.helium.ui.components.HeToggle
import tech.qigai.helium.ui.components.HeroSurface
import tech.qigai.helium.ui.components.TagKind
import tech.qigai.helium.ui.components.WhiteCard
import tech.qigai.helium.ui.components.heCardShadow
import tech.qigai.helium.ui.icons.HeIcons
import tech.qigai.helium.ui.theme.HeButton
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeShapes

private enum class MemTab(val label: String) { ALL("全部"), MEMORY("记忆"), SKILLS("技能") }

/** 05 记忆与技能 — memory pills (add / long-press to forget) + skill cards with toggles. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MemorySkillsScreen(
    memories: List<MemoryItem>,
    skills: List<Skill>,
    onAdd: (String) -> Unit,
    onForget: (MemoryItem) -> Unit,
    onBack: () -> Unit,
    toast: (String) -> Unit,
) {
    var tab by remember { mutableStateOf(MemTab.ALL) }
    val skillOn = remember { mutableStateMapOf<String, Boolean>().apply { skills.forEach { put(it.id, it.enabled) } } }
    var adding by remember { mutableStateOf(false) }
    var forgetting by remember { mutableStateOf<MemoryItem?>(null) }

    Column(Modifier.fillMaxSize()) {
        SubTopBar(
            title = null,
            onBack = onBack,
            trailing = { Spacer(Modifier.width(40.dp)) },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Segmented(tab) { tab = it }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Helium\n记得的事", fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink, modifier = Modifier.weight(1f))
                HePillButton("管理", { toast("记忆管理（导出 / 清空 / 不再保存规则）即将支持") }, kind = ButtonKind.TONAL, height = 44.dp)
            }
            if (tab != MemTab.SKILLS) {
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (memories.isEmpty()) "还没有记忆：在对话里告诉 Helium 你的偏好，或点「+ 添加」" else "长按一条可遗忘", fontSize = 12.sp, color = HeColors.Ink3)
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    memories.forEach { m ->
                        Box(
                            Modifier
                                .height(48.dp)
                                .heCardShadow(HeShapes.Pill, 6.dp)
                                .clip(HeShapes.Pill)
                                .background(Color.White)
                                .border(1.dp, HeColors.Hair, HeShapes.Pill)
                                .combinedClickable(onClick = { toast(if (m.inferred) "推断：${m.text}" else "你说过：${m.text}") }, onLongClick = { forgetting = m })
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(m.text + if (m.inferred) " · 推断" else "", fontSize = 16.sp, color = HeColors.Ink)
                        }
                    }
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
                            .clickable { adding = true }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+ 添加", fontSize = 16.sp, color = HeColors.Ink2)
                    }
                }
            }
            if (tab != MemTab.MEMORY) {
                Spacer(Modifier.height(28.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("技能", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink, modifier = Modifier.weight(1f))
                    Text("全部 ${skills.size} 个", fontSize = 14.sp, color = HeColors.Ink2)
                }
                Spacer(Modifier.height(16.dp))
                if (skills.isEmpty()) {
                    EmptyState("还没有技能", "技能是写给 Helium 的说明书（例如“写周报的格式”）。自建技能在下一个版本接入。")
                }
                skills.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { s ->
                            SkillCard(s, skillOn[s.id] == true, { skillOn[s.id] = it }, Modifier.weight(1f))
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (adding) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            containerColor = Color.White,
            title = { Text("让 Helium 记住") },
            text = {
                Column {
                    Text("只记你说过的话；可以随时遗忘。", fontSize = 13.sp, color = HeColors.Ink2)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text("例如：出差住高楼层") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onAdd(text.trim())
                    adding = false
                }, enabled = text.isNotBlank()) { Text("记住") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("取消") } },
        )
    }
    forgetting?.let { m ->
        AlertDialog(
            onDismissRequest = { forgetting = null },
            containerColor = Color.White,
            title = { Text("遗忘这条记忆？") },
            text = { Text("「${m.text}」会被全部删除，并生成一条「不再保存」规则。") },
            confirmButton = {
                TextButton(onClick = { onForget(m); forgetting = null; toast("已遗忘") }) { Text("遗忘", color = HeColors.CoralInk) }
            },
            dismissButton = { TextButton(onClick = { forgetting = null }) { Text("取消") } },
        )
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

private fun SkillIcon.vector() = when (this) {
    SkillIcon.DINING -> HeIcons.Utensils
    SkillIcon.PLANE -> HeIcons.Plane
    SkillIcon.BAG -> HeIcons.Bag
    SkillIcon.MAIL -> HeIcons.Mail
}

private fun SkillIcon.shape() = when (this) {
    SkillIcon.DINING -> GlassShape.SCALLOP
    SkillIcon.PLANE -> GlassShape.FLOWER
    SkillIcon.BAG -> GlassShape.PILL
    SkillIcon.MAIL -> GlassShape.SQUARE
}

@Composable
private fun SkillCard(s: Skill, on: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier) {
    val inner: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                GlassIcon(s.icon.vector(), size = 48.dp, shape = s.icon.shape(), onHero = s.running)
                Spacer(Modifier.weight(1f))
                if (s.running) HeTag("进行中", TagKind.GLASS, dot = false, height = 28.dp, fontSize = 13)
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (s.running) Color.White else HeColors.Ink)
                    Text(
                        if (on || s.running) s.meta else "已关闭", fontSize = 13.sp,
                        color = if (s.running) Color.White.copy(alpha = 0.85f) else HeColors.Ink2,
                    )
                }
                HeToggle(on, onToggle, onHero = s.running)
            }
        }
    }
    if (s.running) {
        HeroSurface(modifier.height(160.dp)) { inner() }
    } else {
        WhiteCard(modifier.height(160.dp)) { inner() }
    }
}
