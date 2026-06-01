// The specular highlight and its gravity rotation follow the compose-miuix-ui example
// (Apache-2.0, © 2026 compose-miuix-ui contributors). See licenses/THIRD_PARTY.md.
package com.paulneja.quorvi.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.paulneja.quorvi.ui.design.liquid.lens
import com.paulneja.quorvi.ui.design.liquid.vibrancy
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt

data class GlassBackdrops(val chrome: Backdrop? = null, val overlay: Backdrop? = null, val top: Backdrop? = null)

val LocalGlassBackdrops = staticCompositionLocalOf { GlassBackdrops() }
val LocalGlassHighlight = staticCompositionLocalOf<State<Highlight>?> { null }

private val specular = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.14f),
        innerBlurRadius = 2.dp,
        primaryLight = LightSource(LightPosition(0.5f, -0.3f, -0.05f), Color.White, 1f),
        secondaryLight = LightSource(LightPosition(0.5f, 0.8f, -0.5f), Color.White, 0.4f),
        dualPeak = true,
    ),
)

private const val LIGHT_REF_X = 0.5f
private const val LIGHT_REF_Y = 0.7f
private const val ANGLE_STEP = (3.0 * PI / 180.0).toFloat()

@Composable
fun rememberGravityHighlight(extraDegrees: Float = -45f): State<Highlight> {
    val tilt = rememberDeviceTilt()
    return remember(tilt, extraDegrees) {
        derivedStateOf {
            val t = tilt.value
            val magnitude = t.gravityX * t.gravityX + t.gravityY * t.gravityY
            val angle = if (magnitude > 0.01f) (atan2(t.gravityY, t.gravityX) / ANGLE_STEP).roundToInt() * ANGLE_STEP else (-PI / 2).toFloat()
            val rad = angle + (extraDegrees * PI / 180.0).toFloat()
            val style = specular.style as BloomStroke
            specular.copy(
                style = style.copy(
                    primaryLight = style.primaryLight.copy(
                        position = LightPosition(LIGHT_REF_X + cos(rad), LIGHT_REF_Y + sin(rad), style.primaryLight.position.z),
                    ),
                ),
            )
        }
    }
}

internal data class GlassOptics(val tint: Float, val blur: Dp, val band: Dp, val displacement: Dp, val dispersion: Float)

internal fun opticsFor(level: GlassLevel, look: com.paulneja.quorvi.core.settings.Look = com.paulneja.quorvi.core.settings.Look()): GlassOptics {
    val base = when (level) {
        GlassLevel.CHROME -> GlassOptics(tint = 0.22f, blur = 3.dp, band = 20.dp, displacement = 30.dp, dispersion = 0.12f)
        GlassLevel.PANEL -> GlassOptics(tint = 0.24f, blur = 3.dp, band = 26.dp, displacement = 38.dp, dispersion = 0.12f)
        GlassLevel.OVERLAY -> GlassOptics(tint = 0.40f, blur = 16.dp, band = 28.dp, displacement = 36.dp, dispersion = 0.10f)
        GlassLevel.TOP -> GlassOptics(tint = 0.55f, blur = 24.dp, band = 0.dp, displacement = 0.dp, dispersion = 0f)
    }
    val r = look.refraction / 0.6f
    val tintScale = look.tint / 0.24f
    val extraBlur = (look.blurDp - 3f).dp
    return when (look.glass) {
        com.paulneja.quorvi.core.settings.GlassStyle.LIQUID -> base.copy(
            tint = base.tint * tintScale, blur = (base.blur + extraBlur).coerceAtLeast(0.dp),
            band = base.band * r.coerceAtMost(1.5f), displacement = base.displacement * r, dispersion = if (base.dispersion > 0f) look.dispersion else 0f,
        )
        com.paulneja.quorvi.core.settings.GlassStyle.CLEAR -> base.copy(tint = base.tint * 0.35f * tintScale, blur = 0.dp, band = base.band * r, displacement = base.displacement * r, dispersion = if (base.dispersion > 0f) look.dispersion else 0f)
        com.paulneja.quorvi.core.settings.GlassStyle.FROSTED -> base.copy(tint = (base.tint * 1.6f * tintScale), blur = (base.blur + 18.dp + extraBlur).coerceAtLeast(8.dp), band = 0.dp, displacement = 0.dp, dispersion = 0f)
        com.paulneja.quorvi.core.settings.GlassStyle.SOLID, com.paulneja.quorvi.core.settings.GlassStyle.MATERIAL_YOU, com.paulneja.quorvi.core.settings.GlassStyle.MATERIAL -> base.copy(tint = 0.92f, blur = 0.dp, band = 0.dp, displacement = 0.dp, dispersion = 0f)
    }
}

@Composable
fun Modifier.liquidGlass(
    shape: Shape,
    level: GlassLevel,
    refraction: Dp? = null,
    blurRadius: Dp? = null,
    density: Float? = null,
): Modifier {
    val backdrops = LocalGlassBackdrops.current
    val backdrop = when (level) {
        GlassLevel.OVERLAY -> backdrops.overlay
        GlassLevel.TOP -> backdrops.top
        else -> backdrops.chrome
    }
    val tint = LocalWallpaperTint.current
    val intensity = LocalGlassIntensity.current
    val look = LocalLook.current
    if (look.glass.material) return materialSurface(shape, level)
    if (look.glass == com.paulneja.quorvi.core.settings.GlassStyle.SOLID || backdrop == null || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU || !isRuntimeShaderSupported()) {
        return glass(shape, level, tint, intensity, solid = look.glass == com.paulneja.quorvi.core.settings.GlassStyle.SOLID, withGrain = look.grain || look.glass != com.paulneja.quorvi.core.settings.GlassStyle.SOLID, edge = look.edge)
    }
    val optics = opticsFor(level, look)
    val highlight = LocalGlassHighlight.current
    val alpha = density ?: optics.tint
    val surface = tint.copy(alpha = 0.18f).compositeOver(Color(0xFF0C0F14)).copy(alpha = alpha.coerceIn(0.06f, 0.92f))
    val blur = blurRadius ?: optics.blur
    val displacement = refraction ?: optics.displacement
    val band = if (refraction != null) refraction * 0.7f else optics.band
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            val displacementPx = displacement.toPx()
            if (displacementPx > 0f) padding = maxOf(padding, displacementPx)
            if (blur >= 16.dp) downscaleFactor = 2
            vibrancy()
            if (blur > 0.dp) blur(blur.toPx(), blur.toPx())
            if (displacementPx > 0f) lens(refractionHeight = band.toPx(), refractionAmount = displacementPx, chromaticAberration = optics.dispersion)
        },
        highlight = if (highlight != null && (level == GlassLevel.CHROME || level == GlassLevel.PANEL)) ({ highlight.value.copy(alpha = 0.6f) }) else null,
        onDrawSurface = { drawRect(surface) },
    ).glassRim(shape, look.edge)
}
