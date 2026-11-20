package tech.qigai.helium.data.llm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import okio.Buffer
import tech.qigai.helium.agent.AgentEvent
import tech.qigai.helium.agent.History
import tech.qigai.helium.agent.ModelMessage

class ImageWireFormatTest {
    private val loader = ImageLoader { ref -> if (ref == "missing.jpg") null else ImageData("image/jpeg", "B64-$ref") }
    private val history = listOf(ModelMessage(ModelMessage.Role.USER, "这是什么", images = listOf("a.jpg", "missing.jpg")))

    private fun ep(api: WireApi) = LlmEndpoint(ProviderId.CUSTOM, api, "https://x.test/v1", "k", "m")
    private fun body(r: okhttp3.Request) = JSONObject(Buffer().also { r.body!!.writeTo(it) }.readUtf8())

    @Test fun openAiUsesImageUrlDataUri() {
        val msg = body(WireFormat.chatCompletions(ep(WireApi.CHAT_COMPLETIONS), "sys", history, emptyList(), loader))
            .getJSONArray("messages").getJSONObject(1)
        val parts = msg.getJSONArray("content")
        assertEquals(2, parts.length())
        assertEquals("text", parts.getJSONObject(0).getString("type"))
        assertEquals("这是什么", parts.getJSONObject(0).getString("text"))
        assertEquals("image_url", parts.getJSONObject(1).getString("type"))
        assertEquals("data:image/jpeg;base64,B64-a.jpg", parts.getJSONObject(1).getJSONObject("image_url").getString("url"))
    }

    @Test fun anthropicUsesBase64ImageBlockBeforeText() {
        val content = body(WireFormat.anthropic(ep(WireApi.ANTHROPIC_MESSAGES), "sys", history, emptyList(), loader))
            .getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        val img = content.getJSONObject(0)
        assertEquals("image", img.getString("type"))
        val src = img.getJSONObject("source")
        assertEquals("base64", src.getString("type"))
        assertEquals("image/jpeg", src.getString("media_type"))
        assertEquals("B64-a.jpg", src.getString("data"))
        assertEquals("text", content.getJSONObject(1).getString("type"))
        assertEquals("这是什么", content.getJSONObject(1).getString("text"))
    }

    @Test fun geminiUsesInlineData() {
        val parts = body(WireFormat.gemini(ep(WireApi.GEMINI), "sys", history, emptyList(), loader))
            .getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        val inline = parts.getJSONObject(0).getJSONObject("inlineData")
        assertEquals("image/jpeg", inline.getString("mimeType"))
        assertEquals("B64-a.jpg", inline.getString("data"))
        assertEquals("这是什么", parts.getJSONObject(1).getString("text"))
    }

    @Test fun imageOnlyTurnsAreValidPerProtocol() {
        val only = listOf(ModelMessage(ModelMessage.Role.USER, "", images = listOf("a.jpg")))
        val oa = body(WireFormat.chatCompletions(ep(WireApi.CHAT_COMPLETIONS), null, only, emptyList(), loader)).getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals(1, oa.length())
        val an = body(WireFormat.anthropic(ep(WireApi.ANTHROPIC_MESSAGES), null, only, emptyList(), loader)).getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertFalse(an.getJSONObject(1).getString("text").isBlank())
        val ge = body(WireFormat.gemini(ep(WireApi.GEMINI), null, only, emptyList(), loader)).getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertEquals(1, ge.length())
        assertTrue(ge.getJSONObject(0).has("inlineData"))
    }

    @Test fun textOnlyTurnsStayPlainStrings() {
        val plain = listOf(ModelMessage(ModelMessage.Role.USER, "你好"))
        val m = body(WireFormat.chatCompletions(ep(WireApi.CHAT_COMPLETIONS), null, plain, emptyList(), loader)).getJSONArray("messages").getJSONObject(0)
        assertEquals("你好", m.getString("content"))
        val a = body(WireFormat.anthropic(ep(WireApi.ANTHROPIC_MESSAGES), null, plain, emptyList(), loader)).getJSONArray("messages").getJSONObject(0)
        assertEquals("你好", a.getString("content"))
    }

    @Test fun historyKeepsOnlyRecentImageTurns() {
        val events = (1..5).map { AgentEvent.UserMessage("图$it", at = it.toLong(), images = listOf("i$it.jpg")) }
        val model = History.toModel(events, maxImageTurns = 2)
        assertEquals(listOf(0, 0, 0, 1, 1), model.map { it.images.size })
        assertTrue(model[0].content.contains("图片"))
        assertTrue(model[0].content.endsWith("图1"))
    }
}
