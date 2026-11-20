package tech.qigai.helium.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import tech.qigai.helium.data.llm.LlmEndpoint
import tech.qigai.helium.data.llm.ModelCatalog
import tech.qigai.helium.data.llm.ModelOption
import tech.qigai.helium.data.llm.ProviderId
import tech.qigai.helium.data.llm.WireApi

/** Which backend `web_search` uses. Exa's public MCP is free and keyless, so it is the default and the fallback. */
enum class SearchProviderId(val label: String) { EXA_MCP("Exa（免费）"), DEEPSEEK("DeepSeek") }

/**
 * Search + Helium Cloud (the Cloudflare Worker) settings. Secrets are only exposed as booleans. DeepSeek search goes
 * through the Worker when one is configured (key stays server-side), otherwise straight to api.deepseek.com.
 */
data class SearchSettings(
    val provider: SearchProviderId = SearchProviderId.EXA_MCP,
    val cloudUrl: String = "",
    val hasCloudToken: Boolean = false,
    val hasDeepSeekKey: Boolean = false,
) {
    val cloudConfigured: Boolean get() = cloudUrl.isNotBlank() && hasCloudToken
    val deepSeekReady: Boolean get() = cloudConfigured || hasDeepSeekKey
}

/** Snapshot of model settings. API keys are only exposed as "configured or not" — never rendered. */
data class LlmSettings(
    val provider: ProviderId = ProviderId.OPENCODE_GO,
    val openCodeModel: String = ModelCatalog.openCodeDefaults.first().id,
    val openCodeModels: List<ModelOption> = ModelCatalog.openCodeDefaults,
    val geminiModel: String = ModelCatalog.geminiDefaults.first().id,
    val customBaseUrl: String = "",
    val customModel: String = "",
    val hasOpenCodeKey: Boolean = false,
    val hasGeminiKey: Boolean = false,
    val hasCustomKey: Boolean = false,
    /** Helium Cloud is connected, so OpenCode Go / Gemini work with keys held on the Worker. */
    val viaCloud: Boolean = false,
) {
    val activeModelLabel: String
        get() = when (provider) {
            ProviderId.OPENCODE_GO -> openCodeModels.firstOrNull { it.id == openCodeModel }?.label ?: openCodeModel
            ProviderId.GEMINI -> geminiModel
            ProviderId.CUSTOM -> customModel.ifBlank { "未填写模型" }
        }

    fun isConfigured(p: ProviderId): Boolean = when (p) {
        ProviderId.OPENCODE_GO -> hasOpenCodeKey || viaCloud
        ProviderId.GEMINI -> hasGeminiKey || viaCloud
        ProviderId.CUSTOM -> hasCustomKey && customBaseUrl.isNotBlank() && customModel.isNotBlank()
    }
}

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("helium_settings", Context.MODE_PRIVATE)
    private val vault = SecureStore(context)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<LlmSettings> = _state.asStateFlow()

    val vaultCount: Int get() = vault.count()

    private val _search = MutableStateFlow(loadSearch())
    val search: StateFlow<SearchSettings> = _search.asStateFlow()

    private fun loadSearch() = SearchSettings(
        provider = runCatching { SearchProviderId.valueOf(prefs.getString(K_SEARCH, null) ?: "") }.getOrDefault(SearchProviderId.EXA_MCP),
        cloudUrl = prefs.getString(K_CLOUD_URL, null).orEmpty(),
        hasCloudToken = vault.has(V_CLOUD_TOKEN),
        hasDeepSeekKey = vault.has(V_DEEPSEEK),
    )

    fun setSearchProvider(p: SearchProviderId) {
        prefs.edit().putString(K_SEARCH, p.name).apply(); _search.value = loadSearch()
    }

    fun setCloud(url: String, token: String?) {
        prefs.edit().putString(K_CLOUD_URL, url.trim().trimEnd('/')).apply()
        if (token != null) vault.put(V_CLOUD_TOKEN, token.trim().ifBlank { null })
        _search.value = loadSearch(); refresh()
    }

    fun setDeepSeekKey(key: String?) {
        vault.put(V_DEEPSEEK, key?.trim()?.ifBlank { null }); _search.value = loadSearch()
    }

    fun cloudToken(): String = vault.get(V_CLOUD_TOKEN).orEmpty()

    /** Base URL + bearer for DeepSeek tool APIs: the Worker when configured, else DeepSeek directly. */
    fun deepSeekToolRoute(): Pair<String, String> {
        val s = _search.value
        return if (s.cloudConfigured) s.cloudUrl to vault.get(V_CLOUD_TOKEN).orEmpty()
        else DEEPSEEK_BASE to vault.get(V_DEEPSEEK).orEmpty()
    }

    private fun load(): LlmSettings {
        val provider = runCatching { ProviderId.valueOf(prefs.getString(K_PROVIDER, null) ?: "") }.getOrDefault(ProviderId.OPENCODE_GO)
        val models = prefs.getString(K_OC_MODELS, null)?.let { decodeModels(it) }?.takeIf { it.isNotEmpty() }
            ?: ModelCatalog.openCodeDefaults
        return LlmSettings(
            provider = provider,
            openCodeModel = prefs.getString(K_OC_MODEL, null) ?: ModelCatalog.openCodeDefaults.first().id,
            openCodeModels = models,
            geminiModel = prefs.getString(K_GEMINI_MODEL, null) ?: ModelCatalog.geminiDefaults.first().id,
            customBaseUrl = prefs.getString(K_CUSTOM_BASE, null).orEmpty(),
            customModel = prefs.getString(K_CUSTOM_MODEL, null).orEmpty(),
            hasOpenCodeKey = vault.has(V_OPENCODE),
            hasGeminiKey = vault.has(V_GEMINI),
            hasCustomKey = vault.has(V_CUSTOM),
            viaCloud = !prefs.getString(K_CLOUD_URL, null).isNullOrBlank() && vault.has(V_CLOUD_TOKEN),
        )
    }

    private fun refresh() {
        _state.value = load()
    }

    fun setProvider(p: ProviderId) {
        prefs.edit().putString(K_PROVIDER, p.name).apply(); refresh()
    }

    fun setOpenCodeModel(id: String) {
        prefs.edit().putString(K_OC_MODEL, id.trim()).apply(); refresh()
    }

    /** Merge a freshly fetched model list with the defaults (defaults first, so the 4 featured stay on top). */
    fun setOpenCodeModels(fetched: List<ModelOption>) {
        val merged = LinkedHashMap<String, ModelOption>()
        ModelCatalog.openCodeDefaults.forEach { d -> merged[d.id] = fetched.firstOrNull { it.id == d.id }?.copy(supportsVision = d.supportsVision) ?: d }
        fetched.forEach { merged.putIfAbsent(it.id, it) }
        prefs.edit().putString(K_OC_MODELS, encodeModels(merged.values.toList())).apply(); refresh()
    }

    fun setGeminiModel(id: String) {
        prefs.edit().putString(K_GEMINI_MODEL, id.trim()).apply(); refresh()
    }

    fun setCustom(baseUrl: String, model: String) {
        prefs.edit().putString(K_CUSTOM_BASE, baseUrl.trim()).putString(K_CUSTOM_MODEL, model.trim()).apply(); refresh()
    }

    fun setApiKey(p: ProviderId, key: String?) {
        vault.put(vaultName(p), key?.trim()); refresh()
    }

    /** Resolve the active provider into a concrete endpoint for one request. */
    fun activeEndpoint(): LlmEndpoint {
        val s = _state.value
        return when (s.provider) {
            ProviderId.OPENCODE_GO -> {
                val opt = s.openCodeModels.firstOrNull { it.id == s.openCodeModel }
                // Unknown (hand-typed) ids default to the OpenAI-compatible route, and to no vision:
                // an unverified model must never be sent pixels it cannot see.
                val api = opt?.api ?: WireApi.CHAT_COMPLETIONS
                val base = if (api == WireApi.ANTHROPIC_MESSAGES) ModelCatalog.OPENCODE_ANTHROPIC_BASE else ModelCatalog.OPENCODE_OPENAI_BASE
                val (url, key) = viaCloud(vault.get(V_OPENCODE), base, "https://opencode.ai/inference/go", "opencode-go")
                LlmEndpoint(provider = s.provider, api = api, baseUrl = url, apiKey = key, model = s.openCodeModel, supportsVision = opt?.supportsVision ?: false)
            }
            ProviderId.GEMINI -> {
                val (url, key) = viaCloud(vault.get(V_GEMINI), ModelCatalog.GEMINI_BASE, "https://generativelanguage.googleapis.com", "gemini")
                LlmEndpoint(s.provider, WireApi.GEMINI, url, key, s.geminiModel, supportsVision = true)
            }
            ProviderId.CUSTOM -> LlmEndpoint(s.provider, WireApi.CHAT_COMPLETIONS, s.customBaseUrl, vault.get(V_CUSTOM).orEmpty(), s.customModel, supportsVision = false)
        }
    }

    /**
     * A key on the phone wins. Without one, and with Helium Cloud connected, the request goes to the Worker's
     * `/llm/<provider>` route, which swaps the device token for the server-held key.
     */
    private fun viaCloud(localKey: String?, base: String, upstreamOrigin: String, route: String): Pair<String, String> {
        val c = _search.value
        if (!localKey.isNullOrBlank() || !c.cloudConfigured) return base to localKey.orEmpty()
        return (c.cloudUrl + "/llm/" + route + base.removePrefix(upstreamOrigin)) to cloudToken()
    }

    private fun vaultName(p: ProviderId) = when (p) {
        ProviderId.OPENCODE_GO -> V_OPENCODE
        ProviderId.GEMINI -> V_GEMINI
        ProviderId.CUSTOM -> V_CUSTOM
    }

    private fun encodeModels(list: List<ModelOption>): String = JSONArray().apply {
        list.forEach { put(JSONObject().put("id", it.id).put("label", it.label).put("api", it.api.name).put("supportsVision", it.supportsVision)) }
    }.toString()

    private fun decodeModels(raw: String): List<ModelOption> = runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val api = runCatching { WireApi.valueOf(o.optString("api")) }.getOrNull() ?: return@mapNotNull null
            ModelOption(o.optString("id"), o.optString("label"), api, o.optBoolean("supportsVision", false))
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val K_PROVIDER = "provider"
        const val K_OC_MODEL = "opencode_model"
        const val K_OC_MODELS = "opencode_models"
        const val K_GEMINI_MODEL = "gemini_model"
        const val K_CUSTOM_BASE = "custom_base"
        const val K_CUSTOM_MODEL = "custom_model"
        const val V_OPENCODE = "key.opencode_go"
        const val V_GEMINI = "key.gemini"
        const val V_CUSTOM = "key.custom"
        const val K_SEARCH = "search_provider"
        const val K_CLOUD_URL = "cloud_url"
        const val V_CLOUD_TOKEN = "key.cloud_token"
        const val V_DEEPSEEK = "key.deepseek"
        const val DEEPSEEK_BASE = "https://api.deepseek.com"
    }
}
