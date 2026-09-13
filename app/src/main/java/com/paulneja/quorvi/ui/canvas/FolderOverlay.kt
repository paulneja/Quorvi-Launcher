package com.paulneja.quorvi.ui.canvas

import com.paulneja.quorvi.ui.design.liquidGlass
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.paulneja.quorvi.R
import com.paulneja.quorvi.launcher.home.CanvasNode
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.LocalGlassIntensity
import com.paulneja.quorvi.ui.design.LocalWallpaperTint
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.glass
import kotlinx.coroutines.launch

@Composable
fun FolderOverlay(
    folder: CanvasNode.Folder,
    origin: Rect?,
    geometry: CanvasGeometry,
    progress: Animatable<Float, *>,
    onClose: () -> Unit,
    onRename: (String) -> Unit,
    onOpenApp: (CanvasNode.App) -> Unit,
    onLongPressApp: (CanvasNode.App, PointerId, Offset, Rect) -> Unit,
) {
    val motion = Quorvi.motion
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var title by remember(folder.id) { mutableStateOf(folder.title) }
    var panel by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(folder.id) { progress.animateTo(1f, motion.settle()) }
    val close: () -> Unit = {
        focus.clearFocus()
        if (title != folder.title) onRename(title)
        scope.launch {
            progress.animateTo(0f, motion.panel())
            onClose()
        }
    }
    BackHandler(onBack = close)

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
            .pointerInput(folder.id) { detectTapGestures { close() } },
        contentAlignment = Alignment.Center,
    ) {
        Scrim(0.28f)
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .onGloballyPositioned { panel = it.boundsInRoot() }
                .graphicsLayer {
                    val p = progress.value
                    val from = origin
                    if (from != null && panel.width > 0f) {
                        val sx = from.width / panel.width
                        val sy = from.height / panel.height
                        scaleX = sx + (1f - sx) * p
                        scaleY = sy + (1f - sy) * p
                        translationX = (from.center.x - panel.center.x) * (1f - p)
                        translationY = (from.center.y - panel.center.y) * (1f - p)
                    }
                }
                .liquidGlass(Quorvi.shapes.panel, GlassLevel.OVERLAY, refraction = 16.dp, blurRadius = 20.dp)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp),
        ) {
            BasicTextField(
                value = title,
                onValueChange = { title = it.take(40) },
                singleLine = true,
                textStyle = Quorvi.type.title.copy(color = Quorvi.colors.text),
                cursorBrush = SolidColor(Quorvi.colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); onRename(title) }),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (title.isEmpty()) Text(stringResource(R.string.folder_default), style = Quorvi.type.title, color = Quorvi.colors.textSecondary)
                    inner()
                },
            )
            Spacer(Modifier.height(16.dp))
            folder.apps.chunked(FOLDER_COLUMNS).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    row.forEach { app ->
                        NodeHost(
                            node = app,
                            geometry = geometry,
                            hidden = false,
                            onLongPress = { id, at, bounds -> onLongPressApp(app, id, at, bounds) },
                            onClick = { onOpenApp(app) },
                            modifier = Modifier.weight(1f),
                        ) { AppIconView(app.app, 52.dp, showLabel = true, labelColor = Quorvi.colors.text, shadow = false) }
                    }
                    repeat(FOLDER_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private const val FOLDER_COLUMNS = 4
