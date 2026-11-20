package tech.qigai.helium

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isEditableText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tech.qigai.helium.agent.AgentEvent
import tech.qigai.helium.agent.GrantScope
import tech.qigai.helium.data.db.ApprovalEntity
import tech.qigai.helium.data.db.MemoryCandidateEntity
import tech.qigai.helium.data.settings.LlmSettings
import tech.qigai.helium.tasks.TaskDraft
import tech.qigai.helium.tasks.TaskKind
import tech.qigai.helium.ui.screens.ApprovalScreen
import tech.qigai.helium.ui.screens.ChatInputBar
import tech.qigai.helium.ui.screens.ChatScreen
import tech.qigai.helium.ui.screens.ChatViewModel
import tech.qigai.helium.ui.screens.MemoryCandidateCard
import tech.qigai.helium.ui.screens.TasksScreen

/**
 * M7 主流程 Compose UI 测试（androidTest，需模拟器；CI 的 ui-test job 跑它）。
 *
 * 刻意不用真实网络：聊天测试只验证“输入 → 点发送 → 回调拿到文本”与
 * “已发消息渲染出来”，不真的调模型；审批/记忆/任务测试都用假回调断言。
 * 这样测试在无 Key、无网络的 CI 模拟器上也能稳定通过。
 */
@RunWith(AndroidJUnit4::class)
class MainFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private fun chatVm(): ChatViewModel {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        return ChatViewModel(app)
    }

    @Test
    fun chatComposer_sendDeliversTypedText() {
        val vm = chatVm()
        val sent = mutableListOf<String>()
        compose.setContent {
            ChatInputBar(vm = vm, onSend = { sent += vm.draft.value }, toast = {})
        }
        // 占位文案是只读 Text 节点，必须用可编辑节点输入。
        compose.onAllNodes(isEditableText())[0].performTextInput("南京明天要带伞吗")
        compose.onNodeWithText("南京明天要带伞吗").assertIsDisplayed()
        compose.onNodeWithContentDescription("发送").performClick()
        assertEquals(listOf("南京明天要带伞吗"), sent)
    }

    @Test
    fun chatScreen_rendersSentMessage() {
        val vm = chatVm()
        vm.events += AgentEvent.UserMessage("渲染检查：我对花生过敏")
        compose.setContent {
            ChatScreen(
                vm = vm,
                settings = LlmSettings(),
                onSelectModel = {},
                onBack = {},
                onOpenTimeline = {},
                onOpenModels = {},
                toast = {},
            )
        }
        compose.onNodeWithText("渲染检查：我对花生过敏").assertIsDisplayed()
    }

    @Test
    fun approvalCard_approveCallsBackWithOnceScope() {
        val decided = mutableListOf<Triple<String, Boolean, GrantScope?>>()
        val pending = listOf(
            ApprovalEntity(
                requestId = "r1", conversationId = "c1", tool = "sms_send",
                title = "发送短信", detail = "给张三发短信：我晚到 10 分钟", target = "张三",
                risk = "HIGH", warning = null, scopes = "ONCE",
                status = "PENDING", createdAt = 1L, decidedAt = null,
            ),
        )
        compose.setContent {
            ApprovalScreen(
                pending = pending,
                onDecide = { id, approved, scope -> decided += Triple(id, approved, scope) },
                onClose = {},
            )
        }
        compose.onNodeWithText("给张三发短信：我晚到 10 分钟").assertIsDisplayed()
        compose.onNodeWithText("批准（仅这一次）").performClick()
        assertEquals(listOf(Triple("r1", true, GrantScope.ONCE)), decided)
    }

    @Test
    fun approvalCard_rejectCallsBackWithFalse() {
        val decided = mutableListOf<Triple<String, Boolean, GrantScope?>>()
        val pending = listOf(
            ApprovalEntity(
                requestId = "r2", conversationId = "c1", tool = "sms_send",
                title = "发送短信", detail = "给张三发短信：我晚到 10 分钟", target = "张三",
                risk = "HIGH", warning = null, scopes = "ONCE",
                status = "PENDING", createdAt = 1L, decidedAt = null,
            ),
        )
        compose.setContent {
            ApprovalScreen(
                pending = pending,
                onDecide = { id, approved, scope -> decided += Triple(id, approved, scope) },
                onClose = {},
            )
        }
        compose.onNodeWithText("拒绝").performClick()
        assertEquals(listOf(Triple("r2", false, null)), decided)
    }

    @Test
    fun memoryCandidate_acceptSavesIt() {
        val accepted = mutableListOf<Long>()
        val candidates = listOf(
            MemoryCandidateEntity(
                id = 7L, conversationId = "c1", text = "我对花生过敏",
                domain = "饮食", inferred = false, status = "PENDING", createdAt = 1L,
            ),
        )
        compose.setContent {
            MemoryCandidateCard(
                candidates = candidates,
                onAccept = { accepted += it },
                onDismiss = {},
                onNever = {},
            )
        }
        compose.onNodeWithText("要我记住吗？").assertIsDisplayed()
        compose.onNodeWithText("我对花生过敏").assertIsDisplayed()
        compose.onNodeWithText("记住").performClick()
        assertEquals(listOf(7L), accepted)
    }

    @Test
    fun taskCreateSheet_createsDailyReminder() {
        val created = mutableListOf<TaskDraft>()
        compose.setContent {
            TasksScreen(
                tasks = emptyList(),
                running = emptySet(),
                projects = emptyList(),
                exactAlarms = true,
                onFixExactAlarms = {},
                onToggle = { _, _ -> },
                onOpenTask = {},
                onCreate = { created += it },
                onOpenMe = {},
                onOpenTimeline = {},
                toast = {},
            )
        }
        compose.onNodeWithContentDescription("新建定时任务").performClick()
        compose.onNodeWithText("新建定时任务").assertIsDisplayed()
        // 表单里第一个可编辑框是提醒内容（占位“喝水”），默认每天 08:00。
        compose.onAllNodes(isEditableText())[0].performTextInput("喝水")
        compose.onNodeWithText("创建").performClick()
        assertEquals(1, created.size)
        assertEquals("喝水", created[0].prompt)
        assertEquals(TaskKind.DAILY, created[0].spec.kind)
    }
}
