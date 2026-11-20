package tech.qigai.helium.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import tech.qigai.helium.data.llm.ChatTurn
import tech.qigai.helium.data.llm.LlmClient
import tech.qigai.helium.data.llm.ModelCatalog
import tech.qigai.helium.data.llm.ProviderId
import tech.qigai.helium.data.llm.Role
import tech.qigai.helium.data.settings.LlmSettings
import tech.qigai.helium.data.settings.SearchProviderId
import tech.qigai.helium.data.settings.SettingsRepository
import tech.qigai.helium.ui.components.ButtonKind
import tech.qigai.helium.ui.components.HePillButton
import tech.qigai.helium.ui.components.HeTag
import tech.qigai.helium.ui.components.TagKind
import tech.qigai.helium.ui.components.WhiteCard
import tech.qigai.helium.ui.icons.HeIcons
import tech.qigai.helium.ui.theme.HeButton
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeShapes
import java.util.UUID

private enum class KeyDialog { NONE, OPENCODE, GEMINI, CUSTOM, OPENCODE_MODEL, GEMINI_MODEL, DEEPSEEK, CLOUD }

/** 10 模型与推理 — Miuix-style segmented provider switcher + grouped preference rows + live connection test. */
@Composable
fun ModelsScreen(settings: LlmSettings, repo: SettingsRepository, onBack: () -> Unit, toast: (String) -> Unit) {
    var dialog by remember { mutableStateOf(KeyDialog.NONE) }
    var loadingModels by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val client = remember { LlmClient() }
    val search by repo.search.collectAsState()
    val configured = settings.isConfigured(settings.provider)

    Column(Modifier.fillMaxSize()) {
        SubTopBar("模型与推理", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            // 1. Active Model Status Strip
            WhiteCard(Modifier.fillMaxWidth(), shape = HeShapes.Card) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(settings.activeModelLabel, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            HeTag(
                                if (configured) "已就绪" else "未配置",
                                if (configured) TagKind.MINT else TagKind.AMBER,
                                height = 24.dp,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when (settings.provider) {
                                ProviderId.OPENCODE_GO -> "OpenCode Go"
                                ProviderId.GEMINI -> "Gemini API"
                                ProviderId.CUSTOM -> settings.customBaseUrl.ifBlank { "自定义 OpenAI 兼容" }
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HePillButton(
                        if (testing) "测试中…" else "测试连接",
                        {
                            if (!testing) {
                                testing = true
                                scope.launch {
                                    val start = System.currentTimeMillis()
                                    val res = runCatching {
                                        val ep = repo.activeEndpoint()
                                        client.stream(ep, null, listOf(ChatTurn(Role.USER, "ping")), UUID.randomUUID().toString()).collect()
                                    }
                                    val ms = System.currentTimeMillis() - start
                                    testing = false
                                    res.onSuccess { toast("连接正常 · ${ms}ms") }
                                        .onFailure { toast("连接失败：${it.message ?: it.javaClass.simpleName}") }
                                }
                            }
                        },
                        kind = ButtonKind.TONAL,
                        height = HeButton.Small,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // 2. Segmented Provider Switcher (Miuix style)
            ProviderSegmentedBar(
                selected = settings.provider,
                onSelect = { repo.setProvider(it) },
            )

            Spacer(Modifier.height(14.dp))

            // 3. Focused Provider Configuration Group
            AnimatedContent(
                targetState = settings.provider,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "provider",
            ) { provider ->
                when (provider) {
                    ProviderId.OPENCODE_GO -> WhiteCard(Modifier.fillMaxWidth(), shape = HeShapes.Card) {
                        Column {
                            SectionLabel("选择模型")
                            settings.openCodeModels.forEachIndexed { idx, m ->
                                if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 18.dp))
                                ModelSelectRow(
                                    title = m.label,
                                    subtitle = m.id,
                                    selected = settings.openCodeModel == m.id,
                                    onClick = { repo.setOpenCodeModel(m.id) },
                                )
                            }
                            if (settings.openCodeModels.none { it.id == settings.openCodeModel }) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 18.dp))
                                ModelSelectRow(
                                    title = settings.openCodeModel,
                                    subtitle = "自定义模型",
                                    selected = true,
                                    onClick = {},
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            ActionRow(
                                title = "OpenCode Go API Key",
                                value = if (settings.hasOpenCodeKey) "已保存" else if (settings.viaCloud) "Cloud 托管" else "未填写",
                                accentValue = !settings.hasOpenCodeKey && !settings.viaCloud,
                                onClick = { dialog = KeyDialog.OPENCODE },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 18.dp))
                            ActionRow(
                                title = if (loadingModels) "正在刷新模型列表…" else "刷新可用模型",
                                value = "${settings.openCodeModels.size} 个",
                                onClick = {
                                    if (!loadingModels) {
                                        loadingModels = true
                                        scope.launch {
                                            runCatching { client.fetchOpenCodeModels() }
                                                .onSuccess { list -> repo.setOpenCodeModels(list); toast("已更新 ${list.size} 个模型") }
                                                .onFailure { e -> toast("获取失败：${e.message ?: e.javaClass.simpleName}") }
                                            loadingModels = false
                                        }
                                    }
                                },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 18.dp))
                            ActionRow(
                                title = "手动输入模型 ID",
                                value = settings.openCodeModel,
                                onClick = { dialog = KeyDialog.OPENCODE_MODEL },
                            )
                        }
                    }

                    ProviderId.GEMINI -> WhiteCard(Modifier.fillMaxWidth(), shape = HeShapes.Card) {
                        Column {
                            SectionLabel("选择模型")
                            ModelCatalog.geminiDefaults.forEachIndexed { idx, m ->
                                if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 18.dp))
                                ModelSelectRow(
                                    title = m.label,
                                    subtitle = m.id,
                                    selected = settings.geminiModel == m.id,
                                    onClick = { repo.setGeminiModel(m.id) },
                                )
                            }
                            if (ModelCatalog.geminiDefaults.none { it.id == settings.geminiModel }) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 18.dp))
                                ModelSelectRow(
                                    title = settings.geminiModel,
                                    subtitle = "自定义模型",
                                    selected = true,
                                    onClick = {},
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            ActionRow(
                                title = "Gemini API Key",
                                value = if (settings.hasGeminiKey) "已保存" else if (settings.viaCloud) "Cloud 托管" else "未填写",
                                accentValue = !settings.hasGeminiKey && !settings.viaCloud,
                                onClick = { dialog = KeyDialog.GEMINI },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 18.dp))
                            ActionRow(
                                title = "手动输入模型 ID",
                                value = settings.geminiModel,
                                onClick = { dialog = KeyDialog.GEMINI_MODEL },
                            )
                        }
                    }

                    ProviderId.CUSTOM -> WhiteCard(Modifier.fillMaxWidth(), shape = HeShapes.Card) {
                        Column {
                            SectionLabel("OpenAI 兼容接口")
                            ActionRow(
                                title = "模型名称",
                                value = settings.customModel.ifBlank { "未填写" },
                                accentValue = settings.customModel.isBlank(),
                                onClick = { dialog = KeyDialog.CUSTOM },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 18.dp))
                            ActionRow(
                                title = "Base URL",
                                value = settings.customBaseUrl.ifBlank { "未填写" },
                                onClick = { dialog = KeyDialog.CUSTOM },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 18.dp))
                            ActionRow(
                                title = "API Key",
                                value = if (settings.hasCustomKey) "已保存" else "未填写",
                                accentValue = !settings.hasCustomKey,
                                onClick = { dialog = KeyDialog.CUSTOM },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // 4. Search & Cloud Relay Group
            Text("联网搜索与中转", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            Spacer(Modifier.height(10.dp))
            WhiteCard(Modifier.fillMaxWidth(), shape = HeShapes.Card) {
                Column {
                    ModelSelectRow(
                        title = "Exa",
                        subtitle = "默认公共搜索接口",
                        selected = search.provider == SearchProviderId.EXA_MCP,
                        onClick = { repo.setSearchProvider(SearchProviderId.EXA_MCP) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 18.dp))
                    ModelSelectRow(
                        title = "DeepSeek 搜索",
                        subtitle = if (search.cloudConfigured) "经 Helium Cloud 调用" else if (search.hasDeepSeekKey) "直连 api.deepseek.com" else "需配置 DeepSeek Key",
                        selected = search.provider == SearchProviderId.DEEPSEEK,
                        onClick = {
                            if (!search.deepSeekReady) dialog = KeyDialog.DEEPSEEK
                            repo.setSearchProvider(SearchProviderId.DEEPSEEK)
                        },
                    )
                    if (!search.cloudConfigured) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 18.dp))
                        ActionRow(
                            title = "DeepSeek API Key",
                            value = if (search.hasDeepSeekKey) "已保存" else "未填写",
                            onClick = { dialog = KeyDialog.DEEPSEEK },
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ActionRow(
                        title = "Helium Cloud",
                        value = if (search.cloudConfigured) "已连接" else "未连接",
                        onClick = { dialog = KeyDialog.CLOUD },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(HeIcons.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("所有凭据均经 Android Keystore 加密存储于本机", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    when (dialog) {
        KeyDialog.NONE -> Unit
        KeyDialog.OPENCODE -> SecretDialog(
            title = "OpenCode Go API Key",
            hint = "在 opencode.ai 控制台获取 Key，仅用于模型请求。",
            hasExisting = settings.hasOpenCodeKey,
            onDismiss = { dialog = KeyDialog.NONE },
            onSave = { repo.setApiKey(ProviderId.OPENCODE_GO, it); repo.setProvider(ProviderId.OPENCODE_GO); dialog = KeyDialog.NONE; toast("已保存") },
            onClear = { repo.setApiKey(ProviderId.OPENCODE_GO, null); dialog = KeyDialog.NONE },
        )
        KeyDialog.GEMINI -> SecretDialog(
            title = "Gemini API Key",
            hint = "在 Google AI Studio 创建 Key，仅用于 generativelanguage.googleapis.com。",
            hasExisting = settings.hasGeminiKey,
            onDismiss = { dialog = KeyDialog.NONE },
            onSave = { repo.setApiKey(ProviderId.GEMINI, it); repo.setProvider(ProviderId.GEMINI); dialog = KeyDialog.NONE; toast("已保存") },
            onClear = { repo.setApiKey(ProviderId.GEMINI, null); dialog = KeyDialog.NONE },
        )
        KeyDialog.OPENCODE_MODEL -> TextDialog("OpenCode Go 模型 ID", settings.openCodeModel, "glm-5.3") {
            if (it != null && it.isNotBlank()) {
                repo.setOpenCodeModel(it); repo.setProvider(ProviderId.OPENCODE_GO)
            }
            dialog = KeyDialog.NONE
        }
        KeyDialog.GEMINI_MODEL -> TextDialog("Gemini 模型 ID", settings.geminiModel, "gemini-2.5-pro") {
            if (it != null && it.isNotBlank()) repo.setGeminiModel(it)
            dialog = KeyDialog.NONE
        }
        KeyDialog.DEEPSEEK -> SecretDialog(
            title = "DeepSeek API Key",
            hint = "在 platform.deepseek.com 创建，用于搜索与推理。",
            hasExisting = search.hasDeepSeekKey,
            onDismiss = { dialog = KeyDialog.NONE },
            onSave = { repo.setDeepSeekKey(it); repo.setSearchProvider(SearchProviderId.DEEPSEEK); dialog = KeyDialog.NONE; toast("已保存") },
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
private fun ProviderSegmentedBar(selected: ProviderId, onSelect: (ProviderId) -> Unit) {
    val items = listOf(
        ProviderId.OPENCODE_GO to "OpenCode Go",
        ProviderId.GEMINI to "Gemini",
        ProviderId.CUSTOM to "自定义",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(HeShapes.Pill)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (id, label) ->
            val active = id == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(HeShapes.Pill)
                    .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(id) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        title,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun ModelSelectRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SelectMark(selected)
    }
}

@Composable
private fun ActionRow(title: String, value: String, accentValue: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(
            value,
            fontSize = 13.sp,
            color = if (accentValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Icon(HeIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SelectMark(selected: Boolean) {
    if (selected) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Icon(HeIcons.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    } else {
        Box(Modifier.size(22.dp).clip(CircleShape).border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape))
    }
}

@Composable
private fun SecretDialog(title: String, hint: String, hasExisting: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit, onClear: () -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(hint, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    placeholder = { Text(if (hasExisting) "已保存，输入新 Key 替换" else "粘贴 API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = value.isNotBlank()) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (hasExisting) TextButton(onClick = onClear) { Text("清除", color = HeColors.CoralInk) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun TextDialog(title: String, initial: String, placeholder: String, onDone: (String?) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, placeholder = { Text(placeholder) }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onDone(value.trim()) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("取消") } },
    )
}

@Composable
private fun CustomDialog(settings: LlmSettings, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var base by remember { mutableStateOf(settings.customBaseUrl) }
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(settings.customModel) }
    val presets = listOf(
        Triple("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
        Triple("R1 推理", "https://api.deepseek.com/v1", "deepseek-reasoner"),
        Triple("OpenAI", "https://api.openai.com/v1", "gpt-4.1"),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义 OpenAI 兼容") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { (label, pBase, pModel) ->
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { base = pBase; model = pModel }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(base, { base = it }, singleLine = true, label = { Text("Base URL") }, placeholder = { Text("https://api.deepseek.com/v1") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    key, { key = it }, singleLine = true, label = { Text("API Key") },
                    placeholder = { Text(if (settings.hasCustomKey) "已保存，留空不修改" else "sk-…") },
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
                Text("填写 Cloudflare Worker 地址与设备令牌，留空地址即断开。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(base, { base = it }, singleLine = true, label = { Text("Worker 地址") }, placeholder = { Text("https://cloud.qigai.tech") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    token, { token = it }, singleLine = true,
                    label = { Text(if (hasToken) "设备令牌 · 留空保持不变" else "设备令牌") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton({ onSave(base.trim(), token.trim()) }) { Text("保存") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}
