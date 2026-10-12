package com.paulneja.quorvi.core.command

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow

object Calculator {
    fun evaluate(input: String): Double? {
        val text = input.trim().replace(',', '.').replace('×', '*').replace('÷', '/').replace(" ", "")
        if (text.isEmpty() || text.none { it.isDigit() } || text.none { it in "+-*/^%()" }) return null
        if (text.any { !(it.isDigit() || it in ".+-*/^%()") }) return null
        return runCatching { Parser(text).parse() }.getOrNull()?.takeIf { it.isFinite() }
    }

    fun short(value: Double, locale: java.util.Locale = java.util.Locale.getDefault()): String {
        val f = java.text.NumberFormat.getNumberInstance(locale)
        f.maximumFractionDigits = if (kotlin.math.abs(value) >= 100) 1 else 2
        f.isGroupingUsed = kotlin.math.abs(value) >= 10_000
        return f.format(value)
    }

    fun format(value: Double): String {
        val bd = BigDecimal(value).setScale(10, RoundingMode.HALF_UP).stripTrailingZeros()
        return if (bd.scale() <= 0) bd.toBigInteger().toString() else bd.toPlainString()
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parse(): Double {
            val v = expr()
            require(i == s.length)
            return v
        }

        private fun expr(): Double {
            var v = term()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val r = term()
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        private fun term(): Double {
            var v = power()
            while (i < s.length && (s[i] == '*' || s[i] == '/')) {
                val op = s[i++]
                val r = power()
                v = if (op == '*') v * r else v / r
            }
            return v
        }

        private fun power(): Double {
            val base = unary()
            if (i < s.length && s[i] == '^') {
                i++
                return base.pow(power())
            }
            return base
        }

        private fun unary(): Double {
            if (i < s.length && s[i] == '-') { i++; return -unary() }
            if (i < s.length && s[i] == '+') { i++; return unary() }
            return postfix()
        }

        private fun postfix(): Double {
            var v = atom()
            while (i < s.length && s[i] == '%') { i++; v /= 100.0 }
            return v
        }

        private fun atom(): Double {
            require(i < s.length)
            if (s[i] == '(') {
                i++
                val v = expr()
                require(i < s.length && s[i] == ')')
                i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            require(i > start)
            return s.substring(start, i).toDouble()
        }
    }
}

object Units {
    private data class Unit(val names: List<String>, val group: String, val toBase: (Double) -> Double, val fromBase: (Double) -> Double)

    private fun linear(group: String, factor: Double, vararg names: String) = Unit(names.toList(), group, { it * factor }, { it / factor })

    private val units = listOf(
        linear("length", 1.0, "m", "metro", "metros", "meter", "meters"),
        linear("length", 1000.0, "km", "kilometro", "kilometros", "kilometer", "kilometers"),
        linear("length", 0.01, "cm", "centimetro", "centimetros"),
        linear("length", 0.001, "mm"),
        linear("length", 1609.344, "mi", "milla", "millas", "mile", "miles"),
        linear("length", 0.3048, "ft", "pie", "pies", "foot", "feet"),
        linear("length", 0.0254, "in", "pulgada", "pulgadas", "inch", "inches"),
        linear("mass", 1.0, "kg", "kilo", "kilos", "kilogramo", "kilogramos"),
        linear("mass", 0.001, "g", "gramo", "gramos", "gram", "grams"),
        linear("mass", 0.45359237, "lb", "lbs", "libra", "libras", "pound", "pounds"),
        linear("mass", 0.028349523125, "oz", "onza", "onzas", "ounce", "ounces"),
        linear("volume", 1.0, "l", "litro", "litros", "liter", "liters"),
        linear("volume", 0.001, "ml"),
        linear("volume", 3.785411784, "gal", "galon", "galones", "gallon", "gallons"),
        Unit(listOf("c", "°c", "celsius"), "temp", { it }, { it }),
        Unit(listOf("f", "°f", "fahrenheit"), "temp", { (it - 32) * 5 / 9 }, { it * 9 / 5 + 32 }),
        Unit(listOf("k", "kelvin"), "temp", { it - 273.15 }, { it + 273.15 }),
    )

    private val pattern = Regex("""^\s*(-?\d+(?:[.,]\d+)?)\s*([a-z°]+)\s+(?:a|en|to|in)\s+([a-z°]+)\s*$""")

    fun convert(input: String): Pair<Double, String>? {
        val m = pattern.find(input.lowercase()) ?: return null
        val value = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val from = units.firstOrNull { m.groupValues[2] in it.names } ?: return null
        val to = units.firstOrNull { m.groupValues[3] in it.names } ?: return null
        if (from.group != to.group) return null
        return to.fromBase(from.toBase(value)) to to.names.first()
    }
}
