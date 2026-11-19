package tech.qigai.helium.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.qigai.helium.ui.icons.HeIcons
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeGradients
import tech.qigai.helium.ui.theme.HeShapes
import tech.qigai.helium.ui.theme.ambientFloat
import tech.qigai.helium.ui.theme.rememberAmbientMotion

/** Agent orb. Idle = quiet frosted glass + single-accent scallop; active = slow spin + halo ("只有工作中的东西发光"). */
@Composable
fun AgentOrb(modifier: Modifier = Modifier, size: Dp = 96.dp, active: Boolean = false, onClick: (() -> Unit)? = null) {
    val spin = ambientFloat(0f, 360f, if (active) 6000 else 24000)
    val breathe = ambientFloat(0.96f, 1.04f, 2400, reverse = true)
    var m = modifier.size(size)
    if (onClick != null) {
        m = m.clip(CircleShape).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
    }
    Box(m, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size).heCardShadow(CircleShape, if (active) 14.dp else 10.dp)) {
            val r = this.size.minDimension / 2f
            val c = Offset(r, r)
            drawCircle(Brush.verticalGradient(listOf(Color.White, Color(0xFFEEF0F8))), r, c)
            drawCircle(Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0f))), r - 1f, c, style = Stroke(1.5f))
            drawCircle(Color(0xFFDCE0EC).copy(alpha = 0.6f), r - 0.5f, c, style = Stroke(1f))
            val halo = if (active) 0.32f else 0.14f
            drawCircle(
                Brush.radialGradient(listOf(HeColors.Accent.copy(alpha = halo), HeColors.Accent.copy(alpha = 0f)), center = Offset(r, r * 1.08f), radius = r * 0.66f),
                r * 0.66f, Offset(r, r * 1.08f),
            )
        }
        Canvas(
            Modifier
                .size(size * 0.56f)
                .rotate(spin)
                .scale(if (active) breathe else 1f),
        ) {
            val r = this.size.minDimension / 2f
            drawPath(lobedPath(r, r, r, 12, 0.07f), Brush.linearGradient(HeGradients.Orb, Offset.Zero, Offset(r * 2, r * 2)))
        }
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2f
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0f)), center = Offset(r * 0.84f, r * 0.8f), radius = r * 0.26f),
                r * 0.26f, Offset(r * 0.84f, r * 0.8f),
            )
        }
    }
}

/** 12-lobed gradient avatar that marks Helium's own messages. */
@Composable
fun FlowerAvatar(modifier: Modifier = Modifier, size: Dp = 24.dp, spinning: Boolean = false) {
    val spin = ambientFloat(0f, 360f, 4000, enabled = spinning && rememberAmbientMotion())
    Canvas(modifier.size(size).rotate(if (spinning) spin else 0f)) {
        val r = this.size.minDimension / 2f
        drawPath(lobedPath(r, r, r, 12, 0.08f), Brush.linearGradient(HeGradients.Orb, Offset.Zero, Offset(r * 2, r * 2)))
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0f)), center = Offset(r * 0.8f, r * 0.75f), radius = r * 0.35f),
            r * 0.35f, Offset(r * 0.8f, r * 0.75f),
        )
    }
}

/**
 * Input capsule. States: idle (sparkle + mic), typing (send), running (继续说… + stop in the same spot).
 * Stopping never loses the draft.
 */
@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "让 Helium 帮你…",
    running: Boolean = false,
    onStop: () -> Unit = {},
    onAttach: () -> Unit = {},
    onSkills: () -> Unit = {},
    onMic: () -> Unit = {},
) {
    val shape = RoundedCornerShape(28.dp)
    val strokeAlpha = when {
        running -> 0.55f
        value.isNotEmpty() -> 0.45f
        else -> 0.22f
    }
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .heCardShadow(shape)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, HeColors.Hair, shape)
            .border(1.dp, Brush.linearGradient(HeGradients.AuroraSunrise.map { it.copy(alpha = strokeAlpha) }), shape)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIcon(HeIcons.Plus, HeColors.Mist2, HeColors.Ink, onAttach, contentDescription = "附件")
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).padding(vertical = 8.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(if (running) "继续说…" else placeholder, color = HeColors.Ink2, fontSize = 16.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = HeColors.Ink),
                cursorBrush = SolidColor(HeColors.Accent),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(8.dp))
        when {
            running -> CircleIcon(HeIcons.Stop, HeColors.Ink, Color.White, onStop, contentDescription = "停止")
            value.isNotBlank() -> CircleIcon(HeIcons.ArrowUp, HeColors.Accent, Color.White, onSend, contentDescription = "发送")
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIcon(HeIcons.Sparkle, Color.White, HeColors.Accent, onSkills, contentDescription = "技能")
                Spacer(Modifier.width(8.dp))
                CircleIcon(HeIcons.Mic, HeColors.Ink, Color.White, onMic, contentDescription = "语音")
            }
        }
    }
}

enum class NavTab(val route: String, val label: String) {
    HOME("home", "首页"),
    CHAT("chat", "对话"),
    GLANCE("glance", "速览"),
    TASKS("tasks", "任务"),
}

private fun NavTab.icon() = when (this) {
    NavTab.HOME -> HeIcons.Home
    NavTab.CHAT -> HeIcons.Chat
    NavTab.GLANCE -> HeIcons.Glance
    NavTab.TASKS -> HeIcons.Tasks
}

/** v0.3 bottom nav: 首页 · 对话 · 速览 · 任务. Active = accent-tint pill + icon + label; null = no selection. */
@Composable
fun HeBottomNav(current: NavTab?, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .heCardShadow(HeShapes.Pill)
            .clip(HeShapes.Pill)
            .background(Color.White)
            .border(1.dp, HeColors.Hair, HeShapes.Pill)
            .padding(8.dp)
            .animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavTab.entries.forEach { tab ->
            val active = tab == current
            Row(
                Modifier
                    .height(48.dp)
                    .clip(HeShapes.Pill)
                    .background(if (active) HeColors.AccentTint else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(tab) }
                    .padding(horizontal = if (active) 14.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(tab.icon(), tab.label, tint = if (active) HeColors.Accent else HeColors.Ink2, modifier = Modifier.size(24.dp))
                if (active) {
                    Spacer(Modifier.width(6.dp))
                    Text(tab.label, color = HeColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
    }
}
