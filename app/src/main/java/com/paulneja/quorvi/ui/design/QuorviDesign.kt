package com.paulneja.quorvi.ui.design

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.paulneja.quorvi.R
import com.paulneja.quorvi.core.settings.GlassStyle
import com.paulneja.quorvi.core.settings.Look
import com.paulneja.quorvi.core.settings.QuorviSettings
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

val Geist = FontFamily(
    Font(R.font.geist_extralight, FontWeight.ExtraLight),
    Font(R.font.geist_light, FontWeight.Light),
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
)

val GeistMono = FontFamily(
    Font(R.font.geist_mono_regular, FontWeight.Normal),
    Font(R.font.geist_mono_medium, FontWeight.Medium),
)

@Immutable
data class QuorviColors(
    val sunken: Color = Color(0xFF111113),
    val panel: Color = Color(0xFF202024),
    val content: Color = Color(0xFF18181B),
    val field: Color = Color(0x14FFFFFF),
    val border: Color = Color(0x14FFFFFF),
    val text: Color = Color(0xFFF4F5F7),
    val textSecondary: Color = Color(0xA3F4F5F7),
    val textTertiary: Color = Color(0x80F4F5F7),
    val overlayRest: Color = Color(0x0DFFFFFF),
    val overlayHover: Color = Color(0x14FFFFFF),
    val overlayActive: Color = Color(0x29FFFFFF),
    val accent: Color = Color(0xFF5EA7FF),
    val onAccent: Color = Color(0xFF08121F),
    val danger: Color = Color(0xFFFF6B6B),
    val success: Color = Color(0xFF3DDC84),
    val warning: Color = Color(0xFFF5B54A),
    val glass: Color = Color(0xFF0C0F14),
    val scrim: Color = Color(0x8C05080C),
    val onWallpaper: Color = Color(0xFFFFFFFF),
) {
    companion object {
        fun fromScheme(s: ColorScheme) = QuorviColors(
            sunken = s.surfaceContainerLowest,
            panel = s.surfaceContainer,
            content = s.surface,
            field = s.surfaceContainerHighest,
            border = s.outlineVariant,
            text = s.onSurface,
            textSecondary = s.onSurfaceVariant,
            textTertiary = s.onSurfaceVariant.copy(alpha = 0.7f),
            accent = s.primary,
            onAccent = s.onPrimary,
            danger = s.error,
            glass = s.surfaceContainer,
            scrim = s.scrim.copy(alpha = 0.55f),
        )
    }
}

@Immutable
data class QuorviShapes(
    val control: Shape = RoundedCornerShape(12.dp),
    val panel: Shape = RoundedCornerShape(24.dp),
    val icon: Shape = RoundedCornerShape(16.dp),
    val sheet: Shape = RoundedCornerShape(30.dp),
    val pill: Shape = RoundedCornerShape(50),
)

@Immutable
data class QuorviType(
    val clock: TextStyle = TextStyle(fontFamily = Geist, fontSize = 96.sp, lineHeight = 88.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = (-0.035).em, fontFeatureSettings = "tnum"),
    val headline: TextStyle = TextStyle(fontFamily = Geist, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.02).em),
    val title: TextStyle = TextStyle(fontFamily = Geist, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em),
    val metric: TextStyle = TextStyle(fontFamily = Geist, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.02).em, fontFeatureSettings = "tnum"),
    val body: TextStyle = TextStyle(fontFamily = Geist, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    val action: TextStyle = TextStyle(fontFamily = Geist, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    val label: TextStyle = TextStyle(fontFamily = Geist, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
    val caption: TextStyle = TextStyle(fontFamily = Geist, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    val section: TextStyle = TextStyle(fontFamily = Geist, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.06.em),
    val mono: TextStyle = TextStyle(fontFamily = GeistMono, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"),
)

enum class MotionProfile { INSTANT, BALANCED, CINEMATIC }

@Immutable
data class QuorviMotion(val profile: MotionProfile, val speed: Float = 1f, val bounce: Float = 0.35f) {
    val enabled: Boolean get() = profile != MotionProfile.INSTANT

    fun <T> settle(): AnimationSpec<T> = when (profile) {
        MotionProfile.INSTANT -> snap()
        MotionProfile.BALANCED -> spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium * speed * speed)
        MotionProfile.CINEMATIC -> spring(dampingRatio = (1f - bounce * 0.55f).coerceIn(0.35f, 1f), stiffness = 900f * speed * speed)
    }

    fun <T> quick(): AnimationSpec<T> = if (enabled) tween((140 / speed).toInt()) else snap()

    fun <T> panel(): AnimationSpec<T> = if (enabled) tween((180 / speed).toInt()) else snap()

    companion object {
        fun from(look: com.paulneja.quorvi.core.settings.Look): QuorviMotion = when (look.motion) {
            com.paulneja.quorvi.core.settings.MotionStyle.OFF -> QuorviMotion(MotionProfile.INSTANT)
            com.paulneja.quorvi.core.settings.MotionStyle.SUBTLE -> QuorviMotion(MotionProfile.BALANCED, look.speed, 0f)
            com.paulneja.quorvi.core.settings.MotionStyle.FLUID -> QuorviMotion(MotionProfile.CINEMATIC, look.speed, look.bounce)
            com.paulneja.quorvi.core.settings.MotionStyle.DRAMATIC -> QuorviMotion(MotionProfile.CINEMATIC, look.speed * 0.85f, (look.bounce + 0.35f).coerceAtMost(1f))
        }
    }
}

private fun TextStyle.scaled(f: Float): TextStyle = copy(fontSize = fontSize * f, lineHeight = lineHeight * f)

fun QuorviType.scaled(f: Float): QuorviType = if (f == 1f) this else QuorviType(
    clock = clock.scaled(f), headline = headline.scaled(f), title = title.scaled(f), metric = metric.scaled(f), body = body.scaled(f),
    action = action.scaled(f), label = label.scaled(f), caption = caption.scaled(f), section = section.scaled(f), mono = mono.scaled(f),
)

val LocalLook = staticCompositionLocalOf { com.paulneja.quorvi.core.settings.Look() }

val LocalQuorviColors = staticCompositionLocalOf { QuorviColors() }
val LocalQuorviShapes = staticCompositionLocalOf { QuorviShapes() }
val LocalQuorviType = staticCompositionLocalOf { QuorviType() }
val LocalQuorviMotion = staticCompositionLocalOf { QuorviMotion(MotionProfile.CINEMATIC) }

object Quorvi {
    val colors: QuorviColors @Composable @ReadOnlyComposable get() = LocalQuorviColors.current
    val shapes: QuorviShapes @Composable @ReadOnlyComposable get() = LocalQuorviShapes.current
    val type: QuorviType @Composable @ReadOnlyComposable get() = LocalQuorviType.current
    val motion: QuorviMotion @Composable @ReadOnlyComposable get() = LocalQuorviMotion.current
    val look: com.paulneja.quorvi.core.settings.Look @Composable @ReadOnlyComposable get() = LocalLook.current
}

@Composable
fun QuorviDesign(
    profile: MotionProfile = MotionProfile.CINEMATIC,
    accent: Color = QuorviColors().accent,
    wallpaperTint: Color = Color(0xFF2A3140),
    glass: Float = 0.7f,
    look: com.paulneja.quorvi.core.settings.Look? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val motion = remember(profile, look) {
        val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        when {
            scale == 0f -> QuorviMotion(MotionProfile.INSTANT)
            look != null -> QuorviMotion.from(look)
            else -> QuorviMotion(profile)
        }
    }
    val resolved = look ?: rememberStoredLook()
    val scheme = materialScheme(resolved.glass, accent)
    val colors = remember(accent, scheme) { scheme?.let { QuorviColors.fromScheme(it) } ?: QuorviColors(accent = accent) }
    val shapes = remember(resolved.radiusDp) { QuorviShapes(panel = RoundedCornerShape(resolved.radiusDp.dp), sheet = RoundedCornerShape((resolved.radiusDp + 6).dp)) }
    val type = remember(resolved.textScale) { QuorviType().scaled(resolved.textScale) }
    val provided: @Composable () -> Unit = {
        CompositionLocalProvider(
            LocalLook provides resolved,
            LocalQuorviShapes provides shapes,
            LocalQuorviType provides type,
            LocalQuorviMotion provides motion,
            LocalQuorviColors provides colors,
            LocalWallpaperTint provides if (scheme != null) scheme.surfaceTint else wallpaperTint,
            LocalGlassIntensity provides glass,
            content = content,
        )
    }
    if (scheme != null) MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, content = provided) else provided()
}

@Composable
private fun materialScheme(style: GlassStyle, accent: Color): ColorScheme? {
    if (!style.material) return null
    val context = LocalContext.current
    return remember(style, accent) {
        if (style == GlassStyle.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context)
        else darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF08121F),
            primaryContainer = accent.copy(alpha = 0.3f).compositeOver(Color(0xFF1B1D22)),
            surfaceTint = accent,
            background = Color(0xFF121316),
            surface = Color(0xFF121316),
            surfaceContainerLowest = Color(0xFF0D0E11),
            surfaceContainerLow = Color(0xFF1A1B1F),
            surfaceContainer = Color(0xFF1E2024),
            surfaceContainerHigh = Color(0xFF282A2F),
            surfaceContainerHighest = Color(0xFF33353A),
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface LookEntryPoint {
    fun settings(): QuorviSettings
}

@Composable
private fun rememberStoredLook(): Look {
    val context = LocalContext.current
    val flow = remember { EntryPointAccessors.fromApplication(context.applicationContext, LookEntryPoint::class.java).settings().look }
    val look by flow.collectAsState(LookCache.last ?: Look())
    LookCache.last = look
    return look
}

internal object LookCache {
    @Volatile var last: Look? = null
}
