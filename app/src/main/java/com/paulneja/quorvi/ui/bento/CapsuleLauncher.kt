package com.paulneja.quorvi.ui.bento

import android.text.format.DateUtils
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paulneja.quorvi.R
import com.paulneja.quorvi.agent.capsules.CapsuleEntity
import com.paulneja.quorvi.ui.design.BentoBlock
import com.paulneja.quorvi.ui.design.BlockState
import com.paulneja.quorvi.ui.design.Glyph
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.panels.PanelsViewModel

@Composable
fun CapsuleLauncher(panels: PanelsViewModel, onCreate: () -> Unit) {
    val capsules by panels.capsules.collectAsStateWithLifecycle()
    val running by panels.runningCapsules.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val state = if (capsules.isEmpty()) BlockState.Empty(stringResource(R.string.blk_capsules_empty), stringResource(R.string.blk_capsules_new), onCreate) else BlockState.Ready
    BentoBlock(state = state, padding = 10.dp, title = stringResource(R.string.blk_capsules), glyph = Glyphs.Bolt) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = (maxWidth / 140.dp).toInt().coerceIn(1, 4)
            val rows = (maxHeight / 58.dp).toInt().coerceAtLeast(1)
            val slots = columns * rows
            val shown = if (capsules.size + 1 > slots) capsules.take(slots - 1) else capsules
            val cells: List<CapsuleEntity?> = shown + null
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cells.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { c ->
                            if (c == null) NewButton(Modifier.weight(1f), onCreate) else CapsuleButton(c, c.id in running, Modifier.weight(1f), onOpen = { com.paulneja.quorvi.ui.chat.ChatActivity.open(context, chat = com.paulneja.quorvi.agent.capsules.CapsuleLaunch.chatId(c.id)) }) { panels.launchCapsule(c) }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun CapsuleButton(c: CapsuleEntity, running: Boolean, modifier: Modifier, onOpen: () -> Unit, onRun: () -> Unit) {
    val haptics = com.paulneja.quorvi.ui.design.rememberHaptics()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    val pulse = rememberInfiniteTransition(label = "running")
    val glow by pulse.animateFloat(0.25f, 0.75f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "glow")
    val accent = Quorvi.colors.accent
    Row(
        modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale }.clip(Quorvi.shapes.control)
            .background(if (running) accent.copy(alpha = 0.10f + 0.12f * glow) else Color.White.copy(alpha = 0.07f))
            .border(1.dp, if (running) accent.copy(alpha = glow) else Color.Transparent, Quorvi.shapes.control)

            .combinedClickable(interactionSource = source, indication = null, onLongClick = { haptics.pickup(); onOpen() }) { haptics.confirm(); onRun() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(if (running) Glyphs.Retry else if (c.json.contains("\"prompt\"")) Glyphs.Sparkle else Glyphs.Bolt, 16.dp, accent, Modifier.graphicsLayer { if (running) rotationZ = glow * 360f })
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, style = Quorvi.type.caption.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (running) stringResource(R.string.capsule_running) else c.lastRunAt?.let { DateUtils.getRelativeTimeSpanString(it).toString() } ?: stringResource(R.string.capsule_never),
                style = Quorvi.type.label, color = if (running) accent else Quorvi.colors.textTertiary, maxLines = 1,
            )
        }
    }
}

@Composable
private fun NewButton(modifier: Modifier, onCreate: () -> Unit) {
    Box(
        modifier.fillMaxSize().clip(Quorvi.shapes.control).border(1.dp, Color.White.copy(alpha = 0.14f), Quorvi.shapes.control).clickable(onClick = onCreate),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(Glyphs.Plus, 16.dp, Quorvi.colors.textSecondary)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.capsule_new_ai), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1)
        }
    }
}
