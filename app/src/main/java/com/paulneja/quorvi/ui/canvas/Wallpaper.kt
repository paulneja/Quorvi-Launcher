package com.paulneja.quorvi.ui.canvas

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.paulneja.quorvi.R
import com.paulneja.quorvi.launcher.wallpaper.WallpaperState
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.liquidGlass
import kotlin.math.roundToInt

@Composable
fun WallpaperLayer(image: ImageBitmap, blurred: ImageBitmap?, position: () -> Float, pageCount: () -> Int, blur: () -> Float, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val scale = maxOf(size.height / image.height, size.width / image.width)
        val drawW = image.width * scale
        val drawH = image.height * scale
        val pages = pageCount()
        val fraction = if (pages > 1) (position() / (pages - 1)).coerceIn(0f, 1f) else 0.5f
        val x = -(drawW - size.width) * fraction
        val y = -(drawH - size.height) / 2f
        drawImage(
            image = image,
            dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
            dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt()),
        )
        val amount = blur()
        if (blurred != null && amount > 0f) {
            drawImage(
                image = blurred,
                dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
                dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt()),
                alpha = amount.coerceIn(0f, 1f),
                filterQuality = FilterQuality.High,
            )
        }
    }
}

@Composable
fun WallpaperSheet(
    state: WallpaperState,
    canReadSystem: Boolean,
    onPick: (Uri, Boolean) -> Unit,
    onUseCurrent: () -> Unit,
    onSystemPicker: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var lockToo by remember { mutableStateOf(true) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            onPick(uri, lockToo)
            onClose()
        }
    }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onClose() } }, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .padding(12.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .fillMaxWidth()
                .liquidGlass(Quorvi.shapes.panel, GlassLevel.TOP)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(vertical = 12.dp),
        ) {
            Text(stringResource(R.string.wallpaper_title), style = Quorvi.type.title, color = Quorvi.colors.text, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            SheetRow(stringResource(R.string.wallpaper_pick), stringResource(R.string.wallpaper_pick_hint)) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            Row(Modifier.fillMaxWidth().clickable { lockToo = !lockToo }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.wallpaper_lock_too), style = Quorvi.type.body, color = Quorvi.colors.text, modifier = Modifier.weight(1f))
                Switch(checked = lockToo, onCheckedChange = { lockToo = it })
            }
            val currentHint = when {
                state.systemIsLive -> stringResource(R.string.wallpaper_current_live)
                !canReadSystem -> stringResource(R.string.wallpaper_current_hint)
                else -> null
            }
            SheetRow(stringResource(R.string.wallpaper_current), currentHint, enabled = !state.systemIsLive) {
                if (canReadSystem) {
                    onUseCurrent()
                    onClose()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
            SheetRow(stringResource(R.string.wallpaper_system), null) {
                onSystemPicker()
                onClose()
            }
        }
    }
}

@Composable
private fun SheetRow(title: String, hint: String?, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(title, style = Quorvi.type.action, color = if (enabled) Quorvi.colors.text else Quorvi.colors.textSecondary)
        if (hint != null) {
            Spacer(Modifier.width(2.dp))
            Text(hint, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
        }
    }
}
