package com.paulneja.quorvi.voice

import android.app.role.RoleManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paulneja.quorvi.agent.models.ProviderEntity
import com.paulneja.quorvi.core.settings.QuorviSettings
import com.paulneja.quorvi.core.settings.VoiceConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: QuorviSettings,
    private val api: VoiceApi,
    private val providersRepo: com.paulneja.quorvi.agent.models.ModelProviders,
) : ViewModel() {
    data class Models(val speech: List<String>, val hearing: List<String>, val failed: Boolean = false)

    data class Effective(val provider: String, val gemini: Boolean, val stt: String, val tts: String, val voice: String)

    val config: StateFlow<VoiceConfig> = settings.voice.stateIn(viewModelScope, SharingStarted.Eagerly, VoiceConfig())
    private val _providers = MutableStateFlow<List<ProviderEntity>>(emptyList())
    val providers: StateFlow<List<ProviderEntity>> = _providers
    val effective: StateFlow<Effective?> = settings.voice.map { c ->
        api.endpoint(c)?.let { e -> Effective(e.provider.name, e.gemini, api.sttModel(e, c.copy(sttModel = "")), api.ttsModel(e, c.copy(ttsModel = "")), api.voice(e, c.copy(voice = ""))) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val player = VoicePlayer(context, api) { config.value }
    val testError: StateFlow<String?> = player.error
    val testing: StateFlow<Boolean> = player.speaking

    private val _models = MutableStateFlow<Models?>(null)
    val models: StateFlow<Models?> = _models

    init {
        viewModelScope.launch { _providers.value = api.candidates() }
        viewModelScope.launch {
            var loadedFor: String? = null
            settings.voice.collect { c ->
                val e = api.endpoint(c) ?: run { _models.value = Models(emptyList(), emptyList(), failed = true); return@collect }
                if (e.provider.id == loadedFor) return@collect
                loadedFor = e.provider.id
                _models.value = null
                _models.value = runCatching { providersRepo.listModels(e.provider.id).map { it.id } }
                    .map { ids -> Models(VoiceModels.speech(ids), VoiceModels.hearing(ids, e.gemini)) }
                    .getOrElse { Models(emptyList(), emptyList(), failed = true) }
            }
        }
    }

    fun update(change: (VoiceConfig) -> VoiceConfig) = viewModelScope.launch { settings.setVoice(change) }

    fun setEnabled(on: Boolean) = viewModelScope.launch {
        settings.setVoice { it.copy(enabled = on) }
        VoiceFeature.apply(context, on)
        if (!on) player.stop()
    }

    fun test(sample: String) {
        player.stop()
        player.say(sample)
    }

    private val previews = mutableMapOf<String, Speech>()
    private val _previewing = MutableStateFlow<String?>(null)

    val previewing: StateFlow<String?> = _previewing

    fun preview(voice: String, sample: String) {
        val c = config.value.copy(voice = voice)
        val key = listOf(c.providerId, c.ttsModel, voice, c.style).joinToString("|")
        player.stop()
        _previewing.value = voice
        player.say(sample) { previews[key] ?: api.speak(sample, c).also { previews[key] = it } }
        viewModelScope.launch { player.awaitDone(); if (_previewing.value == voice) _previewing.value = null }
    }

    fun isAssistant(): Boolean = runCatching { context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT) }.getOrDefault(false)

    override fun onCleared() = player.stop()
}
