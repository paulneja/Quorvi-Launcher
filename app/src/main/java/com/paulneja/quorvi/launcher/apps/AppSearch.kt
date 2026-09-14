package com.paulneja.quorvi.launcher.apps

import java.text.Normalizer
import java.util.Locale

object AppSearch {
    private val marks = Regex("\\p{M}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(marks, "").lowercase(Locale.ROOT).trim()

    fun score(label: String, query: String): Int {
        val q = normalize(query)
        if (q.isEmpty()) return 1
        val l = normalize(label)
        val words = l.split(separators).filter { it.isNotEmpty() }
        return when {
            l == q -> 100
            l.startsWith(q) -> 80
            words.any { it.startsWith(q) } -> 60
            q.length >= 2 && words.joinToString("") { it.take(1) }.startsWith(q) -> 40
            l.contains(q) -> 20
            else -> 0
        }
    }

    fun <T> filter(items: List<T>, query: String, label: (T) -> String): List<T> {
        if (normalize(query).isEmpty()) return items
        return items.mapNotNull { item -> score(label(item), query).takeIf { it > 0 }?.let { item to it } }
            .sortedByDescending { it.second }
            .map { it.first }
    }
}
