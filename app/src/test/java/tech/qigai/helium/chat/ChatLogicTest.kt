package tech.qigai.helium.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.qigai.helium.agent.AgentEvent
import tech.qigai.helium.agent.EventCodec
import tech.qigai.helium.agent.GrantScope
import tech.qigai.helium.agent.Risk
import tech.qigai.helium.agent.ToolCall

class SearchTextTest {
    @Test fun splitsCjkPerCharacterAndLowercasesWords() {
        assertEquals("今 天 天 气 不 错", SearchText.normalize("今天天气不错！"))
        assertEquals("gpt4 模 型 很 强", SearchText.normalize("GPT4模型，很强。"))
        assertEquals("hello world", SearchText.normalize("  Hello,\n world  "))
    }

    @Test fun indexesOnlyUserAndAssistantText() {
        assertEquals("你 好", SearchText.forEvent("user", """{"text":"你好","at":1}"""))
        assertEquals("ok", SearchText.forEvent("assistant", """{"text":"OK","reasoning":"想","at":1}"""))
        assertEquals("", SearchText.forEvent("observation", """{"output":"天气","at":1}"""))
        assertEquals("", SearchText.forEvent("user", "not json"))
    }
}

class FtsQueryTest {
    @Test fun cjkRunBecomesCharacterPhrase() {
        assertEquals("\"天 气\"", FtsQuery.build("天气"))
        assertEquals("\"好\"", FtsQuery.build("好"))
    }

    @Test fun wordsBecomePrefixTermsAndMixedRunsSplit() {
        assertEquals("gpt4* \"模 型\"", FtsQuery.build("GPT4模型"))
        assertEquals("\"周 杰 伦\" jay*", FtsQuery.build("周杰伦 jay"))
    }

    @Test fun dropsOperatorsQuotesAndBrackets() {
        assertEquals("a* or* b*", FtsQuery.build("\"a\" OR(-b*)"))
        assertEquals("\"南 京\" near*", FtsQuery.build("南京 NEAR"))
        assertEquals("\"引\"", FtsQuery.build("引\""))
    }

    @Test fun blankOrPunctuationOnlyIsNull() {
        assertNull(FtsQuery.build("   "))
        assertNull(FtsQuery.build("？！。,.*\"()"))
    }

    @Test fun queryMatchesIndexedForm() {
        // The phrase terms of the query must appear, in order and adjacent, in the indexed text.
        val indexed = SearchText.normalize("明天南京天气怎么样，要带伞吗")
        val phrase = FtsQuery.build("南京天气")!!.removeSurrounding("\"")
        assertTrue(indexed.contains(phrase))
    }

    @Test fun capsTermCount() {
        val q = FtsQuery.build((1..20).joinToString(" ") { "w$it" })!!
        assertEquals(8, q.split(" ").size)
    }
}

class SnippetTest {
    @Test fun windowsAroundFirstHit() {
        val text = "前面很多很多字。".repeat(10) + "这里提到了南京天气。" + "后面还有很多字".repeat(10)
        val s = Snippet.around(text, "南京", radius = 6)
        assertTrue(s.startsWith("…"))
        assertTrue(s.endsWith("…"))
        assertTrue(s.contains("南京"))
    }

    @Test fun noHitReturnsHead() {
        assertEquals("短文本", Snippet.around("短文本", "无关"))
    }
}

class TitlePromptTest {
    @Test fun promptCarriesBothTurnsAndTruncates() {
        val p = TitlePrompt.build("帮我规划周末去杭州", "好的，第一天去西湖。" + "长".repeat(2000))
        assertTrue(p.contains("用户：帮我规划周末去杭州"))
        assertTrue(p.contains("助手：好的，第一天去西湖。"))
        assertTrue(p.contains("不超过 12 个字"))
        assertTrue(p.length < 1600)
    }

    @Test fun cleanStripsPrefixQuotesPunctuationAndThinking() {
        assertEquals("杭州周末行程", TitlePrompt.clean("标题：「杭州周末行程」。"))
        assertEquals("杭州周末行程", TitlePrompt.clean("<think>先想想</think>\n\n\"杭州周末行程\"\n解释：……"))
        assertEquals("Weekend Plan", TitlePrompt.clean("Title: **Weekend Plan!**"))
    }

    @Test fun cleanCapsLengthAndRejectsBlank() {
        assertEquals(TitlePrompt.MAX_CHARS, TitlePrompt.clean("一".repeat(40))!!.length)
        assertNull(TitlePrompt.clean("  \n「」。 "))
        assertNull(TitlePrompt.clean(""))
    }

    @Test fun provisionalTitle() {
        assertEquals("第一行", TitlePrompt.provisional("第一行\n第二行"))
        assertEquals("图片", TitlePrompt.provisional("", hasImages = true))
        assertEquals("新对话", TitlePrompt.provisional("  "))
    }
}

class RecoveryTest {
    private val user = AgentEvent.UserMessage("讲个故事", at = 100)

    @Test fun userMessageTailIsInterruptedWithPartialText() {
        val fix = Recovery.interruptedTail(listOf(user), PendingReply("从前有座山", "想想"))
        assertNotNull(fix)
        assertTrue(fix!!.interrupted)
        assertEquals("从前有座山", fix.text)
        assertEquals("想想", fix.reasoning)
        assertTrue(fix.at > user.at)
    }

    @Test fun unfinishedToolStepsAreInterrupted() {
        val call = AgentEvent.AssistantMessage("", toolCalls = listOf(ToolCall("c1", "web_search", "{}")), at = 101)
        assertNotNull(Recovery.interruptedTail(listOf(user, call), null))
        val obs = AgentEvent.ToolObservation("c1", "web_search", "结果", at = 102)
        assertNotNull(Recovery.interruptedTail(listOf(user, call, obs), null))
        val ask = AgentEvent.ApprovalRequest("r", "c1", "t", "t", "d", "x", Risk.HIGH, listOf(GrantScope.ONCE), at = 103)
        assertNotNull(Recovery.interruptedTail(listOf(user, call, ask), null))
        assertEquals("", Recovery.interruptedTail(listOf(user, call, obs), null)!!.text)
    }

    @Test fun finishedStoppedFailedOrAlreadyInterruptedTailsAreLeftAlone() {
        assertNull(Recovery.interruptedTail(emptyList(), null))
        assertNull(Recovery.interruptedTail(listOf(user, AgentEvent.AssistantMessage("答案", at = 101)), PendingReply("x", "")))
        assertNull(Recovery.interruptedTail(listOf(user, AgentEvent.AssistantMessage("半", stopped = true, at = 101)), null))
        assertNull(Recovery.interruptedTail(listOf(user, AgentEvent.AssistantMessage("", error = "网络", at = 101)), null))
        assertNull(Recovery.interruptedTail(listOf(user, AgentEvent.AssistantMessage("", interrupted = true, at = 101)), null))
    }

    @Test fun interruptedAndImagesSurviveCodec() {
        val a = AgentEvent.AssistantMessage("半句", interrupted = true, at = 5)
        val back = EventCodec.decode(EventCodec.kind(a), EventCodec.encode(a)) as AgentEvent.AssistantMessage
        assertTrue(back.interrupted)
        val u = AgentEvent.UserMessage("这是什么", at = 6, images = listOf("img-1.jpg", "img-2.jpg"))
        val ub = EventCodec.decode(EventCodec.kind(u), EventCodec.encode(u)) as AgentEvent.UserMessage
        assertEquals(listOf("img-1.jpg", "img-2.jpg"), ub.images)
        // Old rows without the new fields still decode.
        val old = EventCodec.decode("assistant", """{"text":"x","at":1}""") as AgentEvent.AssistantMessage
        assertFalse(old.interrupted)
        assertTrue((EventCodec.decode("user", """{"text":"x","at":1}""") as AgentEvent.UserMessage).images.isEmpty())
    }
}

class ImageScaleTest {
    @Test fun capsLongestEdgeKeepingAspect() {
        assertEquals(1568 to 1176, ImageScale.target(4032, 3024))
        assertEquals(1176 to 1568, ImageScale.target(3024, 4032))
        assertEquals(800 to 600, ImageScale.target(800, 600))
        assertEquals(1568 to 1, ImageScale.target(10000, 3))
    }

    @Test fun sampleSizeNeverUndershoots() {
        assertEquals(2, ImageScale.sampleSize(4032, 3024))
        assertEquals(1, ImageScale.sampleSize(1568, 1000))
        assertEquals(4, ImageScale.sampleSize(8000, 6000))
        assertTrue(8000 / ImageScale.sampleSize(8000, 6000) >= 1568)
    }
}
