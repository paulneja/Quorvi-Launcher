package com.paulneja.quorvi

import android.app.Application
import com.paulneja.quorvi.core.CrashGuard
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@HiltAndroidApp
class QuorviApp : Application() {
    @javax.inject.Inject lateinit var capsuleTriggers: com.paulneja.quorvi.agent.capsules.CapsuleTriggers
    @javax.inject.Inject lateinit var settings: com.paulneja.quorvi.core.settings.QuorviSettings
    @javax.inject.Inject lateinit var ai: com.paulneja.quorvi.agent.engine.AiSwitch

    override fun onCreate() {
        com.paulneja.quorvi.core.backup.FullBackup.applyPending(this)
        super.onCreate()
        if (isMainProcess()) {
            CrashGuard.install(this)
            capsuleTriggers.start()

            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                kotlinx.coroutines.flow.combine(ai.enabled, settings.voice) { on, voice -> on to voice.enabled }.distinctUntilChanged().collect { (on, voice) ->
                    com.paulneja.quorvi.voice.VoiceFeature.applyAi(this@QuorviApp, on, voice)
                }
            }
        }
    }

    private fun isMainProcess(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) getProcessName() == packageName else true
}
