package tech.qigai.helium.data.device

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.qigai.helium.agent.tools.SmsComposer

/**
 * ACTION_SENDTO smsto:<number> with sms_body: the SMS app sends, Helium never holds SEND_SMS. A background run
 * (approved from a notification) can't start an activity, so it gets a tap-to-open notification instead.
 */
class AndroidSms(
    private val context: Context,
    private val foreground: () -> Boolean,
    private val notifyOpen: (title: String, body: String, intent: Intent) -> Boolean,
) : SmsComposer {
    override suspend fun compose(number: String?, body: String): Boolean = withContext(Dispatchers.Main) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number.orEmpty())))
            .putExtra("sms_body", body)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) return@withContext false
        if (!foreground()) return@withContext notifyOpen("短信已写好，点这里打开发送", (number ?: "选择收件人") + "：" + body, intent)
        try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}
