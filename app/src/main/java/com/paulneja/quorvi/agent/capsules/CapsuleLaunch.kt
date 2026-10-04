package com.paulneja.quorvi.agent.capsules

import android.content.Context
import android.content.Intent
import com.paulneja.quorvi.ops.CoreService

object CapsuleLaunch {
    const val ACTION = "com.paulneja.quorvi.action.RUN_CAPSULE"
    const val EXTRA_ID = "capsule"
    const val EXTRA_REASON = "reason"

    fun start(context: Context, capsuleId: String, reason: String? = null) {
        val intent = Intent(context, CoreService::class.java).setAction(ACTION).putExtra(EXTRA_ID, capsuleId).putExtra(EXTRA_REASON, reason)
        runCatching { context.startForegroundService(intent) }
    }

    fun chatId(capsuleId: String) = "capsule-$capsuleId"
}
