@file:OptIn(ExperimentalMaterial3Api::class)

package tech.qigai.helium.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tech.qigai.helium.HeliumApplication
import tech.qigai.helium.data.db.FeedItemRow
import tech.qigai.helium.data.db.FeedSourceEntity
import tech.qigai.helium.data.model.CardSize
import tech.qigai.helium.glance.CardDefinition
import tech.qigai.helium.glance.CardLine
import tech.qigai.helium.glance.CardView
import tech.qigai.helium.glance.GlanceRepository
import tech.qigai.helium.glance.WeatherView
import tech.qigai.helium.glance.openInBrowser
import tech.qigai.helium.ui.components.ButtonKind
import tech.qigai.helium.ui.components.GlassIcon
import tech.qigai.helium.ui.components.GlassShape
import tech.qigai.helium.ui.components.HePillButton
import tech.qigai.helium.ui.components.HeTag
import tech.qigai.helium.ui.components.Illo
import tech.qigai.helium.ui.components.RoundIconButton
import tech.qigai.helium.ui.components.SectionHeader
import tech.qigai.helium.ui.components.TagKind
import tech.qigai.helium.ui.components.WhiteCard
import tech.qigai.helium.ui.icons.HeIcons
import tech.qigai.helium.ui.illo.IlloTone
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeNum
import tech.qigai.helium.ui.theme.HeShapes
import java.time.Instant
import java.time.ZoneId

/** 07 速览 — real weather, the user's RSS / web / X sources with "为什么推给你", and generated custom cards. */
@Composable
fun GlanceScreen(onOpenMe: () -> Unit, toast: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as HeliumApplication
    val repo = app.glance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val weather by repo.weather.collectAsState(initial = null)
    val sources by repo.sources.collectAsState(initial = emptyList())
    val items by repo.items.collectAsState(initial = emptyList())
    val cards by repo.cards.collectAsState(initial = emptyList())
    val busy by repo.busy.collectAsState()
    var filter by remember { mutableStateOf<Long?>(null) }
    var sourcesSheet by remember { mutableStateOf(false) }
    var citySheet by remember { mutableStateOf(false) }
    var cardSheet by remember { mutableStateOf(false) }
    var editCards by remember { mutableStateOf(false) }
    val modelReady = remember { repo.modelReady() }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        scope.launch {
            repo.useDeviceLocation().onFailure { toast("天气刷新失败：${it.message}") }
            if (!ok) toast("没有定位权限，天气用的是${repo.manualPlace?.name ?: "默认城市南京"}")
        }
    }

    // Opening 速览 refreshes whatever is stale (weather < 30 min is served from cache).
    LaunchedEffect(Unit) { runCatching { repo.refreshAll(force = false, foreground = true) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        RootHeader(title = "今日速览", eyebrow = todayLabel(), onAvatar = onOpenMe)
        Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (busy) "正在刷新…" else "天气、订阅和卡片每小时自动更新" + (weather?.let { " · ${hhmm(it.fetchedAt)} 更新" } ?: ""),
                color = HeColors.Ink2, fontSize = 13.sp, modifier = Modifier.weight(1f),
            )
            RoundIconButton(HeIcons.Refresh, {
                scope.launch { runCatching { repo.refreshAll(force = true, foreground = true) }.onFailure { toast("刷新失败：${it.message}") } }
            }, contentDescription = "刷新")
        }
        Spacer(Modifier.height(12.dp))
        WeatherWide(weather, Modifier.padding(horizontal = 24.dp)) { citySheet = true }

        Spacer(Modifier.height(24.dp))
        SectionHeader("订阅", Modifier.padding(horizontal = 24.dp), action = "管理来源", onAction = { sourcesSheet = true })
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip("全部", filter == null) { filter = null }
            sources.forEach { s -> Chip(s.title, filter == s.id, icon = sourceIcon(s.kind)) { filter = s.id } }
            Chip("添加", false, icon = HeIcons.Plus) { sourcesSheet = true }
        }
        val shown = items.filter { filter == null || it.sourceId == filter }.take(30)
        FeedList(shown, hasSources = sources.isNotEmpty(), modelReady = modelReady, Modifier.padding(horizontal = 24.dp),
            onOpen = { openInBrowser(context, it.link) }, onAdd = { sourcesSheet = true })

        Spacer(Modifier.height(28.dp))
        SectionHeader("我的卡片", Modifier.padding(horizontal = 24.dp), action = if (cards.isEmpty()) null else if (editCards) "完成" else "编辑", onAction = { editCards = !editCards })
        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            cards.forEach { c -> LiveCard(c, editCards) { scope.launch { repo.removeCard(c.id) } } }
            MakeCardEntry { cardSheet = true }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (sourcesSheet) {
        SourcesSheet(repo, sources, onDismiss = { sourcesSheet = false }, toast = toast)
    }
    if (citySheet) {
        CitySheet(
            repo, weather,
            onDismiss = { citySheet = false },
            onUseLocation = {
                citySheet = false
                if (repo.locationGranted()) scope.launch { repo.useDeviceLocation().onFailure { toast("定位天气失败：${it.message}") } }
                else locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            toast = toast,
        )
    }
    if (cardSheet) {
        CustomCardSheet(repo, onDismiss = { cardSheet = false }, onAdded = { cardSheet = false; toast("已添加到速览") })
    }
}

private fun hhmm(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).let { "%02d:%02d".format(it.hour, it.minute) }

private fun ago(at: Long, now: Long = System.currentTimeMillis()): String {
    val m = (now - at) / 60_000
    return when {
        m < 1 -> "刚刚"
        m < 60 -> "$m 分钟前"
        m < 24 * 60 -> "${m / 60} 小时前"
        m < 7 * 24 * 60 -> "${m / (24 * 60)} 天前"
        else -> Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).let { "${it.monthValue}月${it.dayOfMonth}日" }
    }
}

private fun sourceIcon(kind: String) = when (kind) {
    "WEB" -> HeIcons.Globe
    "X" -> null
    else -> HeIcons.List
}

@Composable
private fun Chip(label: String, active: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
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
        Text(label, fontSize = 14.sp, maxLines = 1, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, color = if (active) HeColors.Accent else HeColors.Ink)
    }
}

@Composable
private fun WeatherWide(w: WeatherView?, modifier: Modifier, onClick: () -> Unit) {
    WhiteCard(modifier.fillMaxWidth().height(156.dp), onClick = onClick) {
        if (w == null) {
            Column(Modifier.padding(20.dp)) {
                Text("天气", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink)
                Spacer(Modifier.height(6.dp))
                Text("正在获取 Open-Meteo 天气…", fontSize = 13.sp, color = HeColors.Ink2)
            }
            return@WhiteCard
        }
        val r = w.report
        Illo(GlanceRepository.illoFor(r.kind), IlloTone.LIGHT, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = 12.dp).size(110.dp, 88.dp))
        Column(Modifier.padding(20.dp).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(w.place, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink)
                Spacer(Modifier.width(8.dp))
                HeTag(when (w.placeSource) { "gps" -> "定位"; "manual" -> "手动"; else -> "默认" }, TagKind.NEUTRAL, height = 22.dp, fontSize = 11)
            }
            Text(r.label, fontSize = 13.sp, color = HeColors.Ink2)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(r.tempText, style = HeNum.style(44), color = HeColors.Ink)
                Spacer(Modifier.width(12.dp))
                Text(r.detail(), fontSize = 12.sp, color = HeColors.Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
    }
}

@Composable
private fun FeedList(
    items: List<FeedItemRow>,
    hasSources: Boolean,
    modelReady: Boolean,
    modifier: Modifier,
    onOpen: (FeedItemRow) -> Unit,
    onAdd: () -> Unit,
) {
    WhiteCard(modifier.fillMaxWidth()) {
        if (items.isEmpty()) {
            Column(Modifier.padding(20.dp)) {
                Text(if (hasSources) "这些来源还没有新条目" else "还没有订阅来源", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink)
                Spacer(Modifier.height(4.dp))
                Text("添加 RSS / Atom 地址、任意网页，或通过 RSSHub / Nitter 订阅 X 账号。", fontSize = 13.sp, color = HeColors.Ink2, lineHeight = 19.sp)
                if (!hasSources) {
                    Spacer(Modifier.height(14.dp))
                    HePillButton("添加来源", onAdd, kind = ButtonKind.TONAL, icon = HeIcons.Plus, height = 40.dp)
                }
            }
            return@WhiteCard
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            items.forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(color = HeColors.Hair)
                Column(
                    Modifier.fillMaxWidth().clip(HeShapes.Row).clickable(enabled = row.link.isNotBlank()) { onOpen(row) }.padding(horizontal = 4.dp, vertical = 12.dp),
                ) {
                    Text(row.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text("${row.sourceTitle} · ${ago(row.publishedAt)}", fontSize = 12.sp, color = HeColors.Ink3, maxLines = 1)
                    val reason = row.reason
                    if (reason != null) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(HeIcons.Sparkle, null, tint = HeColors.Accent, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("为什么推给你：$reason", fontSize = 12.sp, color = HeColors.Ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    } else if (row.summary.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(row.summary, fontSize = 13.sp, color = HeColors.Ink2, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
                    }
                }
            }
            if (!modelReady) {
                Text("配置模型后，每条都会写上「为什么推给你」。", fontSize = 12.sp, color = HeColors.Ink3, modifier = Modifier.padding(4.dp, 4.dp, 4.dp, 10.dp))
            }
        }
    }
}

@Composable
private fun SheetFrame(title: String, subtitle: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Color.White, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink)
                    Spacer(Modifier.height(4.dp))
                    Text(subtitle, fontSize = 13.sp, color = HeColors.Ink2)
                }
                RoundIconButton(HeIcons.Close, { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }, contentDescription = "关闭")
            }
            Spacer(Modifier.height(20.dp))
            content()
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SourcesSheet(repo: GlanceRepository, sources: List<FeedSourceEntity>, onDismiss: () -> Unit, toast: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var bridge by remember { mutableStateOf(repo.xBridge) }
    var adding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    SheetFrame("管理来源", "支持 RSS / Atom、普通网页与 X 账号", onDismiss) {
        OutlinedTextField(input, { input = it; error = null }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("粘贴网址，或 @账号") })
        error?.let { Spacer(Modifier.height(6.dp)); Text(it, fontSize = 12.sp, color = HeColors.CoralInk) }
        Spacer(Modifier.height(10.dp))
        HePillButton(if (adding) "正在读取…" else "添加", {
            if (adding) return@HePillButton
            adding = true
            scope.launch {
                repo.addSource(input)
                    .onSuccess { s -> input = ""; toast("已添加「${s.title}」"); launch { runCatching { repo.writeReasons() } } }
                    .onFailure { error = it.message ?: "添加失败" }
                adding = false
            }
        }, Modifier.fillMaxWidth(), ButtonKind.PRIMARY, HeIcons.Plus, enabled = input.isNotBlank() && !adding)

        Spacer(Modifier.height(24.dp))
        Text("X 桥接", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink2)
        Spacer(Modifier.height(6.dp))
        Text("X 没有公开订阅源。填你自己的 RSSHub（/twitter/user/<账号>）或 Nitter（/<账号>/rss）实例地址。", fontSize = 12.sp, color = HeColors.Ink3, lineHeight = 18.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(bridge, { bridge = it }, singleLine = true, modifier = Modifier.weight(1f), placeholder = { Text("https://rsshub.example.com") })
            Spacer(Modifier.width(8.dp))
            HePillButton("保存", { repo.xBridge = bridge; toast(if (bridge.isBlank()) "已清除 X 桥接" else "已保存") }, kind = ButtonKind.TONAL, height = 48.dp)
        }

        Spacer(Modifier.height(24.dp))
        Text("已添加 · ${sources.size}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink2)
        sources.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        s.lastError?.let { "上次刷新失败：$it" } ?: "${when (s.kind) { "WEB" -> "网页"; "X" -> "X"; else -> "RSS" }} · ${s.url}",
                        fontSize = 12.sp, color = if (s.lastError != null) HeColors.CoralInk else HeColors.Ink3, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                RoundIconButton(HeIcons.Close, { scope.launch { repo.removeSource(s.id) } }, contentDescription = "移除")
            }
            HorizontalDivider(color = HeColors.Hair)
        }
    }
}

@Composable
private fun CitySheet(repo: GlanceRepository, w: WeatherView?, onDismiss: () -> Unit, onUseLocation: () -> Unit, toast: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var city by remember { mutableStateOf(repo.manualPlace?.name.orEmpty()) }
    var working by remember { mutableStateOf(false) }
    SheetFrame("天气城市", "当前：${w?.place ?: "南京"} · 数据来自 Open-Meteo", onDismiss) {
        HePillButton("使用手机定位", onUseLocation, Modifier.fillMaxWidth(), ButtonKind.TONAL, HeIcons.Pin)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(city, { city = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("或输入城市，例如 南京") })
        Spacer(Modifier.height(10.dp))
        HePillButton(if (working) "正在查找…" else "使用这个城市", {
            working = true
            scope.launch {
                repo.setManualCity(city).onSuccess { toast("天气已切换到 ${it.display}"); onDismiss() }.onFailure { toast(it.message ?: "找不到这个城市") }
                working = false
            }
        }, Modifier.fillMaxWidth(), enabled = city.isNotBlank() && !working)
    }
}

@Composable
private fun MakeCardEntry(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(HeShapes.Card)
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
            Text("说一句话，比如「做一张盯 BTC 价格的卡」", fontSize = 13.sp, color = HeColors.Ink2)
        }
    }
}

@Composable
private fun AiBadge() {
    Row(
        Modifier.height(22.dp).clip(HeShapes.Pill).background(HeColors.AccentTint).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(HeIcons.Sparkle, null, tint = HeColors.Accent, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text("AI 生成", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Accent)
    }
}

private fun intervalLabel(min: Int) = when {
    min >= 24 * 60 -> "每天"
    min % 60 == 0 -> if (min == 60) "每小时" else "每 ${min / 60} 小时"
    else -> "每 $min 分钟"
}

@Composable
private fun LiveCard(c: CardView, editing: Boolean, onRemove: () -> Unit) {
    WhiteCard(Modifier.fillMaxWidth()) {
        CardBody(
            title = c.title,
            meta = c.error?.let { "刷新失败：$it" }
                ?: ("${intervalLabel(c.intervalMinutes)}刷新 · " + (c.lastRefreshAt?.let { "${hhmm(it)} 更新" } ?: "等待首次刷新") + (if (c.host.isNotBlank()) " · ${c.host}" else "")),
            lines = c.lines,
            size = c.size,
            history = c.history,
            trailing = { if (editing) RoundIconButton(HeIcons.Close, onRemove, contentDescription = "删除") else AiBadge() },
        )
    }
}

@Composable
private fun CardBody(title: String, meta: String, lines: List<CardLine>, size: String, history: List<Double>, trailing: @Composable () -> Unit) {
    Column(Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIcon(HeIcons.Chart, size = 40.dp, shape = GlassShape.SQUARE)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(meta, fontSize = 12.sp, color = HeColors.Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing()
        }
        val first = lines.firstOrNull()
        if (first != null && first.raw != null && first.value.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(first.value, style = HeNum.style(32), color = HeColors.Ink)
            if (history.size >= 2) Sparkline(history, Modifier.fillMaxWidth().height(36.dp).padding(vertical = 4.dp))
        }
        val rest = if (first?.raw != null) lines.drop(1) else lines
        val shown = when (size) {
            "SMALL" -> rest.take(1)
            "LARGE" -> rest
            else -> rest.take(3)
        }
        shown.forEachIndexed { i, l ->
            if (i > 0 || first?.raw != null) HorizontalDivider(color = HeColors.Hair)
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(l.label, fontSize = 14.sp, color = HeColors.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (l.value.isNotBlank()) {
                    val tint = when {
                        l.value.startsWith("+") -> HeColors.MintInk
                        l.value.startsWith("-") -> HeColors.CoralInk
                        else -> HeColors.Ink2
                    }
                    Text(l.value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = tint)
                }
            }
        }
        if (lines.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("还没有数据", fontSize = 13.sp, color = HeColors.Ink3)
        }
    }
}

@Composable
private fun Sparkline(values: List<Double>, modifier: Modifier) {
    Canvas(modifier) {
        val lo = values.min()
        val hi = values.max()
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        val p = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = size.height * (1 - ((v - lo) / span).toFloat())
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, HeColors.Accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(HeColors.Accent, 3.dp.toPx(), Offset(size.width, size.height * (1 - ((values.last() - lo) / span).toFloat())))
    }
}

/** 08 「让 Helium 做一张卡」: sentence → definition (template or model) → live preview → size + interval → add. */
@Composable
private fun CustomCardSheet(repo: GlanceRepository, onDismiss: () -> Unit, onAdded: () -> Unit) {
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("做一张盯 BTC 价格的卡") }
    var def by remember { mutableStateOf<CardDefinition?>(null) }
    var lines by remember { mutableStateOf<List<CardLine>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(CardSize.WIDE) }
    var minutes by remember { mutableStateOf(60) }

    fun generate() {
        working = true
        error = null
        scope.launch {
            repo.generateCard(prompt).onSuccess { d ->
                def = d
                minutes = d.intervalMinutes
                size = runCatching { CardSize.valueOf(d.size) }.getOrDefault(CardSize.WIDE)
                lines = repo.preview(d).onFailure { error = "预览失败：${it.message}" }.getOrNull()
            }.onFailure { def = null; lines = null; error = it.message }
            working = false
        }
    }

    SheetFrame("让 Helium 做一张卡", "一句话描述，Helium 找数据源、写抽取规则并按时刷新", onDismiss) {
        Box(
            Modifier.fillMaxWidth().clip(HeShapes.Card).background(HeColors.Mist3).border(BorderStroke(2.dp, HeColors.Accent), HeShapes.Card).padding(20.dp),
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
                    HePillButton(if (working) "生成中…" else if (def == null) "生成" else "重新生成", { if (!working) generate() },
                        kind = ButtonKind.WHITE, icon = HeIcons.Sparkle, height = 40.dp, enabled = prompt.isNotBlank() && !working)
                }
            }
        }
        error?.let { Spacer(Modifier.height(10.dp)); Text(it, fontSize = 13.sp, color = HeColors.CoralInk, lineHeight = 19.sp) }
        val d = def
        if (d != null) {
            Spacer(Modifier.height(20.dp))
            Text("预览", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink2)
            Spacer(Modifier.height(10.dp))
            WhiteCard(Modifier.fillMaxWidth(), shadow = false) {
                CardBody(d.title, "${intervalLabel(minutes)}刷新 · 实时数据", lines.orEmpty(), size.name, emptyList()) { AiBadge() }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(HeIcons.Link, null, tint = HeColors.Ink3, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("来源：${d.url.substringAfter("://").substringBefore("?")} · ${d.fields.size} 个字段", fontSize = 12.sp, color = HeColors.Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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
                listOf(60, 240, 24 * 60).forEach { m -> Chip(intervalLabel(m), m == minutes) { minutes = m } }
            }
            Spacer(Modifier.height(20.dp))
            HePillButton("添加到速览", {
                scope.launch {
                    repo.addCard(prompt, d.copy(intervalMinutes = minutes.coerceAtLeast(CardDefinition.MIN_INTERVAL), size = size.name), lines)
                    onAdded()
                }
            }, Modifier.fillMaxWidth(), ButtonKind.PRIMARY, HeIcons.Plus)
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
