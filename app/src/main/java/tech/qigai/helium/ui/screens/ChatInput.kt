package tech.qigai.helium.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.qigai.helium.data.media.VoiceInput
import tech.qigai.helium.ui.components.ButtonKind
import tech.qigai.helium.ui.components.Composer
import tech.qigai.helium.ui.components.HePillButton
import tech.qigai.helium.ui.icons.HeIcons
import tech.qigai.helium.ui.theme.HeButton
import tech.qigai.helium.ui.theme.HeColors
import java.io.File

/** Voice + attachment state shared by the Home and Chat composers. */
@Stable
class ChatInputState {
    var chooser by mutableStateOf(false)
    var listening by mutableStateOf(false)
    var partial by mutableStateOf("")
}

/**
 * The composer with everything around it: offline/send error banner (draft kept, one-tap 重试), 编辑中 banner,
 * attached image thumbnails, live speech text, the 拍照/相册 chooser and hold-to-talk voice input.
 */
@Composable
fun ChatInputBar(
    vm: ChatViewModel,
    onSend: () -> Unit,
    toast: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "问 Helium 任何事…",
    running: Boolean = false,
    onStop: () -> Unit = {},
    /** 技能 picker → 管理专长 / 新建专长. */
    onManageSkills: () -> Unit = {},
) {
    val context = LocalContext.current
    val st = remember { ChatInputState() }
    var skillPicker by remember { mutableStateOf(false) }
    val voice = remember { VoiceInput(context.applicationContext) }
    DisposableEffect(voice) { onDispose { voice.release() } }
    var holding by remember { mutableStateOf(false) }

    // Survives the activity being recreated (or killed) while the camera app is in front.
    var cameraTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.addImage(uri, fromCamera = false, onFail = toast)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val target = cameraTarget?.let(Uri::parse)
        cameraTarget = null
        if (ok && target != null) vm.addImage(target, fromCamera = true, onFail = toast)
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        toast(if (granted) "已允许麦克风，按住麦克风说话" else "没有麦克风权限，无法语音输入")
    }

    fun onMicHold(pressed: Boolean) {
        if (pressed) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
                return
            }
            if (!voice.available) {
                toast("这台手机没有可用的语音识别服务")
                return
            }
            holding = true
            st.listening = true
            st.partial = ""
            voice.start(
                onPartial = { st.partial = it },
                onFinal = { text ->
                    st.listening = false
                    st.partial = ""
                    if (text.isNotBlank()) {
                        val d = vm.draft.value
                        vm.draft.value = if (d.isBlank()) text else "$d $text"
                    }
                },
                onError = { msg ->
                    st.listening = false
                    st.partial = ""
                    toast(msg)
                },
            )
        } else if (holding) {
            holding = false
            voice.stop()
        }
    }

    Column(modifier.fillMaxWidth()) {
        vm.sendError.value?.let { err ->
            Banner(err, tint = HeColors.CoralTint, ink = HeColors.CoralInk) {
                HePillButton("重试", { vm.send() }, kind = ButtonKind.WHITE, height = HeButton.Small, icon = HeIcons.Refresh)
            }
            Spacer(Modifier.height(8.dp))
        }
        if (vm.editingAt.value != null) {
            Banner("正在编辑这条消息，发送后会替换它和之后的回复", tint = HeColors.Mist2, ink = HeColors.Ink2) {
                TextButton(onClick = vm::cancelEdit) { Text("取消", color = HeColors.Ink2, fontSize = 13.sp) }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (vm.attachments.isNotEmpty() || vm.importing.value) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
                vm.attachments.forEach { ref ->
                    Box {
                        Thumb(vm.imageFile(ref), 64.dp)
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp).clip(CircleShape)
                                .background(HeColors.Ink.copy(alpha = 0.7f)).clickable { vm.removeImage(ref) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(HeIcons.Close, "移除图片", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(12.dp)) }
                    }
                }
                if (vm.importing.value) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(HeColors.Mist2), contentAlignment = Alignment.Center) {
                        Text("处理中", fontSize = 11.sp, color = HeColors.Ink3)
                    }
                }
            }
        }
        if (st.listening && st.partial.isNotBlank()) {
            Text(
                st.partial, fontSize = 14.sp, color = HeColors.Accent, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
            )
        }
        Composer(
            value = vm.draft.value,
            onValueChange = { vm.draft.value = it; if (vm.sendError.value != null && it.isBlank()) vm.sendError.value = null },
            onSend = onSend,
            running = running,
            onStop = onStop,
            placeholder = placeholder,
            onAttach = { st.chooser = true },
            onSkills = { skillPicker = true },
            sendEnabled = vm.draft.value.isNotBlank() || vm.attachments.isNotEmpty(),
            onMicHold = ::onMicHold,
            listening = st.listening,
        )
    }

    if (skillPicker) {
        val skills by vm.skills.collectAsState()
        SkillPickerDialog(
            skills,
            onPick = { vm.useSkill(it); skillPicker = false },
            onManage = { skillPicker = false; onManageSkills() },
            onDismiss = { skillPicker = false },
        )
    }

    if (st.chooser) {
        AlertDialog(
            onDismissRequest = { st.chooser = false },
            confirmButton = { TextButton(onClick = { st.chooser = false }) { Text("取消") } },
            title = { Text("添加图片") },
            text = {
                Column {
                    ChooserRow("拍照") {
                        st.chooser = false
                        val uri = vm.newCameraUri()
                        cameraTarget = uri.toString()
                        runCatching { camera.launch(uri) }.onFailure { toast("没有找到相机应用") }
                    }
                    ChooserRow("从相册选择") {
                        st.chooser = false
                        pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
            },
        )
    }
}

@Composable
private fun ChooserRow(label: String, onClick: () -> Unit) {
    Text(
        label, fontSize = 16.sp, color = HeColors.Ink,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 4.dp),
    )
}

@Composable
private fun Banner(text: String, tint: androidx.compose.ui.graphics.Color, ink: androidx.compose.ui.graphics.Color, action: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(tint).padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = ink, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        action()
    }
}

/** Decodes a small preview of an attachment off the main thread. */
@Composable
fun rememberThumb(file: File, maxPx: Int): ImageBitmap? {
    val bmp by produceState<ImageBitmap?>(null, file.path, maxPx) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, b)
                var s = 1
                while (maxOf(b.outWidth, b.outHeight) / (s * 2) >= maxPx) s *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
            }.getOrNull()
        }
    }
    return bmp
}

@Composable
fun Thumb(file: File, size: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val bmp = rememberThumb(file, 320)
    val m = modifier.size(size).clip(RoundedCornerShape(14.dp)).background(HeColors.Mist2)
    Box(if (onClick != null) m.clickable(onClick = onClick) else m) {
        if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
    }
}

/** Full-width preview of one attachment. */
@Composable
fun ImagePreviewDialog(file: File, onDismiss: () -> Unit) {
    val bmp = rememberThumb(file, 1568)
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().heightIn(min = 120.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onDismiss)) {
            if (bmp != null) Image(bmp, "图片", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
        }
    }
}
