package com.paulneja.quorvi.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paulneja.quorvi.R
import com.paulneja.quorvi.ops.CoreClient
import com.paulneja.quorvi.ui.chat.ChatActivity
import com.paulneja.quorvi.ui.chat.MarkdownText
import com.paulneja.quorvi.ui.chat.ModeOrb
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Glyph
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.QuorviDesign
import com.paulneja.quorvi.ui.design.liquidGlass
import com.paulneja.quorvi.ui.settings.SettingsActivity
import com.paulneja.quorvi.ui.settings.SettingsModule
import com.paulneja.quorvi.ui.theme.QuorviTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AssistantActivity : ComponentActivity() {
    @Inject lateinit var core: CoreClient
    private val vm: AssistantViewModel by viewModels()
    private var micGranted by mutableStateOf(false)
    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        micGranted = ok
        if (ok) vm.listen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (savedInstanceState == null) {
            val typed = intent.getStringExtra(EXTRA_TEXT)
            when {
                typed != null -> vm.type(typed)
                micGranted -> vm.listen()
                else -> askMic.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        setContent {
            QuorviTheme {
                QuorviDesign {
                    AssistantScreen(
                        vm, micGranted,
                        onMic = { if (micGranted) vm.listen() else askMic.launch(Manifest.permission.RECORD_AUDIO) },
                        onClose = ::finish,
                        onOpenChat = { id -> ChatActivity.open(this, chat = id); finish() },
                        onSettings = { startActivity(SettingsActivity.intent(this, SettingsModule.AI, "voice")); finish() },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        core.attach()
        core.setVisible(true)
    }

    override fun onStop() {
        core.setVisible(false)
        core.detach()
        super.onStop()

        if (!isChangingConfigurations) { vm.stopSpeaking(); finish() }
    }

    companion object {
        const val EXTRA_TEXT = "text"

        fun intent(context: Context): Intent = Intent(context, AssistantActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@Composable
private fun AssistantScreen(vm: AssistantViewModel, micGranted: Boolean, onMic: () -> Unit, onClose: () -> Unit, onOpenChat: (String) -> Unit, onSettings: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    val voiceError by vm.voiceError.collectAsStateWithLifecycle()
    val rise = remember { Animatable(1f) }
    LaunchedEffect(Unit) { rise.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 260f)) }
    var typing by rememberSaveable { mutableStateOf("") }
    val none = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize().clickable(interactionSource = none, indication = null, onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().imePadding().windowInsetsPadding(WindowInsets.navigationBars).padding(10.dp)
                .graphicsLayer { translationY = rise.value * 220f; alpha = 1f - rise.value }
                .clip(RoundedCornerShape(30.dp)).liquidGlass(RoundedCornerShape(30.dp), GlassLevel.OVERLAY)
                .background(Color(0xD9101014))
                .clickable(interactionSource = none, indication = null) {}
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Nexo", style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text, modifier = Modifier.weight(1f))
                ui.chatId?.let { id -> IconButton(Glyphs.Message) { onOpenChat(id) } }
                IconButton(Glyphs.Gear, onSettings)
                IconButton(Glyphs.Close, onClose)
            }
            Box(
                Modifier.padding(vertical = 8.dp).size(132.dp).clip(CircleShape).clickable(interactionSource = none, indication = null) {
                    when (ui.phase) {
                        AssistantViewModel.Phase.LISTENING -> vm.finishListening()
                        AssistantViewModel.Phase.SPEAKING -> onMic()
                        AssistantViewModel.Phase.TRANSCRIBING, AssistantViewModel.Phase.THINKING -> Unit
                        else -> onMic()
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                com.paulneja.quorvi.ui.chat.orb.NexoOrb(
                    96.dp,
                    com.paulneja.quorvi.ui.chat.orb.OrbMode.CHAT,
                    com.paulneja.quorvi.ui.chat.orb.OrbStates.of(ui.phase), level = level,
                )
            }
            AnimatedContent(ui.phase to ui.noProvider, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "status") { (phase, noProvider) ->
                Text(
                    when {
                        noProvider -> stringResource(R.string.voice_no_provider)
                        !micGranted && phase == AssistantViewModel.Phase.IDLE -> stringResource(R.string.voice_need_mic)
                        else -> stringResource(
                            when (phase) {
                                AssistantViewModel.Phase.LISTENING -> R.string.voice_listening
                                AssistantViewModel.Phase.TRANSCRIBING -> R.string.voice_transcribing
                                AssistantViewModel.Phase.THINKING -> R.string.voice_thinking
                                AssistantViewModel.Phase.SPEAKING -> R.string.voice_speaking
                                AssistantViewModel.Phase.ERROR -> R.string.voice_tap_retry
                                AssistantViewModel.Phase.IDLE -> R.string.voice_tap_to_talk
                            },
                        )
                    },
                    style = Quorvi.type.caption, color = Quorvi.colors.textSecondary,
                )
            }
            if (ui.noProvider) Text(
                stringResource(R.string.voice_setup), style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.accent,
                modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onSettings).padding(horizontal = 12.dp, vertical = 8.dp),
            )
            AnimatedVisibility(ui.heard.isNotBlank()) {
                Text("“${ui.heard}”", style = Quorvi.type.body, color = Quorvi.colors.textSecondary, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            AnimatedVisibility(ui.reply.isNotBlank()) {
                Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).padding(top = 8.dp).verticalScroll(rememberScrollState())) {
                    MarkdownText(ui.reply, color = Quorvi.colors.text)
                }
            }
            (ui.error ?: voiceError?.let { stringResource(R.string.voice_tts_failed, it) })?.let {
                Text(it, style = Quorvi.type.caption, color = Quorvi.colors.danger, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    typing, { typing = it }, singleLine = true,
                    textStyle = Quorvi.type.body.copy(color = Quorvi.colors.text), cursorBrush = SolidColor(Quorvi.colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { vm.type(typing); typing = "" }),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 16.dp, vertical = 12.dp),
                    decorationBox = { inner -> Box { if (typing.isEmpty()) Text(stringResource(R.string.voice_type_hint), style = Quorvi.type.body, color = Quorvi.colors.textTertiary); inner() } },
                )
                Spacer(Modifier.width(8.dp))
                val listening = ui.phase == AssistantViewModel.Phase.LISTENING
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(if (typing.isNotBlank() || listening) Quorvi.colors.accent else Color.White.copy(alpha = 0.10f))
                        .clickable { if (typing.isNotBlank()) { vm.type(typing); typing = "" } else if (listening) vm.finishListening() else onMic() },
                    contentAlignment = Alignment.Center,
                ) {
                    Glyph(if (typing.isNotBlank()) Glyphs.Send else if (listening) Glyphs.Check else Glyphs.Mic, 20.dp, if (typing.isNotBlank() || listening) Quorvi.colors.onAccent else Quorvi.colors.text)
                }
            }
        }
    }
}

@Composable
private fun IconButton(glyph: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Glyph(glyph, 17.dp, Quorvi.colors.textSecondary)
    }
}
