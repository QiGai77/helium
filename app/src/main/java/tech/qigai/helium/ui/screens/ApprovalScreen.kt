package tech.qigai.helium.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.qigai.helium.agent.GrantScope
import tech.qigai.helium.agent.Risk
import tech.qigai.helium.data.db.ApprovalEntity
import tech.qigai.helium.ui.components.ButtonKind
import tech.qigai.helium.ui.components.HePillButton
import tech.qigai.helium.ui.components.HeTag
import tech.qigai.helium.ui.components.HeroSurface
import tech.qigai.helium.ui.components.TagKind
import tech.qigai.helium.ui.components.heCardShadow
import tech.qigai.helium.ui.icons.HeIcons
import tech.qigai.helium.ui.theme.HeButton
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeGradients
import tech.qigai.helium.ui.theme.HeShapes

/** 04 审批 — every pending stored review. Approving resumes the waiting agent run; rejecting tells it no. */
@Composable
fun ApprovalScreen(
    pending: List<ApprovalEntity>,
    onDecide: (requestId: String, approved: Boolean, scope: GrantScope?) -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SubTopBar("待你确认", onClose, backIcon = HeIcons.Close, trailing = {
            HeTag(if (pending.isNotEmpty()) "${pending.size} 件" else "完成", TagKind.NEUTRAL, dot = false, height = 32.dp, fontSize = 14)
        })
        val cur = pending.firstOrNull()
        if (cur == null) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("没有待确认的事", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink)
                Spacer(Modifier.height(8.dp))
                Text("Helium 要发送、删除或保存重要信息前，会先停在这里等你", fontSize = 14.sp, color = HeColors.Ink2)
                Spacer(Modifier.height(24.dp))
                HePillButton("回到首页", onClose, kind = ButtonKind.PRIMARY)
            }
            return@Column
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                targetState = cur,
                transitionSpec = { (slideInVertically { it / 6 } + fadeIn()) togetherWith fadeOut() },
                label = "approval",
            ) { a -> ApprovalCard(a) }
            Spacer(Modifier.height(28.dp))
            val scopes = cur.scopes.split(',').mapNotNull { runCatching { GrantScope.valueOf(it) }.getOrNull() }.ifEmpty { listOf(GrantScope.ONCE) }
            HePillButton("批准（${scopes.first().label}）", { onDecide(cur.requestId, true, scopes.first()) }, Modifier.fillMaxWidth(), ButtonKind.PRIMARY, HeIcons.Lock)
            if (scopes.size > 1) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    scopes.drop(1).forEach { s ->
                        HePillButton(s.label, { onDecide(cur.requestId, true, s) }, Modifier.weight(1f), ButtonKind.TONAL, height = HeButton.Medium)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            HePillButton("拒绝", { onDecide(cur.requestId, false, null) }, Modifier.fillMaxWidth(), ButtonKind.OUTLINE)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(HeIcons.Shield, null, tint = HeColors.Ink2, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (scopes.size == 1 && cur.risk == Risk.HIGH.name) "高风险操作只能单次批准" else "授权可以在「我的 → 权限」随时撤销", fontSize = 13.sp, color = HeColors.Ink2)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ApprovalCard(a: ApprovalEntity) {
    val shape = RoundedCornerShape(32.dp)
    val risk = runCatching { Risk.valueOf(a.risk) }.getOrDefault(Risk.HIGH)
    Column(
        Modifier
            .fillMaxWidth()
            .heCardShadow(shape, 14.dp)
            .clip(shape)
            .background(Color.White),
    ) {
        HeroSurface(Modifier.fillMaxWidth().height(160.dp), recipe = HeGradients.Sunrise, shape = RoundedCornerShape(0.dp), shadow = false) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeTag("待审批", TagKind.AMBER, shape = HeShapes.Tag, height = 28.dp, fontSize = 13)
                    Spacer(Modifier.width(8.dp))
                    HeTag("风险 ${risk.label}", TagKind.GLASS, dot = false, shape = HeShapes.Tag, height = 28.dp, fontSize = 13)
                    if (a.tainted) {
                        Spacer(Modifier.width(8.dp))
                        HeTag("读过外部内容", TagKind.GLASS, dot = false, shape = HeShapes.Tag, height = 28.dp, fontSize = 13)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(a.title, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink, maxLines = 2)
                Text("工具：${toolDisplayName(a.tool)}（${a.tool}）", fontSize = 12.sp, color = HeColors.Ink.copy(alpha = 0.7f))
            }
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Text("目标", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink2)
            Text(a.target, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink)
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = HeColors.Hair)
            Text("完整内容", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink2)
            Spacer(Modifier.height(4.dp))
            Text(a.detail, fontSize = 15.sp, lineHeight = 22.sp, color = HeColors.Ink)
            if (a.warning != null) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = HeColors.Hair)
                Text(a.warning, fontSize = 13.sp, lineHeight = 19.sp, color = HeColors.CoralInk)
            }
        }
    }
}
