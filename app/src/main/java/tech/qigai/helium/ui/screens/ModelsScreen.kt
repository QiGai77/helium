@file:OptIn(ExperimentalLayoutApi::class)

package tech.qigai.helium.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tech.qigai.helium.data.llm.LlmClient
import tech.qigai.helium.data.llm.ModelCatalog
import tech.qigai.helium.data.llm.ProviderId
import tech.qigai.helium.data.llm.WireApi
import tech.qigai.helium.data.settings.LlmSettings
import tech.qigai.helium.data.settings.SearchProviderId
import tech.qigai.helium.data.settings.SettingsRepository
import tech.qigai.helium.ui.components.ButtonKind
import tech.qigai.helium.ui.components.GlassIcon
import tech.qigai.helium.ui.components.HePillButton
import tech.qigai.helium.ui.components.HeTag
import tech.qigai.helium.ui.components.TagKind
import tech.qigai.helium.ui.components.WhiteCard
import tech.qigai.helium.ui.icons.HeIcons
import tech.qigai.helium.ui.theme.HeButton
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeShapes

private enum class KeyDialog { NONE, OPENCODE, GEMINI, CUSTOM, OPENCODE_MODEL, GEMINI_MODEL, DEEPSEEK, CLOUD }

/** 10 模型与推理 — OpenCode Go (default) / Gemini API / 自带 OpenAI 兼容. Keys go to the Keystore vault. */
@Composable
fun ModelsScreen(settings: LlmSettings, repo: SettingsRepository, onBack: () -> Unit, toast: (String) -> Unit) {
    var dialog by remember { mutableStateOf(KeyDialog.NONE) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val client = remember { LlmClient() }
    val search by repo.search.collectAsState()

    Column(Modifier.fillMaxSize()) {
        SubTopBar("模型与推理", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text("选择 Helium 思考与执行时调用的模型", fontSize = 15.sp, color = HeColors.Ink2)
            Spacer(Modifier.height(16.dp))

            // ---- OpenCode Go
            ProviderCard(
                icon = HeIcons.Cpu,
                title = "OpenCode Go",
                selected = settings.provider == ProviderId.OPENCODE_GO,
                tags = buildList {
                    add("默认" to TagKind.ACCENT)
                    add((if (settings.hasOpenCodeKey) "已启用" else "未配置") to (if (settings.hasOpenCodeKey) TagKind.MINT else TagKind.NEUTRAL))
                },
                desc = if (settings.hasOpenCodeKey) "API Key 接入 · 已配置 · 对话与任务默认使用" else "API Key 接入 · 在 opencode.ai 控制台获取 Go 订阅的 Key",
                onSelect = { repo.setProvider(ProviderId.OPENCODE_GO) },
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    settings.openCodeModels.forEach { m ->
                        ModelChip(
                            label = m.label + if (m.api == WireApi.ANTHROPIC_MESSAGES) " ·messages" else "",
                            active = settings.openCodeModel == m.id,
                        ) {
                            repo.setOpenCodeModel(m.id)
                            repo.setProvider(ProviderId.OPENCODE_GO)
                        }
                    }
                    if (settings.openCodeModels.none { it.id == settings.openCodeModel }) {
                        ModelChip(settings.openCodeModel, true) {}
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HePillButton(
                        if (loading) "获取中…" else "刷新模型列表",
                        {
                            if (!loading) {
                                loading = true
                                scope.launch {
                                    try {
                                        val list = client.fetchOpenCodeModels()
                                        repo.setOpenCodeModels(list)
                                        toast("已获取 ${list.size} 个可用模型")
                                    } catch (e: Exception) {
                                        toast("获取失败：${e.message ?: e.javaClass.simpleName}")
                                    } finally {
                                        loading = false
                                    }
                                }
                            }
                        },
                        kind = ButtonKind.TONAL, icon = HeIcons.Refresh, height = HeButton.Small,
                    )
                    HePillButton("手填模型 ID", { dialog = KeyDialog.OPENCODE_MODEL }, kind = ButtonKind.TONAL, height = HeButton.Small)
                }
                Spacer(Modifier.height(12.dp))
                HePillButton(
                    if (settings.hasOpenCodeKey) "更换 OpenCode Go API Key" else "填入 OpenCode Go API Key",
                    { dialog = KeyDialog.OPENCODE },
                    Modifier.fillMaxWidth(), ButtonKind.OUTLINE, HeIcons.Key, HeButton.Medium,
                )
            }
            Spacer(Modifier.height(16.dp))

            // ---- Gemini API
            ProviderCard(
                icon = HeIcons.Sparkle,
                title = "Gemini API",
                selected = settings.provider == ProviderId.GEMINI,
                tags = listOf((if (settings.hasGeminiKey) "已配置" else "未配置") to (if (settings.hasGeminiKey) TagKind.MINT else TagKind.NEUTRAL)),
                desc = "使用 Google AI Studio 的 API Key",
                onSelect = {
                    if (!settings.hasGeminiKey) dialog = KeyDialog.GEMINI
                    repo.setProvider(ProviderId.GEMINI)
                },
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModelCatalog.geminiDefaults.forEach { m ->
                        ModelChip(m.label, settings.geminiModel == m.id) { repo.setGeminiModel(m.id) }
                    }
                    if (ModelCatalog.geminiDefaults.none { it.id == settings.geminiModel }) ModelChip(settings.geminiModel, true) {}
                    ModelChip("手填…", false) { dialog = KeyDialog.GEMINI_MODEL }
                }
                Spacer(Modifier.height(14.dp))
                HePillButton(
                    if (settings.hasGeminiKey) "更换 Gemini API Key" else "填入你的 Gemini API Key",
                    { dialog = KeyDialog.GEMINI },
                    Modifier.fillMaxWidth(), ButtonKind.OUTLINE, HeIcons.Key, HeButton.Medium,
                )
            }
            Spacer(Modifier.height(16.dp))

            // ---- Custom OpenAI-compatible
            ProviderCard(
                icon = HeIcons.Server,
                title = "自带 API Key",
                selected = settings.provider == ProviderId.CUSTOM,
                tags = listOf("OpenAI 兼容" to TagKind.NEUTRAL),
                desc = if (settings.isConfigured(ProviderId.CUSTOM)) "${settings.customModel} · ${settings.customBaseUrl}" else "填写 Base URL、API Key 与模型名",
                onSelect = {
                    if (!settings.isConfigured(ProviderId.CUSTOM)) dialog = KeyDialog.CUSTOM
                    repo.setProvider(ProviderId.CUSTOM)
                },
            ) {
                ModelChip("任意 OpenAI 兼容模型", false) {}
                Spacer(Modifier.height(14.dp))
                HePillButton(
                    if (settings.isConfigured(ProviderId.CUSTOM)) "编辑" else "添加",
                    { dialog = KeyDialog.CUSTOM },
                    Modifier.fillMaxWidth(), ButtonKind.TONAL, HeIcons.Plus, HeButton.Medium,
                )
            }
            Spacer(Modifier.height(28.dp))

            // ---- 联网搜索 (web_search tool backend)
            Text("联网搜索", fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = HeColors.Ink)
            Spacer(Modifier.height(4.dp))
            Text("Agent 调用 web_search 时使用的搜索服务", fontSize = 13.sp, color = HeColors.Ink2)
            Spacer(Modifier.height(12.dp))
            ProviderCard(
                icon = HeIcons.Search,
                title = "Exa",
                selected = search.provider == SearchProviderId.EXA_MCP,
                tags = listOf("默认" to TagKind.ACCENT, "免费" to TagKind.MINT),
                desc = "公共 MCP 接口，无需 Key；其他服务不可用时也会自动回退到这里",
                onSelect = { repo.setSearchProvider(SearchProviderId.EXA_MCP) },
            ) {}
            Spacer(Modifier.height(12.dp))
            ProviderCard(
                icon = HeIcons.Globe,
                title = "DeepSeek 搜索",
                selected = search.provider == SearchProviderId.DEEPSEEK,
                tags = listOf((if (search.deepSeekReady) "已配置" else "未配置") to (if (search.deepSeekReady) TagKind.MINT else TagKind.NEUTRAL)),
                desc = when {
                    search.cloudConfigured -> "经 Helium Cloud 调用，Key 存在服务器 · 每千次约 \$0.75"
                    search.hasDeepSeekKey -> "直连 api.deepseek.com · 每千次约 \$0.75"
                    else -> "中文结果更好，按次计费；需要 DeepSeek Key 或 Helium Cloud"
                },
                onSelect = {
                    if (!search.deepSeekReady) dialog = KeyDialog.DEEPSEEK
                    repo.setSearchProvider(SearchProviderId.DEEPSEEK)
                },
            ) {
                if (!search.cloudConfigured) {
                    HePillButton(
                        if (search.hasDeepSeekKey) "更换 DeepSeek API Key" else "填入 DeepSeek API Key",
                        { dialog = KeyDialog.DEEPSEEK },
                        Modifier.fillMaxWidth(), ButtonKind.OUTLINE, HeIcons.Key, HeButton.Medium,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            ProviderCard(
                icon = HeIcons.Server,
                title = "Helium Cloud",
                selected = search.cloudConfigured,
                tags = listOf((if (search.cloudConfigured) "已连接" else "可选") to (if (search.cloudConfigured) TagKind.MINT else TagKind.NEUTRAL)),
                desc = if (search.cloudConfigured) search.cloudUrl else "你自己的 Cloudflare Worker：Key 放服务器，手机直连不通时走它中转",
                onSelect = { dialog = KeyDialog.CLOUD },
            ) {
                HePillButton(
                    if (search.cloudConfigured) "编辑" else "连接 Worker",
                    { dialog = KeyDialog.CLOUD },
                    Modifier.fillMaxWidth(), ButtonKind.TONAL, HeIcons.Plug, HeButton.Medium,
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(HeIcons.Lock, null, tint = HeColors.Ink2, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("API Key 经 Android Keystore 加密存放在凭据保险箱，只用于调用对应模型", fontSize = 12.sp, color = HeColors.Ink2)
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    when (dialog) {
        KeyDialog.NONE -> Unit
        KeyDialog.OPENCODE -> SecretDialog(
            title = "OpenCode Go API Key",
            hint = "在 opencode.ai 控制台 → Go 订阅里创建 API Key。以 Bearer 方式调用 opencode.ai/inference/go。",
            hasExisting = settings.hasOpenCodeKey,
            onDismiss = { dialog = KeyDialog.NONE },
            onSave = { repo.setApiKey(ProviderId.OPENCODE_GO, it); repo.setProvider(ProviderId.OPENCODE_GO); dialog = KeyDialog.NONE; toast("已保存到凭据保险箱") },
            onClear = { repo.setApiKey(ProviderId.OPENCODE_GO, null); dialog = KeyDialog.NONE },
        )
        KeyDialog.GEMINI -> SecretDialog(
            title = "Gemini API Key",
            hint = "在 Google AI Studio 创建 API Key（aistudio.google.com）。只用于 generativelanguage.googleapis.com。",
            hasExisting = settings.hasGeminiKey,
            onDismiss = { dialog = KeyDialog.NONE },
            onSave = { repo.setApiKey(ProviderId.GEMINI, it); repo.setProvider(ProviderId.GEMINI); dialog = KeyDialog.NONE; toast("已保存到凭据保险箱") },
            onClear = { repo.setApiKey(ProviderId.GEMINI, null); dialog = KeyDialog.NONE },
        )
        KeyDialog.OPENCODE_MODEL -> TextDialog("OpenCode Go 模型 ID", settings.openCodeModel, "例如 glm-5.3、kimi-k3、deepseek-v4-pro") {
            if (it != null && it.isNotBlank()) {
                repo.setOpenCodeModel(it); repo.setProvider(ProviderId.OPENCODE_GO)
            }
            dialog = KeyDialog.NONE
        }
        KeyDialog.GEMINI_MODEL -> TextDialog("Gemini 模型 ID", settings.geminiModel, "例如 gemini-flash-latest、gemini-2.5-pro") {
            if (it != null && it.isNotBlank()) repo.setGeminiModel(it)
            dialog = KeyDialog.NONE
        }
        KeyDialog.DEEPSEEK -> SecretDialog(
            title = "DeepSeek API Key",
            hint = "在 platform.deepseek.com 创建。只用于 api.deepseek.com/v1/tools/search。",
            hasExisting = search.hasDeepSeekKey,
            onDismiss = { dialog = KeyDialog.NONE },
            onSave = { repo.setDeepSeekKey(it); repo.setSearchProvider(SearchProviderId.DEEPSEEK); dialog = KeyDialog.NONE; toast("已保存到凭据保险箱") },
            onClear = { repo.setDeepSeekKey(null); dialog = KeyDialog.NONE },
        )
        KeyDialog.CLOUD -> CloudDialog(
            url = search.cloudUrl,
            hasToken = search.hasCloudToken,
            onDismiss = { dialog = KeyDialog.NONE },
            onSave = { url, token ->
                repo.setCloud(url, if (url.isBlank()) "" else token.ifBlank { null })
                dialog = KeyDialog.NONE
                toast(if (url.isBlank()) "已断开 Helium Cloud" else "已保存")
            },
        )
        KeyDialog.CUSTOM -> CustomDialog(
            settings = settings,
            onDismiss = { dialog = KeyDialog.NONE },
            onSave = { base, key, model ->
                repo.setCustom(base, model)
                if (key.isNotBlank()) repo.setApiKey(ProviderId.CUSTOM, key)
                repo.setProvider(ProviderId.CUSTOM)
                dialog = KeyDialog.NONE
                toast("已保存")
            },
        )
    }
}

@Composable
private fun ProviderCard(
    icon: ImageVector,
    title: String,
    selected: Boolean,
    tags: List<Pair<String, TagKind>>,
    desc: String,
    onSelect: () -> Unit,
    content: @Composable () -> Unit,
) {
    WhiteCard(
        Modifier.fillMaxWidth(),
        shape = HeShapes.Panel,
        border = if (selected) HeColors.Accent else HeColors.Hair,
        borderWidth = if (selected) 2.dp else 1.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.clickable(onClick = onSelect), verticalAlignment = Alignment.Top) {
                GlassIcon(icon, size = 40.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = HeColors.Ink)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { (t, k) -> HeTag(t, k, height = 26.dp) }
                    }
                }
                SelectMark(selected)
            }
            Spacer(Modifier.height(14.dp))
            Text(desc, fontSize = 14.sp, color = HeColors.Ink2)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SelectMark(selected: Boolean) {
    if (selected) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(HeColors.Accent), contentAlignment = Alignment.Center) {
            Icon(HeIcons.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    } else {
        Box(Modifier.size(26.dp).clip(CircleShape).border(2.dp, HeColors.Outline, CircleShape))
    }
}

@Composable
private fun ModelChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(36.dp)
            .clip(HeShapes.Pill)
            .background(if (active) HeColors.AccentTint else HeColors.Mist2)
            .then(if (active) Modifier.border(1.5.dp, HeColors.Accent, HeShapes.Pill) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, color = if (active) HeColors.Accent else HeColors.Ink)
    }
}

@Composable
private fun SecretDialog(title: String, hint: String, hasExisting: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit, onClear: () -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(title) },
        text = {
            Column {
                Text(hint, fontSize = 13.sp, color = HeColors.Ink2)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    placeholder = { Text(if (hasExisting) "已保存（不显示）· 输入新 Key 以替换" else "粘贴 API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value) }, enabled = value.isNotBlank()) { Text("保存") } },
        dismissButton = {
            Row {
                if (hasExisting) TextButton(onClick = onClear) { Text("删除", color = HeColors.CoralInk) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun TextDialog(title: String, initial: String, hint: String, onDone: (String?) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        containerColor = Color.White,
        title = { Text(title) },
        text = {
            Column {
                Text(hint, fontSize = 13.sp, color = HeColors.Ink2)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onDone(value.trim()) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("取消") } },
    )
}

@Composable
private fun CustomDialog(settings: LlmSettings, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var base by remember { mutableStateOf(settings.customBaseUrl) }
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(settings.customModel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("自带 OpenAI 兼容 API Key") },
        text = {
            Column {
                Text("Base URL 填到 /v1 为止，Helium 会请求 {Base URL}/chat/completions（流式）。", fontSize = 13.sp, color = HeColors.Ink2)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(base, { base = it }, singleLine = true, label = { Text("Base URL") }, placeholder = { Text("https://api.example.com/v1") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    key, { key = it }, singleLine = true, label = { Text("API Key") },
                    placeholder = { Text(if (settings.hasCustomKey) "已保存（留空则不修改）" else "sk-…") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(model, { model = it }, singleLine = true, label = { Text("模型名") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(base, key, model) },
                enabled = base.isNotBlank() && model.isNotBlank() && (key.isNotBlank() || settings.hasCustomKey),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}


@Composable
private fun CloudDialog(url: String, hasToken: Boolean, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var base by remember { mutableStateOf(url) }
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Helium Cloud") },
        text = {
            Column {
                Text("填你部署的 Worker 地址和设备令牌（HELIUM_TOKENS 里的一项）。留空地址即断开。", fontSize = 13.sp, color = HeColors.Ink2)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(base, { base = it }, singleLine = true, label = { Text("Worker 地址") }, placeholder = { Text("https://cloud.qigai.tech") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    token, { token = it }, singleLine = true,
                    label = { Text(if (hasToken) "设备令牌（留空保持不变）" else "设备令牌") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton({ onSave(base.trim(), token.trim()) }) { Text("保存") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}
