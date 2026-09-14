package com.paulneja.quorvi.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

data class MenuItem(
    val label: String,
    val glyph: ImageVector? = null,
    val image: ImageBitmap? = null,
    val danger: Boolean = false,
    val onLongClick: (() -> Unit)? = null,
    val run: () -> Unit,
)

data class MenuSection(val items: List<MenuItem>)

@Stable
class MenuState {
    var anchor by mutableStateOf(Offset.Zero)
        private set
    var sections by mutableStateOf<List<MenuSection>>(emptyList())
        private set
    val visible: Boolean get() = sections.isNotEmpty()

    fun show(at: Offset, content: List<MenuSection>) {
        anchor = at
        sections = content.filter { it.items.isNotEmpty() }
    }

    fun dismiss() {
        sections = emptyList()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuorviMenuHost(state: MenuState) {
    if (!state.visible) return
    val colors = Quorvi.colors
    val shapes = Quorvi.shapes
    val motion = Quorvi.motion
    val tint = LocalWallpaperTint.current
    val intensity = LocalGlassIntensity.current
    val appear = remember(state.sections) { Animatable(if (motion.enabled) 0f else 1f) }
    LaunchedEffect(state.sections) { appear.animateTo(1f, motion.quick()) }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    val margin = with(LocalDensity.current) { 12.dp.roundToPx() }

    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { state.dismiss() } }) {
        val maxW = constraints.maxWidth
        val maxH = constraints.maxHeight
        val above = state.anchor.y > maxH / 2
        val x = (state.anchor.x - menuSize.width / 2f).toInt().coerceIn(margin, (maxW - menuSize.width - margin).coerceAtLeast(margin))
        val y = (if (above) state.anchor.y - menuSize.height - margin else state.anchor.y + margin).toInt()
            .coerceIn(margin, (maxH - menuSize.height - margin).coerceAtLeast(margin))
        Column(
            Modifier
                .offset { IntOffset(x, y) }
                .onSizeChanged { menuSize = it }
                .graphicsLayer {
                    val p = appear.value
                    alpha = p
                    scaleX = 0.94f + 0.06f * p
                    scaleY = 0.94f + 0.06f * p
                    translationY = (1f - p) * (if (above) 8f else -8f)
                    transformOrigin = TransformOrigin(0.5f, if (above) 1f else 0f)
                }
                .widthIn(min = 200.dp, max = 280.dp)
                .liquidGlass(shapes.panel, GlassLevel.TOP, refraction = 12.dp, blurRadius = 24.dp)
                .padding(vertical = 6.dp),
        ) {
            state.sections.forEachIndexed { index, section ->
                if (index > 0) Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.6f)))
                section.items.forEach { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .combinedClickable(
                                onClick = { state.dismiss(); item.run() },
                                onLongClick = item.onLongClick?.let { long -> { state.dismiss(); long() } },
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tintColor = if (item.danger) colors.danger else colors.text
                        when {
                            item.image != null -> Image(item.image, null, Modifier.size(22.dp))
                            item.glyph != null -> Image(rememberVectorPainter(item.glyph), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(tintColor))
                            else -> Spacer(Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(item.label, style = Quorvi.type.action, color = tintColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
