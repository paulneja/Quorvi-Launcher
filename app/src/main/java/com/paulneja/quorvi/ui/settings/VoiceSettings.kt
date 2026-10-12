package com.paulneja.quorvi.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import com.paulneja.quorvi.ui.design.ChoiceRow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paulneja.quorvi.R
import com.paulneja.quorvi.core.settings.VoiceConfig
import com.paulneja.quorvi.ui.design.ActionRow
import com.paulneja.quorvi.ui.design.Hint
import com.paulneja.quorvi.ui.design.InputField
import com.paulneja.quorvi.ui.design.Label
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.Panel
import com.paulneja.quorvi.ui.design.Section
import com.paulneja.quorvi.ui.design.SliderRow
import com.paulneja.quorvi.ui.design.SwitchRow
import kotlin.math.roundToInt
import com.paulneja.quorvi.ui.operations.Chip
import com.paulneja.quorvi.voice.AssistantActivity
import com.paulneja.quorvi.voice.VoiceSettingsViewModel

@Composable
fun VoiceSettings(vm: VoiceSettingsViewModel) {
    val context = LocalContext.current
    val config by vm.config.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val effective by vm.effective.collectAsStateWithLifecycle()
    val testError by vm.testError.collectAsStateWithLifecycle()
    val testing by vm.testing.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val previewing by vm.previewing.collectAsStateWithLifecycle()
    val previewText = stringResource(R.string.voice_preview_text)
    var assistant by remember { mutableStateOf(vm.isAssistant()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { assistant = vm.isAssistant() }
    val sample = stringResource(R.string.voice_sample)
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Panel {
                SwitchRow(stringResource(R.string.voice_enable), config.enabled) { on -> vm.setEnabled(on) }
                Hint(stringResource(if (config.enabled) R.string.voice_enable_on else R.string.voice_enable_off))
                Text(stringResource(R.string.voice_experimental), style = Quorvi.type.caption, color = Quorvi.colors.accent, modifier = Modifier.padding(bottom = 10.dp))
            }
        }
        if (!config.enabled) return@LazyColumn
        item { Section(stringResource(R.string.voice_sys_section)) }
        item {
            Panel {
                Text(
                    stringResource(if (assistant) R.string.voice_is_assistant else R.string.voice_not_assistant), style = Quorvi.type.body,
                    color = if (assistant) Quorvi.colors.success else Quorvi.colors.textSecondary, modifier = Modifier.padding(vertical = 10.dp),
                )
                if (!assistant) ActionRow(stringResource(R.string.voice_make_assistant), stringResource(R.string.voice_make_assistant_desc)) {
                    val open = listOf(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS), Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                    open.firstOrNull { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
                }
                ActionRow(stringResource(R.string.voice_try_now), null) { context.startActivity(AssistantActivity.intent(context)) }
            }
        }
        item { Section(stringResource(R.string.voice_provider_section)) }
        item {
            Panel {
                if (providers.isEmpty()) Hint(stringResource(R.string.voice_no_provider))
                else Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.voice_auto), accent = config.providerId.isEmpty()) { vm.update { it.copy(providerId = "", voice = "", ttsModel = "", sttModel = "") } }
                    providers.forEach { p -> Chip(p.name, accent = config.providerId == p.id) { vm.update { if (it.providerId == p.id) it else it.copy(providerId = p.id, voice = "", ttsModel = "", sttModel = "") } } }
                }
                Hint(stringResource(R.string.voice_provider_hint))
            }
        }
        item { Section(stringResource(R.string.voice_voice_section)) }
        item {
            Panel {
                Label(stringResource(R.string.voice_voice))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    effective?.let { e ->
                        val voices = if (e.gemini) com.paulneja.quorvi.voice.VoiceModels.geminiVoices else com.paulneja.quorvi.voice.VoiceModels.openAiVoices
                        voices.forEach { v -> Chip(v, accent = e.voice.equals(v, true)) { vm.update { it.copy(voice = v) }; vm.preview(v, previewText) } }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(previewing != null) {
                    androidx.compose.foundation.layout.Column(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(Quorvi.colors.accent.copy(alpha = 0.10f)).padding(12.dp),
                    ) {
                        Text(stringResource(R.string.voice_preview_playing, previewing.orEmpty()), style = Quorvi.type.label, color = Quorvi.colors.accent)
                        Text("“$previewText”", style = Quorvi.type.body, color = Quorvi.colors.text, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Label(stringResource(R.string.voice_style))
                InputField(config.style, { v -> vm.update { it.copy(style = v) } }, stringResource(R.string.voice_default_style), singleLine = false)
                Hint(stringResource(R.string.voice_style_hint))
                SwitchRow(stringResource(R.string.voice_speak), config.speak) { on -> vm.update { it.copy(speak = on) } }
                SwitchRow(stringResource(R.string.voice_follow_up), config.followUp) { on -> vm.update { it.copy(followUp = on) } }
                SwitchRow(stringResource(R.string.voice_barge_in), config.bargeIn) { on -> vm.update { it.copy(bargeIn = on) } }
                SliderRow(stringResource(R.string.voice_speed), config.speed, 0.8f..1.6f, format = { s -> val v = (s * 100).roundToInt(); "×${v / 100}.${(v % 100).toString().padStart(2, '0')}" }) { s ->
                    vm.update { it.copy(speed = (s * 20).roundToInt() / 20f) }
                }
                ActionRow(stringResource(if (testing) R.string.voice_testing else R.string.voice_test), testError?.let { stringResource(R.string.voice_tts_failed, it) }) { vm.test(sample) }
            }
        }
        item { Section(stringResource(R.string.voice_models_section)) }
        item {
            Panel {
                ModelPicker(
                    stringResource(R.string.voice_tts_model), config.ttsModel, effective?.tts, models?.speech, models?.failed == true,
                ) { m -> vm.update { it.copy(ttsModel = m) } }
                ModelPicker(
                    stringResource(R.string.voice_stt_model), config.sttModel, effective?.stt, models?.hearing, models?.failed == true,
                ) { m -> vm.update { it.copy(sttModel = m) } }
                Hint(stringResource(R.string.voice_models_hint))
            }
        }
    }
}

@Composable
private fun ModelPicker(title: String, current: String, auto: String?, options: List<String>?, failed: Boolean, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, androidx.compose.animation.core.tween(160), label = "chevron")
    androidx.compose.foundation.layout.Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { open = !open }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(title, style = Quorvi.type.body, color = Quorvi.colors.text)
                Text(
                    current.ifBlank { stringResource(R.string.voice_auto_value, auto ?: "—") }, style = Quorvi.type.caption,
                    color = if (current.isBlank()) Quorvi.colors.textSecondary else Quorvi.colors.accent, maxLines = 1,
                )
            }
            com.paulneja.quorvi.ui.design.Glyph(com.paulneja.quorvi.ui.design.Glyphs.Down, 14.dp, Quorvi.colors.textTertiary, Modifier.rotate(turn))
        }
        androidx.compose.animation.AnimatedVisibility(open) {
            androidx.compose.foundation.layout.Column(Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                ChoiceRow(stringResource(R.string.voice_auto_value, auto ?: "—"), selected = current.isBlank()) { onPick(""); open = false }
                when {
                    options == null && !failed -> Hint(stringResource(R.string.voice_models_loading))
                    failed || options.isNullOrEmpty() -> Hint(stringResource(R.string.voice_models_none))
                    else -> options.forEach { m -> ChoiceRow(m, selected = current == m) { onPick(m); open = false } }
                }

                if (failed || options?.isEmpty() == true) {
                    var other by remember(current) { mutableStateOf(current) }
                    Label(stringResource(R.string.voice_model_other))
                    InputField(other, { other = it.trim(); onPick(other) }, stringResource(R.string.voice_model_other_hint))
                }
            }
        }
    }
}
