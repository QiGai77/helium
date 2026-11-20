package tech.qigai.helium.agent.tools

import org.json.JSONObject
import tech.qigai.helium.agent.Risk

/** Opens the phone's SMS app with recipient and body filled in. Android: ACTION_SENDTO smsto:, no SEND_SMS. */
fun interface SmsComposer {
    /** [number] null = let the user pick the recipient in the SMS app. False when no SMS app could be opened. */
    suspend fun compose(number: String?, body: String): Boolean
}

object PhoneNumbers {
    private val allowed = Regex("^\\+?\\d{3,15}$")

    /** "138 0013-8000" → "13800138000"; null when it isn't a phone number (e.g. a contact name). */
    fun normalize(raw: String): String? {
        val s = raw.trim().replace(Regex("[\\s\\-().]"), "")
        return s.takeIf { allowed.matches(it) }
    }
}

/**
 * HIGH risk, always "仅这一次": the approval card shows the recipient and the full text. After approval the SMS app
 * opens prefilled and the user taps send there, so nothing leaves the phone without two explicit taps.
 */
class SmsSendTool(private val composer: SmsComposer) : Tool {
    override val spec = ToolSpec(
        "sms_send",
        "给某人发短信：打开手机短信应用并填好收件人和内容（需要用户先在审批卡上批准，再在短信应用里点发送）。" +
            "to 填手机号；只知道名字时填名字并把名字也放进 name，用户会在短信应用里选联系人。",
        params(
            Triple("to", "string", "收件人手机号，或联系人名字"),
            Triple("body", "string", "短信全文"),
            Triple("name", "string", "收件人称呼（可选），例如“张三”"),
            required = listOf("to", "body"),
        ),
    )

    override val exfiltrates get() = true
    override fun risk(args: JSONObject) = Risk.HIGH

    private fun name(args: JSONObject) = args.optString("name").trim().ifEmpty {
        args.optString("to").trim().takeIf { PhoneNumbers.normalize(it) == null }.orEmpty()
    }

    override fun target(args: JSONObject): String {
        val n = PhoneNumbers.normalize(args.optString("to"))
        return "sms:" + (n ?: args.optString("to").trim().ifEmpty { "?" })
    }

    override fun title(args: JSONObject): String {
        val who = name(args).ifEmpty { args.optString("to").trim() }
        return "给 $who 发短信"
    }

    override fun detail(args: JSONObject): String {
        val number = PhoneNumbers.normalize(args.optString("to"))
        val who = name(args)
        return buildString {
            append("收件人：")
            append(
                when {
                    who.isNotEmpty() && number != null -> "$who（$number）"
                    number != null -> number
                    else -> "$who（没有号码，打开短信应用后由你选择联系人）"
                },
            )
            append("\n\n全文：\n")
            append(args.optString("body"))
        }
    }

    override suspend fun run(args: JSONObject, ctx: ToolContext): ToolOutput {
        val body = args.optString("body").trim()
        if (body.isEmpty()) return ToolOutput("短信内容不能为空", isError = true)
        val number = PhoneNumbers.normalize(args.optString("to"))
        if (!composer.compose(number, body)) return ToolOutput("没有能打开的短信应用。", isError = true)
        return ToolOutput(
            if (number != null) "已打开短信应用，收件人 $number 和内容都已填好；用户在短信应用里点发送后才会发出。"
            else "已打开短信应用并填好内容；用户需要在短信应用里选择「${name(args)}」并点发送。",
        )
    }
}
