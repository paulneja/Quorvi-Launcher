package com.paulneja.quorvi.launcher.apps

import android.icu.text.Collator
import android.icu.text.Transliterator
import android.icu.util.ULocale
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class LetterSection(val letter: Char, val start: Int, val size: Int)

object DrawerIndex {
    private val marks = Regex("\\p{M}+")

    // 设备上的 ICU 规则不一定包含汉字音译，逐个尝试并用真实汉字探测，避免拿到空规则。
    private val transliterator: Transliterator? by lazy {
        listOf("Han-Latin", "Han-Latin; Latin-ASCII", "Any-Latin; Latin-ASCII")
            .mapNotNull { id -> runCatching { Transliterator.getInstance(id) }.getOrNull() }
            .firstOrNull { t ->
                runCatching { t.transliterate("中文测试") }.getOrNull().orEmpty()
                    .count { it.isAsciiLetter() } >= 2
            }
    }

    private val initials = ConcurrentHashMap<Char, Char>()

    @Synchronized
    fun initialOf(char: Char): Char? = initials[char] ?: run {
        val t = transliterator ?: return@run null
        val roman = runCatching { t.transliterate(char.toString()) }.getOrNull().orEmpty()
        val stripped = Normalizer.normalize(roman, Normalizer.Form.NFD).replace(marks, "")
        stripped.firstOrNull { it.isAsciiLetter() }?.uppercaseChar()?.also { initials[char] = it }
    }

    fun letterOf(label: String): Char {
        val first = label.trim().firstOrNull() ?: return '#'
        Normalizer.normalize(first.toString(), Normalizer.Form.NFD).replace(marks, "")
            .firstOrNull { it.isAsciiLetter() }?.uppercaseChar()?.let { return it }
        if (first.isLetter()) initialOf(first)?.let { return it }
        return if (first.isLetter()) first.uppercaseChar() else '#'
    }

    private fun Char.isAsciiLetter(): Boolean = this in 'A'..'Z' || this in 'a'..'z'

    private fun comparator(locale: Locale): Comparator<String> {
        val c = Collator.getInstance(
            if (locale.language == "zh") ULocale.forLanguageTag("zh-u-co-pinyin")
            else ULocale.forLocale(locale)
        )
        return Comparator { a, b -> c.compare(a, b) }
    }

    fun <T> order(items: List<T>, label: (T) -> String, locale: Locale = Locale.getDefault()): List<T> {
        val byLabel = comparator(locale)
        return items.map { it to letterOf(label(it)) }
            .sortedWith { (a, ka), (b, kb) ->
                when {
                    ka != kb -> if (ka == '#') 1 else if (kb == '#') -1 else ka.compareTo(kb)
                    else -> byLabel.compare(label(a), label(b))
                }
            }
            .map { it.first }
    }

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
