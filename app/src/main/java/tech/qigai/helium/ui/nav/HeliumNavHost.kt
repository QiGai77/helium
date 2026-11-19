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
import tech.qigai.helium.ui.screens.ChatScreen
import tech.qigai.helium.ui.screens.ChatViewModel
import tech.qigai.helium.ui.screens.GlanceScreen
import tech.qigai.helium.ui.screens.HomeScreen
import tech.qigai.helium.ui.screens.MeScreen
import tech.qigai.helium.ui.screens.MemorySkillsScreen
import tech.qigai.helium.ui.screens.ModelsScreen
import tech.qigai.helium.ui.screens.TasksScreen
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
                composable(Routes.GLANCE) { GlanceScreen(onOpenMe = { open(Routes.ME) }, toast = toast) }
                composable(Routes.TASKS) {
                    TasksScreen(onOpenMe = { open(Routes.ME) }, onOpenTimeline = { open(Routes.TIMELINE) }, toast = toast)
                }
                composable(Routes.ME) {
                    MeScreen(
                        settings = settings,
                        vaultCount = app.settings.vaultCount,
                        onBack = { nav.popBackStack() },
                        onOpenModels = { open(Routes.MODELS) },
                        onOpenMemory = { open(Routes.MEMORY) },
                        onOpenApproval = { open(Routes.APPROVAL) },
                        toast = toast,
                    )
                }
                composable(Routes.MODELS) {
                    ModelsScreen(settings = settings, repo = app.settings, onBack = { nav.popBackStack() }, toast = toast)
                }
                composable(Routes.MEMORY) { MemorySkillsScreen(onBack = { nav.popBackStack() }, toast = toast) }
                composable(Routes.APPROVAL) { ApprovalScreen(onClose = { nav.popBackStack() }, toast = toast) }
                composable(Routes.TIMELINE) {
                    TimelineScreen(onBack = { nav.popBackStack() }, onOpenApproval = { open(Routes.APPROVAL) }, toast = toast)
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
