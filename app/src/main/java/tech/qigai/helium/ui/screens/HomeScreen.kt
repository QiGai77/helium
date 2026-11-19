package tech.qigai.helium.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.qigai.helium.data.model.HeroEvent
import tech.qigai.helium.data.db.ApprovalEntity
import tech.qigai.helium.ui.components.AgentOrb
import tech.qigai.helium.ui.components.Composer
import tech.qigai.helium.ui.components.Dot
import tech.qigai.helium.ui.components.HeTag
import tech.qigai.helium.ui.components.HeroSurface
import tech.qigai.helium.ui.components.Illo
import tech.qigai.helium.ui.components.TagKind
import tech.qigai.helium.ui.components.WhiteCard
import tech.qigai.helium.ui.icons.HeIcons
import tech.qigai.helium.ui.illo.IlloTone
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeNum
import tech.qigai.helium.ui.theme.HeShapes

/** 01 首页 — greeting + 现在最要紧 hero + 2 hints + agent orb + composer. */
@Composable
fun HomeScreen(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    agentRunning: Boolean,
    pending: List<ApprovalEntity>,
    lastConversation: String?,
    onOpenChat: () -> Unit,
    onOpenGlance: () -> Unit,
    onOpenApproval: () -> Unit,
    onOpenMe: () -> Unit,
    toast: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            RootHeader(
                title = greeting(),
                eyebrow = todayLabel(),
                subtitle = when {
                    agentRunning -> "Helium 正在替你做事"
                    pending.isNotEmpty() -> "有 ${pending.size} 件事等你确认"
                    else -> "说一句话，我来动手"
                },
                onAvatar = onOpenMe,
            )
            Spacer(Modifier.height(24.dp))
            EmptyHeroCard(Modifier.padding(horizontal = 24.dp), onClick = onOpenGlance)
            Spacer(Modifier.height(24.dp))
            if (pending.isNotEmpty()) {
                HintRow(HeColors.Amber, "${pending.size} 件待你确认", pending.first().title, onOpenApproval, Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(10.dp))
            }
            if (lastConversation != null) {
                HintRow(HeColors.Accent, "继续上次对话", lastConversation, onOpenChat, Modifier.padding(horizontal = 24.dp))
            }
            Spacer(Modifier.height(28.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AgentOrb(size = 96.dp, active = agentRunning, onClick = onOpenChat)
            }
            Spacer(Modifier.height(24.dp))
        }
        Composer(
            value = draft,
            onValueChange = onDraftChange,
            onSend = onSend,
            modifier = Modifier.padding(horizontal = 24.dp),
            onAttach = { toast("附件即将支持") },
            onSkills = { toast("技能面板即将支持") },
            onMic = { toast("语音输入即将支持") },
        )
        Spacer(Modifier.height(12.dp))
    }
}

/** Hero event card with the event illustration in the top-right (only hero gradient on Home). */
@Composable
fun HeroEventCard(e: HeroEvent, modifier: Modifier = Modifier, height: Int = 208, showTag: Boolean = true, onClick: () -> Unit = {}) {
    HeroSurface(modifier.fillMaxWidth().height(height.dp), onClick = onClick) {
        Illo(
            e.kind, IlloTone.HERO,
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 8.dp, y = if (showTag) 20.dp else 4.dp)
                .size(156.dp, 125.dp),
        )
        Column(Modifier.padding(24.dp).fillMaxSize()) {
            if (showTag) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeTag(e.tag, TagKind.GLASS, dot = false, height = 28.dp, fontSize = 13)
                }
                Spacer(Modifier.height(14.dp))
            }
            Text(e.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(e.meta, color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(e.time, style = HeNum.style(if (showTag) 60 else 56), color = Color.White, modifier = Modifier.weight(1f))
                HeTag(e.route, TagKind.GLASS, dot = false, height = 32.dp, fontSize = 13, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
    }
}

@Composable
private fun HintRow(dot: Color, title: String, meta: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    WhiteCard(modifier.fillMaxWidth().height(56.dp), shape = HeShapes.Row, onClick = onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
            Dot(dot)
            Spacer(Modifier.width(12.dp))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink)
            Spacer(Modifier.width(10.dp))
            Text(meta, fontSize = 13.sp, color = HeColors.Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(HeIcons.ChevronRight, null, tint = HeColors.Ink3, modifier = Modifier.size(18.dp))
        }
    }
}

/** Hero shown until a real event source (calendar, flights, parcels) has something for today. */
@Composable
private fun EmptyHeroCard(modifier: Modifier = Modifier, onClick: () -> Unit) {
    HeroSurface(modifier.fillMaxWidth().height(168.dp), onClick = onClick) {
        Column(Modifier.padding(24.dp).fillMaxSize()) {
            HeTag(todayLabel(), TagKind.GLASS, dot = false, height = 28.dp, fontSize = 13)
            Spacer(Modifier.weight(1f))
            Text("今天还没有要紧的事", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("接入日历、航班和快递后，最要紧的一件会出现在这里。", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}
