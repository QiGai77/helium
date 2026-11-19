package tech.qigai.helium.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.qigai.helium.ui.components.AvatarButton
import tech.qigai.helium.ui.components.RoundIconButton
import tech.qigai.helium.ui.icons.HeIcons
import tech.qigai.helium.ui.theme.HeColors
import java.time.LocalDate
import java.time.LocalTime

/** Secondary-page top bar: 40dp round back button, centred title, optional trailing content. */
@Composable
fun SubTopBar(
    title: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backIcon: ImageVector = HeIcons.ArrowLeft,
    subtitle: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = { Spacer(Modifier.size(40.dp)) },
) {
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        RoundIconButton(backIcon, onBack, Modifier.align(Alignment.CenterStart), contentDescription = "返回")
        if (title != null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink, textAlign = TextAlign.Center)
                subtitle?.invoke()
            }
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

/** Root-tab header: date eyebrow, big title, optional subtitle and right-side actions (avatar = 我的). */
@Composable
fun RootHeader(
    title: String,
    onAvatar: () -> Unit,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    inlineSubtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(eyebrow, color = HeColors.Ink2, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(title, style = MaterialTheme.typography.displaySmall, color = HeColors.Ink)
                if (inlineSubtitle != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(inlineSubtitle, color = HeColors.Ink2, fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(subtitle, color = HeColors.Ink2, fontSize = 15.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            actions()
            Spacer(Modifier.width(8.dp))
            AvatarButton(onAvatar)
        }
    }
}

private val WEEK = arrayOf("一", "二", "三", "四", "五", "六", "日")

fun weekdayCn(d: LocalDate = LocalDate.now()): String = WEEK[d.dayOfWeek.value - 1]

fun todayLabel(d: LocalDate = LocalDate.now()): String = "${d.monthValue}月${d.dayOfMonth}日 · 周${weekdayCn(d)}"

fun greeting(t: LocalTime = LocalTime.now()): String = when (t.hour) {
    in 5..10 -> "早上好"
    in 11..12 -> "中午好"
    in 13..17 -> "下午好"
    else -> "晚上好"
}

fun weekdayShort(index1to7: Int): String = WEEK[(index1to7 - 1).coerceIn(0, 6)]
