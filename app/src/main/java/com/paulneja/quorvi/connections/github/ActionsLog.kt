package com.paulneja.quorvi.connections.github

data class LogSection(val title: String, val lines: List<String>, val errors: List<String>)

object ActionsLog {
    private val timestamp = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z\s?""")
    private val ansi = Regex("""\u001B\[[0-9;]*[A-Za-z]""")

    fun clean(line: String): String = ansi.replace(timestamp.replace(line.removePrefix("\uFEFF"), ""), "")

    fun sections(raw: String): List<LogSection> {
        val result = mutableListOf<LogSection>()
        var title = "Setup"
        var lines = mutableListOf<String>()
        var errors = mutableListOf<String>()
        fun flush() {
            if (lines.isNotEmpty() || errors.isNotEmpty()) result += LogSection(title, lines, errors)
        }
        raw.lineSequence().map(::clean).forEach { line ->
            when {
                line.startsWith("##[group]Run ") -> {
                    flush()
                    title = line.removePrefix("##[group]Run ").trim()
                    lines = mutableListOf()
                    errors = mutableListOf()
                }
                line.startsWith("##[group]") || line.startsWith("##[endgroup]") -> Unit
                line.startsWith("##[error]") -> {
                    val message = line.removePrefix("##[error]")
                    errors += message
                    lines += "✖ $message"
                }
                else -> lines += line
            }
        }
        flush()
        return result
    }

    fun failureExcerpt(raw: String, context: Int = 30): String {
        val sections = sections(raw)
        val failing = sections.lastOrNull { it.errors.isNotEmpty() } ?: sections.lastOrNull() ?: return ""
        return buildString {
            appendLine("Step: ${failing.title}")
            failing.lines.takeLast(context).forEach { appendLine(it) }
        }.trim()
    }
}
