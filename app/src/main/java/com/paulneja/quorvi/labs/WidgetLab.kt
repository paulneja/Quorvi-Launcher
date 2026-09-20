package com.paulneja.quorvi.labs

import android.app.Activity
import com.paulneja.quorvi.launcher.widgets.WidgetHostManager
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import androidx.activity.ComponentActivity
import androidx.core.content.edit
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class WidgetLabController(private val activity: ComponentActivity, private val widgets: WidgetHostManager) {
    private val host = widgets.host
    private val manager = widgets.manager
    private val prefs = activity.getSharedPreferences("widget_lab", Activity.MODE_PRIVATE)
    private var pending: Int? = null

    val hosted = mutableStateListOf<Int>().apply {
        addAll(prefs.getString(KEY, "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }.filter { manager.getAppWidgetInfo(it) != null })
    }
    val log = mutableStateOf("")

    private val bind = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pending ?: return@registerForActivityResult
        if (result.resultCode == Activity.RESULT_OK) configureOrAdd(id) else abandon(id, "bind denied")
    }

    fun providers(): List<AppWidgetProviderInfo> =
        manager.installedProviders.sortedBy { it.loadLabel(activity.packageManager).lowercase() }

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    fun add(provider: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pending = id
        if (manager.bindAppWidgetIdIfAllowed(id, provider.profile, provider.provider, null)) {
            log.value = "bound $id without prompt"
            configureOrAdd(id)
        } else {
            log.value = "asking the user to allow binding $id"
            bind.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.profile),
            )
        }
    }

    fun remove(id: Int) {
        host.deleteAppWidgetId(id)
        hosted.remove(id)
        save()
    }

    fun view(id: Int): AppWidgetHostView = host.createView(activity, id, manager.getAppWidgetInfo(id)).apply {
        addOnLayoutChangeListener { v, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) reportSize(v as AppWidgetHostView, r - l, b - t)
        }
    }

    private fun reportSize(view: AppWidgetHostView, widthPx: Int, heightPx: Int) {
        val density = activity.resources.displayMetrics.density
        val w = widthPx / density
        val h = heightPx / density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h)))
        } else {
            @Suppress("DEPRECATION")
            view.updateAppWidgetSize(null, w.toInt(), h.toInt(), w.toInt(), h.toInt())
        }
    }

    fun startListening() = widgets.startListening()

    fun stopListening() = widgets.stopListening()

    fun onActivityResult(requestCode: Int, resultCode: Int) {
        if (requestCode != CONFIGURE_REQUEST) return
        val id = pending ?: return
        if (resultCode == Activity.RESULT_OK) commit(id) else abandon(id, "configuration cancelled")
    }

    private fun configureOrAdd(id: Int) {
        val info = manager.getAppWidgetInfo(id)
        if (info?.configure != null && info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL == 0) {
            log.value = "opening configuration for $id"
            runCatching { host.startAppWidgetConfigureActivityForResult(activity, id, 0, CONFIGURE_REQUEST, null) }
                .onFailure { abandon(id, "configure failed: ${it.message}") }
        } else {
            commit(id)
        }
    }

    private fun commit(id: Int) {
        pending = null
        hosted.add(id)
        save()
        log.value = "hosting $id"
    }

    private fun abandon(id: Int, why: String) {
        pending = null
        host.deleteAppWidgetId(id)
        log.value = why
    }

    private fun save() {
        prefs.edit { putString(KEY, hosted.joinToString(",")) }
    }

    companion object {
        private const val CONFIGURE_REQUEST = 41
        private const val KEY = "ids"
    }
}

@Composable
fun WidgetLab(controller: WidgetLabController) {
    val density = LocalDensity.current
    val pm = androidx.compose.ui.platform.LocalContext.current.packageManager
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(controller.log.value.ifEmpty { "Pick a provider to host it." }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp)) }
        items(controller.hosted.toList(), key = { "w$it" }) { id ->
            val info = controller.info(id)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#$id ${info?.provider?.flattenToShortString().orEmpty()}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { controller.remove(id) }) { Text("Remove") }
                }
                val heightDp = with(density) { (info?.minHeight ?: 110).toDp() }.coerceAtLeast(60.dp)
                AndroidView(factory = { controller.view(id) }, modifier = Modifier.fillMaxWidth().height(heightDp))
            }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        items(controller.providers(), key = { "p" + it.provider.flattenToShortString() + it.profile.hashCode() }) { provider ->
            Text(
                provider.loadLabel(pm) + "  ·  " + provider.provider.packageName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().clickable { controller.add(provider) }.padding(vertical = 10.dp),
            )
        }
    }
}
