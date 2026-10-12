package com.paulneja.quorvi.core

import com.paulneja.quorvi.core.command.Calculator
import com.paulneja.quorvi.core.command.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {
    @Test
    fun followsPrecedenceAndParentheses() {
        assertEquals(14.0, Calculator.evaluate("2+3*4")!!, 1e-9)
        assertEquals(20.0, Calculator.evaluate("(2+3)*4")!!, 1e-9)
        assertEquals(512.0, Calculator.evaluate("2^3^2")!!, 1e-9)
        assertEquals(-6.0, Calculator.evaluate("-2*3")!!, 1e-9)
    }

    @Test
    fun acceptsCommaDecimalsAndPercent() {
        assertEquals(3.75, Calculator.evaluate("1,5*2,5")!!, 1e-9)
        assertEquals(0.2, Calculator.evaluate("20%")!!, 1e-9)
        assertEquals(30.0, Calculator.evaluate("200*15%")!!, 1e-9)
    }

    @Test
    fun ignoresThingsThatAreNotMath() {
        assertNull(Calculator.evaluate("chrome"))
        assertNull(Calculator.evaluate("42"))
        assertNull(Calculator.evaluate("2+"))
        assertNull(Calculator.evaluate("1/0"))
    }

    @Test
    fun formatsWithoutNoise() {
        assertEquals("0.3", Calculator.format(Calculator.evaluate("0.1+0.2")!!))
        assertEquals("4", Calculator.format(4.0))
        assertEquals("3,11", Calculator.short(3.1068559612, java.util.Locale.forLanguageTag("es-AR")))
        assertEquals("3.11", Calculator.short(3.1068559612, java.util.Locale.US))
        assertEquals("1609,3", Calculator.short(1609.344, java.util.Locale.forLanguageTag("es-AR")))
    }

    @Test
    fun convertsUnitsWithinTheSameGroup() {
        val (km, unit) = Units.convert("10 mi a km")!!
        assertEquals(16.09344, km, 1e-6)
        assertEquals("km", unit)
        assertEquals(212.0, Units.convert("100 c to f")!!.first, 1e-9)
        assertNull(Units.convert("10 kg a km"))
        assertNull(Units.convert("hola"))
    }
}
