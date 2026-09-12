package com.paulneja.quorvi.ui.canvas

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import com.paulneja.quorvi.launcher.apps.AppEntry
import com.paulneja.quorvi.launcher.apps.IconCache

val LocalIconCache = staticCompositionLocalOf<IconCache> { error("IconCache not provided") }

val LocalBadges = androidx.compose.runtime.compositionLocalOf { emptySet<String>() }

@Composable
fun AppIcon(app: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val cache = LocalIconCache.current
    val version by cache.version.collectAsState()
    // produceState keeps its value when the keys change, so a reused slot must reload or it shows the previous app's icon.
    val icon by produceState<ImageBitmap?>(cache.peek(app.key), app.key, version) {
        value = cache.peek(app.key) ?: cache.load(app)
    }
    val bitmap = icon
    val badged = app.key.packageName in LocalBadges.current
    val look = com.paulneja.quorvi.ui.design.Quorvi.look
    val accent = com.paulneja.quorvi.ui.design.Quorvi.colors.accent
    Box(modifier.size(size)) {
        if (bitmap != null) {
            val shadow = if (look.iconShadow) Modifier.shadow(size * 0.12f, iconShape(look.iconStyle, size), clip = false) else Modifier
            when (look.iconStyle) {
                com.paulneja.quorvi.core.settings.IconStyle.ORIGINAL -> Image(bitmap = bitmap, contentDescription = null, modifier = shadow.size(size))
                com.paulneja.quorvi.core.settings.IconStyle.ROUND, com.paulneja.quorvi.core.settings.IconStyle.SQUIRCLE ->
                    Image(bitmap = bitmap, contentDescription = null, modifier = shadow.size(size).clip(iconShape(look.iconStyle, size)))
                com.paulneja.quorvi.core.settings.IconStyle.TINTED -> Box(
                    shadow.size(size).clip(iconShape(look.iconStyle, size)).background(accent.copy(alpha = 0.20f)).background(Color(0x330C0F14)),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size * 0.78f), colorFilter = remember(accent) { tintFilter(accent) })
                }
            }
        } else {
            Box(Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)))
        }
        if (badged) Box(Modifier.align(androidx.compose.ui.Alignment.TopEnd).size(size * 0.22f).clip(CircleShape).background(com.paulneja.quorvi.ui.design.Quorvi.colors.accent))
    }
}

private fun iconShape(style: com.paulneja.quorvi.core.settings.IconStyle, size: Dp): androidx.compose.ui.graphics.Shape = when (style) {
    com.paulneja.quorvi.core.settings.IconStyle.ROUND -> CircleShape
    else -> androidx.compose.foundation.shape.RoundedCornerShape(size * 0.3f)
}

private fun tintFilter(accent: Color): androidx.compose.ui.graphics.ColorFilter {
    val gray = androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
    val r = 0.55f + accent.red * 0.45f
    val g = 0.55f + accent.green * 0.45f
    val b = 0.55f + accent.blue * 0.45f
    val tint = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(r, 0f, 0f, 0f, 30f, 0f, g, 0f, 0f, 30f, 0f, 0f, b, 0f, 30f, 0f, 0f, 0f, 1f, 0f))

    tint.timesAssign(gray)
    return androidx.compose.ui.graphics.ColorFilter.colorMatrix(tint)
}
