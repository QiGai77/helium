package tech.qigai.helium.data.llm

/** Which model provider Helium talks to. */
enum class ProviderId(val title: String) {
    OPENCODE_GO("OpenCode Go"),
    GEMINI("Gemini API"),
    CUSTOM("自带 API Key"),
}

/** Wire protocol used for one model. */
enum class WireApi {
    /** OpenAI-compatible `POST {base}/chat/completions`, SSE stream. */
    CHAT_COMPLETIONS,

    /** Anthropic-compatible `POST {base}/messages`, SSE stream (OpenCode Go serves some models only this way). */
    ANTHROPIC_MESSAGES,

    /** Google Generative Language API `models/{id}:streamGenerateContent?alt=sse`. */
    GEMINI,
}

/**
 * One model the picker can offer. [supportsVision] means the provider actually accepts image input
 * for this model id: only set it where that is verified. Unknown / unverified models default to
 * `false` — the wire format then turns attached pictures into a text note instead of silently
 * sending pixels a text-only model cannot see.
 */
data class ModelOption(val id: String, val label: String, val api: WireApi, val supportsVision: Boolean = false)

object ModelCatalog {
    /** OpenCode Go endpoints (verified against https://opencode.ai/inference/go/v1/models, 2026-11-19). */
    const val OPENCODE_OPENAI_BASE = "https://opencode.ai/inference/go/openai/v1"
    const val OPENCODE_ANTHROPIC_BASE = "https://opencode.ai/inference/go/anthropic/v1"
    const val OPENCODE_MODELS_URL = "https://opencode.ai/inference/go/v1/models"

    const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta"

    /**
     * Default OpenCode Go models shown in the picker. IDs taken from the public models endpoint:
     * glm-5.3 / kimi-k3 / deepseek-v4-pro are `chat-completions`; qwen3.8-max is served via `messages` only.
     * Vision is left off for all four: none of these ids is a verified vision model on this route, and
     * an unverified model must default to no-vision (see [ModelOption]).
     */
    val openCodeDefaults = listOf(
        ModelOption("glm-5.3", "GLM-5.3", WireApi.CHAT_COMPLETIONS),
        ModelOption("kimi-k3", "Kimi K3", WireApi.CHAT_COMPLETIONS),
        ModelOption("deepseek-v4-pro", "DeepSeek V4 Pro", WireApi.CHAT_COMPLETIONS),
        ModelOption("qwen3.8-max", "Qwen3.8 Max", WireApi.ANTHROPIC_MESSAGES),
    )

    /**
     * Gemini model ids are editable in settings; these are only suggestions. All Gemini models on the
     * Generative Language API accept image input, so they are marked vision-capable.
     */
    val geminiDefaults = listOf(
        ModelOption("gemini-flash-latest", "Gemini Flash（latest）", WireApi.GEMINI, supportsVision = true),
        ModelOption("gemini-pro-latest", "Gemini Pro（latest）", WireApi.GEMINI, supportsVision = true),
        ModelOption("gemini-2.5-flash", "Gemini 2.5 Flash", WireApi.GEMINI, supportsVision = true),
        ModelOption("gemini-2.5-pro", "Gemini 2.5 Pro", WireApi.GEMINI, supportsVision = true),
    )
}

enum class Role { SYSTEM, USER, ASSISTANT }

data class ChatTurn(val role: Role, val content: String)

sealed interface StreamEvent {
    data class Text(val delta: String) : StreamEvent
    data class ToolCall(val call: tech.qigai.helium.agent.ToolCall) : StreamEvent
    data class Reasoning(val delta: String) : StreamEvent
    data class Done(val finishReason: String?) : StreamEvent
}

class LlmException(message: String, val httpCode: Int? = null) : Exception(message)

/** Resolved, ready-to-call endpoint for one chat request. */
data class LlmEndpoint(
    val provider: ProviderId,
    val api: WireApi,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    /** Whether this model accepts image input; false (the default) means pictures become a text note. */
    val supportsVision: Boolean = false,
)
