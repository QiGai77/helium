package tech.qigai.helium.ui.screens

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.qigai.helium.HeliumApplication
import tech.qigai.helium.data.llm.ChatTurn
import tech.qigai.helium.data.llm.LlmClient
import tech.qigai.helium.data.llm.LlmException
import tech.qigai.helium.data.llm.Role
import tech.qigai.helium.data.llm.StreamEvent
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String,
    val reasoning: String = "",
    val streaming: Boolean = false,
    val error: String? = null,
    val stopped: Boolean = false,
    val model: String? = null,
    val at: LocalDateTime = LocalDateTime.now(),
)

/** Real streaming chat against the configured provider. History lives in memory for v0.1. */
class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = (app as HeliumApplication).settings
    private val client = LlmClient()
    private val ids = AtomicLong(1)

    val messages = mutableStateListOf<ChatMessage>()
    val draft = mutableStateOf("")
    val running = mutableStateOf(false)
    private var job: Job? = null
    private var generation = 0

    /** Stable per conversation (x-opencode-session); a new conversation gets a new id. */
    private var sessionId = UUID.randomUUID().toString()

    fun send(text: String = draft.value) {
        val prompt = text.trim()
        if (prompt.isEmpty()) return
        if (running.value) stop()
        draft.value = ""
        messages += ChatMessage(ids.getAndIncrement(), Role.USER, prompt)
        val history = messages.filter { it.error == null && (it.role == Role.USER || it.text.isNotBlank()) }
            .map { ChatTurn(it.role, it.text) }
        val replyId = ids.getAndIncrement()
        messages += ChatMessage(replyId, Role.ASSISTANT, "", streaming = true, model = settings.state.value.activeModelLabel)
        running.value = true
        val gen = ++generation
        job = viewModelScope.launch {
            val endpoint = withContext(Dispatchers.IO) { settings.activeEndpoint() }
            val text = StringBuilder()
            val reasoning = StringBuilder()
            try {
                client.stream(endpoint, SYSTEM_PROMPT, history, sessionId).collect { ev ->
                    when (ev) {
                        is StreamEvent.Text -> text.append(ev.delta)
                        is StreamEvent.Reasoning -> reasoning.append(ev.delta)
                        is StreamEvent.Done -> Unit
                    }
                    update(replyId) { it.copy(text = text.toString(), reasoning = reasoning.toString()) }
                }
                update(replyId) { it.copy(streaming = false) }
            } catch (e: CancellationException) {
                update(replyId) { it.copy(streaming = false, stopped = true) }
                throw e
            } catch (e: LlmException) {
                update(replyId) { it.copy(streaming = false, error = e.message) }
            } catch (e: Exception) {
                update(replyId) { it.copy(streaming = false, error = e.message ?: e.javaClass.simpleName) }
            } finally {
                if (gen == generation) running.value = false
            }
        }
    }

    /** Stop keeps whatever was streamed so far and never touches the draft. */
    fun stop() {
        job?.cancel()
        job = null
        running.value = false
        val idx = messages.indexOfLast { it.streaming }
        if (idx >= 0) messages[idx] = messages[idx].copy(streaming = false, stopped = true)
    }

    fun retryLast() {
        val lastUser = messages.lastOrNull { it.role == Role.USER } ?: return
        val idx = messages.indexOf(lastUser)
        while (messages.size > idx) messages.removeAt(messages.lastIndex)
        send(lastUser.text)
    }

    fun newConversation() {
        stop()
        messages.clear()
        sessionId = UUID.randomUUID().toString()
    }

    private inline fun update(id: Long, f: (ChatMessage) -> ChatMessage) {
        val idx = messages.indexOfLast { it.id == id }
        if (idx >= 0) messages[idx] = f(messages[idx])
    }

    companion object {
        val SYSTEM_PROMPT = """
            你是 Helium，用户手机里的个人 AI agent。回答要直接、具体、简洁，先给答案再给理由；默认使用用户的语言（通常是简体中文）。
            你在花钱、发送消息、删除内容或做出承诺前一定先征得用户同意。
            当前版本（v0.1）只能对话：还不能真正操作浏览器、手机或账户。如果用户请你执行这类动作，说明你会怎么做、需要用户确认什么，并给出可以手动完成的步骤。
        """.trimIndent()
    }
}
