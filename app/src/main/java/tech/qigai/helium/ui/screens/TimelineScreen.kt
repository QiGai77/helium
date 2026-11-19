package tech.qigai.helium.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.qigai.helium.data.sample.Sources
import tech.qigai.helium.data.sample.StepState
import tech.qigai.helium.data.sample.TimelineStep
import tech.qigai.helium.ui.components.ButtonKind
import tech.qigai.helium.ui.components.Dot
import tech.qigai.helium.ui.components.HePillButton
import tech.qigai.helium.ui.components.HeTag
import tech.qigai.helium.ui.components.Illo
import tech.qigai.helium.ui.components.RoundIconButton
import tech.qigai.helium.ui.components.SampleBadge
import tech.qigai.helium.ui.components.TagKind
import tech.qigai.helium.ui.components.WhiteCard
import tech.qigai.helium.ui.components.heCardShadow
import tech.qigai.helium.ui.icons.HeIcons
import tech.qigai.helium.ui.illo.IlloKind
import tech.qigai.helium.ui.illo.IlloTone
import tech.qigai.helium.ui.theme.HeButton
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeGradients
import tech.qigai.helium.ui.theme.HeNum
import tech.qigai.helium.ui.theme.HeShapes

/** 03 任务时间线 — progress bar is the screen's single hero; nodes: done ● / running ■ / waiting ○ / future ▢. */
@Composable
fun TimelineScreen(onBack: () -> Unit, onOpenApproval: () -> Unit, toast: (String) -> Unit) {
    val run = remember { Sources.runs.timeline() }
    var paused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        SubTopBar(null, onBack, trailing = { RoundIconButton(HeIcons.More, { toast("更多操作即将支持") }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(run.title, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink)
                Spacer(Modifier.width(10.dp))
                SampleBadge()
            }
            Spacer(Modifier.height(6.dp))
            Text("${if (paused) "已暂停" else run.status} · 已完成 ${run.done}/${run.total}", fontSize = 15.sp, color = HeColors.Ink2)
            Spacer(Modifier.height(20.dp))
            ProgressBar(run.done.toFloat() / run.total)
            Spacer(Modifier.height(24.dp))
            run.steps.forEachIndexed { i, s ->
                StepRow(s, isFirst = i == 0, isLast = i == run.steps.lastIndex, prevDone = i > 0 && run.steps[i - 1].state == StepState.DONE, onOpenApproval = onOpenApproval)
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(
            Modifier
                .padding(horizontal = 40.dp, vertical = 12.dp)
                .fillMaxWidth()
                .heCardShadow(HeShapes.Pill)
                .clip(HeShapes.Pill)
                .background(Color.White)
                .border(1.dp, HeColors.Hair, HeShapes.Pill)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HePillButton(if (paused) "继续" else "暂停", { paused = !paused }, Modifier.weight(1f), ButtonKind.TONAL, HeIcons.Pause, HeButton.Medium)
            HePillButton("查看详情", { toast("步骤截图与产物在 v1 接入浏览器代理后提供") }, Modifier.weight(1f), ButtonKind.OUTLINE, height = HeButton.Medium)
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(12.dp).clip(HeShapes.Pill).background(HeColors.Mist2).border(1.dp, HeColors.Hair, HeShapes.Pill)) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(HeShapes.Pill)
                .background(Brush.horizontalGradient(listOf(HeGradients.Aurora.base.first().second, HeGradients.Lagoon.base.last().second, HeGradients.Lagoon.base.first().second)))
                .drawBehind {
                    drawRect(Color.White.copy(alpha = 0.3f), size = size.copy(height = size.height / 2))
                },
        )
    }
}

@Composable
private fun StepRow(s: TimelineStep, isFirst: Boolean, isLast: Boolean, prevDone: Boolean, onOpenApproval: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // rail + node
        Box(Modifier.width(56.dp).fillMaxHeight()) {
            Canvas(Modifier.fillMaxSize()) {
                val x = 26.dp.toPx()
                val nodeY = 42.dp.toPx()
                val w = 3.dp.toPx()
                if (!isFirst) drawLine(if (prevDone) HeColors.Accent else HeColors.Hair, Offset(x, 0f), Offset(x, nodeY), w)
                if (!isLast) drawLine(if (s.state == StepState.DONE) HeColors.Accent else HeColors.Hair, Offset(x, nodeY), Offset(x, size.height), w)
            }
            Node(s.state, Modifier.padding(start = 26.dp - 13.dp, top = 42.dp - 13.dp))
        }
        Box(Modifier.weight(1f).padding(vertical = 8.dp)) { StepCard(s, onOpenApproval) }
    }
}

@Composable
private fun Node(state: StepState, modifier: Modifier) {
    when (state) {
        StepState.DONE -> Box(modifier.size(26.dp).clip(CircleShape).background(HeColors.Accent), contentAlignment = Alignment.Center) {
            Icon(HeIcons.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
        StepState.RUNNING -> Box(
            modifier.size(28.dp).heCardShadow(RoundedCornerShape(9.dp), 4.dp).clip(RoundedCornerShape(9.dp)).background(Color.White)
                .border(2.dp, HeColors.Accent, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(HeColors.Accent)) }
        StepState.WAITING -> Box(modifier.size(26.dp).clip(CircleShape).background(Color.White).border(3.dp, HeColors.Amber, CircleShape))
        StepState.FUTURE -> Box(modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(HeColors.Hair))
    }
}

@Composable
private fun StepCard(s: TimelineStep, onOpenApproval: () -> Unit) {
    when (s.state) {
        StepState.FUTURE -> Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .drawBehind {
                    drawRoundRect(
                        HeColors.Outline,
                        cornerRadius = CornerRadius(28.dp.toPx()),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                    )
                }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink3, modifier = Modifier.weight(1f))
            Text("未开始", fontSize = 13.sp, color = HeColors.Ink3)
        }
        StepState.WAITING -> WhiteCard(Modifier.fillMaxWidth().height(64.dp)) {
            Row(Modifier.fillMaxSize().padding(start = 20.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Dot(HeColors.Amber)
                Spacer(Modifier.width(10.dp))
                Text(s.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink, modifier = Modifier.weight(1f))
                HePillButton("去审批", onOpenApproval, kind = ButtonKind.PRIMARY, height = 40.dp)
            }
        }
        StepState.RUNNING -> WhiteCard(Modifier.fillMaxWidth().height(64.dp), border = HeColors.Accent, borderWidth = 2.dp) {
            Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink, modifier = Modifier.weight(1f))
                HeTag("进行中", TagKind.ACCENT, height = 28.dp, fontSize = 13)
            }
        }
        StepState.DONE -> WhiteCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink, modifier = Modifier.weight(1f))
                    if (s.time != null) Text(s.time, style = HeNum.style(14, FontWeight.Normal), color = HeColors.Ink2)
                }
                if (s.chips.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.chips.forEachIndexed { i, c -> HeTag(c, TagKind.NEUTRAL, dot = false, height = 28.dp, fontSize = 12) }
                    }
                }
                if (s.thumbs > 0) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(s.thumbs) { i ->
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                    .background(Brush.verticalGradient(if (i == 0) listOf(Color(0xFFB9CCEB), Color(0xFF7FB7C9)) else listOf(Color(0xFFEFE7DE), Color(0xFFD9C3A8)))),
                                contentAlignment = Alignment.Center,
                            ) { Illo(IlloKind.HOTEL, IlloTone.HERO, Modifier.size(40.dp, 32.dp)) }
                            Spacer(Modifier.width(6.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        if (s.note != null) {
                            Text(s.note, fontSize = 12.sp, color = HeColors.Ink2, lineHeight = 17.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                    }
                }
            }
        }
    }
}
