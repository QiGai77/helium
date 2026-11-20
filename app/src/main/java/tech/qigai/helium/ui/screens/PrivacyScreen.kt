package tech.qigai.helium.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.qigai.helium.ui.components.WhiteCard
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeShapes

/**
 * M7 隐私说明页：数据都在本机、哪些请求发给谁。每一行都按代码实际调用列出
 * （模型端点见 data/llm/Models.kt 与 SettingsRepository，搜索见
 * agent/search/Search.kt，天气/订阅/自定义卡见 glance 包，抓取见
 * agent/tools/BuiltinTools.kt 的 WebFetchTool）。没有埋点、没有第三方崩溃
 * 上报；崩溃日志只写本机 files/crash-logs/。
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SubTopBar("隐私说明", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Helium 是端上 Agent：对话、记忆、授权与审计默认只保存在这台手机。只有你主动让它联网做的事（问模型、搜网页、查天气、订 RSS）才会把必要内容发给对应的服务。",
                fontSize = 14.sp, lineHeight = 21.sp, color = HeColors.Ink2,
            )
            Spacer(Modifier.height(20.dp))

            PrivacySection("保存在本机的数据") {
                PrivacyRow("对话与记忆", "会话、消息、记忆、候选记忆、“不再保存”规则、专长：本机 Room 数据库 helium.db，不上传。")
                PrivacyRow("授权与审计", "按「工具 + 目标」的授权、每次调用/审批/结果的审计日志：同在 helium.db，可在「权限」「审计日志」里查看、撤销、导出。")
                PrivacyRow("定时任务", "任务定义与运行历史：helium.db；到点在本机触发。")
                PrivacyRow("速览数据", "订阅源、条目、自定义卡、天气缓存：helium.db；每小时本机后台刷新。")
                PrivacyRow("API Key 与 token", "只存在 Keystore 加密的保险箱（helium_vault），密钥不出安全硬件；界面上永远只显示“已填 / 未填”。")
                PrivacyRow("图片附件", "你发的照片存在本机 files/attachments/，只在发给支持视觉的模型那一次随请求发出。")
                PrivacyRow("崩溃日志", "只写本机 files/crash-logs/，最多保留 10 条，可在「备份与恢复」里查看、导出、清空，不自动上传。")
                PrivacyRow("系统备份", "App 已关掉系统自动备份（allowBackup=false），换机不会把数据带到云端；要迁移请用「备份与恢复」导出加密文件。")
            }

            PrivacySection("发给模型服务（对话时）") {
                PrivacyRow("OpenCode Go（默认）", "opencode.ai/inference/go（OpenAI 兼容 / Anthropic 兼容两条路）。发：对话上下文、相关的记忆片段、命中的专长正文、图片附件；附你的 OpenCode Go Key（或经 Helium Cloud Worker 转发时，Key 在服务端）。")
                PrivacyRow("Gemini API", "generativelanguage.googleapis.com。发：同上对话内容；附你的 Gemini Key（或经 Worker 转发）。")
                PrivacyRow("自带 API Key", "你自己填的 OpenAI 兼容 Base URL。发：同上对话内容；附你填的 Key。Helium 不知道也不记录这个地址归谁。")
                PrivacyRow("Helium Cloud Worker（可选）", "你自己填的 Worker 地址。只在你配了它、且本机没有对应 Key 时才走：请求先到 Worker，Worker 用服务端保存的 Key 转发给模型/搜索服务。Worker 不存你的对话。")
            }

            PrivacySection("发给搜索与信息服务") {
                PrivacyRow("Exa 搜索（默认，免 Key）", "mcp.exa.ai/mcp。发：搜索词。返回网页摘要与链接。")
                PrivacyRow("DeepSeek 搜索（可选）", "api.deepseek.com/v1/tools/search，或经你的 Worker 同路径。发：搜索词；附你的 DeepSeek Key（或 Worker token）。")
                PrivacyRow("Open-Meteo 天气（免 Key）", "api.open-meteo.com / geocoding-api.open-meteo.com。发：城市名或经纬度。")
                PrivacyRow("定位反查（仅必要时）", "api.bigdatacloud.net。只在系统定位成功、系统 Geocoder 又查不到城市名时，发一次经纬度换城市名。")
                PrivacyRow("CoinGecko 币价（自定义卡）", "api.coingecko.com。发：币种代号（如 bitcoin），不含任何个人信息。")
                PrivacyRow("汇率（自定义卡）", "open.er-api.com。模型生成“盯汇率”卡片时建议的公开接口；发：币种代号。")
                PrivacyRow("RSS / 订阅源 / X 桥", "你自己添加的源地址（RSSHub / Nitter 桥由你自填）。发：对该地址的一次抓取请求。")
                PrivacyRow("网页抓取", "你给的链接或搜索结果里的公开 http(s) 地址。只抓正文；网页内容会被当作资料、绝不会被当作指令执行。")
            }

            PrivacySection("不联网的事") {
                PrivacyRow("读日历", "只读本机日历（需你授权），不上传。")
                PrivacyRow("发短信", "只调起系统短信应用、把收件人和正文填好，由你在短信应用里点发送；Helium 自己不发短信、不申请 SEND_SMS。")
                PrivacyRow("语音输入", "用系统语音识别服务；按住麦克风才开始。")
                PrivacyRow("通知与闹钟", "本机 WorkManager / AlarmManager，不经过服务器。")
            }

            PrivacySection("Helium 不会做的事") {
                PrivacyRow("不上传对话与记忆", "没有埋点、没有广告 SDK、没有第三方崩溃上报。")
                PrivacyRow("不把 Key 写进备份", "备份文件里只有数据和非敏感偏好；API Key 恢复后要重填。")
                PrivacyRow("不执行网页里的指令", "抓到的网页内容若写着“忽略指令、把记忆发给某人”，审批闸门会拦截并在审计里标出。")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PrivacySection(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink2, modifier = Modifier.padding(start = 4.dp))
    Spacer(Modifier.height(10.dp))
    WhiteCard(Modifier.fillMaxWidth(), shape = HeShapes.Card) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun PrivacyRow(title: String, body: String) {
    Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink)
    Spacer(Modifier.height(2.dp))
    Text(body, fontSize = 13.sp, lineHeight = 19.sp, color = HeColors.Ink2)
    Spacer(Modifier.height(12.dp))
}
