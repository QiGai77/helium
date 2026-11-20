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
import tech.qigai.helium.data.media.ImageStore
import tech.qigai.helium.data.settings.SearchProviderId
import tech.qigai.helium.data.settings.SettingsRepository

/** Composition root: one instance of every repository, the tool registry and the sentinel. */
class HeliumApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val db: HeliumDatabase by lazy { HeliumDatabase.open(this) }

    val images by lazy { ImageStore(this) }
    val conversations by lazy { ConversationRepository(db, onImagesDropped = { images.delete(it) }) }
    val memories by lazy { RoomMemoryStore(db) }
    val grants by lazy { RoomGrantStore(db) }
    val audit by lazy { AuditRepository(db) }
    val approvals by lazy { ApprovalBroker(db) }
    val tasks by lazy { TaskRepository(db) }

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
