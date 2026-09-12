package com.paulneja.quorvi.ui.canvas

import android.app.ActivityOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import com.paulneja.quorvi.launcher.apps.AppEntry
import com.paulneja.quorvi.ui.design.Quorvi
import kotlin.math.roundToInt

@Composable
fun rememberAppLauncher(viewModel: CanvasViewModel): (AppEntry, Rect?) -> Unit {
    val view = LocalView.current
    val fromIcon = Quorvi.look.appOpenFromIcon && Quorvi.motion.enabled
    return remember(view, fromIcon, viewModel) {
        { app, bounds ->
            val rect = bounds?.takeIf { !it.isEmpty }?.let { android.graphics.Rect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt()) }
            val options = if (fromIcon && rect != null) {
                ActivityOptions.makeScaleUpAnimation(view, rect.left, rect.top, rect.width().coerceAtLeast(1), rect.height().coerceAtLeast(1)).toBundle()
            } else {
                null
            }
            viewModel.launch(app, rect, options)
        }
    }
}
