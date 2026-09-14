package com.paulneja.quorvi.launcher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

@Singleton
class WidgetHostManager @Inject constructor(@ApplicationContext private val context: Context) {
    val host = AppWidgetHost(context, HOST_ID)
    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private var listening = 0

    fun startListening() {
        if (listening++ == 0) runCatching { host.startListening() }
    }

    fun stopListening() {
        if (--listening == 0) runCatching { host.stopListening() }
    }

    fun allocate(): Int = host.allocateAppWidgetId()

    fun release(id: Int) = runCatching { host.deleteAppWidgetId(id) }

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    fun providers(): List<AppWidgetProviderInfo> = manager.installedProviders

    fun bindIfAllowed(id: Int, provider: AppWidgetProviderInfo): Boolean =
        manager.bindAppWidgetIdIfAllowed(id, provider.profile, provider.provider, null)

    fun needsConfiguration(info: AppWidgetProviderInfo): Boolean =
        info.configure != null && info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL == 0

    fun createView(activityContext: Context, id: Int): AppWidgetHostView =
        host.createView(activityContext, id, manager.getAppWidgetInfo(id)).apply {
            addOnLayoutChangeListener { v, l, t, r, b, ol, ot, or, ob ->
                if (r - l != or - ol || b - t != ob - ot) reportSize(v as AppWidgetHostView, r - l, b - t)
            }
        }

    fun span(info: AppWidgetProviderInfo, cellWidthDp: Float, cellHeightDp: Float, columns: Int, rows: Int): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            return info.targetCellWidth.coerceIn(1, columns) to info.targetCellHeight.coerceIn(1, rows)
        }
        val density = context.resources.displayMetrics.density
        val w = ceil(info.minWidth / density / cellWidthDp).toInt().coerceIn(1, columns)
        val h = ceil(info.minHeight / density / cellHeightDp).toInt().coerceIn(1, rows)
        return w to h
    }

    private fun reportSize(view: AppWidgetHostView, widthPx: Int, heightPx: Int) {
        val density = context.resources.displayMetrics.density
        val w = widthPx / density
        val h = heightPx / density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h)))
        } else {
            @Suppress("DEPRECATION")
            view.updateAppWidgetSize(null, w.toInt(), h.toInt(), w.toInt(), h.toInt())
        }
    }

    companion object {
        const val HOST_ID = 4100
    }
}
