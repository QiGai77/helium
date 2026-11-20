package tech.qigai.helium.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

private val SH = ZoneId.of("Asia/Shanghai")
private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
ZonedDateTime.of(y, mo, d, h, mi, 0, 0, SH).toInstant().toEpochMilli()

/** M6: X/RSSHub defaults and honesty labels, plus SMS trip parsing on fake SMS text. */
class GlanceM6Test {
private val now = at(2026, 11, 20, 12, 0) // Fri 2026-11-20 12:00 CST

// ------------------------------------------------------------- X / RSSHub

@Test fun `x defaults to the public rsshub instance`() {
assertEquals("https://rsshub.app", XFeeds.DEFAULT_RSS_HUB)
assertEquals(XFeeds.DEFAULT_RSS_HUB, XFeeds.effectiveBridge(""))
assertEquals(XFeeds.DEFAULT_RSS_HUB, XFeeds.effectiveBridge(" "))
assertEquals("https://rsshub.example.com", XFeeds.effectiveBridge("https://rsshub.example.com/"))
assertTrue(XFeeds.isDefaultBridge(""))
assertFalse(XFeeds.isDefaultBridge("https://rsshub.example.com"))
assertEquals("https://rsshub.app/twitter/user/sama", XFeeds.feedUrl(XFeeds.effectiveBridge(""), "sama"))
}

@Test fun `x bridge warnings are honest`() {
val def = XFeeds.bridgeNote("")!!
assertTrue(def, def.contains("不稳定") && def.contains("自担"))
assertTrue(def.contains("rsshub.app"))
val nitter = XFeeds.bridgeNote("https://nitter.net")!!
assertTrue(nitter, nitter.contains("已死"))
assertTrue(XFeeds.isNitterBridge("https://nitter.privacyredirect.com"))
assertFalse(XFeeds.isNitterBridge("https://rsshub.app"))
assertNull(XFeeds.bridgeNote("https://rsshub.example.com"))
}

// ------------------------------------------------------------- SMS trips

@Test fun `flight sms parses`() {
val sms = "尊敬的旅客，您11月21日MU5101航班，上海虹桥-北京首都，起飞时间08:00，请提前2小时到机场办理手续。"
val t = SmsTripParser.parse(sms, "10690000", at(2026, 11, 20, 10, 0), now, SH)!!
assertEquals(SmsTrip.Kind.FLIGHT, t.kind)
assertEquals("航班 MU5101", t.title)
assertEquals(at(2026, 11, 21, 8, 0), t.begin)
assertEquals("上海虹桥-北京首都", t.route)
}

@Test fun `train sms parses`() {
val sms = "您购买的11月21日G1234次列车，南京南站-上海虹桥站，09:30开，请凭身份证进站乘车。"
val t = SmsTripParser.parse(sms, "12306", at(2026, 11, 20, 10, 0), now, SH)!!
assertEquals(SmsTrip.Kind.TRAIN, t.kind)
assertEquals("高铁 G1234次", t.title)
assertEquals(at(2026, 11, 21, 9, 30), t.begin)
assertEquals("南京南站-上海虹桥站", t.route)
}

@Test fun `train number plus date is enough without a keyword`() {
val sms = "您购买的11月21日K123次，南京-上海，10:00开。"
val t = SmsTripParser.parse(sms, null, at(2026, 11, 20, 10, 0), now, SH)!!
assertEquals("火车 K123次", t.title)
assertEquals(at(2026, 11, 21, 10, 0), t.begin)
}

@Test fun `december sms about a january flight rolls to next year`() {
val dec30 = at(2026, 12, 30, 12, 0)
val sms = "您1月2日CZ3101航班，广州白云-北京大兴，起飞时间07:30。"
val t = SmsTripParser.parse(sms, "10690000", at(2026, 12, 30, 10, 0), dec30, SH)!!
assertEquals(at(2027, 1, 2, 7, 30), t.begin)
}

@Test fun `non trip and stale sms are ignored`() {
assertNull(SmsTripParser.parse("您的验证码是123456，请勿泄露。", "1069", now, now, SH))
// A flight number with no flight word is not a trip notice.
assertNull(SmsTripParser.parse("MU5101 11月21日 08:00", null, now, now, SH))
// Yesterday's flight: next-year roll lands > 60 days out, so it is dropped.
assertNull(SmsTripParser.parse("您11月19日MU5101航班，上海-北京，起飞08:00。", null, now, now, SH))
// SMS from over 90 days ago never resurfaces.
assertNull(
SmsTripParser.parse(
"您11月21日MU5101航班，上海-北京，起飞08:00。",
null, at(2026, 8, 1, 10, 0), now, SH,
),
)
}

@Test fun `pick returns the nearest upcoming trip`() {
val trips = listOf(
SmsTrip(SmsTrip.Kind.TRAIN, "高铁 G2次", at(2026, 11, 21, 9, 30), null, null),
SmsTrip(SmsTrip.Kind.FLIGHT, "航班 MU1", at(2026, 11, 20, 18, 0), null, null),
)
assertEquals("航班 MU1", SmsTripParser.pick(trips, now)!!.title)
assertNull(SmsTripParser.pick(emptyList(), now))
}
}
