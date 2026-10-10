package com.paulneja.quorvi.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import com.paulneja.quorvi.core.settings.QuorviSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NexoRecognitionService : RecognitionService() {
    @Inject lateinit var api: VoiceApi
    @Inject lateinit var settings: QuorviSettings
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var recorder: VoiceRecorder? = null

    override fun onStartListening(intent: Intent?, listener: Callback) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listener.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS); return
        }
        job?.cancel()
        val rec = VoiceRecorder().also { recorder = it }
        job = scope.launch {
            listener.readyForSpeech(Bundle())
            val wav = runCatching { rec.listen(onSpeech = { scope.launch { listener.beginningOfSpeech() } }) }.getOrElse { listener.error(SpeechRecognizer.ERROR_AUDIO); return@launch }
            listener.endOfSpeech()
            if (wav == null) { listener.error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT); return@launch }
            val text = runCatching { api.transcribe(wav, settings.voiceNow()) }.getOrElse {
                listener.error(if (it is VoiceException && it.reason == VoiceException.Reason.NO_PROVIDER) SpeechRecognizer.ERROR_SERVER else SpeechRecognizer.ERROR_NETWORK); return@launch
            }
            if (text.isBlank()) { listener.error(SpeechRecognizer.ERROR_NO_MATCH); return@launch }
            listener.results(Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text)) })
        }
    }

    override fun onStopListening(listener: Callback) { recorder?.finish() }

    override fun onCancel(listener: Callback) { job?.cancel() }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
