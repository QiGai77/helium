package tech.qigai.helium.data.llm

import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.qigai.helium.agent.ModelMessage

/**
 * M2 vision gate: only a model flagged as vision-capable ever receives image pixels. A model
 * without the flag gets a text note instead — never a silent image upload, never a silent drop.
 * Unknown / unverified models default to no vision (see [ModelOption]).
 */
class VisionGateTest {
    private val loader = ImageLoader { ref -> ImageData("image/jpeg", "B64-$ref") }
    private val history = listOf(ModelMessage(ModelMessage.Role.USER, "这是什么", images = listOf("a.jpg", "b.jpg")))

    private fun ep(api: WireApi, vision: Boolean) =
        LlmEndpoint(ProviderId.CUSTOM, api, "https://x.test/v1", "k", "m", supportsVision = vision)

    private fun body(r: okhttp3.Request) = JSONObject(Buffer().also { r.body!!.writeTo(it) }.readUtf8())

    @Test fun nonVisionOpenAiGetsTextNoteNotImageUrl() {
        val msg = body(WireFormat.chatCompletions(ep(WireApi.CHAT_COMPLETIONS, false), "sys", history, emptyList(), loader))
            .getJSONArray("messages").getJSONObject(1)
        assertTrue(msg.get("content") is String)
        val content = msg.getString("content")
        assertTrue(content.contains("不支持看图"))
        assertTrue(content.contains("2 张图片"))
        assertTrue(content.contains("这是什么"))
        assertFalse(content.contains("B64-"))
        assertFalse(body(WireFormat.chatCompletions(ep(WireApi.CHAT_COMPLETIONS, false), "sys", history, emptyList(), loader)).toString().contains("image_url"))
    }

    @Test fun nonVisionAnthropicGetsTextNoteNotImageBlock() {
        val msg = body(WireFormat.anthropic(ep(WireApi.ANTHROPIC_MESSAGES, false), "sys", history, emptyList(), loader))
            .getJSONArray("messages").getJSONObject(0)
        assertTrue(msg.get("content") is String)
        assertTrue(msg.getString("content").contains("不支持看图"))
        assertFalse(body(WireFormat.anthropic(ep(WireApi.ANTHROPIC_MESSAGES, false), "sys", history, emptyList(), loader)).toString().contains("B64-"))
    }

    @Test fun nonVisionGeminiGetsTextNoteNotInlineData() {
        val parts = body(WireFormat.gemini(ep(WireApi.GEMINI, false), "sys", history, emptyList(), loader))
            .getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertEquals(1, parts.length())
        assertTrue(parts.getJSONObject(0).getString("text").contains("不支持看图"))
        assertFalse(parts.getJSONObject(0).has("inlineData"))
    }

    @Test fun visionModelsStillInlineImagesOnAllProtocols() {
        val oa = body(WireFormat.chatCompletions(ep(WireApi.CHAT_COMPLETIONS, true), "sys", history, emptyList(), loader))
            .getJSONArray("messages").getJSONObject(1).getJSONArray("content")
        assertEquals("image_url", oa.getJSONObject(1).getString("type"))
        val an = body(WireFormat.anthropic(ep(WireApi.ANTHROPIC_MESSAGES, true), "sys", history, emptyList(), loader))
            .getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals("image", an.getJSONObject(0).getString("type"))
        val ge = body(WireFormat.gemini(ep(WireApi.GEMINI, true), "sys", history, emptyList(), loader))
            .getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertTrue(ge.getJSONObject(0).has("inlineData"))
    }

    @Test fun imageOnlyTurnToNonVisionModelIsStillAValidTextMessage() {
        val only = listOf(ModelMessage(ModelMessage.Role.USER, "", images = listOf("a.jpg")))
        val oa = body(WireFormat.chatCompletions(ep(WireApi.CHAT_COMPLETIONS, false), null, only, emptyList(), loader))
            .getJSONArray("messages").getJSONObject(0)
        assertTrue(oa.get("content") is String)
        assertTrue(oa.getString("content").contains("不支持看图"))
        val ge = body(WireFormat.gemini(ep(WireApi.GEMINI, false), null, only, emptyList(), loader))
            .getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertEquals(1, ge.length())
        assertTrue(ge.getJSONObject(0).getString("text").contains("不支持看图"))
    }

    @Test fun textOnlyTurnsAreUnchangedByTheGate() {
        val plain = listOf(ModelMessage(ModelMessage.Role.USER, "你好"))
        val m = body(WireFormat.chatCompletions(ep(WireApi.CHAT_COMPLETIONS, false), null, plain, emptyList(), loader))
            .getJSONArray("messages").getJSONObject(0)
        assertEquals("你好", m.getString("content"))
    }

    @Test fun catalogFlagsOnlyVerifiedVisionModels() {
        // Gemini models on the Generative Language API accept image input.
        assertTrue(ModelCatalog.geminiDefaults.all { it.supportsVision })
        // The OpenCode Go defaults are not verified vision models: unknown defaults to no vision.
        assertTrue(ModelCatalog.openCodeDefaults.none { it.supportsVision })
        // A fresh endpoint / option defaults to no vision.
        assertFalse(LlmEndpoint(ProviderId.CUSTOM, WireApi.CHAT_COMPLETIONS, "https://x.test", "k", "m").supportsVision)
        assertFalse(ModelOption("some-model", "Some Model", WireApi.CHAT_COMPLETIONS).supportsVision)
    }
}
