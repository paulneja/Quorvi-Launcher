package com.paulneja.quorvi.ui.chat

import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paulneja.quorvi.R
import com.paulneja.quorvi.agent.capsules.Capsule
import com.paulneja.quorvi.agent.chat.ChatMessageEntity
import com.paulneja.quorvi.agent.chat.ChatStatus
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Glyph
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.liquidGlass
import kotlinx.serialization.json.Json

private val capsuleJson = Json { ignoreUnknownKeys = true }

@Composable
fun OfferCard(m: ChatMessageEntity, animate: Boolean, onAccept: () -> Unit, onDismiss: () -> Unit) {
    CardFrame(animate, accent = m.status == ChatStatus.DONE) {
        CardHeader(Glyphs.Bolt, stringResource(R.string.ultra_offer_label), stringResource(R.string.ultra_offer_title))
        if (m.text.isNotBlank()) Text(m.text, style = Quorvi.type.body, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.ultra_offer_note), style = Quorvi.type.caption, color = Quorvi.colors.textTertiary, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(12.dp))
        AnimatedContent(m.status, transitionSpec = { (fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.7f), initialScale = 0.9f)) togetherWith fadeOut(tween(120)) }, label = "offer") { status ->
            when (status) {
                ChatStatus.ACCEPTED -> Resolved(Glyphs.Check, stringResource(R.string.ultra_offer_accepted), Quorvi.colors.success)
                ChatStatus.DISMISSED -> Resolved(Glyphs.Close, stringResource(R.string.ultra_offer_dismissed), Quorvi.colors.textTertiary)
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardButton(stringResource(R.string.ultra_offer_accept), primary = true, onClick = onAccept)
                    CardButton(stringResource(R.string.ultra_offer_stay), primary = false, onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
fun CapsuleCard(m: ChatMessageEntity, animate: Boolean, onSave: () -> Unit, onDismiss: () -> Unit) {
    val capsule = remember(m.text) { runCatching { capsuleJson.decodeFromString(Capsule.serializer(), m.text) }.getOrNull() } ?: return
    CardFrame(animate, accent = m.status == ChatStatus.DONE) {
        CardHeader(Glyphs.Bolt, stringResource(R.string.capsule_draft_label), capsule.name)
        if (capsule.description.isNotBlank()) Text(capsule.description, style = Quorvi.type.body, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(10.dp))
        Text(com.paulneja.quorvi.ui.operations.triggerSummary(capsule.trigger), style = Quorvi.type.caption.copy(fontWeight = FontWeight.Medium), color = Quorvi.colors.accent, modifier = Modifier.padding(bottom = 6.dp))
        if (capsule.ai) {
            Text(stringResource(R.string.capsule_ai_does), style = Quorvi.type.caption, color = Quorvi.colors.textTertiary)
            Text(capsule.prompt, style = Quorvi.type.body, color = Quorvi.colors.text, modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.05f)).padding(10.dp))
        }
        capsule.steps.take(12).forEachIndexed { i, step ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(Quorvi.colors.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = Quorvi.type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = Quorvi.colors.accent)
                }
                Spacer(Modifier.width(8.dp))
                Text(step.tool, style = Quorvi.type.mono.copy(fontSize = 12.sp), color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(12.dp))
        AnimatedContent(m.status, transitionSpec = { (fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.7f), initialScale = 0.9f)) togetherWith fadeOut(tween(120)) }, label = "capsule") { status ->
            when (status) {
                ChatStatus.ACCEPTED -> Resolved(Glyphs.Check, stringResource(R.string.capsule_draft_saved), Quorvi.colors.success)
                ChatStatus.DISMISSED -> Resolved(Glyphs.Close, stringResource(R.string.capsule_draft_dismissed), Quorvi.colors.textTertiary)
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardButton(stringResource(R.string.capsule_draft_save), primary = true, onClick = onSave)
                    CardButton(stringResource(R.string.capsule_draft_discard), primary = false, onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
fun PlanCard(m: ChatMessageEntity, animate: Boolean, onApprove: () -> Unit, onChange: (String) -> Unit) {
    var changing by androidx.compose.runtime.saveable.rememberSaveable(m.id) { androidx.compose.runtime.mutableStateOf(false) }
    var note by androidx.compose.runtime.saveable.rememberSaveable(m.id) { androidx.compose.runtime.mutableStateOf("") }
    CardFrame(animate, accent = m.status == ChatStatus.DONE) {
        CardHeader(Glyphs.Note, stringResource(R.string.plan_label), stringResource(R.string.plan_title))
        Spacer(Modifier.height(10.dp))
        MarkdownText(m.text)
        Spacer(Modifier.height(12.dp))
        AnimatedContent(m.status, transitionSpec = { (fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.7f), initialScale = 0.9f)) togetherWith fadeOut(tween(120)) }, label = "plan") { status ->
            when (status) {
                ChatStatus.ACCEPTED -> Resolved(Glyphs.Check, stringResource(R.string.plan_approved), Quorvi.colors.success)
                ChatStatus.DISMISSED -> Resolved(Glyphs.Pen, stringResource(R.string.plan_changed), Quorvi.colors.textTertiary)
                else -> Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CardButton(stringResource(R.string.plan_approve), primary = true, onClick = onApprove)
                        CardButton(stringResource(R.string.plan_change), primary = false) { changing = !changing }
                    }
                    androidx.compose.animation.AnimatedVisibility(changing) {
                        Column(Modifier.padding(top = 10.dp)) {
                            com.paulneja.quorvi.ui.design.InputField(note, { note = it }, stringResource(R.string.plan_change_hint), singleLine = false)
                            if (note.isNotBlank()) Box(Modifier.padding(top = 6.dp)) { CardButton(stringResource(R.string.plan_change_send), primary = true) { onChange(note); changing = false } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardFrame(animate: Boolean, accent: Boolean, content: @Composable () -> Unit) {
    val border by animateColorAsState(if (accent) Quorvi.colors.accent.copy(alpha = 0.55f) else Quorvi.colors.border, tween(300), label = "border")
    Column(
        Modifier.fillMaxWidth().padding(start = 38.dp).entrance(animate, fromBelow = false)
            .clip(RoundedCornerShape(22.dp)).liquidGlass(RoundedCornerShape(22.dp), GlassLevel.PANEL).border(1.dp, border, RoundedCornerShape(22.dp)).padding(16.dp),
    ) { content() }
}

@Composable
private fun CardHeader(glyph: ImageVector, label: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Quorvi.colors.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) { Glyph(glyph, 17.dp, Quorvi.colors.accent) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
            Text(title, style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CardButton(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(14.dp)).background(if (primary) Quorvi.colors.accent else Color.White.copy(alpha = 0.07f)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(label, style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = if (primary) Quorvi.colors.onAccent else Quorvi.colors.text)
    }
}

@Composable
private fun Resolved(glyph: ImageVector, label: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Glyph(glyph, 15.dp, tint)
        Spacer(Modifier.width(6.dp))
        Text(label, style = Quorvi.type.caption.copy(fontWeight = FontWeight.Medium), color = tint)
    }
}
