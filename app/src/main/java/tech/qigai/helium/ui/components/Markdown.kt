package tech.qigai.helium.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.qigai.helium.ui.theme.HeColors

/** Minimal Markdown for assistant replies: headings, lists, quotes, fenced code, **bold**, *italic*, `code`, [links](…). */
private sealed interface MdBlock {
    data class Para(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val marker: String, val text: String, val indent: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data object Rule : MdBlock
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
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    code.append(lines[i]).append('\n')
                    i++
                }
                out += MdBlock.Code(code.toString().trimEnd('\n'))
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
                    is MdBlock.Code -> Text(
                        b.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = HeColors.Ink,
                        softWrap = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(HeColors.Mist2)
                            .horizontalScroll(rememberScrollState())
                            .padding(14.dp),
                    )
                    MdBlock.Rule -> Text("· · ·", color = HeColors.Ink3, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
