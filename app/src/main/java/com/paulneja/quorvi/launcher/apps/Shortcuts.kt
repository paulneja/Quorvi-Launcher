package com.paulneja.quorvi.launcher.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppShortcut(val info: ShortcutInfo, val label: String, val icon: ImageBitmap?)

@Singleton
class Shortcuts @Inject constructor(@ApplicationContext private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val iconPx = (context.resources.displayMetrics.density * 24).toInt()

    val available: Boolean get() = runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    suspend fun forApp(app: AppEntry, limit: Int = 4): List<AppShortcut> = withContext(Dispatchers.IO) {
        if (!available) return@withContext emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(app.key.packageName)
            .setActivity(app.key.component)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        runCatching { launcherApps.getShortcuts(query, app.user) }.getOrNull().orEmpty()
            .filter { it.isEnabled }
            .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
            .take(limit)
            .map { info ->
                val icon = runCatching {
                    launcherApps.getShortcutIconDrawable(info, context.resources.displayMetrics.densityDpi)?.toBitmap(iconPx, iconPx)?.asImageBitmap()
                }.getOrNull()
                AppShortcut(info, (info.shortLabel ?: info.longLabel ?: info.id).toString(), icon)
            }
    }

    fun start(shortcut: AppShortcut): Boolean = runCatching {
        launcherApps.startShortcut(shortcut.info, null, null)
    }.isSuccess

    fun start(packageName: String, id: String, user: android.os.UserHandle): Boolean = runCatching {
        launcherApps.startShortcut(packageName, id, null, null, user)
    }.isSuccess

    suspend fun pinned(packageName: String, id: String, user: android.os.UserHandle): AppShortcut? = withContext(Dispatchers.IO) {
        if (!available) return@withContext null
        val query = LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setShortcutIds(listOf(id))
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
        val info = runCatching { launcherApps.getShortcuts(query, user) }.getOrNull()?.firstOrNull() ?: return@withContext null
        val icon = runCatching {
            launcherApps.getShortcutIconDrawable(info, context.resources.displayMetrics.densityDpi)?.toBitmap(iconPx * 2, iconPx * 2)?.asImageBitmap()
        }.getOrNull()
        AppShortcut(info, (info.shortLabel ?: info.longLabel ?: info.id).toString(), icon)
    }

    fun pin(packageName: String, ids: List<String>, user: android.os.UserHandle) = runCatching {
        launcherApps.pinShortcuts(packageName, ids, user)
    }
}
