package com.paulneja.quorvi.ui.canvas

import androidx.compose.foundation.lazy.items
import androidx.compose.ui.zIndex
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.paulneja.quorvi.R
import com.paulneja.quorvi.launcher.home.CanvasNode
import com.paulneja.quorvi.launcher.home.CanvasState
import com.paulneja.quorvi.launcher.home.PageMeta
import com.paulneja.quorvi.launcher.home.PageTemplate
import com.paulneja.quorvi.ui.design.ChipButton
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Glyph
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.liquidGlass
import com.paulneja.quorvi.ui.design.liquidPress
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

fun PageTemplate.glyph(): ImageVector = when (this) {
    PageTemplate.HOME -> Glyphs.Home
    PageTemplate.TODAY -> Glyphs.Bento
    PageTemplate.CONTROL -> Glyphs.Sliders
    PageTemplate.NEXO -> Glyphs.Agent
    PageTemplate.APPS -> Glyphs.Apps
    PageTemplate.EMPTY -> Glyphs.Widgets
    PageTemplate.START, PageTemplate.MINIMAL -> Glyphs.Home
}

@Composable
fun PageTemplate.label(): String = stringResource(
    when (this) {
        PageTemplate.HOME -> R.string.page_home
        PageTemplate.TODAY -> R.string.page_today
        PageTemplate.CONTROL -> R.string.page_control
        PageTemplate.NEXO -> R.string.page_nexo
        PageTemplate.APPS -> R.string.page_apps
        PageTemplate.EMPTY -> R.string.page_empty
        PageTemplate.START, PageTemplate.MINIMAL -> R.string.page_home
    },
)

@Composable
fun PageMeta.name(index: Int): String = title?.takeIf { it.isNotBlank() }
    ?: if (template == PageTemplate.EMPTY) stringResource(R.string.page_numbered, index + 1) else template.label()

@Composable
fun PanelBar(pages: List<PageMeta>, position: () -> Float, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, labels: Boolean = true) {
    BoxWithConstraints(modifier.height(60.dp).liquidGlass(Quorvi.shapes.pill, GlassLevel.CHROME).padding(4.dp)) {
        val count = pages.size.coerceAtLeast(1)
        val itemWidth = maxWidth / count
        Box(
            Modifier
                .offset { IntOffset((itemWidth.toPx() * position().coerceIn(0f, (count - 1).toFloat())).roundToInt(), 0) }
                .graphicsLayer {
                    val p = position()
                    scaleX = 1f + sin(PI * abs(p - p.toInt())).toFloat() * 0.35f
                }
                .width(itemWidth)
                .height(52.dp)
                .liquidGlass(Quorvi.shapes.pill, GlassLevel.CHROME, refraction = 16.dp, density = 0.10f)
                .background(Color.White.copy(alpha = 0.08f), Quorvi.shapes.pill),
        )
        Row(Modifier.fillMaxSize()) {
            pages.forEachIndexed { index, page ->
                val selected = (position() + 0.5f).toInt() == index
                val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .liquidPress(interaction, Quorvi.shapes.pill)
                        .clip(Quorvi.shapes.pill)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val tint = if (selected) Quorvi.colors.text else Quorvi.colors.textSecondary
                    Glyph(page.template.glyph(), 21.dp, tint)
                    if (count <= 5 && labels) {
                        Spacer(Modifier.height(2.dp))
                        Text(page.name(index), style = Quorvi.type.label.copy(fontWeight = FontWeight.SemiBold), color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun SearchButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier.size(60.dp).liquidPress(interaction, Quorvi.shapes.pill).liquidGlass(Quorvi.shapes.pill, GlassLevel.CHROME).clip(CircleShape).clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(Glyphs.Search, 22.dp) }
}

@Composable
fun PagePill(name: String, count: Int, current: Int, open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val turn by animateFloatAsState(if (open) 180f else 0f, Quorvi.motion.quick(), label = "chevron")
    Row(
        modifier
            .height(40.dp)
            .liquidGlass(Quorvi.shapes.pill, GlassLevel.CHROME)
            .clip(Quorvi.shapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        if (count > 1) repeat(count.coerceAtMost(8)) { i ->
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(width = if (i == current) 12.dp else 5.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(if (i == current) Quorvi.colors.text else Quorvi.colors.text.copy(alpha = 0.35f)),
            )
        }
        Spacer(Modifier.width(8.dp))
        Glyph(Glyphs.Down, 14.dp, Quorvi.colors.textSecondary, Modifier.graphicsLayer { rotationZ = turn })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PagesSheet(
    canvas: CanvasState,
    current: Int,
    onSelect: (Int) -> Unit,
    onAdd: (PageTemplate) -> Unit,
    onEdit: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClose: () -> Unit,
    onSettings: () -> Unit = {},
) {
    BackHandler(onBack = onClose)
    var adding by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<Int?>(null) }
    val motion = Quorvi.motion

    val appear = remember { androidx.compose.animation.core.Animatable(if (motion.enabled) 0f else 1f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { appear.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 380f)) }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
            .background(Quorvi.colors.scrim.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { onClose() } },
    ) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 52.dp, start = 12.dp, end = 12.dp)
                .graphicsLayer {
                    val v = appear.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                    scaleX = 0.6f + 0.4f * v
                    scaleY = 0.3f + 0.7f * v
                    translationY = (1f - v) * -24.dp.toPx()
                }
                .fillMaxWidth()
                .liquidGlass(Quorvi.shapes.sheet, GlassLevel.OVERLAY)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pages_title).uppercase(), style = Quorvi.type.section, color = Quorvi.colors.textTertiary, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.pages_hint), style = Quorvi.type.caption, color = Quorvi.colors.textTertiary, maxLines = 1)
            }

            val haptics = com.paulneja.quorvi.ui.design.rememberHaptics()
            val density = androidx.compose.ui.platform.LocalDensity.current
            val slot = with(density) { (64.dp + 9.dp).toPx() }
            var dragging by remember { mutableStateOf<Int?>(null) }
            var dragX by remember { mutableStateOf(0f) }
            Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState(), enabled = dragging == null), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                canvas.pageMeta.forEachIndexed { index, meta ->
                    val selected = index == current
                    val lifted = dragging == index
                    val scale by androidx.compose.animation.core.animateFloatAsState(if (lifted) 1.08f else 1f, label = "lift")
                    Column(
                        Modifier
                            .width(64.dp)
                            .zIndex(if (lifted) 1f else 0f)
                            .graphicsLayer { translationX = if (lifted) dragX else 0f; scaleX = scale; scaleY = scale; shadowElevation = if (lifted) 12f else 0f }
                            .clip(Quorvi.shapes.control)
                            .pointerInput(index, canvas.pageCount) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { haptics.pickup(); dragging = index; dragX = 0f; optionsFor = index },
                                    onDragEnd = { haptics.drop(); dragging = null; dragX = 0f },
                                    onDragCancel = { dragging = null; dragX = 0f },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        val from = dragging ?: return@detectDragGesturesAfterLongPress
                                        dragX += amount.x
                                        val to = reorderTarget(dragX, slot, from, canvas.pageCount)
                                        if (to != null) {
                                            onMove(from, to)
                                            haptics.snap()
                                            dragX -= if (to > from) slot else -slot
                                            dragging = to
                                            optionsFor = to
                                        }
                                    },
                                )
                            }
                            .combinedClickable(onClick = { onSelect(index) }, onLongClick = { optionsFor = index }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PagePreview(canvas.pages.getOrNull(index).orEmpty(), canvas.spec.columns, canvas.spec.rows, selected)
                        Spacer(Modifier.height(7.dp))
                        Text(meta.name(index), style = Quorvi.type.label.copy(fontWeight = FontWeight.SemiBold), color = if (selected) Quorvi.colors.text else Quorvi.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            val target = optionsFor
            if (target != null) {
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (target > 0) ChipButton(stringResource(R.string.pages_move_left), Glyphs.Back, { onMove(target, target - 1); optionsFor = target - 1 })
                    if (target < canvas.pageCount - 1) ChipButton(stringResource(R.string.pages_move_right), Glyphs.Chevron, { onMove(target, target + 1); optionsFor = target + 1 })
                    if (canvas.pageCount > 1) {
                        var confirm by remember(target) { mutableStateOf(false) }
                        val empty = canvas.pages.getOrNull(target).orEmpty().isEmpty()
                        ChipButton(stringResource(if (confirm) R.string.pages_remove_confirm else R.string.pages_remove), Glyphs.Trash, {
                            if (empty || confirm) { onRemove(target); optionsFor = null } else confirm = true
                        })
                    }
                }
            }
            val full = canvas.pageCount >= canvas.spec.maxPages
            if (adding) {
                if (full) {
                    Text(stringResource(R.string.pages_full, canvas.spec.maxPages), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(top = 14.dp, start = 4.dp))
                } else {
                    Text(stringResource(R.string.pages_pick_template), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp, start = 4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(PageTemplate.TODAY, PageTemplate.CONTROL, PageTemplate.NEXO, PageTemplate.APPS, PageTemplate.EMPTY)) { t ->
                            ChipButton(t.label(), t.glyph(), { adding = false; onAdd(t) })
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetButton(stringResource(R.string.pages_add), Glyphs.Plus, Modifier.weight(1f)) { adding = !adding }
                SheetButton(stringResource(R.string.pages_edit), Glyphs.Edit, Modifier.weight(1f), onEdit)
                SheetButton(stringResource(R.string.settings_title_nexo), Glyphs.Gear, Modifier.weight(1f), onSettings)
            }
        }
    }
}

@Composable
private fun SheetButton(label: String, glyph: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(42.dp).clip(Quorvi.shapes.control).background(Color.White.copy(alpha = 0.08f)).clickable(onClick = onClick).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(glyph, 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, style = Quorvi.type.action, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PagePreview(nodes: List<CanvasNode>, columns: Int, rows: Int, selected: Boolean) {
    val accent = Quorvi.colors.accent
    Canvas(
        Modifier
            .size(64.dp, 132.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.06f))
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(7.dp),
    ) {
        val cw = size.width / columns
        val ch = size.height / rows
        nodes.forEach { n ->
            val gap = 2.dp.toPx()
            drawRoundRect(
                Color.White.copy(alpha = if (n is CanvasNode.App) 0.26f else 0.18f),
                topLeft = Offset(n.rect.x * cw + gap / 2, n.rect.y * ch + gap / 2),
                size = Size(n.rect.w * cw - gap, n.rect.h * ch - gap),
                cornerRadius = CornerRadius(if (n is CanvasNode.App) 6f else 8f),
            )
        }
    }
}

internal fun reorderTarget(dragX: Float, slot: Float, from: Int, count: Int): Int? = when {
    dragX > slot / 2 && from < count - 1 -> from + 1
    dragX < -slot / 2 && from > 0 -> from - 1
    else -> null
}
