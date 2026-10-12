package com.paulneja.quorvi.voice

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paulneja.quorvi.R
import com.paulneja.quorvi.ui.chat.ModeOrb
import com.paulneja.quorvi.ui.design.Glyph
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.chat.horizon.horizonAnchor
import com.paulneja.quorvi.ui.chat.horizon.horizonFrame

@Composable
fun VoiceModeOverlay(vm: AssistantViewModel, ultra: Boolean, onMic: () -> Unit, onClose: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    val none = remember { MutableInteractionSource() }
    val phase = ui.phase
    val horizon = com.paulneja.quorvi.ui.chat.horizon.rememberHorizon(ultra)
    LaunchedEffect(ultra) { horizon.snap(ultra) }
    Box(Modifier.fillMaxSize().horizonFrame(horizon)) {
    com.paulneja.quorvi.ui.chat.horizon.NexoField(
        horizon, busy = phase == AssistantViewModel.Phase.THINKING || phase == AssistantViewModel.Phase.TRANSCRIBING, modifier = Modifier.fillMaxSize(),
        level = level, listening = phase == AssistantViewModel.Phase.LISTENING, speaking = phase == AssistantViewModel.Phase.SPEAKING,
        top = Color(0xFF05060A), bottom = Color(0xFF0B0D14),
    )
    Box(
        Modifier.fillMaxSize()
            .clickable(interactionSource = none, indication = null) {}
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(280.dp).clip(CircleShape).clickable(interactionSource = none, indication = null) {
                    when (phase) {
                        AssistantViewModel.Phase.LISTENING -> vm.finishListening()
                        AssistantViewModel.Phase.TRANSCRIBING, AssistantViewModel.Phase.THINKING -> Unit
                        else -> onMic()
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                com.paulneja.quorvi.ui.chat.orb.NexoOrb(
                    190.dp,
                    if (ultra) com.paulneja.quorvi.ui.chat.orb.OrbMode.ULTRA else com.paulneja.quorvi.ui.chat.orb.OrbMode.CHAT,
                    com.paulneja.quorvi.ui.chat.orb.OrbStates.of(phase), level = level,
                    modifier = Modifier.horizonAnchor(horizon),
                )
            }
            Spacer(Modifier.height(36.dp))
            AnimatedContent(phase to ui.noProvider, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) }, label = "status") { (p, noProvider) ->
                Text(
                    if (noProvider) stringResource(R.string.voice_no_provider) else stringResource(
                        when (p) {
                            AssistantViewModel.Phase.LISTENING -> R.string.voice_listening
                            AssistantViewModel.Phase.TRANSCRIBING -> R.string.voice_transcribing
                            AssistantViewModel.Phase.THINKING -> R.string.voice_thinking
                            AssistantViewModel.Phase.SPEAKING -> R.string.voice_mode_speaking
                            AssistantViewModel.Phase.ERROR -> R.string.voice_tap_retry
                            AssistantViewModel.Phase.IDLE -> R.string.voice_tap_to_talk
                        },
                    ),
                    style = Quorvi.type.title, color = Quorvi.colors.text, textAlign = TextAlign.Center,
                )
            }
            val line = when (phase) {
                AssistantViewModel.Phase.SPEAKING, AssistantViewModel.Phase.IDLE -> Speakable.clean(ui.reply)
                else -> ui.heard
            }
            Text(
                ui.error ?: line, style = Quorvi.type.body, color = if (ui.error != null) Quorvi.colors.danger else Quorvi.colors.textSecondary,
                textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(Modifier.padding(bottom = 28.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                val listening = phase == AssistantViewModel.Phase.LISTENING
                RoundButton(if (listening) Glyphs.Check else Glyphs.Mic, accent = listening) { if (listening) vm.finishListening() else onMic() }
                RoundButton(Glyphs.Close, danger = true) { vm.end(); onClose() }
            }
        }
    }
    }
}

@Composable
private fun RoundButton(glyph: androidx.compose.ui.graphics.vector.ImageVector, accent: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val bg = when { accent -> Quorvi.colors.accent; danger -> Quorvi.colors.danger.copy(alpha = 0.22f); else -> Color.White.copy(alpha = 0.10f) }
    Box(Modifier.size(64.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Glyph(glyph, 24.dp, if (accent) Quorvi.colors.onAccent else if (danger) Quorvi.colors.danger else Quorvi.colors.text)
    }
}
