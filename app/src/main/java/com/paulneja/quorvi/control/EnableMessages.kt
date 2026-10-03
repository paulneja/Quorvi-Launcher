package com.paulneja.quorvi.control

import android.content.Context
import com.paulneja.quorvi.R

fun EnableResult.message(context: Context, backend: PrivBackend): String {
    val name = when (backend) {
        PrivBackend.ROOT -> "Root"
        PrivBackend.SHIZUKU -> "Shizuku"
        PrivBackend.ADB_TCP -> "ADB"
    }
    return when (this) {
        EnableResult.Ready -> context.getString(R.string.priv_ready, name)
        EnableResult.Denied -> context.getString(if (backend == PrivBackend.SHIZUKU) R.string.priv_denied_shizuku else R.string.priv_denied_root)
        EnableResult.NotRunning -> context.getString(R.string.priv_shizuku_not_running)
        EnableResult.Missing -> context.getString(R.string.priv_root_missing)
    }
}
