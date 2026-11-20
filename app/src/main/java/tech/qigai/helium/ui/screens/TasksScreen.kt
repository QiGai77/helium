@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package tech.qigai.helium.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import tech.qigai.helium.data.db.TaskRunEntity
import tech.qigai.helium.data.model.Project
import tech.qigai.helium.data.model.TaskIcon
import tech.qigai.helium.tasks.Parsed
import tech.qigai.helium.tasks.RunCause
import tech.qigai.helium.tasks.RunStatus
import tech.qigai.helium.tasks.Schedule
import tech.qigai.helium.tasks.TaskAction
import tech.qigai.helium.tasks.TaskArgs
import tech.qigai.helium.tasks.TaskDraft
import tech.qigai.helium.tasks.TaskInfo
import tech.qigai.helium.tasks.TaskKind
import tech.qigai.helium.tasks.Weekdays
import tech.qigai.helium.ui.components.ButtonKind
import tech.qigai.helium.ui.components.GlassIcon
import tech.qigai.helium.ui.components.HePillButton
import tech.qigai.helium.ui.components.HeTag
import tech.qigai.helium.ui.components.HeToggle
import tech.qigai.helium.ui.components.HeroSurface
import tech.qigai.helium.ui.components.RoundIconButton
import tech.qigai.helium.ui.components.SectionHeader
import tech.qigai.helium.ui.components.TagKind
import tech.qigai.helium.ui.components.WhiteCard
import tech.qigai.helium.ui.icons.HeIcons
import tech.qigai.helium.ui.theme.HeButton
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeNum
import tech.qigai.helium.ui.theme.HeShapes
import java.time.LocalDate
import java.time.ZoneId

fun TaskIcon.vector() = when (this) {
    TaskIcon.SUN -> HeIcons.Sun
    TaskIcon.RECEIPT -> HeIcons.Receipt
    TaskIcon.PLANE -> HeIcons.Plane
    TaskIcon.MAIL -> HeIcons.Briefcase
    TaskIcon.CHART -> HeIcons.Chart
}

private fun TaskInfo.icon() = if (action == TaskAction.NOTIFY) HeIcons.Bell else HeIcons.Sparkle

/** Days of the current week (1–7) on which this task fires. */
private fun TaskInfo.daysThisWeek(): Set<Int> = when (spec.kind) {
    TaskKind.DAILY -> (1..7).toSet()
    TaskKind.WEEKLY -> Weekdays.days(spec.weekdays).toSet()
    TaskKind.ONCE -> spec.onceAt?.let { at ->
        val d = java.time.Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        if (!d.isBefore(monday) && d.isBefore(monday.plusDays(7))) setOf(d.dayOfWeek.value) else emptySet()
    } ?: emptySet()
}

/** "下次 11月21日 周六 08:00" / "已暂停" / "已完成". */
fun TaskInfo.nextLabel(running: Boolean): String = when {
    running -> "正在运行…"
    !enabled -> "已暂停"
    nextRunAt == null -> "已完成"
    else -> "下次 " + Schedule.formatAt(nextRunAt, ZoneId.systemDefault())
}

/** 06 任务 — week strip + scheduled tasks + stacked project cards. */
@Composable
fun TasksScreen(
    tasks: List<TaskInfo>,
    running: Set<String>,
    projects: List<Project>,
    exactAlarms: Boolean,
    onFixExactAlarms: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onOpenTask: (String) -> Unit,
    onCreate: (TaskDraft) -> Unit,
    onOpenMe: () -> Unit,
    onOpenTimeline: () -> Unit,
    toast: (String) -> Unit,
) {
    val today = LocalDate.now()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val sunday = monday.plusDays(6)
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        RootHeader(
            title = "任务",
            inlineSubtitle = "定时与项目",
            onAvatar = onOpenMe,
            actions = { RoundIconButton(HeIcons.Plus, { creating = true }, contentDescription = "新建定时任务") },
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
        WeekStrip(today.dayOfWeek.value, tasks.filter { it.enabled }.flatMap { it.daysThisWeek() }.toSet(), Modifier.padding(horizontal = 24.dp))
        if (!exactAlarms && tasks.any { it.enabled }) {
            Spacer(Modifier.height(12.dp))
            WhiteCard(Modifier.padding(horizontal = 24.dp).fillMaxWidth(), color = HeColors.AmberTint, border = null, shadow = false, onClick = onFixExactAlarms) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("开启「闹钟和提醒」权限，任务才能准时触发；现在可能会晚几分钟。", fontSize = 13.sp, lineHeight = 19.sp, color = HeColors.AmberInk, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text("去开启", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HeColors.AmberInk)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (tasks.isEmpty()) {
                EmptyState(
                    "还没有定时任务",
                    "在对话里说「每天早上 8 点给我发早报」或「每天 8 点提醒我喝水」，或点右上角 + 自己建一个。",
                    action = "新建定时任务",
                    onAction = { creating = true },
                )
            }
            tasks.forEach { t -> TaskRow(t, t.id in running, { onToggle(t.id, it) }) { onOpenTask(t.id) } }
        }
        Spacer(Modifier.height(28.dp))
        SectionHeader("项目", Modifier.padding(horizontal = 24.dp), action = "新建", actionIcon = HeIcons.Plus, onAction = { toast("新建项目即将支持") })
        Spacer(Modifier.height(12.dp))
        if (projects.isEmpty()) {
            EmptyState("还没有项目", "多步骤、跨多天的任务会成为一个项目，拥有独立的对话、文件和待办。")
        } else {
            ProjectStack(projects, Modifier.padding(horizontal = 24.dp), onOpenTimeline)
        }
        Spacer(Modifier.height(24.dp))
    }
    if (creating) {
        CreateTaskSheet(onDismiss = { creating = false }, onCreate = { d -> creating = false; onCreate(d) })
    }
}

@Composable
private fun WeekStrip(todayIdx: Int, activeDays: Set<Int>, modifier: Modifier = Modifier) {
    WhiteCard(modifier.fillMaxWidth().height(56.dp), shape = HeShapes.Pill) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            for (d in 1..7) {
                val isToday = d == todayIdx
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(if (isToday) HeColors.Accent else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(weekdayShort(d), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (isToday) Color.White else HeColors.Ink)
                    }
                    if (d in activeDays && !isToday) {
                        Box(Modifier.align(Alignment.BottomCenter).offset(y = (-2).dp).size(4.dp).clip(CircleShape).background(HeColors.Accent.copy(alpha = 0.6f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(t: TaskInfo, running: Boolean, onToggle: (Boolean) -> Unit, onOpen: () -> Unit) {
    WhiteCard(Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = HeShapes.Row, onClick = onOpen) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIcon(t.icon(), size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Schedule.describe(t.spec, ZoneId.systemDefault())} · ${t.nextLabel(running)}",
                    fontSize = 13.sp, color = HeColors.Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (t.lastStatus == "失败") {
                    Text("上次运行失败", fontSize = 12.sp, color = HeColors.CoralInk)
                }
            }
            HeToggle(t.enabled, onToggle)
        }
    }
}

// ---------------------------------------------------------------------------------------------- detail

/** Task detail: schedule, what it does, actions, and the run history (each run opens its timeline). */
@Composable
fun TaskDetailScreen(
    task: TaskInfo?,
    runs: List<TaskRunEntity>,
    running: Boolean,
    onBack: () -> Unit,
    onRunNow: () -> Unit,
    onSkipNext: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onOpenRun: (Long) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        SubTopBar(null, onBack)
        if (task == null) {
            EmptyState("任务不存在", "它可能已经被删除了。", Modifier.weight(1f))
            return@Column
        }
        val zone = ZoneId.systemDefault()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIcon(task.icon(), size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Text(task.title, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink, maxLines = 2, modifier = Modifier.weight(1f))
                HeToggle(task.enabled, onToggle)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeTag(task.action.label, TagKind.NEUTRAL)
                HeTag(Schedule.describe(task.spec, zone), TagKind.ACCENT)
                when {
                    running -> HeTag("运行中", TagKind.MINT)
                    task.lastStatus == "失败" -> HeTag("上次失败", TagKind.AMBER)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(task.nextLabel(running), fontSize = 15.sp, color = HeColors.Ink2)
            Spacer(Modifier.height(16.dp))
            WhiteCard(Modifier.fillMaxWidth(), shadow = false) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (task.action == TaskAction.NOTIFY) "提醒内容" else "每次运行时 Helium 会", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink2)
                    Spacer(Modifier.height(6.dp))
                    Text(task.prompt, fontSize = 15.sp, lineHeight = 22.sp, color = HeColors.Ink)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HePillButton("立即运行", onRunNow, kind = ButtonKind.PRIMARY, height = HeButton.Small, enabled = !running)
                HePillButton("跳过下一次", onSkipNext, kind = ButtonKind.OUTLINE, height = HeButton.Small, enabled = task.enabled && task.nextRunAt != null)
                HePillButton(if (task.enabled) "暂停" else "恢复", { onToggle(!task.enabled) }, kind = ButtonKind.OUTLINE, height = HeButton.Small)
                HePillButton("删除", { confirmDelete = true }, kind = ButtonKind.TONAL, height = HeButton.Small)
            }
            Spacer(Modifier.height(24.dp))
            Text("运行记录", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink)
            Spacer(Modifier.height(10.dp))
            if (runs.isEmpty()) {
                Text("还没有运行过。到点后每次运行的结果、耗时和失败原因都会记在这里。", fontSize = 14.sp, lineHeight = 21.sp, color = HeColors.Ink2)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                runs.forEach { r -> RunRow(r, zone, task.action == TaskAction.AGENT) { onOpenRun(r.id) } }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除「${task?.title}」？") },
            text = { Text("任务和运行记录会被删除，它的对话保留。") },
            confirmButton = { TextButton({ confirmDelete = false; onDelete() }) { Text("删除", color = HeColors.CoralInk) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun RunRow(r: TaskRunEntity, zone: ZoneId, hasTimeline: Boolean, onOpen: () -> Unit) {
    val (label, kind) = when (r.status) {
        RunStatus.SUCCESS -> "成功" to TagKind.MINT
        RunStatus.FAILED -> "失败" to TagKind.AMBER
        RunStatus.RUNNING -> "运行中" to TagKind.ACCENT
        else -> "跳过" to TagKind.NEUTRAL
    }
    val clickable = hasTimeline && r.status != RunStatus.SKIPPED
    WhiteCard(Modifier.fillMaxWidth(), shape = HeShapes.Row, shadow = false, onClick = if (clickable) onOpen else null) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Schedule.formatAt(r.startedAt, zone), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink, modifier = Modifier.weight(1f))
                Text(RunCause.label(r.cause) + if (r.attempt > 1) " · 第 ${r.attempt} 次" else "", fontSize = 12.sp, color = HeColors.Ink3)
                Spacer(Modifier.width(8.dp))
                HeTag(label, kind, height = 22.dp, fontSize = 11)
            }
            val body = r.error ?: r.summary
            if (!body.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(body, fontSize = 13.sp, lineHeight = 19.sp, color = if (r.error != null) HeColors.CoralInk else HeColors.Ink2, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            r.nextRetryAt?.let {
                Spacer(Modifier.height(2.dp))
                Text("将于 ${Schedule.formatAt(it, zone)} 自动重试（需联网）", fontSize = 12.sp, color = HeColors.Ink3)
            }
            r.finishedAt?.let { f ->
                if (clickable) {
                    Spacer(Modifier.height(2.dp))
                    Text("耗时 ${((f - r.startedAt) / 1000).coerceAtLeast(0)} 秒 · 查看时间线", fontSize = 12.sp, color = HeColors.Accent)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- create

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .clip(HeShapes.Pill)
            .background(if (active) HeColors.AccentTint else Color.White)
            .then(if (active) Modifier else Modifier.border(1.dp, HeColors.Hair, HeShapes.Pill))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, color = if (active) HeColors.Accent else HeColors.Ink)
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String, minLines: Int = 1) {
    Box(
        Modifier.fillMaxWidth().clip(HeShapes.Row).background(HeColors.Mist3).border(BorderStroke(1.dp, HeColors.Hair), HeShapes.Row).padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, fontSize = 16.sp, color = HeColors.Ink3)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, color = HeColors.Ink),
            cursorBrush = SolidColor(HeColors.Accent),
            minLines = minLines,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(18.dp))
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink2)
    Spacer(Modifier.height(8.dp))
}

/** 新建定时任务: reminder or agent task, once / daily / weekly. Validation is the same parser the tools use. */
@Composable
private fun CreateTaskSheet(onDismiss: () -> Unit, onCreate: (TaskDraft) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var action by remember { mutableStateOf(TaskAction.NOTIFY) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(TaskKind.DAILY) }
    var time by remember { mutableStateOf("08:00") }
    var days by remember { mutableIntStateOf(Weekdays.WORKDAYS) }
    var dateOffset by remember { mutableIntStateOf(1) }
    var error by remember { mutableStateOf<String?>(null) }

    fun build(): Parsed<TaskDraft> {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val a = JSONObject().put("kind", kind.name.lowercase()).put("time", time)
        if (kind == TaskKind.WEEKLY) a.put("weekdays", JSONArray(Weekdays.days(days)))
        if (kind == TaskKind.ONCE) a.put("date", LocalDate.now(zone).plusDays(dateOffset.toLong()).toString())
        if (title.isNotBlank()) a.put("title", title.trim())
        return if (action == TaskAction.NOTIFY) TaskArgs.parseReminder(a.put("text", body), now, zone)
        else TaskArgs.parseCreate(a.put("prompt", body), now, zone)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Color.White, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("新建定时任务", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink)
                    Spacer(Modifier.height(4.dp))
                    Text("提醒到点弹通知；Agent 任务到点在后台跑一次，结果发通知", fontSize = 13.sp, color = HeColors.Ink2)
                }
                RoundIconButton(HeIcons.Close, { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }, contentDescription = "关闭")
            }
            Label("类型")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskAction.entries.reversed().forEach { a -> Chip(a.label, a == action) { action = a; error = null } }
            }
            Label(if (action == TaskAction.NOTIFY) "提醒我" else "到点时让 Helium")
            Field(body, { body = it; error = null }, if (action == TaskAction.NOTIFY) "喝水" else "查南京今天的天气和我今天的日历，给我 5 行以内的早报", minLines = if (action == TaskAction.NOTIFY) 1 else 3)
            Label("名字 · 可选")
            Field(title, { title = it }, if (action == TaskAction.NOTIFY) "默认用提醒内容" else "早报")
            Label("频率")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TaskKind.ONCE, TaskKind.DAILY, TaskKind.WEEKLY).forEach { k -> Chip(k.label, k == kind) { kind = k; error = null } }
            }
            if (kind == TaskKind.WEEKLY) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { d ->
                        Chip("周" + weekdayShort(d), Weekdays.has(days, d)) { days = days xor Weekdays.bit(d); error = null }
                    }
                }
            }
            if (kind == TaskKind.ONCE) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("今天", "明天", "后天").forEachIndexed { i, l -> Chip(l, dateOffset == i) { dateOffset = i; error = null } }
                }
            }
            Label("时间 · 24 小时制")
            Field(time, { time = it; error = null }, "08:00")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("07:30", "08:00", "12:00", "18:00", "21:00").forEach { t -> Chip(t, t == time) { time = t; error = null } }
            }
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error!!, fontSize = 13.sp, color = HeColors.CoralInk)
            }
            Spacer(Modifier.height(20.dp))
            HePillButton("创建", {
                when (val p = build()) {
                    is Parsed.Err -> error = p.message.replace("prompt", "要做的事").replace("text", "提醒内容")
                    is Parsed.Ok -> onCreate(p.value)
                }
            }, Modifier.fillMaxWidth(), ButtonKind.PRIMARY, HeIcons.Plus)
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------- projects

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
