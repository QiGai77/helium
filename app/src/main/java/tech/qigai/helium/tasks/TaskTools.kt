package tech.qigai.helium.tasks

import org.json.JSONArray
import org.json.JSONObject
import tech.qigai.helium.agent.Risk
import tech.qigai.helium.agent.tools.Tool
import tech.qigai.helium.agent.tools.ToolContext
import tech.qigai.helium.agent.tools.ToolOutput
import tech.qigai.helium.agent.tools.ToolSpec
import java.time.ZoneId

/** A stored task as the tools and screens see it. */
data class TaskInfo(
    val id: String,
    val title: String,
    val prompt: String,
    val action: TaskAction,
    val spec: ScheduleSpec,
    val enabled: Boolean,
    val nextRunAt: Long?,
    val lastStatus: String?,
    val lastRunAt: Long?,
    val failCount: Int,
    val conversationId: String,
    val createdAt: Long,
)

/** What the task tools need from the scheduler. Implemented by [TaskEngine] in the app, by a fake in tests. */
interface TaskService {
    suspend fun create(draft: TaskDraft): TaskInfo
    suspend fun list(): List<TaskInfo>
    suspend fun get(id: String): TaskInfo?
    suspend fun pause(id: String): TaskInfo?
    suspend fun resume(id: String): TaskInfo?
    suspend fun skipNext(id: String): TaskInfo?
    /** False when a run of this task is already in progress. */
    suspend fun runNow(id: String): Boolean
    suspend fun delete(id: String): Boolean
    suspend fun edit(id: String, patch: TaskPatch): TaskInfo?
    /** Exact alarms granted (SCHEDULE_EXACT_ALARM); otherwise times may drift by a few minutes. */
    fun exactAlarms(): Boolean
}

private fun prop(type: String, desc: String) = JSONObject().put("type", type).put("description", desc)

private fun scheduleProps(): JSONObject = JSONObject()
    .put("kind", prop("string", "once（一次）/ daily（每天）/ weekly（每周）").put("enum", JSONArray(listOf("once", "daily", "weekly"))))
    .put("time", prop("string", "本地时间，24 小时制 HH:mm，例如 08:00"))
    .put("weekdays", JSONObject().put("type", "array").put("items", JSONObject().put("type", "integer")).put("description", "weekly 用：1=周一 … 7=周日，例如 [1,2,3,4,5]"))
    .put("date", prop("string", "once 用：YYYY-MM-DD；不填则取下一个到点的时刻"))
    .put("in_minutes", prop("number", "once 用：从现在起多少分钟后（“10 分钟后提醒我”），与 time/date 二选一"))

/** Resolves an id or an unambiguous id prefix (models often shorten UUIDs). */
internal suspend fun TaskService.resolve(id: String): TaskInfo? =
    get(id) ?: list().filter { it.id.startsWith(id) }.singleOrNull()

internal fun TaskInfo.line(zone: ZoneId, now: Long): String {
    val next = when {
        !enabled -> "已暂停"
        nextRunAt == null -> "已完成，不会再运行"
        else -> "下次 " + Schedule.formatAt(nextRunAt, zone, now)
    }
    return "${action.label}「$title」· ${Schedule.describe(spec, zone)} · $next"
}

internal fun exactNote(svc: TaskService) =
    if (svc.exactAlarms()) "" else "\n注意：${TaskScheduler.ExactAlarmNotice.MESSAGE}可以在任务页点横幅去开启。"

class TaskCreateTool(private val svc: TaskService, private val zone: () -> ZoneId = { ZoneId.systemDefault() }, private val clock: () -> Long = System::currentTimeMillis) : Tool {
    override val spec = ToolSpec(
        "task_create",
        "新建定时任务：到点时 Helium 在后台按 prompt 运行一次 agent（可联网、读日历），结果写进该任务自己的对话并发通知。" +
            "适合“每天 8 点给我早报”“每周一总结…”。只是提醒某件事（喝水、开会）请用 reminder_create。",
        JSONObject().put("type", "object").put(
            "properties",
            scheduleProps()
                .put("title", prop("string", "任务名，简短，例如“早报”"))
                .put("prompt", prop("string", "到点时要做的事，写成给 Helium 的完整指令，例如“查南京今天天气、读我今天的日历，给我一份 5 行以内的早报”")),
        ).put("required", JSONArray(listOf("title", "prompt", "kind", "time"))),
    )

    override fun risk(args: JSONObject) = Risk.MEDIUM
    override fun target(args: JSONObject) = "tasks"
    override fun title(args: JSONObject) = "新建定时任务：" + args.optString("title").ifBlank { args.optString("prompt").take(20) }
    override fun detail(args: JSONObject): String = when (val p = TaskArgs.parseCreate(args, clock(), zone())) {
        is Parsed.Ok -> "时间：${Schedule.describe(p.value.spec, zone())}\n每次运行：${p.value.prompt}\n会在后台调用模型和工具，结果发通知。"
        is Parsed.Err -> args.toString(2)
    }

    override suspend fun run(args: JSONObject, ctx: ToolContext): ToolOutput {
        val now = clock()
        val d = when (val p = TaskArgs.parseCreate(args, now, zone())) {
            is Parsed.Err -> return ToolOutput(p.message, isError = true)
            is Parsed.Ok -> p.value
        }
        val t = svc.create(d)
        return ToolOutput("已创建：${t.line(zone(), now)}（id=${t.id}）。任务页可以暂停、跳过或立即运行。" + exactNote(svc))
    }
}

class ReminderCreateTool(private val svc: TaskService, private val zone: () -> ZoneId = { ZoneId.systemDefault() }, private val clock: () -> Long = System::currentTimeMillis) : Tool {
    override val spec = ToolSpec(
        "reminder_create",
        "设置提醒：到点时用精确闹钟直接弹出通知（不运行 agent）。可以是一次、每天或每周，例如“每天早上 8 点提醒我喝水”→ kind=daily, time=08:00, text=喝水；" +
            "“10 分钟后提醒我关火”→ in_minutes=10。提醒会出现在任务页。",
        JSONObject().put("type", "object").put(
            "properties",
            scheduleProps()
                .put("text", prop("string", "提醒内容，例如“喝水”"))
                .put("title", prop("string", "可选，通知标题；默认用 text")),
        ).put("required", JSONArray(listOf("text"))),
    )

    override fun risk(args: JSONObject) = Risk.LOW
    override fun target(args: JSONObject) = "reminders"
    override fun title(args: JSONObject) = "设置提醒：" + args.optString("text").take(20)

    override suspend fun run(args: JSONObject, ctx: ToolContext): ToolOutput {
        val now = clock()
        val d = when (val p = TaskArgs.parseReminder(args, now, zone())) {
            is Parsed.Err -> return ToolOutput(p.message, isError = true)
            is Parsed.Ok -> p.value
        }
        val t = svc.create(d)
        return ToolOutput("已设置：${t.line(zone(), now)}（id=${t.id}）" + exactNote(svc))
    }
}

class TaskListTool(private val svc: TaskService, private val zone: () -> ZoneId = { ZoneId.systemDefault() }, private val clock: () -> Long = System::currentTimeMillis) : Tool {
    override val spec = ToolSpec(
        "task_list",
        "列出所有定时任务和提醒（id、时间、下次运行、上次结果）。修改或删除前先调用拿到 id。",
        JSONObject().put("type", "object").put("properties", JSONObject()),
    )

    override fun risk(args: JSONObject) = Risk.LOW
    override fun title(args: JSONObject) = "查看定时任务"

    override suspend fun run(args: JSONObject, ctx: ToolContext): ToolOutput {
        val all = svc.list()
        if (all.isEmpty()) return ToolOutput("还没有定时任务或提醒。")
        val now = clock()
        return ToolOutput(JSONArray(all.map { t ->
            JSONObject().put("id", t.id).put("type", if (t.action == TaskAction.NOTIFY) "reminder" else "task").put("title", t.title)
                .put("schedule", Schedule.describe(t.spec, zone())).put("enabled", t.enabled)
                .put("next_run", t.nextRunAt?.let { Schedule.formatAt(it, zone(), now) } ?: JSONObject.NULL)
                .put("last_status", t.lastStatus ?: JSONObject.NULL)
                .put("prompt", t.prompt.take(200))
        }).toString())
    }
}

class TaskUpdateTool(private val svc: TaskService, private val zone: () -> ZoneId = { ZoneId.systemDefault() }, private val clock: () -> Long = System::currentTimeMillis) : Tool {
    override val spec = ToolSpec(
        "task_update",
        "管理一个定时任务或提醒：pause 暂停 / resume 恢复 / skip_next 跳过下一次 / run_now 立即运行 / delete 删除 / edit 修改（title、prompt、kind、time、weekdays、date）。id 来自 task_list。",
        JSONObject().put("type", "object").put(
            "properties",
            scheduleProps()
                .put("id", prop("string", "任务 id"))
                .put("action", prop("string", "pause / resume / skip_next / run_now / delete / edit").put("enum", JSONArray(listOf("pause", "resume", "skip_next", "run_now", "delete", "edit"))))
                .put("title", prop("string", "edit 用：新名字"))
                .put("prompt", prop("string", "edit 用：新的指令或提醒内容")),
        ).put("required", JSONArray(listOf("id", "action"))),
    )

    /** Deleting is irreversible; changing what runs unattended or triggering a run needs a look; pausing never hurts. */
    override fun risk(args: JSONObject): Risk = when (TaskArgs.parseOp(args.optString("action"))) {
        UpdateOp.DELETE -> Risk.HIGH
        UpdateOp.EDIT, UpdateOp.RUN_NOW, UpdateOp.RESUME -> Risk.MEDIUM
        UpdateOp.PAUSE, UpdateOp.SKIP_NEXT -> Risk.LOW
        null -> Risk.MEDIUM
    }

    override fun target(args: JSONObject) = "task:" + args.optString("id")
    override fun title(args: JSONObject) = when (TaskArgs.parseOp(args.optString("action"))) {
        UpdateOp.DELETE -> "删除定时任务"
        UpdateOp.EDIT -> "修改定时任务"
        UpdateOp.RUN_NOW -> "立即运行定时任务"
        UpdateOp.RESUME -> "恢复定时任务"
        UpdateOp.PAUSE -> "暂停定时任务"
        UpdateOp.SKIP_NEXT -> "跳过下一次"
        null -> "管理定时任务"
    }

    override suspend fun run(args: JSONObject, ctx: ToolContext): ToolOutput {
        val now = clock()
        val u = when (val p = TaskArgs.parseUpdate(args, now, zone())) {
            is Parsed.Err -> return ToolOutput(p.message, isError = true)
            is Parsed.Ok -> p.value
        }
        val t = svc.resolve(u.id) ?: return ToolOutput("没有 id 为 ${u.id} 的任务，先用 task_list 查。", isError = true)
        val z = zone()
        return when (u.op) {
            UpdateOp.PAUSE -> ToolOutput("已暂停：" + (svc.pause(t.id) ?: t).line(z, now))
            UpdateOp.RESUME -> {
                val r = svc.resume(t.id) ?: t
                if (r.nextRunAt == null) ToolOutput("「${t.title}」的时间已经过去，恢复后不会再运行；用 edit 改个时间。", isError = true)
                else ToolOutput("已恢复：" + r.line(z, now))
            }
            UpdateOp.SKIP_NEXT -> {
                val r = svc.skipNext(t.id) ?: t
                ToolOutput("已跳过下一次：" + r.line(z, now))
            }
            UpdateOp.RUN_NOW -> if (svc.runNow(t.id)) ToolOutput("已开始运行「${t.title}」，完成后会发通知。")
            else ToolOutput("「${t.title}」正在运行，等这次结束再试。", isError = true)
            UpdateOp.DELETE -> if (svc.delete(t.id)) ToolOutput("已删除「${t.title}」。") else ToolOutput("删除失败", isError = true)
            UpdateOp.EDIT -> ToolOutput("已修改：" + (svc.edit(t.id, u.patch) ?: t).line(z, now))
        }
    }
}
