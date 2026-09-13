package com.paulneja.quorvi.ui.canvas

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntSize
import com.paulneja.quorvi.launcher.apps.AppEntry
import com.paulneja.quorvi.launcher.home.CanvasNode
import com.paulneja.quorvi.launcher.home.CellRect

sealed interface DragPayload {
    data class Existing(val node: CanvasNode) : DragPayload
    data class NewApp(val app: AppEntry) : DragPayload
}

sealed interface DropTarget {
    data class Cell(val page: Int, val rect: CellRect) : DropTarget
    data class Dock(val slot: Int) : DropTarget
    data class Onto(val nodeId: Long) : DropTarget
    data object Remove : DropTarget
    data object Uninstall : DropTarget
}

@Stable
class DragController {
    var payload by mutableStateOf<DragPayload?>(null)
        private set
    var dragging by mutableStateOf(false)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set
    var grab = Offset.Zero
        private set
    var visualSize = IntSize.Zero
        private set
    var target by mutableStateOf<DropTarget?>(null)

    private var pointerId: PointerId? = null
    private var downAt = Offset.Zero

    var onDragStarted: () -> Unit = {}
    var onMoved: (Offset) -> Unit = {}
    var onDropped: (DragPayload, DropTarget?) -> Unit = { _, _ -> }
    var onReleasedWithoutDrag: () -> Unit = {}

    val armed: Boolean get() = payload != null

    val draggedNodeId: Long? get() = if (dragging) (payload as? DragPayload.Existing)?.node?.id else null

    fun arm(id: PointerId, item: DragPayload, rootPosition: Offset, bounds: Rect) {
        pointerId = id
        payload = item
        downAt = rootPosition
        pointer = rootPosition
        grab = rootPosition - bounds.topLeft
        visualSize = IntSize(bounds.width.toInt(), bounds.height.toInt())
        dragging = false
        target = null
    }

    fun clear() {
        payload = null
        pointerId = null
        dragging = false
        target = null
    }

    fun Modifier.trackDrags(slop: () -> Float): Modifier = pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val id = pointerId
                if (id == null) {
                    if (event.changes.none { it.pressed }) break
                    continue
                }
                val change = event.changes.firstOrNull { it.id == id }
                if (change == null) {
                    clear()
                    break
                }
                change.consume()
                if (!change.pressed) {
                    val item = payload
                    if (dragging && item != null) onDropped(item, target) else onReleasedWithoutDrag()
                    clear()
                    break
                }
                pointer = change.position
                if (!dragging && (change.position - downAt).getDistance() > slop()) {
                    dragging = true
                    onDragStarted()
                }
                if (dragging) onMoved(change.position)
            }
        }
    }
}

fun Modifier.longPressToArm(
    key: Any,
    enabled: Boolean,
    coordinates: () -> LayoutCoordinates?,
    accepts: ((Offset) -> Boolean)? = null,
    onLongPress: (PointerId, Offset, Rect) -> Unit,
): Modifier = if (!enabled) this else pointerInput(key, accepts) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (accepts != null) {
            val root = coordinates()?.takeIf { it.isAttached }?.localToRoot(down.position)
            if (root == null || !accepts(root)) return@awaitEachGesture
        }
        val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                if (!change.pressed || change.isConsumed) return@withTimeoutOrNull true
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE")
            true
        }
        if (ended != null) return@awaitEachGesture
        val coords = coordinates() ?: return@awaitEachGesture
        if (!coords.isAttached) return@awaitEachGesture
        val topLeft = coords.localToRoot(Offset.Zero)
        val bounds = Rect(topLeft, androidx.compose.ui.geometry.Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
        onLongPress(down.id, coords.localToRoot(down.position), bounds)
    }
}
