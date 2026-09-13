package com.paulneja.quorvi.ui.canvas

import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.paulneja.quorvi.ui.design.liquidGlass
import android.app.AlarmManager
import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.paulneja.quorvi.R
import com.paulneja.quorvi.launcher.apps.AppEntry
import com.paulneja.quorvi.launcher.home.CanvasNode
import com.paulneja.quorvi.launcher.widgets.WidgetHostManager
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.LocalGlassIntensity
import com.paulneja.quorvi.ui.design.LocalWallpaperTint
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.glass
import java.util.Date
import kotlinx.coroutines.delay

private val wallpaperShadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1f), 3f)

fun TextStyle.onWallpaper(): TextStyle = copy(shadow = wallpaperShadow)

@Composable
fun AppIconView(app: AppEntry, iconSize: Dp, showLabel: Boolean, modifier: Modifier = Modifier, labelColor: Color = Color.White, shadow: Boolean = true) {
    Column(modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AppIcon(app, iconSize)
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = app.label,
                style = Quorvi.type.label.let { if (shadow) it.onWallpaper() else it },
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
fun ShortcutView(node: CanvasNode.Shortcut, shortcuts: com.paulneja.quorvi.launcher.apps.Shortcuts, iconSize: Dp, showLabel: Boolean, modifier: Modifier = Modifier) {
    val loaded by androidx.compose.runtime.produceState<com.paulneja.quorvi.launcher.apps.AppShortcut?>(null, node.packageName, node.shortcutId) {
        value = shortcuts.pinned(node.packageName, node.shortcutId, node.user)
    }
    Column(modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val icon = loaded?.icon
        if (icon != null) Image(icon, null, Modifier.size(iconSize))
        else Box(Modifier.size(iconSize).background(Color.White.copy(alpha = 0.12f), Quorvi.shapes.icon))
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = loaded?.label ?: "",
                style = Quorvi.type.label.onWallpaper(),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
fun FolderTile(folder: CanvasNode.Folder, iconSize: Dp, showLabel: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(iconSize).liquidGlass(Quorvi.shapes.icon, GlassLevel.PANEL, refraction = 8.dp).padding(iconSize / 9),
        ) {
            val mini = (iconSize - iconSize * 2 / 9 - 4.dp) / 2
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                folder.apps.take(4).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { row.forEach { AppIcon(it.app, mini) } }
                }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = folder.title.ifBlank { stringResource(R.string.folder_default) },
                style = Quorvi.type.label.onWallpaper(),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

class ContentBounds {
    var rect: androidx.compose.ui.geometry.Rect = androidx.compose.ui.geometry.Rect.Zero
    fun contains(point: androidx.compose.ui.geometry.Offset) = rect.contains(point)
}

@Composable
fun ClockBlock(modifier: Modifier = Modifier, bounds: ContentBounds? = null, extra: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}) {
    val context = LocalContext.current
    val look = Quorvi.look
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(look.clockSeconds) {
        while (true) {
            now = System.currentTimeMillis()
            delay(if (look.clockSeconds) 1_000 - now % 1_000 + 20 else 60_000 - now % 60_000 + 50)
        }
    }
    val is24 = when (look.clockHours) {
        com.paulneja.quorvi.core.settings.HourFormat.H24 -> true
        com.paulneja.quorvi.core.settings.HourFormat.H12 -> false
        com.paulneja.quorvi.core.settings.HourFormat.SYSTEM -> DateFormat.is24HourFormat(context)
    }
    val locale = LocalConfiguration.current.locales[0]
    val hours = remember(now, is24) { DateFormat.format(if (is24) "HH" else "h", now).toString() }
    val minutes = remember(now) { DateFormat.format("mm", now).toString() }
    val seconds = remember(now) { DateFormat.format("ss", now).toString() }
    val date = remember(now, locale, look.clockDate) {
        when (look.clockDate) {
            com.paulneja.quorvi.core.settings.ClockDate.NONE -> null
            com.paulneja.quorvi.core.settings.ClockDate.SHORT -> DateFormat.format(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), now).toString()
            com.paulneja.quorvi.core.settings.ClockDate.FULL -> DateFormat.format(DateFormat.getBestDateTimePattern(locale, "EEEEd"), now).toString()
        }?.replaceFirstChar { it.titlecase(locale) }
    }
    val alarm = remember(now / 60_000, look.clockAlarm, is24) {
        if (!look.clockAlarm) return@remember null
        context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime?.let {
            DateFormat.format(if (is24) "HH:mm" else "h:mm a", Date(it)).toString()
        }
    }
    val color = when (look.clockColor) {
        com.paulneja.quorvi.core.settings.ClockColor.WHITE -> Color.White
        com.paulneja.quorvi.core.settings.ClockColor.ACCENT -> Quorvi.colors.accent
        com.paulneja.quorvi.core.settings.ClockColor.CUSTOM -> Color(look.clockCustomColor)
    }
    val family = when (look.clockFont) {
        com.paulneja.quorvi.core.settings.ClockFont.GEIST -> Quorvi.type.clock.fontFamily
        com.paulneja.quorvi.core.settings.ClockFont.MONO -> Quorvi.type.mono.fontFamily
        com.paulneja.quorvi.core.settings.ClockFont.SERIF -> androidx.compose.ui.text.font.FontFamily.Serif
        com.paulneja.quorvi.core.settings.ClockFont.CONDENSED -> remember { androidx.compose.ui.text.font.FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)) }
    }
    val shadowed: TextStyle.() -> TextStyle = { if (look.clockShadow) onWallpaper() else this }
    val horizontal = when (look.clockAlign) {
        com.paulneja.quorvi.core.settings.ClockAlign.START -> Alignment.Start
        com.paulneja.quorvi.core.settings.ClockAlign.CENTER -> Alignment.CenterHorizontally
        com.paulneja.quorvi.core.settings.ClockAlign.END -> Alignment.End
    }
    val alarmIntent = { runCatching { context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val slop = with(density) { 10.dp.toPx() }
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier.fillMaxSize().padding(start = 14.dp, end = 12.dp, top = 8.dp),
        contentAlignment = when (look.clockAlign) {
            com.paulneja.quorvi.core.settings.ClockAlign.START -> Alignment.TopStart
            com.paulneja.quorvi.core.settings.ClockAlign.CENTER -> Alignment.TopCenter
            com.paulneja.quorvi.core.settings.ClockAlign.END -> Alignment.TopEnd
        },
    ) {
        val compact = maxHeight < 150.dp || maxWidth < 220.dp
        val minimal = look.clockStyle == com.paulneja.quorvi.core.settings.ClockStyle.MINIMAL
        val stacked = look.clockStyle == com.paulneja.quorvi.core.settings.ClockStyle.STACKED
        val wanted = (if (compact) 60f else 96f) * look.clockScale * (if (minimal) 0.62f else 1f)

        val lines = (if (stacked) 2f else 1f) + (if (stacked && look.clockSeconds) 0.4f else 0f)
        val reserved = (if (date != null || alarm != null) 40f else 0f) + 8f
        val fits = (maxHeight.value - reserved) / (lines * 1.22f) / density.fontScale
        val base = minOf(wanted, fits).coerceAtLeast(24f)
        val size = base.sp
        val style = Quorvi.type.clock.copy(
            fontFamily = family, fontSize = size, lineHeight = size * 0.92f,
            fontWeight = androidx.compose.ui.text.font.FontWeight(look.clockWeight),
        )
        Column(
            Modifier
                .onGloballyPositioned { bounds?.rect = it.boundsInRoot().inflate(slop) }
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { alarmIntent() },
            horizontalAlignment = horizontal,
        ) {
            when (look.clockStyle) {
                com.paulneja.quorvi.core.settings.ClockStyle.STACKED -> {
                    Text(hours, style = style.shadowed(), color = color)
                    Text(minutes, style = style.shadowed(), color = color.copy(alpha = 0.72f))
                    if (look.clockSeconds) Text(seconds, style = style.copy(fontSize = size * 0.34f, lineHeight = size * 0.4f).shadowed(), color = color.copy(alpha = 0.6f))
                }
                else -> Row(verticalAlignment = Alignment.Bottom) {
                    Text("$hours:$minutes", style = style.shadowed(), color = color)
                    if (look.clockSeconds) Text(":$seconds", style = style.copy(fontSize = size * 0.42f, lineHeight = size * 0.5f).shadowed(), color = color.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = (base * 0.1f).dp))
                }
            }
            if (date != null || (!minimal && alarm != null)) {
                Spacer(Modifier.height(if (minimal) 4.dp else 10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    date?.let { Text(it, style = Quorvi.type.action.copy(fontSize = if (minimal) 13.sp else 15.sp).shadowed(), color = Color.White) }
                    if (!minimal) extra()
                    if (!minimal && alarm != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(rememberVectorPainter(Glyphs.Alarm), null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.78f)))
                            Spacer(Modifier.width(5.dp))
                            Text(alarm, style = Quorvi.type.action.copy(fontSize = 15.sp, fontFeatureSettings = "tnum").shadowed(), color = Color.White.copy(alpha = 0.78f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WidgetCell(widgetId: Int, widgets: WidgetHostManager, modifier: Modifier = Modifier) {
    val info = remember(widgetId) { widgets.info(widgetId) }
    if (info == null) {
        Box(modifier.fillMaxSize().padding(6.dp).liquidGlass(Quorvi.shapes.panel, GlassLevel.PANEL), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.widget_missing), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(12.dp))
        }
        return
    }
    AndroidView(
        factory = { context -> widgets.createView(context, widgetId) },
        modifier = modifier.fillMaxSize().padding(4.dp),
    )
}

@Composable
fun WidgetGhost(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().glass(Quorvi.shapes.panel, GlassLevel.PANEL, LocalWallpaperTint.current, LocalGlassIntensity.current),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Quorvi.type.caption, color = Quorvi.colors.text, modifier = Modifier.padding(12.dp).fillMaxWidth(), maxLines = 2)
    }
}

@Composable
fun Scrim(alpha: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = alpha)))
}
