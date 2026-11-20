package tech.qigai.helium.ui.components

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.qigai.helium.ui.icons.HeIcons
import tech.qigai.helium.ui.theme.HeButton
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeShapes

enum class ButtonKind { PRIMARY, TONAL, OUTLINE, WHITE, BRAND, TEXT }

/** Pill button in the three heights 56 / 48 / 36. Primary = ink (not the accent). */
@Composable
fun HePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    icon: ImageVector? = null,
    height: Dp = HeButton.Large,
    enabled: Boolean = true,
) {
    val (bg, fg) = when (kind) {
        ButtonKind.PRIMARY -> HeColors.Ink to Color.White
        ButtonKind.TONAL -> HeColors.Mist2 to HeColors.Ink
        ButtonKind.OUTLINE -> Color.White to HeColors.Ink
        ButtonKind.WHITE -> Color.White to HeColors.Ink
        ButtonKind.BRAND -> HeColors.Accent to Color.White
        ButtonKind.TEXT -> Color.Transparent to HeColors.Accent
    }
    var m = modifier.height(height)
    if (kind == ButtonKind.WHITE) m = m.heCardShadow(HeShapes.Pill)
    m = m.clip(HeShapes.Pill).background(if (enabled) bg else bg.copy(alpha = 0.5f))
    if (kind == ButtonKind.OUTLINE) m = m.border(1.dp, HeColors.Outline, HeShapes.Pill)
    if (kind == ButtonKind.WHITE) m = m.border(1.dp, HeColors.Hair, HeShapes.Pill)
    m = m.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    val size = if (height >= HeButton.Large) 16.sp else 15.sp
    Row(
        m.padding(horizontal = if (height >= HeButton.Medium) 20.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) fg else fg.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            color = if (enabled) fg else fg.copy(alpha = 0.6f),
            fontSize = size,
            fontWeight = if (kind == ButtonKind.PRIMARY || kind == ButtonKind.BRAND) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 40dp top-bar glass button: white + hairline + unified shadow. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = HeColors.Ink,
    contentDescription: String? = null,
) {
    Box(
        modifier
            .size(size)
            .heCardShadow(CircleShape, 6.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(1.dp, HeColors.Hair, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.6f))
    }
}

/** v0.3: entry to 我的 — top-right avatar on every tab root. */
@Composable
fun AvatarButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    RoundIconButton(HeIcons.Person, onClick, modifier, tint = HeColors.Ink2, contentDescription = "我的")
}

/** Flat circle with an icon (composer + / send / stop / mic). */
@Composable
fun CircleIcon(
    icon: ImageVector,
    bg: Color,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier.size(size).clip(CircleShape).background(bg).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

enum class TagKind { ACCENT, AMBER, MINT, NEUTRAL, GLASS, HERO }

/** Small status / meta tag. Status colours live only here and in dots. */
@Composable
fun HeTag(
    label: String,
    kind: TagKind = TagKind.ACCENT,
    modifier: Modifier = Modifier,
    dot: Boolean = kind == TagKind.ACCENT || kind == TagKind.AMBER || kind == TagKind.MINT,
    shape: Shape = HeShapes.Pill,
    height: Dp = 24.dp,
    fontSize: Int = 12,
) {
    val (bg, fg, dc) = when (kind) {
        TagKind.ACCENT -> Triple(HeColors.AccentTint, HeColors.Accent, HeColors.Accent)
        TagKind.AMBER -> Triple(HeColors.AmberTint, HeColors.AmberInk, HeColors.Amber)
        TagKind.MINT -> Triple(HeColors.MintTint, HeColors.MintInk, HeColors.Mint)
        TagKind.NEUTRAL -> Triple(HeColors.Mist2, HeColors.Ink2, HeColors.Ink3)
        TagKind.GLASS -> Triple(Color.White.copy(alpha = 0.92f), HeColors.Ink, HeColors.Ink)
        TagKind.HERO -> Triple(Color.White.copy(alpha = 0.22f), Color.White, Color.White)
    }
    Row(
        modifier.height(height).clip(shape).background(bg).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dc))
            Spacer(Modifier.width(5.dp))
        }
        Text(label, color = fg, fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Status dot ≤ 8dp. */
@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/** Helium switch: 52×32 accent track + white knob with check (v0.2). */
@Composable
fun HeToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, onHero: Boolean = false) {
    val knobSize by animateDpAsState(if (checked) 24.dp else 16.dp, label = "knob")
    val knobX by animateDpAsState(if (checked) 24.dp else 8.dp, label = "knobX")
    val track by animateColorAsState(
        when {
            checked && onHero -> HeColors.Ink.copy(alpha = 0.28f)
            checked -> HeColors.Accent
            else -> HeColors.Mist
        },
        label = "track",
    )
    Box(
        modifier
            .size(52.dp, 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(track)
            .then(if (!checked) Modifier.border(2.dp, HeColors.Ink3, RoundedCornerShape(16.dp)) else Modifier)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Box(
            Modifier
                .offset(x = knobX, y = (32.dp - knobSize) / 2)
                .size(knobSize)
                .clip(CircleShape)
                .background(if (checked) Color.White else HeColors.Ink3),
            contentAlignment = Alignment.Center,
        ) {
            if (checked && !onHero) {
                Icon(HeIcons.Check, null, tint = HeColors.Accent, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, actionIcon: ImageVector? = null, onAction: () -> Unit = {}) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null) {
            Row(
                Modifier.clip(HeShapes.Pill).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (actionIcon != null) {
                    Icon(actionIcon, null, tint = HeColors.Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(action, color = HeColors.Accent, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Short user feedback for features that are not wired up yet. */
@Composable
fun rememberToast(): (String) -> Unit {
    val ctx = LocalContext.current
    return { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
}
