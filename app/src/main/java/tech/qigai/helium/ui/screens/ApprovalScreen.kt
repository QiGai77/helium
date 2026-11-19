package tech.qigai.helium.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.qigai.helium.data.sample.Approval
import tech.qigai.helium.data.sample.Sources
import tech.qigai.helium.ui.components.ButtonKind
import tech.qigai.helium.ui.components.HePillButton
import tech.qigai.helium.ui.components.HeTag
import tech.qigai.helium.ui.components.HeroSurface
import tech.qigai.helium.ui.components.SampleBadge
import tech.qigai.helium.ui.components.TagKind
import tech.qigai.helium.ui.components.WhiteCard
import tech.qigai.helium.ui.components.heCardShadow
import tech.qigai.helium.ui.icons.HeIcons
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeGradients
import tech.qigai.helium.ui.theme.HeNum
import tech.qigai.helium.ui.theme.HeShapes

/** 04 审批 — "待你确认" stack. Approve / reject are local in v0.1: nothing is ever paid or sent. */
@Composable
fun ApprovalScreen(onClose: () -> Unit, toast: (String) -> Unit) {
    val items = remember { Sources.approvals.pending() }
    var index by remember { mutableIntStateOf(0) }
    val remaining = items.size - index

    Column(Modifier.fillMaxSize()) {
        SubTopBar("待你确认", onClose, backIcon = HeIcons.Close, trailing = {
            HeTag(if (remaining > 0) "${index + 1} / ${items.size}" else "完成", TagKind.NEUTRAL, dot = false, height = 32.dp, fontSize = 14)
        })
        if (remaining <= 0) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("都处理完了", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink)
                Spacer(Modifier.height(8.dp))
                Text("新的审批会出现在首页提示里", fontSize = 14.sp, color = HeColors.Ink2)
                Spacer(Modifier.height(24.dp))
                HePillButton("回到首页", onClose, kind = ButtonKind.PRIMARY)
            }
        } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth()) {
                // back cards (white / mist), only a sliver visible
                val next = items.getOrNull(index + 1)
                if (items.getOrNull(index + 2) != null) {
                    Box(
                        Modifier.padding(horizontal = 40.dp).fillMaxWidth().height(90.dp)
                            .clip(RoundedCornerShape(32.dp)).background(HeColors.Mist2).border(1.dp, HeColors.Hair, RoundedCornerShape(32.dp)),
                    )
                }
                if (next != null) {
                    WhiteCard(
                        Modifier.padding(horizontal = 20.dp).padding(top = 24.dp).fillMaxWidth().height(100.dp),
                        shape = RoundedCornerShape(32.dp), color = HeColors.Mist3,
                    ) {
                        Text("${next.title.substringBefore(" ·")} · ${next.total}", fontSize = 15.sp, color = HeColors.Ink2, modifier = Modifier.padding(start = 24.dp, top = 22.dp))
                    }
                }
                AnimatedContent(
                    targetState = index,
                    transitionSpec = { (slideInVertically { it / 6 } + fadeIn()) togetherWith fadeOut() },
                    label = "approval",
                    modifier = Modifier.padding(top = if (next != null) 80.dp else 0.dp),
                ) { i ->
                    ApprovalCard(items[i])
                }
            }
            Spacer(Modifier.height(32.dp))
            val cur = items[index]
            HePillButton(
                cur.action,
                {
                    toast("已批准「${cur.title}」（示例：不会真实${cur.kind}）")
                    index++
                },
                Modifier.fillMaxWidth(), ButtonKind.PRIMARY, HeIcons.Lock,
            )
            Spacer(Modifier.height(12.dp))
            HePillButton(
                "拒绝",
                {
                    toast("已拒绝，Helium 不会继续这一步")
                    index++
                },
                Modifier.fillMaxWidth(), ButtonKind.OUTLINE,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(HeIcons.Shield, null, tint = HeColors.Ink2, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("未经你同意，Helium 不会付款", fontSize = 14.sp, color = HeColors.Ink2)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ApprovalCard(a: Approval) {
    val shape = RoundedCornerShape(32.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .heCardShadow(shape, 14.dp)
            .clip(shape)
            .background(Color.White),
    ) {
        HeroSurface(Modifier.fillMaxWidth().height(176.dp), recipe = HeGradients.Sunrise, shape = RoundedCornerShape(0.dp), shadow = false) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeTag("待审批", TagKind.AMBER, shape = HeShapes.Tag, height = 28.dp, fontSize = 13)
                    Spacer(Modifier.width(8.dp))
                    SampleBadge(onHero = true)
                }
                Spacer(Modifier.height(18.dp))
                Text("需要你确认", fontSize = 14.sp, color = HeColors.Ink.copy(alpha = 0.75f))
                Text(a.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink)
                Text(a.origin, fontSize = 12.sp, color = HeColors.Ink.copy(alpha = 0.7f))
            }
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            a.lines.forEach { l -> KV(l.label, l.value) }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = HeColors.Hair)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("合计", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink, modifier = Modifier.weight(1f))
                Text(a.total, style = HeNum.style(32, FontWeight.Bold), color = HeColors.Ink, textAlign = TextAlign.End)
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = HeColors.Hair)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("付款卡", fontSize = 15.sp, color = HeColors.Ink2, modifier = Modifier.weight(1f))
                if (a.card != "—") Icon(HeIcons.Card, null, tint = HeColors.Ink2, modifier = Modifier.size(18.dp).offset(x = (-4).dp))
                Text(a.card, style = HeNum.style(15, FontWeight.Medium), color = HeColors.Ink)
            }
            Spacer(Modifier.height(8.dp))
            KV("送至", a.shipTo)
            Text(a.eta, fontSize = 12.sp, color = HeColors.Ink3)
        }
    }
}

@Composable
private fun KV(k: String, v: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, fontSize = 15.sp, color = HeColors.Ink2, modifier = Modifier.weight(1f))
        Text(v, fontSize = 15.sp, color = HeColors.Ink, fontWeight = FontWeight.Medium)
    }
}
