package com.paulneja.quorvi.launcher

import com.paulneja.quorvi.launcher.wallpaper.BoxBlur
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxBlurTest {
    @Test
    fun aFlatImageStaysFlat() {
        val color = 0xFF336699.toInt()
        val out = BoxBlur.blur(IntArray(20 * 10) { color }, 20, 10, radius = 3)
        assertTrue(out.all { it == color })
    }

    @Test
    fun aBrightDotSpreadsSoftlyAndKeepsItsCenterBrightest() {
        val w = 21; val h = 21
        val pixels = IntArray(w * h) { 0xFF000000.toInt() }.also { it[10 * w + 10] = 0xFFFFFFFF.toInt() }
        val out = BoxBlur.blur(pixels, w, h, radius = 2)
        fun red(x: Int, y: Int) = out[y * w + x] shr 16 and 0xff
        assertTrue(red(10, 10) > red(12, 10))
        assertTrue(red(12, 10) > 0)
        assertEquals(red(8, 10), red(12, 10))
    }
}
