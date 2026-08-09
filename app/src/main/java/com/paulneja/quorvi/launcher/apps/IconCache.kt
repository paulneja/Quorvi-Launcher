package com.paulneja.quorvi.launcher.apps

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.paulneja.quorvi.core.settings.QuorviSettings
import com.paulneja.quorvi.launcher.icons.IconPacks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class IconCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packs: IconPacks,
    settings: QuorviSettings,
    scope: CoroutineScope,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val cache = LruCache<AppKey, ImageBitmap>(MAX_ICONS)
    @Volatile private var pack: String? = null
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    init {
        scope.launch {
            settings.appearance.map { it.iconPack }.distinctUntilChanged().collect {
                pack = it
                cache.evictAll()
                _version.value++
            }
        }
    }
    private val sizePx = (context.resources.displayMetrics.density * ICON_DP).toInt()

    fun peek(key: AppKey): ImageBitmap? = cache.get(key)

    suspend fun load(entry: AppEntry): ImageBitmap? = cache.get(entry.key) ?: withContext(Dispatchers.IO) {
        runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(entry.key.component)
            val dpi = context.resources.displayMetrics.densityDpi
            val themed = pack?.let { packs.mapping(it)?.drawable(entry.key.component, dpi) }
            val drawable = if (themed != null) {
                context.packageManager.getUserBadgedIcon(themed, entry.user)
            } else {
                val info = launcherApps.resolveActivity(intent, entry.user) ?: return@runCatching null
                info.getBadgedIcon(dpi)
            }
            drawable.toBitmap(sizePx, sizePx).asImageBitmap()
        }.getOrNull()?.also { cache.put(entry.key, it) }
    }

    suspend fun preload(entries: List<AppEntry>) = withContext(Dispatchers.IO.limitedParallelism(4)) {
        entries.take(MAX_ICONS).forEach { if (cache.get(it.key) == null) load(it) }
    }

    fun invalidate(packageName: String) {
        cache.snapshot().keys.filter { it.packageName == packageName }.forEach { cache.remove(it) }
    }

    private companion object {
        const val MAX_ICONS = 800
        const val ICON_DP = 56
    }
}
