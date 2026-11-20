package tech.qigai.helium.tasks

import tech.qigai.helium.agent.AgentEvent
import tech.qigai.helium.agent.AgentState

/** Status strings stored in `task_runs.status` (on-disk format: never rename). */
object RunStatus {
    const val RUNNING = "RUNNING"
    const val SUCCESS = "SUCCESS"
    const val FAILED = "FAILED"
    const val SKIPPED = "SKIPPED"
}

/** Why a run started (`task_runs.cause`). */
object RunCause {
    const val SCHEDULED = "SCHEDULED"
    const val RETRY = "RETRY"
    const val MANUAL = "MANUAL"
    const val CATCH_UP = "CATCH_UP"
    const val MISSED = "MISSED"

    fun label(c: String) = when (c) {
        SCHEDULED -> "按时"
        RETRY -> "重试"
        MANUAL -> "立即运行"
        CATCH_UP -> "补跑"
        MISSED -> "错过"
        else -> c
    }
}

sealed interface RunOutcome {
    data class Success(val summary: String) : RunOutcome
    data class Failed(val reason: String) : RunOutcome
}

object RunAnalysis {
    /** Decides how one headless agent run ended from the events it produced and the loop's final state. */
    fun of(runEvents: List<AgentEvent>, state: AgentState): RunOutcome {
        val last = runEvents.lastOrNull { it is AgentEvent.AssistantMessage } as AgentEvent.AssistantMessage?
        if (last?.error != null) return RunOutcome.Failed(FailureReason.classify(last.error))
        return when (state) {
            AgentState.ERROR -> RunOutcome.Failed(FailureReason.classify(last?.error))
            AgentState.STOPPED -> RunOutcome.Failed(if (last?.stopped == true) "运行被中止" else "重复同一步没有进展，已自动停止")
            else -> {
                val text = last?.text?.trim().orEmpty()
                if (last == null) RunOutcome.Failed("模型没有返回内容") else RunOutcome.Success(text.ifEmpty { "已完成" })
            }
        }
    }

    /** First ~[max] chars of an answer, markdown stripped, for a notification. */
    fun preview(text: String, max: Int = 240): String {
        val plain = text.replace(Regex("\\[(\\d+)]"), "").replace(Regex("[*_`#>]+"), "").replace(Regex("\\n{2,}"), "\n").trim()
        return if (plain.length <= max) plain else plain.take(max - 1) + "…"
    }
}
