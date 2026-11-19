@file:OptIn(ExperimentalMaterial3Api::class)

package tech.qigai.helium.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tech.qigai.helium.data.model.CardSize
import tech.qigai.helium.data.model.GlanceCard
import tech.qigai.helium.data.model.HeroEvent
import tech.qigai.helium.data.model.Highlight
import tech.qigai.helium.data.model.Source
import tech.qigai.helium.data.model.XPost
import tech.qigai.helium.ui.components.ButtonKind
import tech.qigai.helium.ui.components.GlassIcon
import tech.qigai.helium.ui.components.GlassShape
import tech.qigai.helium.ui.components.HePillButton
import tech.qigai.helium.ui.components.HeTag
import tech.qigai.helium.ui.components.HeroSurface
import tech.qigai.helium.ui.components.Illo
import tech.qigai.helium.ui.components.RoundIconButton
import tech.qigai.helium.ui.components.SectionHeader
import tech.qigai.helium.ui.components.TagKind
import tech.qigai.helium.ui.components.WhiteCard
import tech.qigai.helium.ui.icons.HeIcons
import tech.qigai.helium.ui.illo.IlloKind
import tech.qigai.helium.ui.illo.IlloTone
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeNum
import tech.qigai.helium.ui.theme.HeShapes

/** 07 速览 — empty until real sources (weather, calendar, RSS, X) are connected in M6. */
@Composable
fun GlanceScreen(today: GlanceToday?, onOpenMe: () -> Unit, toast: (String) -> Unit) {
    if (today == null) {
        Column(Modifier.fillMaxSize()) {
            RootHeader(title = "今日速览", eyebrow = todayLabel(), onAvatar = onOpenMe)
            EmptyState(
                "速览还没有数据源",
                "接入天气、日历、RSS 和 X 之后，这里每天会整理出和你有关的要点，并写明为什么推给你。",
                Modifier.weight(1f),
            )
        }
        return
    }
    GlanceContent(today, onOpenMe, toast)
}

@Composable
private fun GlanceContent(today: GlanceToday, onOpenMe: () -> Unit, toast: (String) -> Unit) {
    var source by remember { mutableStateOf(Source.ALL) }
    var sheet by remember { mutableStateOf(false) }
    val customCards = remember { mutableStateListOf<GlanceCard.Custom>() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        RootHeader(
            title = "今日速览",
            eyebrow = todayLabel(),
            onAvatar = onOpenMe,
        )
        Row(Modifier.padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("从 X、记忆、邮件和网页为你整理 · ${today.updatedAt} 更新", color = HeColors.Ink2, fontSize = 13.sp, modifier = Modifier.weight(1f))
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Source.entries.forEach { s -> SourceChip(s, s == source) { source = s } }
        }
        Spacer(Modifier.height(8.dp))
        val hl = today.highlights.filter { source == Source.ALL || it.sourceKind == source }
        HighlightsCard(hl, Modifier.padding(horizontal = 24.dp))

        if (source == Source.ALL || source == Source.X) {
            Spacer(Modifier.height(28.dp))
            SectionHeader("X 热帖", Modifier.padding(horizontal = 24.dp), action = "更多", onAction = { toast("X 来源需要连接器（v1）") })
            Spacer(Modifier.height(12.dp))
            XPostCard(today.xPost, Modifier.padding(horizontal = 24.dp))
        }

        Spacer(Modifier.height(28.dp))
        SectionHeader("我的卡片", Modifier.padding(horizontal = 24.dp), action = "编辑", onAction = { toast("卡片编辑即将支持") })
        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val cards = today.cards
            cards.filterIsInstance<GlanceCard.Flight>().firstOrNull()?.let { f ->
                HeroEventCard(HeroEvent("", f.title, f.meta, f.time, f.route, IlloKind.FLIGHT), height = 176, showTag = false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                cards.filterIsInstance<GlanceCard.Train>().firstOrNull()?.let { TrainCard(it, Modifier.weight(1f)) }
                cards.filterIsInstance<GlanceCard.Weather>().firstOrNull()?.let { WeatherCard(it, Modifier.weight(1f)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                cards.filterIsInstance<GlanceCard.Budget>().firstOrNull()?.let { BudgetCard(it, Modifier.weight(1f)) }
                cards.filterIsInstance<GlanceCard.Stock>().firstOrNull()?.let { StockCard(it, Modifier.weight(1f)) }
            }
            customCards.forEach { CustomCard(it) }
            cards.filterIsInstance<GlanceCard.Package>().firstOrNull()?.let { PackageStack(it) }
            MakeCardEntry { sheet = true }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (sheet) {
        CustomCardSheet(
            onDismiss = { sheet = false },
            onAdd = { card ->
                customCards += card
                sheet = false
                toast("已添加到速览")
            },
        )
    }
}

@Composable
private fun SourceChip(s: Source, active: Boolean, onClick: () -> Unit) {
    val icon = when (s) {
        Source.MEMORY -> HeIcons.Memory
        Source.MAIL -> HeIcons.Mail
        Source.WEB -> HeIcons.Globe
        else -> null
    }
    Row(
        Modifier
            .height(36.dp)
            .clip(HeShapes.Pill)
            .background(if (active) HeColors.AccentTint else Color.White)
            .then(if (active) Modifier else Modifier.border(1.dp, HeColors.Hair, HeShapes.Pill))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (active) HeColors.Accent else HeColors.Ink2, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(s.label, fontSize = 14.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, color = if (active) HeColors.Accent else HeColors.Ink)
    }
}

@Composable
private fun HighlightsCard(items: List<Highlight>, modifier: Modifier = Modifier) {
    HeroSurface(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIcon(HeIcons.Sparkle, size = 36.dp, shape = GlassShape.SCALLOP, onHero = true)
                Spacer(Modifier.width(12.dp))
                Text("今日要点", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                HeTag("${items.size} 条信息差", TagKind.GLASS, dot = false, height = 28.dp, fontSize = 13)
            }
            if (items.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("这个来源今天没有新的要点", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
            }
            items.forEachIndexed { i, h ->
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.25f))
                Spacer(Modifier.height(12.dp))
                Row {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(h.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp)
                        Spacer(Modifier.height(2.dp))
                        Text("${h.source} · ${h.time}", color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
                        Text("与你相关：${h.relevance}", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun XPostCard(p: XPost, modifier: Modifier = Modifier) {
    WhiteCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(HeColors.Mist2), contentAlignment = Alignment.Center) {
                    Text(p.initials, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink2)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.author, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink)
                    Text("${p.handle} · ${p.time}", fontSize = 13.sp, color = HeColors.Ink2)
                }
                Box(Modifier.size(28.dp).clip(CircleShape).background(HeColors.Mist2), contentAlignment = Alignment.Center) {
                    Text("X", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink2)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(p.text, fontSize = 15.sp, lineHeight = 23.sp, color = HeColors.Ink, modifier = Modifier.padding(horizontal = 8.dp))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Metric(HeIcons.Heart, p.likes)
                Spacer(Modifier.width(20.dp))
                Metric(HeIcons.Repeat, p.reposts)
                Spacer(Modifier.width(20.dp))
                Metric(HeIcons.Chat, p.replies)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(HeShapes.Row).background(HeColors.Mist3).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(HeIcons.Sparkle, null, tint = HeColors.Accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(p.reason, fontSize = 13.sp, color = HeColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Metric(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = HeColors.Ink2, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(value, fontSize = 14.sp, color = HeColors.Ink2, style = HeNum.style(14, FontWeight.Normal))
    }
}

private val BENTO_H = 176.dp

@Composable
private fun TrainCard(c: GlanceCard.Train, modifier: Modifier) {
    WhiteCard(modifier.height(BENTO_H)) {
        Illo(IlloKind.TRAIN, IlloTone.LIGHT, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = 14.dp).size(80.dp, 64.dp))
        Column(Modifier.padding(20.dp).fillMaxSize()) {
            Text(c.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink)
            Text(c.meta, fontSize = 13.sp, color = HeColors.Ink2)
            Spacer(Modifier.weight(1f))
            Text(c.time, style = HeNum.style(36), color = HeColors.Ink)
            Spacer(Modifier.height(4.dp))
            Text(c.detail, fontSize = 12.sp, color = HeColors.Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WeatherCard(c: GlanceCard.Weather, modifier: Modifier) {
    WhiteCard(modifier.height(BENTO_H)) {
        Illo(c.kind, IlloTone.LIGHT, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = 10.dp).size(80.dp, 64.dp))
        Column(Modifier.padding(20.dp).fillMaxSize()) {
            Text("天气", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink)
            Text(c.city, fontSize = 13.sp, color = HeColors.Ink2)
            Spacer(Modifier.weight(1f))
            Text(c.temp, style = HeNum.style(40), color = HeColors.Ink)
            Spacer(Modifier.height(4.dp))
            Text(c.detail, fontSize = 12.sp, color = HeColors.Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BudgetCard(c: GlanceCard.Budget, modifier: Modifier) {
    WhiteCard(modifier.height(BENTO_H)) {
        GlassIcon(HeIcons.Receipt, Modifier.align(Alignment.TopEnd).padding(16.dp), size = 44.dp, shape = GlassShape.SQUARE)
        Column(Modifier.padding(20.dp).fillMaxSize()) {
            Text("本月预算", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink)
            Text("剩余", fontSize = 13.sp, color = HeColors.Ink2)
            Spacer(Modifier.weight(1f))
            Text(c.remaining, style = HeNum.style(32), color = HeColors.Ink)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(HeShapes.Pill).background(HeColors.Mist)) {
                Box(Modifier.fillMaxWidth(c.usedFraction).height(6.dp).clip(HeShapes.Pill).background(HeColors.Accent))
            }
            Spacer(Modifier.height(6.dp))
            Text(c.detail, fontSize = 12.sp, color = HeColors.Ink2, maxLines = 1)
        }
    }
}

@Composable
private fun AiBadge(modifier: Modifier = Modifier) {
    Row(
        modifier.height(22.dp).clip(HeShapes.Pill).background(HeColors.AccentTint).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(HeIcons.Sparkle, null, tint = HeColors.Accent, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text("AI 生成 · 可编辑", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Accent)
    }
}

@Composable
private fun StockCard(c: GlanceCard.Stock, modifier: Modifier) {
    WhiteCard(modifier.height(BENTO_H)) {
        Column(Modifier.padding(16.dp).fillMaxSize()) {
            AiBadge()
            Spacer(Modifier.height(8.dp))
            Text(c.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink, maxLines = 1)
            Text(c.price, style = HeNum.style(28), color = HeColors.Ink)
            Sparkline(c.points, Modifier.fillMaxWidth().weight(1f).padding(vertical = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeTag(c.change, if (c.up) TagKind.MINT else TagKind.NEUTRAL, height = 22.dp, fontSize = 11)
                Spacer(Modifier.width(8.dp))
                Text(c.refresh, fontSize = 11.sp, color = HeColors.Ink3, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Sparkline(points: List<Float>, modifier: Modifier) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val p = Path()
        points.forEachIndexed { i, v ->
            val x = w * i / (points.size - 1)
            val y = h * (1 - v)
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, HeColors.Accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        val lx = w
        val ly = h * (1 - points.last())
        drawCircle(Color.White, 5.dp.toPx(), Offset(lx - 2.dp.toPx(), ly))
        drawCircle(HeColors.Accent, 5.dp.toPx(), Offset(lx - 2.dp.toPx(), ly), style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun PackageStack(c: GlanceCard.Package) {
    Box(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Box(
            Modifier.padding(horizontal = 24.dp).offset(y = (-14).dp).fillMaxWidth().height(40.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(HeColors.Mist2)
                .border(1.dp, HeColors.Hair, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        )
        Box(
            Modifier.padding(horizontal = 12.dp).offset(y = (-7).dp).fillMaxWidth().height(40.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(HeColors.Mist3)
                .border(1.dp, HeColors.Hair, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        )
        WhiteCard(Modifier.fillMaxWidth().height(96.dp)) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(36.dp, 4.dp).clip(HeShapes.Pill).background(HeColors.Hair))
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Illo(IlloKind.PACKAGE, IlloTone.LIGHT, Modifier.size(70.dp, 56.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink)
                    Text(c.meta, fontSize = 13.sp, color = HeColors.Ink2)
                }
                Icon(HeIcons.ChevronRight, null, tint = HeColors.Ink3, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun MakeCardEntry(onClick: () -> Unit) {
    val shape = HeShapes.Card
    Row(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(shape)
            .drawBehind {
                drawRoundRect(
                    color = HeColors.Outline,
                    cornerRadius = CornerRadius(28.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(HeColors.AccentTint), contentAlignment = Alignment.Center) {
            Icon(HeIcons.Plus, null, tint = HeColors.Accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text("让 Helium 做一张卡", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink)
            Text("说一句话，生成一张属于你的卡", fontSize = 13.sp, color = HeColors.Ink2)
        }
    }
}

@Composable
private fun CustomCard(c: GlanceCard.Custom) {
    WhiteCard(Modifier.fillMaxWidth()) {
        CustomCardBody(c)
    }
}

@Composable
private fun CustomCardBody(c: GlanceCard.Custom) {
    Column(Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIcon(HeIcons.Glance, size = 40.dp, shape = GlassShape.SQUARE)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.meta, fontSize = 12.sp, color = HeColors.Ink2)
            }
            AiBadge()
        }
        val shown = when (c.size) {
            CardSize.SMALL -> c.lines.take(1)
            CardSize.WIDE -> c.lines.take(3)
            CardSize.LARGE -> c.lines
        }
        shown.forEachIndexed { i, (t, v) ->
            if (i > 0) HorizontalDivider(color = HeColors.Hair)
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", color = HeColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                Text(t, fontSize = 15.sp, color = HeColors.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(HeIcons.Heart, null, tint = HeColors.Ink3, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(v, fontSize = 13.sp, color = HeColors.Ink2)
            }
        }
    }
}

/** Local stand-in for the card generator: turns the prompt into a sample card preview. */
private fun previewFor(prompt: String, size: CardSize, refresh: String, variant: Int): GlanceCard.Custom {
    val topic = prompt.substringAfter("关于", prompt).substringBefore("的").trim().ifBlank { "AI agent" }.take(16)
    val pools = listOf(
        listOf("手机 Agent 的审批该放在哪一步？" to "2.4k", "开源浏览器代理新版：多标签并行" to "1.8k", "把定时任务交给 Agent 的 5 个坑" to "960", "长上下文降价后，记忆该怎么存？" to "720"),
        listOf("一张图看懂 Agent 的权限作用域" to "3.1k", "让 Agent 帮你比价：踩过的坑" to "1.2k", "语音优先的个人助理会回来吗" to "880", "MCP 服务器安全清单" to "640"),
    )
    return GlanceCard.Custom(
        id = "custom-" + System.nanoTime(),
        title = "X · $topic 热帖",
        meta = "$refresh 更新 · 预览",
        lines = pools[variant % pools.size],
        size = size,
    )
}

/** 08 「让 Helium 做一张卡」 bottom sheet: prompt → preview → size → refresh → add. */
@Composable
private fun CustomCardSheet(onDismiss: () -> Unit, onAdd: (GlanceCard.Custom) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("每天早上给我看 X 上关于 AI agent 的 3 条热帖") }
    var size by remember { mutableStateOf(CardSize.WIDE) }
    var refresh by remember { mutableStateOf("每天 08:00") }
    var variant by remember { mutableStateOf(0) }
    val preview = remember(prompt, size, refresh, variant) { previewFor(prompt, size, refresh, variant) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("让 Helium 做一张卡", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink)
                    Spacer(Modifier.height(4.dp))
                    Text("用一句话描述，Helium 生成并按时刷新", fontSize = 13.sp, color = HeColors.Ink2)
                }
                RoundIconButton(HeIcons.Close, { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }, contentDescription = "关闭")
            }
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(HeShapes.Card)
                    .background(HeColors.Mist3)
                    .border(BorderStroke(2.dp, HeColors.Accent), HeShapes.Card)
                    .padding(20.dp),
            ) {
                Column {
                    BasicTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 24.sp, color = HeColors.Ink),
                        cursorBrush = SolidColor(HeColors.Accent),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        HePillButton("重新生成", { variant++ }, kind = ButtonKind.WHITE, icon = HeIcons.Refresh, height = 40.dp)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("预览", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink2)
            }
            Spacer(Modifier.height(10.dp))
            WhiteCard(Modifier.fillMaxWidth(), shadow = false) { CustomCardBody(preview) }
            Spacer(Modifier.height(20.dp))
            Text("尺寸", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink2)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CardSize.entries.forEach { s -> SizeOption(s, s == size, Modifier.weight(1f)) { size = s } }
            }
            Spacer(Modifier.height(20.dp))
            Text("刷新频率", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink2)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("每天 08:00", "每 4 小时", "每小时", "仅手动").forEach { r ->
                    val active = r == refresh
                    Box(
                        Modifier
                            .height(40.dp)
                            .clip(HeShapes.Pill)
                            .background(if (active) HeColors.AccentTint else Color.White)
                            .then(if (active) Modifier else Modifier.border(1.dp, HeColors.Hair, HeShapes.Pill))
                            .clickable { refresh = r }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(r, fontSize = 14.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, color = if (active) HeColors.Accent else HeColors.Ink)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(HeIcons.Search, null, tint = HeColors.Ink3, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("来源：X 搜索 · 按互动量排序 · 添加后可随时编辑", fontSize = 13.sp, color = HeColors.Ink2)
            }
            Spacer(Modifier.height(20.dp))
            HePillButton("添加到速览", { onAdd(preview) }, Modifier.fillMaxWidth(), ButtonKind.PRIMARY, HeIcons.Plus)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SizeOption(s: CardSize, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(76.dp)
            .clip(HeShapes.Row)
            .background(if (active) HeColors.AccentTint else Color.White)
            .border(if (active) 2.dp else 1.dp, if (active) HeColors.Accent else HeColors.Hair, HeShapes.Row)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val (w, h) = when (s) {
            CardSize.SMALL -> 14.dp to 14.dp
            CardSize.WIDE -> 32.dp to 14.dp
            CardSize.LARGE -> 32.dp to 32.dp
        }
        Box(Modifier.size(w, h).clip(RoundedCornerShape(4.dp)).background(if (active) HeColors.Accent else HeColors.Outline.copy(alpha = 0.7f)))
        Spacer(Modifier.height(8.dp))
        Text(s.label, fontSize = 13.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, color = if (active) HeColors.Accent else HeColors.Ink)
    }
}
