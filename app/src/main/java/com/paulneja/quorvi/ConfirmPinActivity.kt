package com.paulneja.quorvi

import android.appwidget.AppWidgetManager
import android.content.pm.LauncherApps
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import com.paulneja.quorvi.launcher.apps.AppCatalog
import com.paulneja.quorvi.launcher.home.HomeRepository
import com.paulneja.quorvi.launcher.widgets.WidgetHostManager
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.QuorviDesign
import com.paulneja.quorvi.ui.design.glass
import com.paulneja.quorvi.ui.theme.QuorviTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ConfirmPinActivity : ComponentActivity() {
    @Inject lateinit var home: HomeRepository
    @Inject lateinit var widgets: WidgetHostManager
    @Inject lateinit var catalog: AppCatalog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcherApps = getSystemService(LauncherApps::class.java)
        val request = runCatching { launcherApps.getPinItemRequest(intent) }.getOrNull()
        if (request == null || !request.isValid) {
            finish()
            return
        }
        val dpi = resources.displayMetrics.densityDpi
        val label: String
        val icon = when (request.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT -> {
                val info = request.shortcutInfo ?: return finish()
                label = (info.shortLabel ?: info.longLabel ?: info.id).toString()
                runCatching { launcherApps.getShortcutIconDrawable(info, dpi)?.toBitmap(192, 192)?.asImageBitmap() }.getOrNull()
            }
            else -> {
                val info = request.getAppWidgetProviderInfo(this) ?: return finish()
                label = info.loadLabel(packageManager)
                runCatching { (info.loadPreviewImage(this, dpi) ?: info.loadIcon(this, dpi))?.let { it.toBitmap(it.intrinsicWidth.coerceIn(1, 600), it.intrinsicHeight.coerceIn(1, 600)).asImageBitmap() } }.getOrNull()
            }
        }

        setContent {
            QuorviTheme {
                QuorviDesign {
                    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { finish() } }, contentAlignment = Alignment.BottomCenter) {
                        Column(
                            Modifier
                                .padding(12.dp)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .fillMaxWidth()
                                .glass(Quorvi.shapes.panel, GlassLevel.TOP, Quorvi.colors.panel, 1f)
                                .pointerInput(Unit) { detectTapGestures { } }
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(stringResource(R.string.pin_title), style = Quorvi.type.title, color = Quorvi.colors.text)
                            Spacer(Modifier.height(16.dp))
                            icon?.let { Image(it, null, Modifier.heightIn(max = 160.dp).size(if (request.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) 64.dp else 160.dp), contentScale = ContentScale.Fit) }
                            Spacer(Modifier.height(10.dp))
                            Text(label, style = Quorvi.type.action, color = Quorvi.colors.text)
                            Spacer(Modifier.height(20.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(stringResource(R.string.pin_cancel), Modifier.weight(1f)) { finish() }
                                Button(stringResource(R.string.pin_add), Modifier.weight(1f), primary = true) { accept(request) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun accept(request: LauncherApps.PinItemRequest) {
        lifecycleScope.launch {
            when (request.requestType) {
                LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT -> {
                    val info = request.shortcutInfo
                    if (info != null && request.accept()) {
                        home.addShortcut(info.`package`, info.id, catalog.serial(info.userHandle))
                    }
                }
                else -> {
                    val info = request.getAppWidgetProviderInfo(this@ConfirmPinActivity)
                    if (info != null) {
                        val id = widgets.allocate()
                        val bound = widgets.bindIfAllowed(id, info) && request.accept(Bundle().apply { putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, id) })
                        if (!bound) {
                            widgets.release(id)
                        } else {
                            val spec = home.spec.value
                            val metrics = resources.displayMetrics
                            val cellW = metrics.widthPixels / metrics.density / spec.columns
                            val cellH = (metrics.heightPixels / metrics.density - 256f) / spec.rows
                            val (w, h) = widgets.span(info, cellW, cellH, spec.columns, spec.rows)
                            if (home.addWidget(id, w, h, 0) == null) widgets.release(id)
                        }
                    }
                }
            }
            finish()
        }
    }
}

@androidx.compose.runtime.Composable
private fun Button(label: String, modifier: Modifier, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .glass(Quorvi.shapes.control, GlassLevel.CHROME, if (primary) Quorvi.colors.accent else Quorvi.colors.field, if (primary) 1f else 0.5f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Quorvi.type.action, color = Quorvi.colors.text)
    }
}
