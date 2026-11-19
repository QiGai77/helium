package tech.qigai.helium.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import tech.qigai.helium.ui.illo.HeIllustrations
import tech.qigai.helium.ui.illo.IlloKind
import tech.qigai.helium.ui.illo.IlloTone
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeGradients
import tech.qigai.helium.ui.theme.HeShapes
import tech.qigai.helium.ui.theme.MeshRecipe
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** v0.2 unified soft, low card shadow (neutral ink-blue). */
fun Modifier.heCardShadow(shape: Shape, elevation: Dp = 10.dp): Modifier = this.shadow(
    elevation = elevation,
    shape = shape,
    clip = false,
    ambientColor = HeColors.ShadowInk.copy(alpha = 0.06f),
    spotColor = HeColors.ShadowInk.copy(alpha = 0.10f),
)

/** White (or mist) card: hairline + unified shadow. */
@Composable
fun WhiteCard(
    modifier: Modifier = Modifier,
    shape: Shape = HeShapes.Card,
    color: Color = HeColors.Surface,
    shadow: Boolean = true,
    border: Color? = HeColors.Hair,
    borderWidth: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    var m = modifier
    if (shadow) m = m.heCardShadow(shape)
    m = m.clip(shape).background(color)
    if (border != null) m = m.border(borderWidth, border, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m, contentAlignment = contentAlignment, content = content)
}

/** Hero gradient surface: matte mesh + glass light sweep + top-edge highlight rim. One per viewport. */
@Composable
fun HeroSurface(
    modifier: Modifier = Modifier,
    recipe: MeshRecipe = HeGradients.Aurora,
    shape: Shape = HeShapes.Card,
    shadow: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shadowColor = recipe.base.getOrNull(1)?.second ?: HeColors.Accent
    var m = modifier
    if (shadow) {
        m = m.shadow(16.dp, shape, clip = false, ambientColor = shadowColor.copy(alpha = 0.25f), spotColor = shadowColor.copy(alpha = 0.45f))
    }
    m = m.clip(shape)
        .drawWithCache {
            val w = size.width
            val h = size.height
            val base = Brush.linearGradient(
                *recipe.base.toTypedArray(),
                start = Offset(0f, h * 0.1f),
                end = Offset(w, h * 0.9f),
            )
            val sweep = Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0f)),
                start = Offset(0f, 0f),
                end = Offset(w * 0.5f, h * 0.5f + 1f),
            )
            val sweepPath = Path().apply {
                moveTo(w * 0.38f, 0f); lineTo(w * 0.62f, 0f); lineTo(w * 0.30f, h); lineTo(w * 0.06f, h); close()
            }
            onDrawBehind {
                drawRect(base)
                recipe.blobs.forEach { b -> drawBlob(b.cx, b.cy, b.rx, b.ry, b.color, b.alpha) }
                drawBlob(0.78f, -0.1f, 0.55f, 0.5f, Color.White, 0.16f)
                drawPath(sweepPath, sweep)
            }
        }
        .border(
            1.dp,
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.08f))),
            shape,
        )
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m, content = content)
}

/** Soft radial colour blob (stands in for the SVG's blurred ellipses). */
fun DrawScope.drawBlob(cx: Float, cy: Float, rx: Float, ry: Float, color: Color, alpha: Float) {
    val w = size.width
    val h = size.height
    val center = Offset(cx * w, cy * h)
    val rxp = rx * w
    val ryp = ry * h
    if (rxp <= 0f || ryp <= 0f) return
    scale(scaleX = rxp / ryp, scaleY = 1f, pivot = center) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.55f), color.copy(alpha = 0f)),
                center = center,
                radius = ryp,
            ),
            radius = ryp,
            center = center,
        )
    }
}

/** n-lobed "scallop / flower" outline (DESIGN §4 shape semantics). */
fun lobedPath(cx: Float, cy: Float, r: Float, lobes: Int, amp: Float, rot: Float = 0f): Path {
    val steps = lobes * 24
    val p = Path()
    for (i in 0 until steps) {
        val t = 2.0 * PI * i / steps
        val rr = r * (1 - amp + amp * cos(lobes * (t + rot)))
        val x = cx + (rr * cos(t - PI / 2)).toFloat()
        val y = cy + (rr * sin(t - PI / 2)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

class LobedShape(private val lobes: Int, private val amp: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = min(size.width, size.height) / 2f
        return Outline.Generic(lobedPath(size.width / 2f, size.height / 2f, r, lobes, amp))
    }
}

enum class GlassShape { CIRCLE, SCALLOP, FLOWER, PILL, SQUARE }

private fun GlassShape.toShape(): Shape = when (this) {
    GlassShape.CIRCLE -> androidx.compose.foundation.shape.CircleShape
    GlassShape.SCALLOP -> LobedShape(12, 0.07f)
    GlassShape.FLOWER -> LobedShape(8, 0.1f)
    GlassShape.PILL -> androidx.compose.foundation.shape.RoundedCornerShape(percent = 50)
    GlassShape.SQUARE -> androidx.compose.foundation.shape.RoundedCornerShape(30)
}

/** v0.2 icon tile: neutral glass base + inner accent glow + highlight rim + single accent icon. */
@Composable
fun GlassIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: GlassShape = GlassShape.CIRCLE,
    tint: Color = HeColors.Accent,
    onHero: Boolean = false,
    iconSize: Dp = size * 0.55f,
) {
    val s = shape.toShape()
    val m = if (onHero) {
        modifier.size(size).clip(s).background(Color.White.copy(alpha = 0.2f)).border(1.dp, Color.White.copy(alpha = 0.45f), s)
    } else {
        modifier
            .size(size)
            .shadow(3.dp, s, clip = false, ambientColor = HeColors.ShadowInk.copy(alpha = 0.08f), spotColor = HeColors.ShadowInk.copy(alpha = 0.10f))
            .clip(s)
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE9ECF5))))
            .drawBehind {
                val r = this.size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(listOf(tint.copy(alpha = 0.16f), tint.copy(alpha = 0f)), center = Offset(r, r * 1.15f), radius = r * 0.7f),
                    radius = r * 0.7f,
                    center = Offset(r, r * 1.15f),
                )
                drawBlob(0.375f, 0.225f, 0.22f, 0.10f, Color.White, 0.8f)
            }
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color(0xFFDCE0EC).copy(alpha = 0.7f))), s)
    }
    Box(m, contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = if (onHero) Color.White else tint, modifier = Modifier.size(iconSize))
    }
}

/** Event illustration (atmosphere only). */
@Composable
fun Illo(kind: IlloKind, tone: IlloTone, modifier: Modifier = Modifier) {
    Image(imageVector = HeIllustrations.get(kind, tone), contentDescription = null, modifier = modifier)
}
