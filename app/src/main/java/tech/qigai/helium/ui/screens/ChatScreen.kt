package tech.qigai.helium.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.qigai.helium.data.settings.LlmSettings
import tech.qigai.helium.data.llm.ProviderId
import tech.qigai.helium.data.llm.Role
import tech.qigai.helium.data.sample.LiveRun
import tech.qigai.helium.data.sample.Sources
import tech.qigai.helium.ui.components.ButtonKind
import tech.qigai.helium.ui.components.Composer
import tech.qigai.helium.ui.components.Dot
import tech.qigai.helium.ui.components.FlowerAvatar
import tech.qigai.helium.ui.components.HePillButton
import tech.qigai.helium.ui.components.MarkdownText
import tech.qigai.helium.ui.components.RoundIconButton
import tech.qigai.helium.ui.components.SampleBadge
import tech.qigai.helium.ui.components.heCardShadow
import tech.qigai.helium.ui.icons.HeIcons
import tech.qigai.helium.ui.theme.HeButton
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeGradients
import tech.qigai.helium.ui.theme.HeShapes
import tech.qigai.helium.ui.theme.ambientFloat
import java.time.format.DateTimeFormatter

private val HM = DateTimeFormatter.ofPattern("HH:mm")

/** 02 对话 — real streaming chat; the live-run card is sample data and opens the task timeline. */
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    settings: LlmSettings,
    onSelectModel: (String) -> Unit,
    onBack: () -> Unit,
    onOpenTimeline: () -> Unit,
    onOpenModels: () -> Unit,
    toast: (String) -> Unit,
) {
    val running by vm.running
    val listState = rememberLazyListState()
    var menu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    val last = vm.messages.lastOrNull()
    LaunchedEffect(vm.messages.size, last?.text?.length, last?.reasoning?.length) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0) listState.scrollToItem(count - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to Color(0xFFE6E9FB), 0.25f to HeColors.Canvas, 1f to HeColors.Canvas)),
    ) {
        SubTopBar(
            title = "Helium",
            onBack = onBack,
            subtitle = {
                Box {
                    Row(
                        Modifier.clip(HeShapes.Pill).clickable { modelMenu = true }.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Dot(if (running) HeColors.Accent else HeColors.Mint, size = 7.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (running) "正在工作" else settings.activeModelLabel,
                            fontSize = 13.sp, color = HeColors.Ink2,
                        )
                        Icon(HeIcons.ChevronRight, null, tint = HeColors.Ink3, modifier = Modifier.size(14.dp))
                    }
                    ModelMenu(modelMenu, settings, onDismiss = { modelMenu = false }, onSelect = {
                        modelMenu = false
                        onSelectModel(it)
                    }, onMore = {
                        modelMenu = false
                        onOpenModels()
                    })
                }
            },
            trailing = {
                Box {
                    RoundIconButton(HeIcons.More, { menu = true }, contentDescription = "更多")
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("新对话") }, onClick = { menu = false; vm.newConversation() })
                        DropdownMenuItem(text = { Text("模型与推理") }, onClick = { menu = false; onOpenModels() })
                        DropdownMenuItem(text = { Text("任务时间线（示例）") }, onClick = { menu = false; onOpenTimeline() })
                    }
                }
            },
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "live") {
                LiveRunCard(Sources.runs.live(), onOpen = onOpenTimeline, toast = toast)
            }
            if (vm.messages.isEmpty()) {
                item(key = "empty") { EmptyChatHint(settings) }
            } else {
                item(key = "stamp") {
                    Text(
                        "今天 " + vm.messages.first().at.format(HM),
                        color = HeColors.Ink3, fontSize = 13.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(vm.messages, key = { it.id }) { m ->
                if (m.role == Role.USER) UserBubble(m.text) else AssistantMessage(m, onRetry = vm::retryLast, onOpenModels = onOpenModels)
            }
        }

        Composer(
            value = vm.draft.value,
            onValueChange = { vm.draft.value = it },
            onSend = { vm.send() },
            running = running,
            onStop = vm::stop,
            placeholder = "问 Helium 任何事…",
            onAttach = { toast("附件即将支持") },
            onSkills = { toast("技能面板即将支持") },
            onMic = { toast("语音输入即将支持") },
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ModelMenu(open: Boolean, s: LlmSettings, onDismiss: () -> Unit, onSelect: (String) -> Unit, onMore: () -> Unit) {
    DropdownMenu(open, onDismissRequest = onDismiss) {
        if (s.provider == ProviderId.OPENCODE_GO) {
            s.openCodeModels.take(12).forEach { m ->
                DropdownMenuItem(
                    text = { Text((if (m.id == s.openCodeModel) "✓ " else "    ") + m.label) },
                    onClick = { onSelect(m.id) },
                )
            }
        } else {
            DropdownMenuItem(text = { Text("✓ ${s.provider.title} · ${s.activeModelLabel}") }, onClick = onDismiss)
        }
        DropdownMenuItem(text = { Text("更多模型与设置…", color = HeColors.Accent) }, onClick = onMore)
    }
}

@Composable
private fun EmptyChatHint(s: LlmSettings) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("今天想让我帮你做什么？", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            if (s.isConfigured(s.provider)) "当前模型：${s.provider.title} · ${s.activeModelLabel}"
            else "先在「我的 → 模型与推理」填入 ${s.provider.title} 的 API Key",
            fontSize = 13.sp, color = HeColors.Ink2, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text,
            color = Color.White,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(HeColors.Ink)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun AssistantMessage(m: tech.qigai.helium.ui.screens.ChatMessage, onRetry: () -> Unit, onOpenModels: () -> Unit) {
    var showReasoning by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().animateContentSize()) {
        FlowerAvatar(Modifier.padding(top = 2.dp), size = 24.dp, spinning = m.streaming)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (m.reasoning.isNotBlank()) {
                Text(
                    if (showReasoning) "收起思考过程" else if (m.streaming && m.text.isEmpty()) "思考中…（点开查看）" else "查看思考过程",
                    color = HeColors.Accent, fontSize = 13.sp,
                    modifier = Modifier.clip(HeShapes.Pill).clickable { showReasoning = !showReasoning }.padding(vertical = 2.dp),
                )
                if (showReasoning) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        m.reasoning.trim(), color = HeColors.Ink2, fontSize = 13.sp, lineHeight = 20.sp,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(HeColors.Mist2).padding(12.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            when {
                m.text.isNotEmpty() -> MarkdownText(m.text)
                m.streaming && m.reasoning.isBlank() -> ThinkingWave()
            }
            if (m.error != null) {
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(HeColors.CoralTint)
                        .padding(16.dp),
                ) {
                    Text("没能拿到回复", color = HeColors.CoralInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(m.error, color = HeColors.Ink2, fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HePillButton("重试", onRetry, kind = ButtonKind.WHITE, height = HeButton.Small, icon = HeIcons.Refresh)
                        HePillButton("模型设置", onOpenModels, kind = ButtonKind.WHITE, height = HeButton.Small)
                    }
                }
            }
            if (m.stopped) {
                Spacer(Modifier.height(6.dp))
                Text("已停止", color = HeColors.Ink3, fontSize = 12.sp)
            }
        }
    }
}

/** Accent wave that stands in for a spinner while waiting for the first token. */
@Composable
private fun ThinkingWave(modifier: Modifier = Modifier) {
    val phase = ambientFloat(0f, 1f, 1200)
    Canvas(modifier.width(140.dp).height(20.dp)) {
        val w = size.width
        val h = size.height
        val p = Path()
        val steps = 60
        for (i in 0..steps) {
            val x = w * i / steps
            val y = h / 2 + (h * 0.3f) * kotlin.math.sin((i / 6f + phase * 2f) * Math.PI.toFloat())
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(
            p,
            Brush.horizontalGradient(listOf(HeColors.Accent.copy(alpha = 0.1f), HeColors.Accent)),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/** Live run card (示例): gradient hairline is the screen's single hero accent. */
@Composable
fun LiveRunCard(run: LiveRun, onOpen: () -> Unit, toast: (String) -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .heCardShadow(shape)
            .clip(shape)
            .background(Color.White)
            .border(1.5.dp, Brush.linearGradient(HeGradients.AuroraSunrise), shape)
            .clickable(onClick = onOpen)
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(HeColors.Accent, size = 10.dp)
            Spacer(Modifier.width(12.dp))
            Text(run.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink)
            Spacer(Modifier.width(8.dp))
            SampleBadge()
            Spacer(Modifier.weight(1f))
            Text(run.elapsed, fontSize = 14.sp, color = HeColors.Ink2)
        }
        Spacer(Modifier.height(16.dp))
        BrowserPreview()
        Spacer(Modifier.height(16.dp))
        run.steps.forEach { s ->
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (s.done) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(HeColors.MintTint), contentAlignment = Alignment.Center) {
                        Icon(HeIcons.Check, null, tint = HeColors.MintInk, modifier = Modifier.size(14.dp))
                    }
                } else {
                    Spinner(Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    s.text, fontSize = 15.sp,
                    fontWeight = if (s.done) FontWeight.Normal else FontWeight.SemiBold,
                    color = HeColors.Ink,
                )
                if (!s.done) {
                    Spacer(Modifier.width(16.dp))
                    ThinkingWave(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HePillButton("接管", { toast("接管：需要浏览器代理后端（v1）") }, Modifier.weight(1f), ButtonKind.TONAL, HeIcons.Hand, HeButton.Medium)
            HePillButton("暂停", { toast("示例任务，暂停功能在 v1 接入") }, Modifier.weight(1f), ButtonKind.OUTLINE, HeIcons.Pause, HeButton.Medium)
        }
    }
}

@Composable
private fun Spinner(modifier: Modifier = Modifier) {
    val a = ambientFloat(0f, 360f, 900)
    Canvas(modifier) {
        drawArc(HeColors.Accent.copy(alpha = 0.15f), 0f, 360f, false, style = Stroke(2.5.dp.toPx()))
        drawArc(HeColors.Accent, a, 250f, false, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Neutral mock of the browser frame the agent is driving. */
@Composable
private fun BrowserPreview() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(124.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(HeColors.Mist3)
            .border(1.dp, HeColors.Hair, RoundedCornerShape(20.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val pageL = w * 0.22f
            val pageR = w * 0.78f
            drawRoundRect(Color.White, Offset(pageL, 8.dp.toPx()), androidx.compose.ui.geometry.Size(pageR - pageL, h), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
            val bar = { x: Float, y: Float, bw: Float, c: Color ->
                drawRoundRect(c, Offset(x, y), androidx.compose.ui.geometry.Size(bw, 6.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            }
            bar(pageL + 8.dp.toPx(), 16.dp.toPx(), 24.dp.toPx(), HeColors.Hair)
            bar(pageL + 60.dp.toPx(), 16.dp.toPx(), 56.dp.toPx(), HeColors.Hair)
            bar(pageR - 30.dp.toPx(), 14.dp.toPx(), 22.dp.toPx(), HeColors.Accent)
            val colW = (pageR - pageL - 24.dp.toPx()) / 2
            drawRoundRect(HeColors.Mist2, Offset(pageL + 8.dp.toPx(), 30.dp.toPx()), androidx.compose.ui.geometry.Size(colW, 48.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
            bar(pageL + 14.dp.toPx(), 38.dp.toPx(), colW * 0.8f, HeColors.Hair)
            bar(pageL + 14.dp.toPx(), 62.dp.toPx(), colW * 0.45f, HeColors.Accent)
            val imgX = pageL + 16.dp.toPx() + colW
            drawRoundRect(
                Brush.linearGradient(listOf(Color(0xFFE2B886), Color(0xFFC88A6A))),
                Offset(imgX, 30.dp.toPx()), androidx.compose.ui.geometry.Size(colW, 48.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
            )
            drawCircle(Color.White, 15.dp.toPx(), Offset(imgX + colW / 2, 54.dp.toPx()))
            drawCircle(Color(0xFFD9805F), 7.dp.toPx(), Offset(imgX + colW / 2 - 3.dp.toPx(), 52.dp.toPx()))
            drawCircle(Color(0xFF7FA86A), 4.dp.toPx(), Offset(imgX + colW / 2 + 7.dp.toPx(), 56.dp.toPx()))
            bar(imgX, 88.dp.toPx(), colW * 0.8f, HeColors.Hair)
            bar(imgX, 100.dp.toPx(), colW * 0.6f, HeColors.Hair)
            drawRoundRect(HeColors.Mist2, Offset(pageL + 8.dp.toPx(), 86.dp.toPx()), androidx.compose.ui.geometry.Size(colW * 0.45f, 32.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
            drawRoundRect(HeColors.Mist2, Offset(pageL + 14.dp.toPx() + colW * 0.45f, 86.dp.toPx()), androidx.compose.ui.geometry.Size(colW * 0.45f, 32.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(HeIcons.Expand, null, tint = HeColors.Ink, modifier = Modifier.size(16.dp))
        }
    }
}
