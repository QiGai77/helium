package tech.qigai.helium.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.qigai.helium.data.settings.LlmSettings
import tech.qigai.helium.data.llm.ProviderId
import tech.qigai.helium.data.llm.Role
import tech.qigai.helium.agent.AgentEvent
import tech.qigai.helium.agent.GrantScope
import tech.qigai.helium.agent.Risk
import tech.qigai.helium.agent.ToolCall
import tech.qigai.helium.ui.components.ButtonKind
import tech.qigai.helium.ui.components.Composer
import tech.qigai.helium.ui.components.Dot
import tech.qigai.helium.ui.components.FlowerAvatar
import tech.qigai.helium.ui.components.HePillButton
import tech.qigai.helium.ui.components.MarkdownText
import tech.qigai.helium.ui.components.RoundIconButton
import tech.qigai.helium.ui.components.heCardShadow
import tech.qigai.helium.ui.icons.HeIcons
import tech.qigai.helium.ui.theme.HeButton
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeGradients
import tech.qigai.helium.ui.theme.HeShapes
import tech.qigai.helium.ui.theme.ambientFloat
import java.time.format.DateTimeFormatter

private val DAY_HM = DateTimeFormatter.ofPattern("M月d日 HH:mm")

/** 02 对话 — agent chat: streamed replies, tool steps and inline approval cards, all from the event stream. */
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    settings: LlmSettings,
    onSelectModel: (String) -> Unit,
    onBack: () -> Unit,
    onOpenTimeline: () -> Unit,
    onOpenModels: () -> Unit,
    toast: (String) -> Unit,
) {
    val running by vm.running
    val listState = rememberLazyListState()
    var menu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    val streamingText by vm.streamingText
    val streamingReasoning by vm.streamingReasoning
    val items = chatItems(vm.events)
    val clipboard = LocalClipboardManager.current
    val focusAt = vm.focusAt.value
    var preview by remember { mutableStateOf<java.io.File?>(null) }
    val lastAssistantKey = items.lastOrNull { it is ChatItem.Assistant }?.key
    LaunchedEffect(vm.events.size, streamingText.length, streamingReasoning.length, focusAt) {
        val count = listState.layoutInfo.totalItemsCount
        val target = focusAt?.let { at -> items.indexOfFirst { it.at() == at } }?.takeIf { it >= 0 }
        if (target != null) {
            // +1 for the date stamp row at the top of the list.
            listState.scrollToItem(target + 1)
        } else if (count > 0) {
            listState.scrollToItem(count - 1)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to Color(0xFFE6E9FB), 0.25f to HeColors.Canvas, 1f to HeColors.Canvas)),
    ) {
        SubTopBar(
            title = "Helium",
            onBack = onBack,
            subtitle = {
                Box {
                    Row(
                        Modifier.clip(HeShapes.Pill).clickable { modelMenu = true }.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Dot(if (running) HeColors.Accent else HeColors.Mint, size = 7.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (running) "正在工作" else settings.activeModelLabel,
                            fontSize = 13.sp, color = HeColors.Ink2,
                        )
                        Icon(HeIcons.ChevronRight, null, tint = HeColors.Ink3, modifier = Modifier.size(14.dp))
                    }
                    ModelMenu(modelMenu, settings, onDismiss = { modelMenu = false }, onSelect = {
                        modelMenu = false
                        onSelectModel(it)
                    }, onMore = {
                        modelMenu = false
                        onOpenModels()
                    })
                }
            },
            trailing = {
                Box {
                    RoundIconButton(HeIcons.More, { menu = true }, contentDescription = "更多")
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("新对话") }, onClick = { menu = false; vm.newConversation() })
                        DropdownMenuItem(text = { Text("对话列表与搜索") }, onClick = { menu = false; history = true })
                        DropdownMenuItem(text = { Text("本对话时间线") }, onClick = { menu = false; onOpenTimeline() })
                        DropdownMenuItem(text = { Text("模型与推理") }, onClick = { menu = false; onOpenModels() })
                    }
                }
            },
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (vm.events.isEmpty()) {
                item(key = "empty") { EmptyChatHint(settings) }
            } else {
                item(key = "stamp") {
                    Text(
                        ChatViewModel.localTime(vm.events.first().at).format(DAY_HM),
                        color = HeColors.Ink3, fontSize = 13.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(items, key = { it.key }) { item ->
                when (item) {
                    is ChatItem.User -> UserBubble(
                        item,
                        highlighted = focusAt == item.event.at,
                        imageFile = vm::imageFile,
                        onPreview = { preview = it },
                        onCopy = { clipboard.setText(AnnotatedString(item.text)); toast("已复制") },
                        onEdit = { vm.beginEdit(item.event) },
                    )
                    is ChatItem.Assistant -> Column(
                        Modifier.clip(RoundedCornerShape(16.dp)).background(if (focusAt == item.at) HeColors.AccentTint else Color.Transparent),
                    ) {
                        AssistantMessage(item.message, onRetry = vm::retryLast, onOpenModels = onOpenModels)
                        if (item.sources.isNotEmpty()) SourceChips(item.sources)
                        if (!running && item.message.text.isNotBlank()) {
                            MessageActions(
                                onCopy = { clipboard.setText(AnnotatedString(item.message.text)); toast("已复制") },
                                onRegenerate = if (item.key == lastAssistantKey) vm::retryLast else null,
                            )
                        }
                    }
                    is ChatItem.Step -> ToolStepRow(item)
                    is ChatItem.Approval -> InlineApproval(
                        item,
                        awaiting = item.decision == null && vm.state.value == tech.qigai.helium.agent.AgentState.AWAITING_APPROVAL && vm.isAwaiting(item.request),
                        onDecide = { approved, scope -> vm.decide(item.request, approved, scope) },
                    )
                }
            }
            if (running && (streamingText.isNotEmpty() || streamingReasoning.isNotEmpty() || items.lastOrNull() !is ChatItem.Approval)) {
                item(key = "streaming") {
                    AssistantMessage(
                        ChatMessage(-1, Role.ASSISTANT, streamingText, streamingReasoning, streaming = true, model = settings.activeModelLabel),
                        onRetry = vm::retryLast, onOpenModels = onOpenModels,
                    )
                }
            }
        }

        ChatInputBar(
            vm = vm,
            onSend = { vm.send() },
            toast = toast,
            running = running,
            onStop = vm::stop,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(12.dp))
    }
    if (history) {
        HistoryDialog(
            conversations = vm.conversations.collectAsState().value,
            currentId = vm.conversationId.value,
            hits = vm.searchHits.value,
            searching = vm.searching.value,
            onSearch = vm::search,
            onOpen = { history = false; vm.search(""); vm.open(it) },
            onOpenHit = { hit -> history = false; vm.search(""); vm.open(hit.conversationId, focus = hit.at) },
            onNew = { history = false; vm.search(""); vm.newConversation() },
            onRename = vm::rename,
            onDelete = vm::delete,
            onDismiss = { history = false },
        )
    }
    preview?.let { f -> ImagePreviewDialog(f) { preview = null } }
}

@Composable
private fun ModelMenu(open: Boolean, s: LlmSettings, onDismiss: () -> Unit, onSelect: (String) -> Unit, onMore: () -> Unit) {
    DropdownMenu(open, onDismissRequest = onDismiss) {
        if (s.provider == ProviderId.OPENCODE_GO) {
            s.openCodeModels.take(12).forEach { m ->
                DropdownMenuItem(
                    text = { Text((if (m.id == s.openCodeModel) "✓ " else "    ") + m.label) },
                    onClick = { onSelect(m.id) },
                )
            }
        } else {
            DropdownMenuItem(text = { Text("✓ ${s.provider.title} · ${s.activeModelLabel}") }, onClick = onDismiss)
        }
        DropdownMenuItem(text = { Text("更多模型与设置…", color = HeColors.Accent) }, onClick = onMore)
    }
}

@Composable
private fun EmptyChatHint(s: LlmSettings) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("今天想让我帮你做什么？", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            if (s.isConfigured(s.provider)) "当前模型：${s.provider.title} · ${s.activeModelLabel}"
            else "先在「我的 → 模型与推理」填入 ${s.provider.title} 的 API Key",
            fontSize = 13.sp, color = HeColors.Ink2, textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun UserBubble(
    item: ChatItem.User,
    highlighted: Boolean,
    imageFile: (String) -> java.io.File,
    onPreview: (java.io.File) -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        if (item.event.images.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item.event.images.forEach { ref ->
                    val f = imageFile(ref)
                    Thumb(f, 120.dp, onClick = { onPreview(f) })
                }
            }
            if (item.text.isNotBlank()) Spacer(Modifier.height(6.dp))
        }
        if (item.text.isNotBlank()) {
            Box {
                Text(
                    item.text,
                    color = Color.White,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (highlighted) HeColors.Accent else HeColors.Ink)
                        .combinedClickable(onClick = {}, onLongClick = { menu = true })
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("复制") }, onClick = { menu = false; onCopy() })
                    DropdownMenuItem(text = { Text("编辑后重发") }, onClick = { menu = false; onEdit() })
                }
            }
        } else {
            Text("编辑", fontSize = 12.sp, color = HeColors.Ink3, modifier = Modifier.clip(HeShapes.Pill).clickable(onClick = onEdit).padding(4.dp))
        }
    }
}

@Composable
private fun MessageActions(onCopy: () -> Unit, onRegenerate: (() -> Unit)?) {
    Row(Modifier.padding(start = 32.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ActionText("复制", onCopy)
        if (onRegenerate != null) ActionText("重答", onRegenerate)
    }
}

@Composable
private fun ActionText(label: String, onClick: () -> Unit) {
    Text(
        label, fontSize = 12.sp, color = HeColors.Ink3,
        modifier = Modifier.clip(HeShapes.Pill).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private fun ChatItem.at(): Long? = when (this) {
    is ChatItem.User -> event.at
    is ChatItem.Assistant -> at
    else -> null
}

@Composable
private fun AssistantMessage(m: tech.qigai.helium.ui.screens.ChatMessage, onRetry: () -> Unit, onOpenModels: () -> Unit) {
    var showReasoning by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().animateContentSize()) {
        FlowerAvatar(Modifier.padding(top = 2.dp), size = 24.dp, spinning = m.streaming)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (m.reasoning.isNotBlank()) {
                Text(
                    if (showReasoning) "收起思考过程" else if (m.streaming && m.text.isEmpty()) "思考中…（点开查看）" else "查看思考过程",
                    color = HeColors.Accent, fontSize = 13.sp,
                    modifier = Modifier.clip(HeShapes.Pill).clickable { showReasoning = !showReasoning }.padding(vertical = 2.dp),
                )
                if (showReasoning) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        m.reasoning.trim(), color = HeColors.Ink2, fontSize = 13.sp, lineHeight = 20.sp,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(HeColors.Mist2).padding(12.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            when {
                m.text.isNotEmpty() -> MarkdownText(m.text)
                m.streaming && m.reasoning.isBlank() -> ThinkingWave()
            }
            if (m.error != null) {
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(HeColors.CoralTint)
                        .padding(16.dp),
                ) {
                    Text("没能拿到回复", color = HeColors.CoralInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(m.error, color = HeColors.Ink2, fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HePillButton("重试", onRetry, kind = ButtonKind.WHITE, height = HeButton.Small, icon = HeIcons.Refresh)
                        HePillButton("模型设置", onOpenModels, kind = ButtonKind.WHITE, height = HeButton.Small)
                    }
                }
            }
            if (m.stopped) {
                Spacer(Modifier.height(6.dp))
                Text("已停止", color = HeColors.Ink3, fontSize = 12.sp)
            }
            if (m.interrupted) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("已中断", color = HeColors.CoralInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Text("回复生成到一半时 App 被关闭了", color = HeColors.Ink3, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    HePillButton("重试", onRetry, kind = ButtonKind.WHITE, height = HeButton.Small, icon = HeIcons.Refresh)
                }
            }
        }
    }
}

/** Accent wave that stands in for a spinner while waiting for the first token. */
@Composable
private fun ThinkingWave(modifier: Modifier = Modifier) {
    val phase = ambientFloat(0f, 1f, 1200)
    Canvas(modifier.width(140.dp).height(20.dp)) {
        val w = size.width
        val h = size.height
        val p = Path()
        val steps = 60
        for (i in 0..steps) {
            val x = w * i / steps
            val y = h / 2 + (h * 0.3f) * kotlin.math.sin((i / 6f + phase * 2f) * Math.PI.toFloat())
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(
            p,
            Brush.horizontalGradient(listOf(HeColors.Accent.copy(alpha = 0.1f), HeColors.Accent)),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
