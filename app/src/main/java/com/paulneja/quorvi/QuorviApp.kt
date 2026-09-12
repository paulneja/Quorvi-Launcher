package com.paulneja.quorvi

import android.app.Application
import com.paulneja.quorvi.core.CrashGuard
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch

@HiltAndroidApp
class QuorviApp : Application() {
    @javax.inject.Inject lateinit var capsuleTriggers: com.paulneja.quorvi.agent.capsules.CapsuleTriggers
    @javax.inject.Inject lateinit var settings: com.paulneja.quorvi.core.settings.QuorviSettings

    override fun onCreate() {
        com.paulneja.quorvi.core.backup.FullBackup.applyPending(this)
        super.onCreate()
        if (isMainProcess()) {
            CrashGuard.install(this)
            capsuleTriggers.start()

            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { com.paulneja.quorvi.voice.VoiceFeature.apply(this@QuorviApp, settings.voiceNow().enabled) }
        }
    }

    private fun isMainProcess(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) getProcessName() == packageName else true
}
