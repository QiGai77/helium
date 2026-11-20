@file:OptIn(ExperimentalLayoutApi::class)

package tech.qigai.helium.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import tech.qigai.helium.HeliumApplication
import tech.qigai.helium.ui.components.HeBottomNav
import tech.qigai.helium.ui.components.NavTab
import tech.qigai.helium.ui.components.rememberToast
import tech.qigai.helium.ui.screens.ApprovalScreen
import tech.qigai.helium.ui.screens.AuditScreen
import tech.qigai.helium.ui.screens.timelineFrom
import tech.qigai.helium.agent.AgentState
import tech.qigai.helium.data.model.MemoryItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.navigation.NavType
import androidx.navigation.navArgument
import tech.qigai.helium.ui.screens.TaskDetailScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import tech.qigai.helium.ui.screens.ChatScreen
import tech.qigai.helium.ui.screens.ChatViewModel
import tech.qigai.helium.ui.screens.GlanceScreen
import tech.qigai.helium.ui.screens.HomeScreen
import tech.qigai.helium.ui.screens.MeScreen
import tech.qigai.helium.ui.screens.MemorySkillsScreen
import tech.qigai.helium.ui.screens.ModelsScreen
import tech.qigai.helium.ui.screens.TasksScreen
import tech.qigai.helium.ui.screens.nextLabel
import tech.qigai.helium.ui.screens.TimelineScreen
import tech.qigai.helium.ui.theme.HeColors

object Routes {
    const val HOME = "home"
    const val CHAT = "chat"
    const val GLANCE = "glance"
    const val TASKS = "tasks"
    const val ME = "me"
    const val MODELS = "models"
    const val MEMORY = "memory"
    const val APPROVAL = "approval"
    const val TIMELINE = "timeline"
    const val AUDIT = "audit"
    const val TASK = "task/{id}"
    const val TASK_RUN = "taskrun/{runId}"
    fun task(id: String) = "task/$id"
    fun taskRun(runId: Long) = "taskrun/$runId"
}

/** Tabs that show the bottom nav. 对话 is full-screen (design 02: 对话详情页无底栏); 记忆与技能 shows it unselected. */
private fun tabFor(route: String?): NavTab? = when (route) {
    Routes.HOME -> NavTab.HOME
    Routes.GLANCE -> NavTab.GLANCE
    Routes.TASKS -> NavTab.TASKS
    else -> null
}

private fun showsNav(route: String?) = route == Routes.HOME || route == Routes.GLANCE || route == Routes.TASKS || route == Routes.MEMORY

@Composable
fun HeliumNavHost(nav: NavHostController = rememberNavController()) {
    val app = LocalContext.current.applicationContext as HeliumApplication
    val chatVm: ChatViewModel = viewModel()
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val toast = rememberToast()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pending by app.approvals.observePending().collectAsState(initial = emptyList())
    val memories by app.memories.observeAll().collectAsState(initial = emptyList())
    val grants by app.grants.observeAll().collectAsState(initial = emptyList())
    val audit by app.audit.observeRecent().collectAsState(initial = emptyList())
    val weekAgo = remember { System.currentTimeMillis() - 7L * 24 * 3600_000 }
    val auditWeek by app.audit.observeCountSince(weekAgo).collectAsState(initial = 0)
    val taskRows by app.taskEngine.observeAll().collectAsState(initial = emptyList())
    val runningTasks by app.taskEngine.running.collectAsState()
    val pendingRoute by app.pendingRoute.collectAsState()
    val conversations by chatVm.conversations.collectAsState()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val navVisible = showsNav(route) && !WindowInsets.isImeVisible

    fun goTab(tab: NavTab) {
        nav.navigate(tab.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun open(r: String) = nav.navigate(r) { launchSingleTop = true }

    // Notifications deep-link here ("task/<id>", "approval").
    LaunchedEffect(pendingRoute) {
        val r = pendingRoute ?: return@LaunchedEffect
        app.pendingRoute.value = null
        runCatching { open(r) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(HeColors.Canvas)
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                modifier = Modifier.weight(1f).windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        draft = chatVm.draft.value,
                        onDraftChange = { chatVm.draft.value = it },
                        onSend = {
                            chatVm.send()
                            open(Routes.CHAT)
                        },
                        agentRunning = chatVm.running.value,
                        pending = pending,
                        lastConversation = conversations.firstOrNull()?.title,
                        onOpenChat = { open(Routes.CHAT) },
                        onOpenGlance = { goTab(NavTab.GLANCE) },
                        onOpenApproval = { open(Routes.APPROVAL) },
                        onOpenMe = { open(Routes.ME) },
                        toast = toast,
                    )
                }
                composable(Routes.CHAT) {
                    ChatScreen(
                        vm = chatVm,
                        settings = settings,
                        onSelectModel = { app.settings.setOpenCodeModel(it) },
                        onBack = { if (!nav.popBackStack()) goTab(NavTab.HOME) },
                        onOpenTimeline = { open(Routes.TIMELINE) },
                        onOpenModels = { open(Routes.MODELS) },
                        toast = toast,
                    )
                }
                composable(Routes.GLANCE) { GlanceScreen(today = null, onOpenMe = { open(Routes.ME) }, toast = toast) }
                composable(Routes.TASKS) {
                    var exactOk by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(app.taskScheduler.exactAllowed()) }
                    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
                        exactOk = app.taskScheduler.exactAllowed()
                        onPauseOrDispose { }
                    }
                    TasksScreen(
                        tasks = taskRows,
                        running = runningTasks,
                        projects = emptyList(),
                        exactAlarms = exactOk,
                        onFixExactAlarms = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + context.packageName)),
                                    )
                                }
                            }
                        },
                        onToggle = { id, on -> scope.launch { app.taskEngine.setEnabled(id, on) } },
                        onOpenTask = { open(Routes.task(it)) },
                        onCreate = { d ->
                            scope.launch {
                                val t = app.taskEngine.create(d)
                                toast(if (t.nextRunAt != null) "已创建「${t.title}」，${t.nextLabel(false)}" else "已创建「${t.title}」")
                            }
                        },
                        onOpenMe = { open(Routes.ME) },
                        onOpenTimeline = { open(Routes.TIMELINE) },
                        toast = toast,
                    )
                }
                composable(Routes.TASK, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    val task by app.taskEngine.observe(id).collectAsState(initial = null)
                    val runs by app.taskEngine.observeRuns(id).collectAsState(initial = emptyList())
                    TaskDetailScreen(
                        task = task,
                        runs = runs,
                        running = id in runningTasks,
                        onBack = { if (!nav.popBackStack()) goTab(NavTab.TASKS) },
                        onRunNow = { scope.launch { toast(if (app.taskEngine.runNow(id)) "已开始运行，完成后发通知" else "正在运行，等这次结束") } },
                        onSkipNext = { scope.launch { app.taskEngine.skipNext(id)?.let { toast("已跳过，${it.nextLabel(false)}") } } },
                        onToggle = { on -> scope.launch { app.taskEngine.setEnabled(id, on); toast(if (on) "已恢复" else "已暂停，不会再触发") } },
                        onDelete = { scope.launch { app.taskEngine.delete(id); nav.popBackStack(); toast("已删除") } },
                        onOpenRun = { open(Routes.taskRun(it)) },
                    )
                }
                composable(Routes.TASK_RUN, arguments = listOf(navArgument("runId") { type = NavType.LongType })) { entry ->
                    val runId = entry.arguments?.getLong("runId") ?: 0L
                    val runRow by app.taskEngine.observeRun(runId).collectAsState(initial = null)
                    val events by produceState(initialValue = emptyList<tech.qigai.helium.agent.AgentEvent>(), runRow) {
                        value = runRow?.let { app.taskEngine.runEvents(it) } ?: emptyList()
                    }
                    val live = runRow?.status == tech.qigai.helium.tasks.RunStatus.RUNNING
                    TimelineScreen(
                        run = timelineFrom(events, live, false),
                        running = false,
                        onStop = {},
                        onBack = { nav.popBackStack() },
                        onOpenApproval = { open(Routes.APPROVAL) },
                        toast = toast,
                    )
                }
                composable(Routes.ME) {
                    MeScreen(
                        settings = settings,
                        vaultCount = app.settings.vaultCount,
                        onBack = { nav.popBackStack() },
                        onOpenModels = { open(Routes.MODELS) },
                        onOpenMemory = { open(Routes.MEMORY) },
                        onOpenApproval = { open(Routes.APPROVAL) },
                        onOpenAudit = { open(Routes.AUDIT) },
                        memoryCount = memories.size,
                        auditCount = auditWeek,
                        grantCount = grants.size,
                        toast = toast,
                    )
                }
                composable(Routes.MODELS) {
                    ModelsScreen(settings = settings, repo = app.settings, onBack = { nav.popBackStack() }, toast = toast)
                }
                composable(Routes.MEMORY) {
                    MemorySkillsScreen(
                        memories = memories.map { MemoryItem(it.id.toString(), it.text, it.inferred) },
                        skills = emptyList(),
                        onAdd = { text -> scope.launch { app.memories.addManual(text, "其他") } },
                        onForget = { m -> scope.launch { m.id.toLongOrNull()?.let { app.memories.forget(it) } } },
                        onBack = { nav.popBackStack() },
                        toast = toast,
                    )
                }
                composable(Routes.APPROVAL) {
                    ApprovalScreen(
                        pending = pending,
                        onDecide = { id, approved, grant ->
                            scope.launch {
                                val live = app.approvals.decide(id, approved, grant)
                                toast(if (!live) "这一步已经失效（任务已停止）" else if (approved) "已批准，Helium 继续执行" else "已拒绝，Helium 不会执行这一步")
                            }
                        },
                        onClose = { nav.popBackStack() },
                    )
                }
                composable(Routes.TIMELINE) {
                    val st = chatVm.state.value
                    TimelineScreen(
                        run = timelineFrom(chatVm.events, chatVm.running.value, st == AgentState.AWAITING_APPROVAL),
                        running = chatVm.running.value,
                        onStop = chatVm::stop,
                        onBack = { nav.popBackStack() },
                        onOpenApproval = { open(Routes.APPROVAL) },
                        toast = toast,
                    )
                }
                composable(Routes.AUDIT) {
                    AuditScreen(
                        grants = grants,
                        audit = audit,
                        onRevoke = { id -> scope.launch { app.grants.revoke(id); toast("已撤销，下次会重新询问") } },
                        onExport = {
                            scope.launch {
                                val json = org.json.JSONArray(app.audit.all().map { a ->
                                    org.json.JSONObject().put("at", a.at).put("conversationId", a.conversationId).put("kind", a.kind)
                                        .put("tool", a.tool).put("target", a.target).put("detail", a.detail)
                                }).toString(2)
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "helium-audit.json")
                                    putExtra(android.content.Intent.EXTRA_TEXT, json)
                                }
                                context.startActivity(android.content.Intent.createChooser(send, "导出审计日志"))
                            }
                        },
                        onBack = { nav.popBackStack() },
                    )
                }
            }
            AnimatedVisibility(
                visible = navVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 4.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
                    HeBottomNav(
                        current = tabFor(route),
                        onSelect = { tab -> if (tab == NavTab.CHAT) open(Routes.CHAT) else goTab(tab) },
                    )
                }
            }
            if (!navVisible) {
                Box(Modifier.navigationBarsPadding())
            }
        }
    }
}
