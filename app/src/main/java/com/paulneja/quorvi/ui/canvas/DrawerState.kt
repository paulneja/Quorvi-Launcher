package com.paulneja.quorvi.ui.canvas

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Stable
class DrawerState(private val scope: CoroutineScope) {
    var progress by mutableFloatStateOf(0f)
        private set
    var heightPx = 1f
    private var settling: Job? = null
    private var lastDelta = 0f

    val open: Boolean get() = progress > 0f
    val closed: Boolean get() = progress == 0f

    var opening by mutableStateOf(false)
        private set

    fun dragBy(deltaPx: Float) {
        settling?.cancel()
        if (deltaPx != 0f) { lastDelta = deltaPx; opening = deltaPx < 0f }
        progress = (progress - deltaPx / heightPx).coerceIn(0f, 1f)
    }

    fun settle(velocityPx: Float) {
        val target = targetFor(velocityPx)
        lastDelta = 0f
        animateTo(target, -velocityPx / heightPx)
    }

    fun targetFor(velocityPx: Float): Float = when {
        velocityPx < -FLING_PX -> 1f
        velocityPx > FLING_PX -> 0f
        lastDelta < 0f -> if (progress > INTENT) 1f else 0f
        else -> if (progress < 1f - INTENT) 0f else 1f
    }

    fun animateTo(target: Float, velocity: Float = 0f) {
        settling?.cancel()
        opening = target > 0f
        settling = scope.launch {
            animate(progress, target, velocity, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)) { value, _ -> progress = value }
        }
    }

    val nestedScroll = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < 0 && progress < 1f) {
                dragBy(available.y)
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y > 0 && source == NestedScrollSource.UserInput) {
                dragBy(available.y)
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (progress < 1f) {
                settle(available.y)
                return available
            }
            return Velocity.Zero
        }
    }

    private companion object {
        const val FLING_PX = 1200f
        const val INTENT = 0.15f
    }
}

@Composable
fun rememberDrawerState(): DrawerState {
    val scope = rememberCoroutineScope()
    return remember { DrawerState(scope) }
}
