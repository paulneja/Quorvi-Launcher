package com.paulneja.quorvi.launcher.wallpaper

object BoxBlur {
    fun blur(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int = 3): IntArray {
        if (radius < 1 || width < 2 || height < 2) return pixels
        var src = pixels.copyOf()
        var dst = IntArray(pixels.size)
        repeat(passes) {
            horizontal(src, dst, width, height, radius)
            vertical(dst, src, width, height, radius)
        }
        return src
    }

    private fun horizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val window = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var a = 0; var rr = 0; var g = 0; var b = 0
            for (i in -r..r) { val p = src[row + i.coerceIn(0, w - 1)]; a += p ushr 24; rr += p shr 16 and 0xff; g += p shr 8 and 0xff; b += p and 0xff }
            for (x in 0 until w) {
                dst[row + x] = (a / window shl 24) or (rr / window shl 16) or (g / window shl 8) or (b / window)
                val out = src[row + (x - r).coerceIn(0, w - 1)]
                val inn = src[row + (x + r + 1).coerceIn(0, w - 1)]
                a += (inn ushr 24) - (out ushr 24); rr += (inn shr 16 and 0xff) - (out shr 16 and 0xff)
                g += (inn shr 8 and 0xff) - (out shr 8 and 0xff); b += (inn and 0xff) - (out and 0xff)
            }
        }
    }

    private fun vertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val window = 2 * r + 1
        for (x in 0 until w) {
            var a = 0; var rr = 0; var g = 0; var b = 0
            for (i in -r..r) { val p = src[i.coerceIn(0, h - 1) * w + x]; a += p ushr 24; rr += p shr 16 and 0xff; g += p shr 8 and 0xff; b += p and 0xff }
            for (y in 0 until h) {
                dst[y * w + x] = (a / window shl 24) or (rr / window shl 16) or (g / window shl 8) or (b / window)
                val out = src[(y - r).coerceIn(0, h - 1) * w + x]
                val inn = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
                a += (inn ushr 24) - (out ushr 24); rr += (inn shr 16 and 0xff) - (out shr 16 and 0xff)
                g += (inn shr 8 and 0xff) - (out shr 8 and 0xff); b += (inn and 0xff) - (out and 0xff)
            }
        }
    }
}
