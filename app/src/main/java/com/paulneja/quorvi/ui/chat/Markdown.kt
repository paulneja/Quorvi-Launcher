package com.paulneja.quorvi.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paulneja.quorvi.R
import com.paulneja.quorvi.ui.design.GeistMono
import com.paulneja.quorvi.ui.design.Quorvi

sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val text: String, val depth: Int, val number: String? = null) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val language: String, val code: String, val closed: Boolean) : MdBlock
    data object Rule : MdBlock
}

object MarkdownParser {
    private val bullet = Regex("^(\\s*)([-*•])\\s+(.*)$")
    private val numbered = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")
    private val heading = Regex("^(#{1,6})\\s+(.*)$")

    fun parse(text: String): List<MdBlock> {
        val out = mutableListOf<MdBlock>()
        val para = StringBuilder()
        fun flush() {
            if (para.isNotBlank()) out += MdBlock.Paragraph(para.toString().trim())
            para.clear()
        }
        val lines = text.replace("\r\n", "\n").split('\n')
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("```") -> {
                    flush()
                    val lang = trimmed.removePrefix("```").trim()
                    val code = StringBuilder()
                    i++
                    var closed = false
                    while (i < lines.size) {
                        if (lines[i].trimStart().startsWith("```")) { closed = true; break }
                        if (code.isNotEmpty()) code.append('\n')
                        code.append(lines[i])
                        i++
                    }
                    out += MdBlock.Code(lang, code.toString(), closed)
                }
                trimmed.isEmpty() -> flush()
                trimmed.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$")) -> { flush(); out += MdBlock.Rule }
                heading.matches(trimmed) -> {
                    flush()
                    val m = heading.find(trimmed)!!
                    out += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim())
                }
                bullet.matches(line) -> {
                    flush()
                    val m = bullet.find(line)!!
                    out += MdBlock.Bullet(m.groupValues[3], m.groupValues[1].length / 2)
                }
                numbered.matches(line) -> {
                    flush()
                    val m = numbered.find(line)!!
                    out += MdBlock.Bullet(m.groupValues[3], m.groupValues[1].length / 2, m.groupValues[2])
                }
                trimmed.startsWith(">") -> { flush(); out += MdBlock.Quote(trimmed.removePrefix(">").trim()) }
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

    private val inline = Regex("(\\*\\*[^*]+\\*\\*|__[^_]+__|`[^`]+`|\\[[^\\]]+\\]\\([^)\\s]+\\)|(?<![\\w*])\\*[^*\\s][^*]*\\*(?!\\*)|https?://[^\\s)]+)")

    fun inline(text: String, code: SpanStyle, link: TextLinkStyles): AnnotatedString = buildAnnotatedString {
        var last = 0
        for (m in inline.findAll(text)) {
            append(text.substring(last, m.range.first))
            val t = m.value
            when {
                t.startsWith("**") || t.startsWith("__") -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(inline(t.substring(2, t.length - 2), code, link)) }
                t.startsWith("`") -> withStyle(code) { append(" " + t.substring(1, t.length - 1) + " ") }
                t.startsWith("[") -> {
                    val label = t.substring(1, t.indexOf("]("))
                    val url = t.substring(t.indexOf("](") + 2, t.length - 1)
                    withLink(LinkAnnotation.Url(url, link)) { append(label) }
                }
                t.startsWith("http") -> withLink(LinkAnnotation.Url(t, link)) { append(t) }
                else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(t.substring(1, t.length - 1)) }
            }
            last = m.range.last + 1
        }
        append(text.substring(last))
    }
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = Quorvi.colors.text) {
    val blocks = remember(text) { MarkdownParser.parse(text) }
    val code = SpanStyle(fontFamily = GeistMono, fontSize = 13.sp, background = Color.White.copy(alpha = 0.09f), color = color)
    val link = TextLinkStyles(SpanStyle(color = Quorvi.colors.accent, textDecoration = TextDecoration.Underline))
    val body = Quorvi.type.body.copy(color = color, lineHeight = 23.sp, fontSize = 15.5.sp)
    SelectionContainer(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            blocks.forEach { b ->
                when (b) {
                    is MdBlock.Paragraph -> Text(MarkdownParser.inline(b.text, code, link), style = body)
                    is MdBlock.Heading -> Text(
                        MarkdownParser.inline(b.text, code, link),
                        style = body.copy(fontSize = when (b.level) { 1 -> 21.sp; 2 -> 18.5.sp; else -> 16.5.sp }, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    is MdBlock.Bullet -> Row(Modifier.padding(start = (b.depth * 16).dp)) {
                        Text(b.number?.let { "$it." } ?: "•", style = body.copy(color = if (b.number != null) Quorvi.colors.textSecondary else Quorvi.colors.accent), modifier = Modifier.widthIn(min = 18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(MarkdownParser.inline(b.text, code, link), style = body, modifier = Modifier.weight(1f))
                    }
                    is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Quorvi.colors.accent.copy(alpha = 0.6f)))
                        Spacer(Modifier.width(10.dp))
                        Text(MarkdownParser.inline(b.text, code, link), style = body.copy(color = Quorvi.colors.textSecondary))
                    }
                    is MdBlock.Code -> CodeBlock(b)
                    MdBlock.Rule -> Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(Quorvi.colors.border))
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(b: MdBlock.Code) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(b.code) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF07090D))) {
        Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.05f)).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(b.language.ifBlank { "code" }, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.weight(1f))
            Text(
                stringResource(if (copied) R.string.chat_copied else R.string.chat_copy),
                style = Quorvi.type.caption.copy(fontWeight = FontWeight.Medium), color = if (copied) Quorvi.colors.success else Quorvi.colors.textSecondary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { clipboard.setText(AnnotatedString(b.code)); copied = true }.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        Text(
            b.code, style = TextStyle(fontFamily = GeistMono, fontSize = 13.sp, lineHeight = 19.sp, color = Color(0xFFE6E9EF)),
            softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
        )
    }
}
