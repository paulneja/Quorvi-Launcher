package com.paulneja.quorvi.launcher.wallpaper

import android.Manifest
import android.app.WallpaperManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class WallpaperSource { NEXO, SYSTEM, NONE }

data class WallpaperState(
    val image: ImageBitmap? = null,
    val blurred: ImageBitmap? = null,
    val source: WallpaperSource = WallpaperSource.NONE,
    val systemIsLive: Boolean = false,
)

@Singleton
class WallpaperRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scope: CoroutineScope,
) {
    private val wallpapers = WallpaperManager.getInstance(context)
    private val file = File(context.filesDir, "wallpaper.jpg")
    private val _state = MutableStateFlow(WallpaperState())
    val state: StateFlow<WallpaperState> = _state.asStateFlow()

    private val targetHeight: Int get() = max(context.resources.displayMetrics.heightPixels, context.resources.displayMetrics.widthPixels)

    init {
        runCatching {
            wallpapers.addOnColorsChangedListener({ _, which ->
                if (which and WallpaperManager.FLAG_SYSTEM != 0 && !file.exists()) scope.launch { reload() }
            }, Handler(Looper.getMainLooper()))
        }
        scope.launch { reload() }
    }

    val canReadSystem: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
            else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    suspend fun reload() = withContext(Dispatchers.IO) {
        val live = wallpapers.wallpaperInfo != null
        val (bitmap, source) = when {
            file.exists() -> runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() to WallpaperSource.NEXO
            canReadSystem && !live -> readSystem() to WallpaperSource.SYSTEM
            else -> null to WallpaperSource.NONE
        }
        _state.value = WallpaperState(bitmap?.asImageBitmap(), bitmap?.let(::soften)?.asImageBitmap(), if (bitmap == null) WallpaperSource.NONE else source, live)
    }

    suspend fun setFromUri(uri: Uri, alsoLockScreen: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeScaled(uri)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            val which = WallpaperManager.FLAG_SYSTEM or if (alsoLockScreen) WallpaperManager.FLAG_LOCK else 0
            wallpapers.setBitmap(bitmap, null, true, which)
            reload()
        }
    }

    suspend fun importWithRoot(): Boolean = withContext(Dispatchers.IO) {
        val dest = file.absolutePath
        val script = "f=/data/system/users/0/wallpaper; [ -f \"\$f\" ] || f=/data/system/users/0/wallpaper_orig; cat \"\$f\" > $dest && chmod 666 $dest"
        val result = com.paulneja.quorvi.control.RootShell.exec(script, com.paulneja.quorvi.control.RootLevel.READ, "", "", 20)
        val ok = result.exitCode == 0 && file.length() > 0 && runCatching { BitmapFactory.decodeFile(file.path) != null }.getOrDefault(false)
        if (!ok) file.delete()
        reload()
        ok
    }

    val rootAvailable: Boolean get() = com.paulneja.quorvi.control.RootShell.available()

    suspend fun useSystem() = withContext(Dispatchers.IO) {
        file.delete()
        reload()
    }

    @Suppress("MissingPermission")
    private fun readSystem(): Bitmap? = runCatching {
        val drawable = wallpapers.drawable ?: return null
        val scale = targetHeight.toFloat() / drawable.intrinsicHeight.coerceAtLeast(1)
        val w = (drawable.intrinsicWidth * scale).roundToInt().coerceAtLeast(1)
        drawable.toBitmap(w, targetHeight)
    }.getOrNull()

    private fun soften(source: Bitmap): Bitmap {
        val width = (source.width / 4).coerceIn(1, 360)
        val height = (width * source.height / source.width.toFloat()).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(source, width, height, true).copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height).also { small.getPixels(it, 0, width, 0, 0, width, height) }
        val blurred = BoxBlur.blur(pixels, width, height, radius = (width / 22).coerceAtLeast(2))
        small.setPixels(blurred, 0, width, 0, 0, width, height)
        return small
    }

    private fun decodeScaled(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val scale = targetHeight.toFloat() / info.size.height
            if (scale < 1f) decoder.setTargetSize((info.size.width * scale).roundToInt(), targetHeight)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
}
