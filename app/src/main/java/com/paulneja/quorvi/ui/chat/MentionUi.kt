package com.paulneja.quorvi.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.paulneja.quorvi.agent.chat.Mentions
import com.paulneja.quorvi.ui.design.Glyph
import com.paulneja.quorvi.ui.design.Quorvi

data class MentionOption(val word: String, val title: String, val subtitle: String, val glyph: ImageVector)

private val known: Set<String> = Mentions.aliases.keys + setOf("meta", "ultra", "investigar", "research")
private val mention = Regex("(?<![\\w@])@([\\p{L}]+)")

fun highlightMentions(text: String, color: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    Regex("^\\s*/(plan|investigar|meta)\\b").find(text)?.let { m ->
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold, background = color.copy(alpha = 0.16f))) { append(m.value) }
        last = m.range.last + 1
    }
    mention.findAll(text).forEach { m ->
        if (m.groupValues[1].lowercase() !in known) return@forEach
        append(text.substring(last, m.range.first))
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold, background = color.copy(alpha = 0.16f))) { append(m.value) }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

class MentionTransformation(private val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString) = TransformedText(highlightMentions(text.text, color), OffsetMapping.Identity)
}

fun activeMention(text: String): String? = Regex("(?:^|\\s)@([\\p{L}]*)$").find(text)?.groupValues?.get(1)

fun completeMention(text: String, word: String): String = text.replace(Regex("@([\\p{L}]*)$"), "@$word ")

@Composable
fun MentionBar(options: List<MentionOption>, prefix: String = "@", onPick: (MentionOption) -> Unit) {
    LazyRow(contentPadding = PaddingValues(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options, key = { it.word }) { o ->
            Row(
                Modifier.animateItem().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.07f)).clickable { onPick(o) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(o.glyph, 16.dp, Quorvi.colors.accent)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(prefix + o.word, style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text)
                    Text(o.subtitle, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1)
                }
            }
        }
    }
}
