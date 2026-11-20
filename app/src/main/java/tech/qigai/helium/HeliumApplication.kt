package tech.qigai.helium

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tech.qigai.helium.agent.Sentinel
import tech.qigai.helium.agent.TrustMode
import tech.qigai.helium.agent.search.DeepSeekSearch
import tech.qigai.helium.agent.search.ExaMcpSearch
import tech.qigai.helium.agent.search.SearchProvider
import tech.qigai.helium.agent.search.WebSearchTool
import tech.qigai.helium.agent.tools.CalendarReadTool
import tech.qigai.helium.agent.tools.CalendarWriteTool
import tech.qigai.helium.agent.tools.MemoryForgetTool
import tech.qigai.helium.agent.tools.NotifyTool
import tech.qigai.helium.agent.tools.SmsSendTool
import tech.qigai.helium.data.device.AndroidSms
import tech.qigai.helium.agent.tools.MemorySaveTool
import tech.qigai.helium.agent.tools.MemorySearchTool
import tech.qigai.helium.agent.tools.TimeTool
import tech.qigai.helium.agent.tools.ToolRegistry
import tech.qigai.helium.agent.tools.WebFetchTool
import tech.qigai.helium.data.db.ApprovalBroker
import tech.qigai.helium.data.db.AuditRepository
import tech.qigai.helium.data.db.ConversationRepository
import tech.qigai.helium.data.db.HeliumDatabase
import tech.qigai.helium.data.db.RoomGrantStore
import tech.qigai.helium.data.db.RoomMemoryStore
import tech.qigai.helium.data.db.RoomSkillStore
import tech.qigai.helium.agent.tools.LoadSkillTool
import tech.qigai.helium.agent.tools.SkillDeleteTool
import tech.qigai.helium.agent.tools.SkillSaveTool
import tech.qigai.helium.data.db.TaskRepository
import tech.qigai.helium.data.llm.LlmClient
import tech.qigai.helium.data.media.ImageStore
import tech.qigai.helium.data.settings.SearchProviderId
import tech.qigai.helium.data.settings.SettingsRepository
import tech.qigai.helium.data.device.AndroidCalendar
import tech.qigai.helium.data.device.AndroidCalendarWriter
import tech.qigai.helium.tasks.HeliumNotifications
import tech.qigai.helium.tasks.PermissionBroker
import tech.qigai.helium.tasks.ReminderCreateTool
import tech.qigai.helium.tasks.TaskCreateTool
import tech.qigai.helium.tasks.TaskEngine
import tech.qigai.helium.tasks.TaskListTool
import tech.qigai.helium.tasks.TaskScheduler
import tech.qigai.helium.tasks.TaskUpdateTool
import kotlinx.coroutines.flow.MutableStateFlow
import tech.qigai.helium.glance.GlanceRefreshWorker
import tech.qigai.helium.glance.GlanceRepository

/** Composition root: one instance of every repository, the tool registry and the sentinel. */
class HeliumApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val db: HeliumDatabase by lazy { HeliumDatabase.open(this) }

    val images by lazy { ImageStore(this) }
    val conversations by lazy { ConversationRepository(db, onImagesDropped = { images.delete(it) }) }
    val memories by lazy { RoomMemoryStore(db) }
    val skills by lazy { RoomSkillStore(db) }
    val grants by lazy { RoomGrantStore(db) }
    val audit by lazy { AuditRepository(db) }
    val approvals by lazy { ApprovalBroker(db) }
    val tasks by lazy { TaskRepository(db) }
    val glance by lazy { GlanceRepository(this, db, LlmClient.defaultHttp, LlmClient.USER_AGENT, settings, memories) }

    // M5: scheduled tasks, reminders, notifications, runtime permissions.
    val notifications by lazy { HeliumNotifications(this) }
    val permissions by lazy { PermissionBroker(this) }
    val taskScheduler by lazy { TaskScheduler(this) }
    val taskEngine by lazy { TaskEngine(this, taskScheduler, notifications) }

    /** Route a notification asked to open ("task/<id>", "approval"); the nav host consumes and clears it. */
    val pendingRoute = MutableStateFlow<String?>(null)

    val exaSearch: SearchProvider by lazy {
        ExaMcpSearch(
            LlmClient.defaultHttp,
            endpoint = { settings.search.value.let { if (it.cloudConfigured) it.cloudUrl + "/mcp/exa" else ExaMcpSearch.ENDPOINT } },
            bearer = { if (settings.search.value.cloudConfigured) settings.cloudToken() else null },
        )
    }
    val deepSeekSearch: SearchProvider by lazy {
        DeepSeekSearch(LlmClient.defaultHttp, baseUrl = { settings.deepSeekToolRoute().first }, bearer = { settings.deepSeekToolRoute().second })
    }

    /** The user's chosen search backend, falling back to free Exa when DeepSeek isn't set up. */
    fun activeSearch(): SearchProvider = when (settings.search.value.provider) {
        SearchProviderId.DEEPSEEK -> if (settings.search.value.deepSeekReady) deepSeekSearch else exaSearch
        SearchProviderId.EXA_MCP -> exaSearch
    }

    val tools by lazy {
        ToolRegistry(
            listOf(
                TimeTool(),
                WebSearchTool(primary = ::activeSearch, fallback = exaSearch),
                WebFetchTool(LlmClient.defaultHttp, LlmClient.USER_AGENT),
                MemorySearchTool(memories),
                MemorySaveTool(memories),
                MemoryForgetTool(memories),
                LoadSkillTool(skills),
                SkillSaveTool(skills),
                SkillDeleteTool(skills),
                CalendarReadTool(AndroidCalendar(this), permissions),
                CalendarWriteTool(AndroidCalendarWriter(this), permissions),
                NotifyTool(notifications, permissions),
                SmsSendTool(AndroidSms(this, foreground = { permissions.foreground() }, notifyOpen = notifications::openActivity)),
                ReminderCreateTool(taskEngine),
                TaskCreateTool(taskEngine),
                TaskListTool(taskEngine),
                TaskUpdateTool(taskEngine),
            ),
        )
    }

    var trustMode: TrustMode = TrustMode.STANDARD
    val sentinel by lazy { Sentinel(grants, mode = { trustMode }) }

    override fun onCreate() {
        super.onCreate()
        // M7: 崩溃日志只写本机 files/crash-logs/，不上传。
        tech.qigai.helium.crash.CrashHandler.install(this, BuildConfig.VERSION_NAME)
        appScope.launch {
            // Nothing can still be waiting on an approval from a previous process.
            approvals.expireStale()
            grants.purgeExpired()
        }
        appScope.launch {
            // Alarms are lost on force-stop and reboot; re-arm (idempotent) and record runs a dead process left open.
            notifications.ensureChannels()
            runCatching { taskEngine.rearmAll() }
        }
        // 速览: hourly weather / feeds / custom cards refresh.
        runCatching { GlanceRefreshWorker.schedule(this) }
    }
}
