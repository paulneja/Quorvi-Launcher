package com.paulneja.quorvi.ui.canvas

import com.paulneja.quorvi.ui.design.liquidGlass
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.paulneja.quorvi.R
import com.paulneja.quorvi.launcher.apps.AppSearch
import com.paulneja.quorvi.launcher.widgets.WidgetHostManager
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.LocalGlassIntensity
import com.paulneja.quorvi.ui.design.LocalWallpaperTint
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ProviderRow(val info: AppWidgetProviderInfo, val label: String, val app: String)

@Composable
fun WidgetPicker(widgets: WidgetHostManager, cards: List<Pair<String, String>>, onClose: () -> Unit, onPickCard: (String) -> Unit, onPick: (AppWidgetProviderInfo) -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    var query by remember { mutableStateOf("") }
    val rows by produceState(emptyList<ProviderRow>()) {
        value = withContext(Dispatchers.IO) {
            widgets.providers().map { info ->
                val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }.getOrDefault(info.provider.packageName)
                ProviderRow(info, info.loadLabel(pm), app)
            }.sortedWith(compareBy({ it.app.lowercase() }, { it.label.lowercase() }))
        }
    }
    val shown = remember(rows, query) { AppSearch.filter(rows, query) { "${it.app} ${it.label}" } }
    val tint = LocalWallpaperTint.current
    val intensity = LocalGlassIntensity.current

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0C10).copy(alpha = 0.7f)).windowInsetsPadding(WindowInsets.systemBars)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.widgets_title), style = Quorvi.type.title, color = Quorvi.colors.text, modifier = Modifier.weight(1f))
                Image(
                    rememberVectorPainter(Glyphs.Close), null,
                    Modifier.size(40.dp).clickable(onClick = onClose).padding(8.dp),
                    colorFilter = ColorFilter.tint(Quorvi.colors.text),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = Quorvi.type.body.copy(color = Quorvi.colors.text),
                cursorBrush = SolidColor(Quorvi.colors.accent),
                modifier = Modifier.fillMaxWidth().glass(Quorvi.shapes.control, GlassLevel.CHROME, tint, intensity).padding(14.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text(stringResource(R.string.widgets_search), style = Quorvi.type.body, color = Quorvi.colors.textSecondary)
                    inner()
                },
            )
            if (shown.isEmpty() && rows.isNotEmpty()) Text(stringResource(R.string.widgets_empty), style = Quorvi.type.body, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(16.dp))
            LazyColumn(contentPadding = PaddingValues(vertical = 12.dp)) {
                if (query.isBlank()) item(key = "nexo-ci") {
                    Row(Modifier.fillMaxWidth().clickable { onPickCard("ci") }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(96.dp, 72.dp).glass(Quorvi.shapes.control, GlassLevel.CHROME, Quorvi.colors.accent, 0.6f), contentAlignment = Alignment.Center) {
                            Text("CI", style = Quorvi.type.title, color = Quorvi.colors.text)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.card_ci), style = Quorvi.type.action, color = Quorvi.colors.text)
                            Text("Quorvi", style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
                        }
                    }
                }
                if (query.isBlank()) items(cards, key = { "nexo-" + it.first }) { (kind, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onPickCard(kind) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(96.dp, 72.dp).glass(Quorvi.shapes.control, GlassLevel.CHROME, Quorvi.colors.accent, 0.6f), contentAlignment = Alignment.Center) {
                            Text(label.substringBefore(" ·"), style = Quorvi.type.action, color = Quorvi.colors.text)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(label.substringAfter("· "), style = Quorvi.type.action, color = Quorvi.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("Quorvi", style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
                        }
                    }
                }
                items(shown, key = { it.info.provider.flattenToString() + it.info.profile.hashCode() }) { row ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(row.info) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WidgetPreview(row.info, Modifier.size(96.dp, 72.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.label, style = Quorvi.type.action, color = Quorvi.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(row.app, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPreview(info: AppWidgetProviderInfo, modifier: Modifier) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.densityDpi
    val preview by produceState<ImageBitmap?>(null, info.provider) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                (info.loadPreviewImage(context, density) ?: info.loadIcon(context, density))?.let { d ->
                    val w = d.intrinsicWidth.coerceIn(1, 480)
                    val h = d.intrinsicHeight.coerceIn(1, 480)
                    d.toBitmap(w, h).asImageBitmap()
                }
            }.getOrNull()
        }
    }
    Box(modifier.heightIn(min = 48.dp).glass(Quorvi.shapes.control, GlassLevel.CHROME, Color.Black, 0.4f), contentAlignment = Alignment.Center) {
        preview?.let { Image(it, null, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit) }
    }
}
