package com.paulneja.quorvi.voice

object Speakable {
    fun clean(markdown: String): String = markdown
        .replace(Regex("```[\\s\\S]*?(```|$)"), " ")
        .replace(Regex("!\\[[^]]*]\\([^)]*\\)"), " ")
        .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("https?://\\S+"), " ")
        .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")
        .replace(Regex("(?m)^\\s*([-*+]|\\d+[.)])\\s+"), "")
        .replace(Regex("(?m)^\\s*>\\s?"), "")
        .replace(Regex("(?m)^\\s*\\|?\\s*:?-{3,}.*$"), " ")
        .replace("|", ", ")
        .replace(Regex("[*_~`]+"), "")

        .replace(Regex("(?<=[\\p{L}\\d)])[ \\t]*\\n\\s*(?=\\S)"), ", ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

class SpeechChunker(private val minChars: Int = 40) {
    private var sent = 0

    fun next(text: String, final: Boolean): List<String> {
        if (text.length < sent) sent = 0
        val out = mutableListOf<String>()
        while (true) {
            val rest = text.substring(sent)
            val cut = boundary(text, rest) ?: break
            Speakable.clean(rest.substring(0, cut)).takeIf { it.isNotEmpty() }?.let(out::add)
            sent += cut
        }
        if (final) {
            Speakable.clean(text.substring(sent)).takeIf { it.isNotEmpty() }?.let(out::add)
            sent = text.length
        }
        return out
    }

    private fun boundary(full: String, rest: String): Int? {
        val m = Regex("[.!?…:;](?=\\s)|\\n\\s*\\n").findAll(rest)
        for (r in m) {
            val end = r.range.last + 1
            if (end < minChars) continue
            if (full.substring(0, sent + end).split("```").size % 2 == 0) continue
            return end
        }
        return null
    }
}
