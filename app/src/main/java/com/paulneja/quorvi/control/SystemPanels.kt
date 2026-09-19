package com.paulneja.quorvi.control

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.Settings

object SystemPanels {
    @SuppressLint("WrongConstant")
    fun quickSettings(context: Context) {
        val opened = runCatching {
            val service = context.getSystemService("statusbar")
            service.javaClass.getMethod("expandSettingsPanel").invoke(service)
        }.isSuccess
        if (!opened) runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
