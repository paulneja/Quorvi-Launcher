package com.paulneja.quorvi.voice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paulneja.quorvi.R
import com.paulneja.quorvi.agent.chat.ChatLogic
import com.paulneja.quorvi.agent.chat.ChatRepository
import com.paulneja.quorvi.agent.chat.ChatRole
import com.paulneja.quorvi.agent.chat.ChatStatus
import com.paulneja.quorvi.core.net.HttpStatusException
import com.paulneja.quorvi.core.net.NetException
import com.paulneja.quorvi.core.settings.QuorviSettings
import com.paulneja.quorvi.core.settings.VoiceConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AssistantViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chats: ChatRepository,
    private val api: VoiceApi,
    private val settings: QuorviSettings,
) : ViewModel() {
    enum class Phase { IDLE, LISTENING, TRANSCRIBING, THINKING, SPEAKING, ERROR }

    data class Ui(
        val phase: Phase = Phase.IDLE,
        val heard: String = "",
        val reply: String = "",
        val error: String? = null,
        val chatId: String? = null,
        val noProvider: Boolean = false,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui
    private var config = VoiceConfig()
    private val recorder = VoiceRecorder()
    private val player = VoicePlayer(context, api) { config }
    val level: StateFlow<Float> = combine(recorder.level, player.level) { a, b -> maxOf(a, b) }.stateIn(viewModelScope, SharingStarted.Eagerly, 0f)
    val voiceError: StateFlow<String?> = player.error
    private var turn: Job? = null
    private var barge: Job? = null
    @Volatile private var interrupted = false

    fun attach(chatId: String?) {
        if (_ui.value.chatId == null && chatId != null) _ui.update { it.copy(chatId = chatId) }
    }

    fun start(chatId: String?) {
        end()
        _ui.value = Ui(chatId = chatId)
        listen()
    }

    fun end() {
        stopWatching()
        turn?.cancel()
        recorder.finish()
        player.stop()
        _ui.update { it.copy(phase = Phase.IDLE) }
    }

    private fun micAllowed() = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun watchForInterruption() {
        if (!config.bargeIn || barge?.isActive == true || !micAllowed()) return
        interrupted = false
        val r = VoiceRecorder()
        barge = viewModelScope.launch {
            val wav = runCatching {
                r.listen(
                    onSpeech = {
                        interrupted = true
                        viewModelScope.launch { player.stop(); turn?.cancel(); _ui.update { it.copy(phase = Phase.LISTENING) } }
                    },
                    gate = SpeechGate(noSpeechMs = Int.MAX_VALUE, minSpeechMs = 320, minLevel = 0.05f),
                    giveUpMs = Long.MAX_VALUE,
                    duplex = true,
                )
            }.getOrNull()
            if (!interrupted || wav == null) return@launch
            turn = viewModelScope.launch { heard(wav) }
        }
    }

    private fun stopWatching() { barge?.cancel(); barge = null }

    init {
        viewModelScope.launch { settings.voice.collect { config = it } }
    }

    fun listen() {
        player.stop()
        stopWatching()
        turn?.cancel()
        turn = viewModelScope.launch {
            config = settings.voiceNow()
            if (api.endpoint(config) == null) { _ui.update { it.copy(phase = Phase.ERROR, noProvider = true, error = null) }; return@launch }
            _ui.update { it.copy(phase = Phase.LISTENING, error = null, noProvider = false) }
            val wav = try { recorder.listen() } catch (e: VoiceException) { fail(e.message); return@launch }
            if (wav == null) { _ui.update { it.copy(phase = Phase.IDLE) }; return@launch }
            heard(wav)
        }
    }

    private suspend fun heard(wav: ByteArray) {
        _ui.update { it.copy(phase = Phase.TRANSCRIBING) }
        val text = try { api.transcribe(wav, config) } catch (e: Exception) { if (e is CancellationException) throw e; fail(describe(e)); return }
        if (text.isBlank()) { _ui.update { it.copy(phase = Phase.IDLE, error = context.getString(R.string.voice_not_understood)) }; return }
        ask(text)
    }

    fun finishListening() = recorder.finish()

    fun type(text: String) {
        if (text.isBlank()) return
        player.stop()
        stopWatching()
        turn?.cancel()
        turn = viewModelScope.launch {
            config = settings.voiceNow()
            ask(text.trim())
        }
    }

    fun stopSpeaking() {
        player.stop()
        stopWatching()
        if (_ui.value.phase == Phase.SPEAKING) _ui.update { it.copy(phase = Phase.IDLE) }
    }

    private suspend fun ask(text: String) {
        _ui.update { it.copy(phase = Phase.THINKING, heard = text, reply = "", error = null) }
        val previous = _ui.value.chatId
        val before = previous?.let { id -> chats.messages(id).first().map { it.id }.toSet() }.orEmpty()
        val id = chats.send(previous, text, emptyList(), voice = true, title = "🎙 " + ChatLogic.titleFrom(text))
        if (id == null) { fail(context.getString(R.string.voice_agent_unreachable)); return }
        _ui.update { it.copy(chatId = id) }
        val chunker = SpeechChunker()
        val answer = chats.messages(id)
            .mapNotNull { list -> list.lastOrNull { it.role == ChatRole.ASSISTANT && it.id !in before } }
            .transformWhile { m -> emit(m); m.status == ChatStatus.STREAMING }
            .onEach { m ->
                _ui.update { it.copy(reply = m.text) }
                if (config.speak && m.status != ChatStatus.ERROR) chunker.next(m.text, final = m.status != ChatStatus.STREAMING).forEach { s ->
                    player.say(s)
                    _ui.update { it.copy(phase = Phase.SPEAKING) }
                    watchForInterruption()
                }
            }
            .last()
        if (answer.status == ChatStatus.ERROR) { fail(context.getString(R.string.voice_answer_failed)); return }
        if (config.speak) player.awaitDone()
        stopWatching()
        _ui.update { it.copy(phase = Phase.IDLE) }

        if (config.followUp && answer.status == ChatStatus.DONE && micAllowed()) listen()
    }

    private fun fail(message: String?) {
        _ui.update { it.copy(phase = Phase.ERROR, error = message ?: context.getString(R.string.voice_failed)) }
    }

    private fun describe(e: Throwable): String = when (e) {
        is VoiceException -> if (e.reason == VoiceException.Reason.NO_PROVIDER) context.getString(R.string.voice_no_provider) else e.message.orEmpty()
        is HttpStatusException -> when (e.status) {
            401, 403 -> context.getString(R.string.voice_bad_key, e.status)
            404 -> context.getString(R.string.voice_no_model)
            else -> "HTTP ${e.status}: ${e.body.take(160)}"
        }
        is NetException -> context.getString(R.string.voice_offline)
        else -> e.message ?: e::class.simpleName.orEmpty()
    }

    override fun onCleared() {
        stopWatching()
        turn?.cancel()
        player.stop()
    }
}
