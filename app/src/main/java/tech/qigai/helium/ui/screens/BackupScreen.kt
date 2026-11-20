package tech.qigai.helium.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.qigai.helium.crash.CrashEntry
import tech.qigai.helium.crash.CrashLogStore
import tech.qigai.helium.data.backup.BackupCrypto
import tech.qigai.helium.data.backup.BackupException
import tech.qigai.helium.data.backup.BackupManager
import tech.qigai.helium.ui.components.ButtonKind
import tech.qigai.helium.ui.components.HePillButton
import tech.qigai.helium.ui.components.WhiteCard
import tech.qigai.helium.ui.theme.HeButton
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeShapes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * M7 备份与恢复 + 崩溃日志（都在“我的”里进）。
 *
 * 加密方式：备份口令派生密钥（PBKDF2 210,000 次 + AES-256-GCM），不用
 * Android Keystore——Keystore 密钥随卸载失效，卸载后重装就解不开备份，
 * 正好违背 M7 验收 2。口令丢了无法恢复，界面上写明了。
 *
 * 备份不含 API Key / 云端 token（它们在 Keystore 保险箱里，卸载即丢，
 * 备份文件里也绝不写）。恢复成功后界面会明确提示去「模型与推理」重填，
 * 恢复后请重启 App（Room 缓存才与新数据一致）。
 */
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    backup: BackupManager,
    crashes: CrashLogStore,
    onOpenModels: () -> Unit,
    toast: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportPass by remember { mutableStateOf("") }
    var importPass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<BackupManager.BackupResult?>(null) }
    var entries by remember { mutableStateOf(emptyList<CrashEntry>()) }
    var viewing by remember { mutableStateOf<CrashEntry?>(null) }
    var viewingText by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var pendingCrashExport by remember { mutableStateOf<String?>(null) }

    fun refreshCrashes() {
        scope.launch { entries = withContext(Dispatchers.IO) { crashes.list() } }
    }
    LaunchedEffect(Unit) { refreshCrashes() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val pass = exportPass
        busy = true
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { backup.exportTo(it, pass.toCharArray()) }
                }
            }
            busy = false
            if (ok.isSuccess) toast("备份已导出，请妥善保管口令") else toast("导出失败：${ok.exceptionOrNull()?.message ?: "未知错误"}")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val pass = importPass
        busy = true
        scope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { backup.importFrom(it, pass.toCharArray()) }
                }
            }
            busy = false
            r.onSuccess { result = it; refreshCrashes() }
            r.onFailure { e ->
                toast(if (e is BackupException) e.message ?: "恢复失败" else "恢复失败：${e.message ?: "未知错误"}")
            }
        }
    }
    val crashExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val name = pendingCrashExport ?: return@rememberLauncherForActivityResult
        pendingCrashExport = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    val text = crashes.read(name) ?: error("日志已被删除")
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
            }
            toast(if (ok.isSuccess) "崩溃日志已导出" else "导出失败")
        }
    }

    Column(Modifier.fillMaxSize()) {
        SubTopBar("备份与恢复", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(8.dp))
            Text(
                "备份包含会话、记忆、专长、授权、审计、定时任务和速览数据，以及非敏感偏好（提供方、模型名、Worker 地址）。不含 API Key 与云端 token——恢复后需要重新填写。",
                fontSize = 14.sp, lineHeight = 21.sp, color = HeColors.Ink2,
            )
            Spacer(Modifier.height(20.dp))

            SectionTitle("导出备份")
            WhiteCard(Modifier.fillMaxWidth(), shape = HeShapes.Card) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = exportPass, onValueChange = { exportPass = it },
                        label = { Text("备份口令（至少 8 位）") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("口令只用来加密这个文件，Helium 不保存它。忘记口令 = 备份永远打不开。", fontSize = 13.sp, color = HeColors.Ink2)
                    Spacer(Modifier.height(12.dp))
                    HePillButton("导出备份文件", {
                        val err = BackupCrypto.validatePassphrase(exportPass.toCharArray())
                        if (err != null) { toast(err); return@HePillButton }
                        exportLauncher.launch(backup.exportFileName())
                    }, Modifier.fillMaxWidth(), enabled = !busy)
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("恢复备份")
            WhiteCard(Modifier.fillMaxWidth(), shape = HeShapes.Card) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = importPass, onValueChange = { importPass = it },
                        label = { Text("备份口令") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("恢复会替换本机全部数据（先备份当前数据再恢复更稳妥）。口令不对或文件损坏时，本机数据不动。", fontSize = 13.sp, color = HeColors.Ink2)
                    Spacer(Modifier.height(12.dp))
                    HePillButton("选择备份文件并恢复", {
                        val err = BackupCrypto.validatePassphrase(importPass.toCharArray())
                        if (err != null) { toast(err); return@HePillButton }
                        importLauncher.launch(arrayOf("*/*"))
                    }, Modifier.fillMaxWidth(), kind = ButtonKind.TONAL, enabled = !busy)
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("崩溃日志（仅本机，不上传）")
            WhiteCard(Modifier.fillMaxWidth(), shape = HeShapes.Card) {
                Column(Modifier.padding(16.dp)) {
                    if (entries.isEmpty()) {
                        Text("没有崩溃记录。App 崩溃时会自动在这里留一份日志，方便你发给开发者排查。", fontSize = 14.sp, lineHeight = 21.sp, color = HeColors.Ink2)
                    } else {
                        entries.forEach { e ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .clickable {
                                        viewing = e
                                        scope.launch { viewingText = withContext(Dispatchers.IO) { crashes.read(e.fileName) } }
                                    }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink)
                                    Text(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(e.at)), fontSize = 12.sp, color = HeColors.Ink3)
                                }
                                TextButton(onClick = { pendingCrashExport = e.fileName; crashExportLauncher.launch(e.fileName) }) { Text("导出", fontSize = 13.sp) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HePillButton("清空全部崩溃日志", { confirmClear = true }, Modifier.fillMaxWidth(), kind = ButtonKind.OUTLINE, height = HeButton.Medium)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    result?.let { r ->
        AlertDialog(
            onDismissRequest = { result = null },
            title = { Text("恢复完成") },
            text = {
                Text(
                    "已恢复 ${r.totalRows} 条记录（来自 v${r.fromVersion} 的备份，" +
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(r.exportedAt)) + "导出）。\n\n" +
                        "API Key 与云端 token 不在备份里，请到「模型与推理」重新填写，否则无法对话。\n\n" +
                        "请重启 App 一次，让数据恢复完全生效。",
                    fontSize = 14.sp, lineHeight = 21.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { result = null; onOpenModels() }) { Text("去重填 API Key") }
            },
            dismissButton = { TextButton(onClick = { result = null }) { Text("稍后") } },
        )
    }
    viewing?.let { e ->
        AlertDialog(
            onDismissRequest = { viewing = null; viewingText = null },
            title = { Text(e.title) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(viewingText ?: "读取中…", fontSize = 12.sp, lineHeight = 17.sp, color = HeColors.Ink2)
                }
            },
            confirmButton = { TextButton(onClick = { viewing = null; viewingText = null }) { Text("好的") } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空崩溃日志？") },
            text = { Text("全部 ${entries.size} 条崩溃日志会被删除，不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { withContext(Dispatchers.IO) { crashes.clear() }; refreshCrashes(); toast("已清空崩溃日志") }
                }) { Text("清空", color = HeColors.CoralInk) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HeColors.Ink2, modifier = Modifier.padding(start = 4.dp))
    Spacer(Modifier.height(10.dp))
}
