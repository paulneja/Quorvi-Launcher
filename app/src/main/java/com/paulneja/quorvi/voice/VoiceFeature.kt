package com.paulneja.quorvi.voice

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object VoiceFeature {
    private val components = listOf(AssistantActivity::class.java, NexoVoiceService::class.java, NexoSessionService::class.java, NexoRecognitionService::class.java)

    fun apply(context: Context, enabled: Boolean) {
        val pm = context.packageManager
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        components.forEach { c ->
            val name = ComponentName(context, c)
            if (pm.getComponentEnabledSetting(name) != state) runCatching { pm.setComponentEnabledSetting(name, state, PackageManager.DONT_KILL_APP) }
        }
    }
}
