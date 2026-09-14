package com.paulneja.quorvi.launcher.apps

import java.text.Normalizer

data class LetterSection(val letter: Char, val start: Int, val size: Int)

object DrawerIndex {
    fun letterOf(label: String): Char {
        val first = label.trim().firstOrNull() ?: return '#'
        val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).firstOrNull()?.uppercaseChar() ?: return '#'
        return if (base in 'A'..'Z') base else if (first.uppercaseChar() == 'Ñ') 'N' else '#'
    }

    fun <T> order(items: List<T>, label: (T) -> String): List<T> =
        items.sortedWith(compareBy<T>({ letterOf(label(it)).let { c -> if (c == '#') 0 else c.code } }, { label(it).lowercase() }))

    fun sections(labels: List<String>): List<LetterSection> {
        val out = mutableListOf<LetterSection>()
        labels.forEachIndexed { i, label ->
            val letter = letterOf(label)
            val last = out.lastOrNull()
            if (last != null && last.letter == letter) out[out.size - 1] = last.copy(size = last.size + 1)
            else out += LetterSection(letter, i, 1)
        }
        return out
    }

    fun headerPositions(sections: List<LetterSection>, leading: Int): Map<Char, Int> {
        var position = leading
        return sections.associate { s ->
            val at = position
            position += s.size + 1
            s.letter to at
        }
    }

    fun letterAt(fraction: Float, letters: List<Char>): Char? {
        if (letters.isEmpty()) return null
        val i = (fraction.coerceIn(0f, 0.9999f) * letters.size).toInt()
        return letters[i]
    }
}
