package com.paulneja.quorvi.agent.engine

object AgentLog {
    private const val TAG = "QuorviAgent"
    private val secrets = Regex("(?i)(authorization|token|api[_-]?key|password|secret)[\"'=: ]+[^\\s\"',]+|Basic [A-Za-z0-9+/=]{12,}|gh[pousr]_[A-Za-z0-9]{20,}|sk-[A-Za-z0-9_-]{16,}")

    fun clean(text: String, max: Int = 160): String = secrets.replace(text.replace('\n', ' '), "‹redacted›").take(max)

    fun i(run: String?, message: String) = runCatching { android.util.Log.i(TAG, "${run?.take(8) ?: "-"} $message") }

    fun w(run: String?, message: String, e: Throwable? = null) = runCatching {
        android.util.Log.w(TAG, "${run?.take(8) ?: "-"} $message" + (e?.let { " · ${it::class.simpleName}: ${clean(it.message.orEmpty(), 300)}" } ?: ""))
    }
}
