package tech.qigai.helium.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.qigai.helium.BuildConfig
import tech.qigai.helium.data.llm.ProviderId
import tech.qigai.helium.data.sample.Sources
import tech.qigai.helium.data.settings.LlmSettings
import tech.qigai.helium.ui.components.ButtonKind
import tech.qigai.helium.ui.components.GlassIcon
import tech.qigai.helium.ui.components.HePillButton
import tech.qigai.helium.ui.components.HeTag
import tech.qigai.helium.ui.components.HeroSurface
import tech.qigai.helium.ui.components.SampleBadge
import tech.qigai.helium.ui.components.TagKind
import tech.qigai.helium.ui.components.WhiteCard
import tech.qigai.helium.ui.icons.HeIcons
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeShapes

/** 09 我的 — profile hero + 账户 / 能力 / 记忆与文件 / 安全 / 通用. */
@Composable
fun MeScreen(
    settings: LlmSettings,
    vaultCount: Int,
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenApproval: () -> Unit,
    toast: (String) -> Unit,
) {
    var about by remember { mutableStateOf(false) }
    val soon = { name: String -> { toast("「$name」即将支持") } }
    val providerLine = when (settings.provider) {
        ProviderId.OPENCODE_GO -> "默认模型 OpenCode Go · ${settings.activeModelLabel}"
        ProviderId.GEMINI -> "Gemini API · ${settings.activeModelLabel}"
        ProviderId.CUSTOM -> "自带 API Key · ${settings.activeModelLabel}"
    }
    Column(Modifier.fillMaxSize()) {
        SubTopBar("我的", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(8.dp))
            HeroSurface(Modifier.fillMaxWidth().height(120.dp), shape = HeShapes.Panel) {
                Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(HeIcons.Person, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("林夏", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            SampleBadge(onHero = true)
                        }
                        Text("linxia@example.com", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        HeTag("Helium Pro", TagKind.GLASS, dot = false, height = 26.dp, fontSize = 12)
                    }
                }
            }
            Group("账户") {
                SettingRow(HeIcons.Person, "个人资料", "姓名 · 头像 · 邮箱", onClick = soon("个人资料"))
                SettingRow(HeIcons.Link, "Google 账号", "连接 Gmail · 日历 · 云端硬盘（连接器，不用于模型登录）", trailingTag = "未连接", onClick = soon("Google 账号连接"))
                SettingRow(HeIcons.Cpu, "模型与推理", providerLine, sub2 = "可选 Gemini API（需 API Key）· 自带 OpenAI 兼容", onClick = onOpenModels)
            }
            Group("能力") {
                SettingRow(HeIcons.Plug, "连接器", value = "6 个", onClick = soon("连接器"))
                SettingRow(HeIcons.Sparkle, "技能", value = "${Sources.memory.skills().size} 个", onClick = onOpenMemory)
                SettingRow(HeIcons.Server, "MCP", value = "2 个服务器", onClick = soon("MCP"))
            }
            Group("记忆与文件") {
                SettingRow(HeIcons.Memory, "记忆", value = "${Sources.memory.memoryCount()} 条", onClick = onOpenMemory)
                SettingRow(HeIcons.File, "系统文件", "偏好、规则与人设文档", onClick = soon("系统文件"))
                SettingRow(HeIcons.Folder, "项目空间", value = "3 个", onClick = soon("项目空间"))
            }
            Group("安全") {
                SettingRow(HeIcons.Shield, "审批规则", "付款、发消息前需你确认", onClick = onOpenApproval)
                SettingRow(HeIcons.List, "权限与审计", "近 7 天 42 次操作（示例）", onClick = soon("权限与审计"))
                SettingRow(HeIcons.Key, "凭据保险箱", value = "$vaultCount 项", onClick = onOpenModels)
            }
            Group("通用") {
                SettingRow(HeIcons.Bell, "通知", value = "开", onClick = soon("通知"))
                SettingRow(HeIcons.Palette, "外观", value = "浅色", onClick = soon("外观"))
                SettingRow(HeIcons.Globe, "语言", value = "简体中文", onClick = soon("语言"))
                SettingRow(HeIcons.Info, "关于", value = "v${BuildConfig.VERSION_NAME}", onClick = { about = true })
            }
            Spacer(Modifier.height(24.dp))
            HePillButton("退出登录", { toast("v0.1 无账号体系，数据只保存在本机") }, Modifier.fillMaxWidth(), ButtonKind.OUTLINE)
            Spacer(Modifier.height(32.dp))
        }
    }
    if (about) AboutDialog { about = false }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink2, modifier = Modifier.padding(start = 4.dp))
    Spacer(Modifier.height(10.dp))
    WhiteCard(Modifier.fillMaxWidth(), shape = HeShapes.Card) {
        Column { content() }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    sub: String? = null,
    sub2: String? = null,
    value: String? = null,
    trailingTag: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIcon(icon, size = 36.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink)
            if (sub != null) Text(sub, fontSize = 13.sp, color = HeColors.Ink2)
            if (sub2 != null) Text(sub2, fontSize = 12.sp, color = HeColors.Ink3)
        }
        if (trailingTag != null) {
            HeTag(trailingTag, TagKind.NEUTRAL, height = 24.dp)
            Spacer(Modifier.width(8.dp))
        }
        if (value != null) {
            Text(value, fontSize = 14.sp, color = HeColors.Ink2)
            Spacer(Modifier.width(6.dp))
        }
        Icon(HeIcons.ChevronRight, null, tint = HeColors.Ink3, modifier = Modifier.size(18.dp))
    }
    HorizontalDivider(color = HeColors.Hair, modifier = Modifier.padding(start = 66.dp))
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val licenses = remember {
        listOf("OFL-NotoSansSC.txt", "OFL-GoogleSansFlex.txt").joinToString("\n\n————————\n\n") { name ->
            runCatching { ctx.assets.open("licenses/$name").bufferedReader().use { it.readText() } }.getOrDefault("")
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("好的") } },
        title = { Text("Helium v${BuildConfig.VERSION_NAME}") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("个人 AI Agent · MVP。除对话外，页面内容均为示例数据。\n\n开源许可：MIT。字体 Noto Sans SC 与 Google Sans Flex 采用 SIL Open Font License 1.1：", fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Text(licenses, fontSize = 11.sp, color = HeColors.Ink2, lineHeight = 15.sp)
            }
        },
        containerColor = Color.White,
    )
}
