package com.paulneja.quorvi.agent.ultra

import com.paulneja.quorvi.agent.chat.ChatMessageEntity
import com.paulneja.quorvi.agent.chat.ChatRole
import com.paulneja.quorvi.agent.chat.Mentions

object PrRequest {
    private val words = Regex("(?i)(?<![\\p{L}\\d])(prs?|pull[ -]?requests?)(?![\\p{L}\\d])")

    fun asks(text: String): Boolean = Mentions.command(text) == "pr" || words.containsMatchIn(text)

    fun pending(history: List<ChatMessageEntity>): Boolean {
        val asked = history.indexOfLast { it.role == ChatRole.USER && asks(it.text) }
        return asked >= 0 && asked > history.indexOfLast { it.role == ChatRole.PR }
    }
}

object PrDiff {
    private val header = Regex("^diff --git a/(.+?) b/(.+)$")

    fun byFile(diff: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        var path: String? = null
        val current = StringBuilder()
        fun flush() { path?.let { out[it] = current.toString().trimEnd('\n') }; current.clear() }
        for (line in diff.lines()) {
            val m = header.find(line)
            if (m != null) { flush(); path = m.groupValues[2] }
            if (path != null) current.append(line).append('\n')
        }
        flush()
        return out
    }
}
