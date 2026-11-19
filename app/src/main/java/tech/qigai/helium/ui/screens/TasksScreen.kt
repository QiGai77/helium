package tech.qigai.helium.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.qigai.helium.data.sample.Project
import tech.qigai.helium.data.sample.ScheduledTask
import tech.qigai.helium.data.sample.Sources
import tech.qigai.helium.data.sample.TaskIcon
import tech.qigai.helium.ui.components.GlassIcon
import tech.qigai.helium.ui.components.HeTag
import tech.qigai.helium.ui.components.HeToggle
import tech.qigai.helium.ui.components.HeroSurface
import tech.qigai.helium.ui.components.RoundIconButton
import tech.qigai.helium.ui.components.SampleBadge
import tech.qigai.helium.ui.components.SectionHeader
import tech.qigai.helium.ui.components.TagKind
import tech.qigai.helium.ui.components.WhiteCard
import tech.qigai.helium.ui.icons.HeIcons
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeNum
import tech.qigai.helium.ui.theme.HeShapes
import java.time.LocalDate

fun TaskIcon.vector() = when (this) {
    TaskIcon.SUN -> HeIcons.Sun
    TaskIcon.RECEIPT -> HeIcons.Receipt
    TaskIcon.PLANE -> HeIcons.Plane
    TaskIcon.MAIL -> HeIcons.Briefcase
    TaskIcon.CHART -> HeIcons.Chart
}

/** 06 任务 — week strip + scheduled tasks + stacked project cards. */
@Composable
fun TasksScreen(onOpenMe: () -> Unit, onOpenTimeline: () -> Unit, toast: (String) -> Unit) {
    val tasks = remember { Sources.tasks.scheduled() }
    val projects = remember { Sources.tasks.projects() }
    val enabled = remember { mutableStateMapOf<String, Boolean>().apply { tasks.forEach { put(it.id, it.enabled) } } }
    val today = LocalDate.now()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val sunday = monday.plusDays(6)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        RootHeader(
            title = "任务",
            inlineSubtitle = "定时与项目",
            onAvatar = onOpenMe,
            actions = { RoundIconButton(HeIcons.Plus, { toast("新建定时任务即将支持") }, contentDescription = "新建") },
        )
        Spacer(Modifier.height(24.dp))
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("定时任务", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink, modifier = Modifier.weight(1f))
            Text(
                "${monday.monthValue}月${monday.dayOfMonth}日 – ${if (sunday.monthValue != monday.monthValue) "${sunday.monthValue}月" else ""}${sunday.dayOfMonth}日",
                fontSize = 14.sp, color = HeColors.Ink2,
            )
        }
        Spacer(Modifier.height(12.dp))
        WeekStrip(today.dayOfWeek.value, tasks.filter { enabled[it.id] == true }, Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(16.dp))
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tasks.forEach { t -> TaskRow(t, enabled[t.id] == true) { enabled[t.id] = it } }
        }
        Spacer(Modifier.height(28.dp))
        SectionHeader("项目", Modifier.padding(horizontal = 24.dp), action = "新建", actionIcon = HeIcons.Plus, onAction = { toast("新建项目即将支持") })
        Spacer(Modifier.height(12.dp))
        ProjectStack(projects, Modifier.padding(horizontal = 24.dp), onOpenTimeline)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WeekStrip(todayIdx: Int, active: List<ScheduledTask>, modifier: Modifier = Modifier) {
    WhiteCard(modifier.fillMaxWidth().height(56.dp), shape = HeShapes.Pill) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            for (d in 1..7) {
                val isToday = d == todayIdx
                val hasTask = active.any { it.weekdays.size < 7 && d in it.weekdays }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(if (isToday) HeColors.Accent else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(weekdayShort(d), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (isToday) Color.White else HeColors.Ink)
                    }
                    if (hasTask && !isToday) {
                        Box(Modifier.align(Alignment.BottomCenter).offset(y = (-2).dp).size(4.dp).clip(CircleShape).background(HeColors.Accent.copy(alpha = 0.6f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(t: ScheduledTask, on: Boolean, onToggle: (Boolean) -> Unit) {
    WhiteCard(Modifier.fillMaxWidth().height(64.dp), shape = HeShapes.Row) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIcon(t.icon.vector(), size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink)
                Text(t.schedule, fontSize = 13.sp, color = HeColors.Ink2)
            }
            HeToggle(on, onToggle)
        }
    }
}

@Composable
private fun ProjectStack(projects: List<Project>, modifier: Modifier, onOpen: () -> Unit) {
    val back = projects.dropLast(1)
    val front = projects.lastOrNull() ?: return
    Column(modifier.fillMaxWidth()) {
        back.forEachIndexed { i, p ->
            WhiteCard(
                Modifier.fillMaxWidth().height(80.dp).offset(y = (i * -24).dp),
                color = if (i == 0) HeColors.Mist3 else Color.White,
                onClick = onOpen,
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIcon(p.icon.vector(), size = 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(p.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink, modifier = Modifier.weight(1f))
                    HeTag(p.badge, if (i == 0) TagKind.MINT else TagKind.NEUTRAL, height = 26.dp)
                }
            }
        }
        HeroSurface(
            Modifier.fillMaxWidth().height(160.dp).offset(y = (back.size * -24).dp),
            onClick = onOpen,
        ) {
            ProgressRing(front.progress, Modifier.align(Alignment.CenterEnd).padding(end = 24.dp).size(64.dp))
            Column(Modifier.padding(20.dp).fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassIcon(HeIcons.Home, size = 32.dp, onHero = true)
                    Spacer(Modifier.width(12.dp))
                    Text(front.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    SampleBadge(onHero = true)
                }
                Spacer(Modifier.weight(1f))
                Text("${front.todo} 项待办", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("下一步：${front.next}", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(0.dp))
    }
}

@Composable
private fun ProgressRing(progress: Float, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 5.dp.toPx()
            drawArc(Color.White.copy(alpha = 0.3f), 0f, 360f, false, topLeft = Offset(sw / 2, sw / 2), size = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw), style = Stroke(sw))
            drawArc(Color.White, -90f, 360f * progress, false, topLeft = Offset(sw / 2, sw / 2), size = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
        }
        Text("${(progress * 100).toInt()}%", color = Color.White, style = HeNum.style(15, FontWeight.SemiBold))
    }
}
