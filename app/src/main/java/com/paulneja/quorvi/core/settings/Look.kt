package com.paulneja.quorvi.core.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class GlassStyle {
    CLEAR, LIQUID, FROSTED, SOLID, MATERIAL_YOU, MATERIAL;

    val material: Boolean get() = this == MATERIAL_YOU || this == MATERIAL
}

enum class MotionStyle { OFF, SUBTLE, FLUID, DRAMATIC }

enum class IconStyle { ORIGINAL, TINTED, ROUND, SQUIRCLE }

enum class ClockSize { SMALL, MEDIUM, LARGE }

enum class ClockStyle { CLASSIC, STACKED, MINIMAL }

enum class ClockFont { GEIST, MONO, SERIF, CONDENSED }

enum class ClockAlign { START, CENTER, END }

enum class HourFormat { SYSTEM, H12, H24 }

enum class ClockDate { FULL, SHORT, NONE }

enum class ClockColor { WHITE, ACCENT, CUSTOM }

enum class ScreenOrientation { AUTO, PORTRAIT, LANDSCAPE }

enum class HomePreset { CLEAN, FULL, BLANK, MINIMAL }

@Serializable
data class Look(
    val glass: GlassStyle = GlassStyle.LIQUID,
    val refraction: Float = 0.6f,
    val blurDp: Float = 3f,
    val tint: Float = 0.24f,
    val edge: Float = 0.75f,
    val dispersion: Float = 0.12f,
    val radiusDp: Float = 24f,
    val tiltHighlight: Boolean = true,
    val grain: Boolean = false,
    val motion: MotionStyle = MotionStyle.FLUID,
    val speed: Float = 1f,
    val bounce: Float = 0.35f,
    val highRefresh: Boolean = true,
    val appOpenFromIcon: Boolean = true,
    val panelGrow: Boolean = true,
    val parallax: Boolean = true,
    val pressScale: Boolean = true,
    val haptics: Boolean = true,
    val iconStyle: IconStyle = IconStyle.ORIGINAL,
    val iconShadow: Boolean = false,
    val clockSize: ClockSize = ClockSize.LARGE,
    val clockThin: Boolean = true,
    val clockWeather: Boolean = true,
    val clockAlarm: Boolean = true,
    val clockStyle: ClockStyle = ClockStyle.CLASSIC,
    val clockFont: ClockFont = ClockFont.GEIST,
    val clockScale: Float = 1f,
    val clockWeight: Int = 200,
    val clockAlign: ClockAlign = ClockAlign.START,
    val clockHours: HourFormat = HourFormat.SYSTEM,
    val clockSeconds: Boolean = false,
    val clockDate: ClockDate = ClockDate.FULL,
    val clockColor: ClockColor = ClockColor.WHITE,
    val clockCustomColor: Long = 0xFFFFD166L,
    val clockShadow: Boolean = true,
    val textScale: Float = 1f,
    val orientation: ScreenOrientation = ScreenOrientation.AUTO,
    val showBar: Boolean = false,
    val barLabels: Boolean = true,
    val showPill: Boolean = true,
    val accentFromWallpaper: Boolean = false,
    val minimalChrome: Boolean = false,
) {
    fun clamped(): Look = copy(
        refraction = refraction.coerceIn(0f, 1f),
        blurDp = blurDp.coerceIn(0f, 32f),
        tint = tint.coerceIn(0f, 0.9f),
        edge = edge.coerceIn(0f, 1f),
        dispersion = dispersion.coerceIn(0f, 0.4f),
        radiusDp = radiusDp.coerceIn(6f, 40f),
        speed = speed.coerceIn(0.5f, 2f),
        bounce = bounce.coerceIn(0f, 1f),
        textScale = textScale.coerceIn(0.85f, 1.25f),
        clockScale = clockScale.coerceIn(0.45f, 1.6f),
        clockWeight = clockWeight.coerceIn(100, 700),
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        fun decode(raw: String?): Look = raw?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }?.clamped() ?: Look()

        fun encode(look: Look): String = json.encodeToString(serializer(), look.clamped())
    }
}
