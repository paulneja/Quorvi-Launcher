package com.paulneja.quorvi.core

import android.content.Context
import androidx.core.content.edit

class CrashLoop(private val windowMs: Long = 120_000, private val threshold: Int = 2) {
    fun detected(crashes: List<Long>, now: Long): Boolean = crashes.count { now - it in 0..windowMs } >= threshold
}

object CrashGuard {
    private const val PREFS = "crash_guard"
    private const val KEY = "crashes"
    private const val KEEP = 10
    private val loop = CrashLoop()

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, System.currentTimeMillis()) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun inLoop(context: Context, now: Long = System.currentTimeMillis()): Boolean = loop.detected(read(context), now)

    fun clear(context: Context) {
        prefs(context).edit(commit = true) { remove(KEY) }
    }

    private fun record(context: Context, at: Long) {
        val crashes = (read(context) + at).takeLast(KEEP)
        prefs(context).edit(commit = true) { putString(KEY, crashes.joinToString(",")) }
    }

    private fun read(context: Context): List<Long> =
        prefs(context).getString(KEY, null).orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
