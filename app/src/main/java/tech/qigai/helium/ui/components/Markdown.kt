package tech.qigai.helium.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import tech.qigai.helium.ui.theme.HeColors
import tech.qigai.helium.ui.theme.HeShapes

/** Minimal Markdown for assistant replies: headings, lists, quotes, fenced code, **bold**, *italic*, `code`, [links](…). */
private sealed interface MdBlock {
    data class Para(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val marker: String, val text: String, val indent: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val lang: String, val text: String) : MdBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

private fun parseTableRow(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

private fun isTableSeparator(line: String): Boolean {
    val cells = parseTableRow(line)
    return cells.isNotEmpty() && cells.all { it.matches(Regex("^:?-{3,}:?$")) }
}

private fun parseBlocks(src: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val lines = src.replace("\r\n", "\n").split('\n')
    var i = 0
    val para = StringBuilder()
    fun flush() {
        if (para.isNotBlank()) out += MdBlock.Para(para.toString().trimEnd())
        para.clear()
    }
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("```") -> {
                flush()
                val lang = trimmed.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    code.append(lines[i]).append('\n')
                    i++
                }
                out += MdBlock.Code(lang, code.toString().trimEnd('\n'))
            }
            trimmed.startsWith("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1]) -> {
                flush()
                val headers = parseTableRow(trimmed)
                i += 2
                val rows = ArrayList<List<String>>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    rows += parseTableRow(lines[i].trimStart())
                    i++
                }
                out += MdBlock.Table(headers, rows)
                i--
            }
            trimmed.isEmpty() -> flush()
            trimmed.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$")) -> {
                flush(); out += MdBlock.Rule
            }
            trimmed.startsWith("#") -> {
                flush()
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                out += MdBlock.Heading(level, trimmed.drop(level).trim())
            }
            trimmed.startsWith(">") -> {
                flush(); out += MdBlock.Quote(trimmed.removePrefix(">").trim())
            }
            Regex("^([-*+])\\s+.*").matches(trimmed) -> {
                flush()
                out += MdBlock.Bullet("•", trimmed.substring(1).trim(), (line.length - trimmed.length) / 2)
            }
            Regex("^(\\d+)[.)]\\s+.*").matches(trimmed) -> {
                flush()
                val num = trimmed.takeWhile { it.isDigit() }
                out += MdBlock.Bullet("$num.", trimmed.drop(num.length + 1).trim(), (line.length - trimmed.length) / 2)
            }
            else -> {
                if (para.isNotEmpty()) para.append('\n')
                para.append(line)
            }
        }
        i++
    }
    flush()
    return out
}

private val inlineRegex = Regex("""(\*\*(.+?)\*\*)|(`([^`]+)`)|(\[([^\]]+)]\(([^)]+)\))|(\*([^*\s][^*]*?)\*)""")

fun inlineMarkdown(text: String, codeBg: Color = HeColors.Mist2): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in inlineRegex.findAll(text)) {
        append(text.substring(last, m.range.first))
        val g = m.groupValues
        when {
            g[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(g[2]) }
            g[3].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, fontSize = 14.sp)) { append(g[4]) }
            g[5].isNotEmpty() -> withStyle(SpanStyle(color = HeColors.Accent)) { append(g[6]) }
            g[8].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[9]) }
        }
        last = m.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = HeColors.Ink) {
    val blocks = remember(text) { parseBlocks(text) }
    val body = MaterialTheme.typography.bodyLarge.copy(color = color, lineHeight = 26.sp)
    SelectionContainer(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            blocks.forEach { b ->
                when (b) {
                    is MdBlock.Para -> Text(inlineMarkdown(b.text), style = body)
                    is MdBlock.Heading -> Text(
                        inlineMarkdown(b.text),
                        style = (if (b.level <= 2) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium).copy(color = color),
                    )
                    is MdBlock.Bullet -> Row(Modifier.padding(start = (b.indent * 16).dp)) {
                        Text(b.marker, style = body.copy(color = HeColors.Accent), modifier = Modifier.width(if (b.marker.length > 1) 28.dp else 18.dp))
                        Text(inlineMarkdown(b.text), style = body)
                    }
                    is MdBlock.Quote -> Row {
                        Text("┃ ", style = body.copy(color = HeColors.Ink3))
                        Text(inlineMarkdown(b.text), style = body.copy(color = HeColors.Ink2))
                    }
                    is MdBlock.Code -> CodeBlockCard(b.lang, b.text)
                    is MdBlock.Table -> TableCard(b.headers, b.rows)
                    MdBlock.Rule -> Text("· · ·", color = HeColors.Ink3, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun TableCard(headers: List<String>, rows: List<List<String>>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HeColors.Mist3)
            .horizontalScroll(rememberScrollState()),
    ) {
        Row(Modifier.background(HeColors.Mist2).padding(horizontal = 12.dp, vertical = 8.dp)) {
            headers.forEach { h ->
                Text(
                    inlineMarkdown(h),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HeColors.Ink,
                    modifier = Modifier.width(120.dp).padding(end = 10.dp),
                )
            }
        }
        rows.forEach { r ->
            HorizontalDivider(color = HeColors.Hair)
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                headers.indices.forEach { idx ->
                    Text(
                        inlineMarkdown(r.getOrElse(idx) { "" }),
                        fontSize = 13.sp,
                        color = HeColors.Ink2,
                        modifier = Modifier.width(120.dp).padding(end = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(lang: String, code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HeColors.Mist2),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                lang.ifBlank { "code" },
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = HeColors.Ink2,
            )
            Text(
                if (copied) "已复制" else "复制",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (copied) HeColors.Accent else HeColors.Ink2,
                modifier = Modifier
                    .clip(HeShapes.Pill)
                    .clickable {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        HorizontalDivider(color = HeColors.Hair)
        Text(
            code,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = HeColors.Ink,
            softWrap = false,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(14.dp),
        )
    }
}
