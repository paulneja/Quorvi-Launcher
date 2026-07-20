package com.paulneja.quorvi.ui.design

import android.app.Activity
import android.app.WallpaperManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

class WindowEffects(private val activity: Activity) {
    private val wallpapers = WallpaperManager.getInstance(activity)
    private val maxBlurPx = (activity.resources.displayMetrics.density * 28).roundToInt()
    private var appliedBlur = -1
    private var blurSupported = false

    var wallpaperTint by mutableStateOf(Color(0xFF2A3140))
        private set

    private val colorsListener = WallpaperManager.OnColorsChangedListener { _, which ->
        if (which and WallpaperManager.FLAG_SYSTEM != 0) readTint()
    }
    private val blurListener = java.util.function.Consumer<Boolean> { enabled -> blurSupported = enabled }

    fun attach() {
        readTint()
        runCatching { wallpapers.addOnColorsChangedListener(colorsListener, Handler(Looper.getMainLooper())) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val wm = activity.getSystemService(WindowManager::class.java)
            blurSupported = wm.isCrossWindowBlurEnabled
            wm.addCrossWindowBlurEnabledListener(activity.mainExecutor, blurListener)
        }
    }

    fun detach() {
        runCatching { wallpapers.removeOnColorsChangedListener(colorsListener) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { activity.getSystemService(WindowManager::class.java).removeCrossWindowBlurEnabledListener(blurListener) }
        }
    }

    val canBlur: Boolean get() = blurSupported

    fun blur(progress: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !blurSupported) return
        val steps = 8
        val radius = ((progress.coerceIn(0f, 1f) * steps).roundToInt() * maxBlurPx) / steps
        if (radius == appliedBlur) return
        appliedBlur = radius
        activity.window.setBackgroundBlurRadius(radius)
    }

    fun scrollWallpaper(position: Float, pageCount: Int) {
        val token = activity.window.decorView.windowToken ?: return
        val step = if (pageCount > 1) 1f / (pageCount - 1) else 0f
        val x = if (pageCount > 1) (position / (pageCount - 1)).coerceIn(0f, 1f) else 0.5f
        runCatching {
            wallpapers.setWallpaperOffsetSteps(step, 1f)
            wallpapers.setWallpaperOffsets(token, x, 0.5f)
        }
    }

    private fun readTint() {
        val colors = runCatching { wallpapers.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull() ?: return
        wallpaperTint = Color(colors.primaryColor.toArgb())
    }
}

val LocalWindowEffects = staticCompositionLocalOf<WindowEffects?> { null }
