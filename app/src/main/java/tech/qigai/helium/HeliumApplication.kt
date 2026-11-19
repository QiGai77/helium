package tech.qigai.helium

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tech.qigai.helium.agent.Sentinel
import tech.qigai.helium.agent.TrustMode
import tech.qigai.helium.agent.tools.MemoryForgetTool
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
import tech.qigai.helium.data.db.TaskRepository
import tech.qigai.helium.data.llm.LlmClient
import tech.qigai.helium.data.settings.SettingsRepository

/** Composition root: one instance of every repository, the tool registry and the sentinel. */
class HeliumApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val db: HeliumDatabase by lazy { HeliumDatabase.open(this) }

    val conversations by lazy { ConversationRepository(db) }
    val memories by lazy { RoomMemoryStore(db) }
    val grants by lazy { RoomGrantStore(db) }
    val audit by lazy { AuditRepository(db) }
    val approvals by lazy { ApprovalBroker(db) }
    val tasks by lazy { TaskRepository(db) }

    val tools by lazy {
        ToolRegistry(
            listOf(
                TimeTool(),
                WebFetchTool(LlmClient.defaultHttp, LlmClient.USER_AGENT),
                MemorySearchTool(memories),
                MemorySaveTool(memories),
                MemoryForgetTool(memories),
            ),
        )
    }

    var trustMode: TrustMode = TrustMode.STANDARD
    val sentinel by lazy { Sentinel(grants, mode = { trustMode }) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            // Nothing can still be waiting on an approval from a previous process.
            approvals.expireStale()
            grants.purgeExpired()
        }
    }
}
