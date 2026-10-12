package com.paulneja.quorvi.ui.studio

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paulneja.quorvi.BuildConfig
import com.paulneja.quorvi.R
import com.paulneja.quorvi.core.DefaultHome
import com.paulneja.quorvi.core.settings.GridMode
import com.paulneja.quorvi.labs.LabActivity
import com.paulneja.quorvi.launcher.apps.AppCatalog
import com.paulneja.quorvi.launcher.apps.IconCache
import com.paulneja.quorvi.ui.canvas.AppIconView
import com.paulneja.quorvi.ui.canvas.LocalIconCache
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.ActionRow
import com.paulneja.quorvi.ui.design.ChoiceRow
import com.paulneja.quorvi.ui.design.Hint
import com.paulneja.quorvi.ui.design.IconButton
import com.paulneja.quorvi.ui.design.Label
import com.paulneja.quorvi.ui.design.Panel
import com.paulneja.quorvi.ui.design.Section
import com.paulneja.quorvi.ui.design.Segmented
import com.paulneja.quorvi.ui.design.SliderRow
import com.paulneja.quorvi.ui.design.Stepper
import com.paulneja.quorvi.ui.design.SwitchRow
import com.paulneja.quorvi.ui.design.MotionProfile
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.QuorviDesign
import com.paulneja.quorvi.ui.design.glass
import com.paulneja.quorvi.ui.design.liquidGlass
import top.yukonga.miuix.kmp.blur.layerBackdrop
import com.paulneja.quorvi.ui.design.QuorviSegmented
import com.paulneja.quorvi.core.settings.GlassStyle
import com.paulneja.quorvi.core.settings.MotionStyle
import com.paulneja.quorvi.core.settings.IconStyle
import com.paulneja.quorvi.core.settings.ClockSize
import com.paulneja.quorvi.core.settings.HomePreset
import com.paulneja.quorvi.ui.theme.QuorviTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt
import javax.inject.Inject

private val CLOCK_COLORS = listOf(0xFFFFFFFFL, 0xFFFFD166L, 0xFFFF9F5AL, 0xFFFF8FB1L, 0xFFB388FFL, 0xFF7C9CFFL, 0xFF5EA7FFL, 0xFF4DD0E1L, 0xFF5FD39BL, 0xFF1C1C1EL)
private val ACCENTS = listOf(0xFF5EA7FFL, 0xFF7C9CFFL, 0xFFB388FFL, 0xFFFF8FB1L, 0xFFFF9F5AL, 0xFFFFD166L, 0xFF5FD39BL, 0xFF4DD0E1L)

class StudioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(com.paulneja.quorvi.ui.settings.SettingsActivity.intent(this, com.paulneja.quorvi.ui.settings.SettingsModule.HOME))
        finish()
    }
}

@Composable
internal fun StudioModule(viewModel: StudioViewModel, catalog: AppCatalog, module: com.paulneja.quorvi.ui.settings.SettingsModule, onBack: () -> Unit) {
    val HOME = module == com.paulneja.quorvi.ui.settings.SettingsModule.HOME
    val APPS = module == com.paulneja.quorvi.ui.settings.SettingsModule.APPS
    val BACKUP = module == com.paulneja.quorvi.ui.settings.SettingsModule.BACKUP
    val activityContext = androidx.compose.ui.platform.LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    LaunchedEffect(Unit) { viewModel.refreshWallpaper() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is StudioMessage.Imported -> res.getString(R.string.studio_imported, message.report.placed, message.report.missingApps, message.report.widgets)
                StudioMessage.ImportFailed -> res.getString(R.string.studio_import_failed)
                StudioMessage.Exported -> res.getString(R.string.studio_exported)
                StudioMessage.ExportFailed -> res.getString(R.string.studio_export_failed)
                StudioMessage.LayoutCleared -> res.getString(R.string.settings_reset_layout_done)
            }
            Toast.makeText(activityContext, text, Toast.LENGTH_LONG).show()
        }
    }
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val packs by viewModel.iconPacks.collectAsStateWithLifecycle()
    val hidden by viewModel.hiddenApps.collectAsStateWithLifecycle()
    val unsuggested by viewModel.unsuggestedApps.collectAsStateWithLifecycle()
    val wallpaper by viewModel.wallpaper.collectAsStateWithLifecycle()
    val apps by catalog.apps.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { viewModel.setWallpaper(it, true) } }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(viewModel::export) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::import) }
    val colors = Quorvi.colors
    val look by viewModel.look.collectAsStateWithLifecycle()
    val preset by viewModel.preset.collectAsStateWithLifecycle()
    var choosing by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var importing by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var replaceDock by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val importResult by viewModel.importResult.collectAsStateWithLifecycle()
    val dbPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.importFromFile(it, replaceDock) } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.sunken, colors.content))),
    ) {
        LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(Glyphs.Back, onBack)
                    Text(stringResource(module.title), style = Quorvi.type.title, color = colors.text, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    if (HOME) {
                        IconButton(Glyphs.Undo, viewModel::undo, enabled = history.first)
                        IconButton(Glyphs.Redo, viewModel::redo, enabled = history.second)
                    }
                }
            }
            if (HOME) stickyHeader { LivePreview(apps.orEmpty().take(4), wallpaper.image, appearance.iconScale) }

            if (HOME) {
            item { Section(stringResource(R.string.set_start)) }
            item {
                Panel {
                    ActionRow(stringResource(R.string.set_preset), presetName(preset)) { choosing = true }
                    if (viewModel.hasBackup) ActionRow(stringResource(R.string.set_restore), stringResource(R.string.set_restore_hint)) { viewModel.restoreBackup() }
                    ActionRow(stringResource(R.string.import_title), stringResource(R.string.import_hint)) { importing = !importing }
                    if (importing) {
                        SwitchRow(stringResource(R.string.import_dock), replaceDock) { replaceDock = it }
                        val sources = remember { viewModel.importSources }
                        val root = remember { viewModel.rootAvailable }
                        sources.forEach { src ->
                            ActionRow(src.label, stringResource(if (root) R.string.import_with_root else R.string.import_needs_root)) {
                                if (root) viewModel.importFromRoot(src.packageName, replaceDock)
                            }
                        }
                        ActionRow(stringResource(R.string.import_file), stringResource(R.string.import_file_hint)) { dbPicker.launch(arrayOf("*/*")) }
                        importResult?.let { Hint(it) }
                    }
                }
            }
            }

            if (HOME) {
            item { Section(stringResource(R.string.set_glass)) }
            item {
                Panel {
                    QuorviSegmented(
                        listOf(false to stringResource(R.string.style_glass), true to stringResource(R.string.style_material)),
                        look.glass.material, { m -> viewModel.setLook { it.copy(glass = if (m) GlassStyle.MATERIAL_YOU else GlassStyle.LIQUID) } }, Modifier.padding(vertical = 8.dp),
                    )
                    if (look.glass.material) {
                        QuorviSegmented(
                            listOf(GlassStyle.MATERIAL_YOU to stringResource(R.string.style_material_you), GlassStyle.MATERIAL to stringResource(R.string.style_material3)),
                            look.glass, { g -> viewModel.setLook { it.copy(glass = g) } }, Modifier.padding(vertical = 8.dp),
                        )
                        Hint(stringResource(if (look.glass == GlassStyle.MATERIAL_YOU) R.string.style_material_you_hint else R.string.style_material3_hint))
                        SliderRow(stringResource(R.string.glass_radius), look.radiusDp, 6f..40f, { "${it.roundToInt()} dp" }) { v -> viewModel.setLook { it.copy(radiusDp = v) } }
                    } else {
                        QuorviSegmented(
                            listOf(GlassStyle.CLEAR to stringResource(R.string.glass_clear), GlassStyle.LIQUID to stringResource(R.string.glass_liquid), GlassStyle.FROSTED to stringResource(R.string.glass_frosted), GlassStyle.SOLID to stringResource(R.string.glass_solid)),
                            look.glass, { g -> viewModel.setLook { it.copy(glass = g) } }, Modifier.padding(vertical = 8.dp),
                        )
                        SliderRow(stringResource(R.string.glass_refraction), look.refraction, 0f..1f) { v -> viewModel.setLook { it.copy(refraction = v) } }
                        SliderRow(stringResource(R.string.glass_blur), look.blurDp, 0f..32f, { "${it.roundToInt()} dp" }) { v -> viewModel.setLook { it.copy(blurDp = v) } }
                        SliderRow(stringResource(R.string.glass_tint), look.tint, 0f..0.9f) { v -> viewModel.setLook { it.copy(tint = v) } }
                        SliderRow(stringResource(R.string.glass_edge), look.edge, 0f..1f) { v -> viewModel.setLook { it.copy(edge = v) } }
                        SliderRow(stringResource(R.string.glass_dispersion), look.dispersion, 0f..0.4f) { v -> viewModel.setLook { it.copy(dispersion = v) } }
                        SliderRow(stringResource(R.string.glass_radius), look.radiusDp, 6f..40f, { "${it.roundToInt()} dp" }) { v -> viewModel.setLook { it.copy(radiusDp = v) } }
                        SwitchRow(stringResource(R.string.glass_tilt), look.tiltHighlight) { v -> viewModel.setLook { it.copy(tiltHighlight = v) } }
                        SwitchRow(stringResource(R.string.glass_grain), look.grain) { v -> viewModel.setLook { it.copy(grain = v) } }
                    }
                    ActionRow(stringResource(R.string.set_reset_look), null) { viewModel.resetLook() }
                }
            }
            }

            if (HOME) {
            item { Section(stringResource(R.string.set_motion)) }
            item {
                Panel {
                    QuorviSegmented(
                        listOf(MotionStyle.OFF to stringResource(R.string.motion_off), MotionStyle.SUBTLE to stringResource(R.string.motion_subtle), MotionStyle.FLUID to stringResource(R.string.motion_fluid), MotionStyle.DRAMATIC to stringResource(R.string.motion_dramatic)),
                        look.motion, { m -> viewModel.setLook { it.copy(motion = m) } }, Modifier.padding(vertical = 8.dp),
                    )
                    SwitchRow(stringResource(R.string.motion_120), look.highRefresh) { v -> viewModel.setLook { it.copy(highRefresh = v) } }
                    SliderRow(stringResource(R.string.motion_speed), look.speed, 0.5f..2f, { "%.1f×".format(it) }) { v -> viewModel.setLook { it.copy(speed = v) } }
                    SliderRow(stringResource(R.string.motion_bounce), look.bounce, 0f..1f) { v -> viewModel.setLook { it.copy(bounce = v) } }
                    SwitchRow(stringResource(R.string.motion_app_open), look.appOpenFromIcon) { v -> viewModel.setLook { it.copy(appOpenFromIcon = v) } }
                    SwitchRow(stringResource(R.string.motion_panel_grow), look.panelGrow) { v -> viewModel.setLook { it.copy(panelGrow = v) } }
                    SwitchRow(stringResource(R.string.motion_parallax), look.parallax) { v -> viewModel.setLook { it.copy(parallax = v) } }
                    SwitchRow(stringResource(R.string.motion_press), look.pressScale) { v -> viewModel.setLook { it.copy(pressScale = v) } }
                    SwitchRow(stringResource(R.string.motion_haptics), look.haptics) { v -> viewModel.setLook { it.copy(haptics = v) } }
                }
            }
            }

            if (HOME) {
            item { Section(stringResource(R.string.set_icons)) }
            item {
                Panel {
                    QuorviSegmented(
                        listOf(IconStyle.ORIGINAL to stringResource(R.string.icon_original), IconStyle.TINTED to stringResource(R.string.icon_tinted), IconStyle.ROUND to stringResource(R.string.icon_round), IconStyle.SQUIRCLE to stringResource(R.string.icon_squircle)),
                        look.iconStyle, { st -> viewModel.setLook { it.copy(iconStyle = st) } }, Modifier.padding(vertical = 8.dp),
                    )
                    SliderRow(stringResource(R.string.studio_icon_size), appearance.iconScale, 0.8f..1.25f, { "${(it * 100).roundToInt()} %" }) { v -> viewModel.setAppearance { it.copy(iconScale = v) } }
                    SwitchRow(stringResource(R.string.studio_labels), appearance.showLabels) { v -> viewModel.setAppearance { it.copy(showLabels = v) } }
                    SwitchRow(stringResource(R.string.icon_shadow), look.iconShadow) { v -> viewModel.setLook { it.copy(iconShadow = v) } }
                }
            }
            }

            if (HOME) {
            item { Section(stringResource(R.string.set_clock)) }
            item {
                Panel {
                    Label(stringResource(R.string.clock_style))
                    QuorviSegmented(
                        listOf(com.paulneja.quorvi.core.settings.ClockStyle.CLASSIC to stringResource(R.string.clock_style_classic), com.paulneja.quorvi.core.settings.ClockStyle.STACKED to stringResource(R.string.clock_style_stacked), com.paulneja.quorvi.core.settings.ClockStyle.MINIMAL to stringResource(R.string.clock_style_minimal)),
                        look.clockStyle, { s -> viewModel.setLook { it.copy(clockStyle = s) } }, Modifier.padding(vertical = 8.dp),
                    )
                    Label(stringResource(R.string.clock_font))
                    QuorviSegmented(
                        listOf(com.paulneja.quorvi.core.settings.ClockFont.GEIST to "Geist", com.paulneja.quorvi.core.settings.ClockFont.MONO to "Mono", com.paulneja.quorvi.core.settings.ClockFont.SERIF to "Serif", com.paulneja.quorvi.core.settings.ClockFont.CONDENSED to stringResource(R.string.clock_font_condensed)),
                        look.clockFont, { f -> viewModel.setLook { it.copy(clockFont = f) } }, Modifier.padding(vertical = 8.dp),
                    )
                    SliderRow(stringResource(R.string.clock_size), look.clockScale, 0.45f..1.6f, { "${(it * 100).roundToInt()} %" }) { v -> viewModel.setLook { it.copy(clockScale = v) } }
                    Label(stringResource(R.string.clock_weight))
                    QuorviSegmented(
                        listOf(200 to stringResource(R.string.clock_weight_thin), 300 to stringResource(R.string.clock_weight_light), 400 to stringResource(R.string.clock_weight_regular), 600 to stringResource(R.string.clock_weight_bold)),
                        look.clockWeight, { w -> viewModel.setLook { it.copy(clockWeight = w) } }, Modifier.padding(vertical = 8.dp),
                    )
                    Label(stringResource(R.string.clock_align))
                    QuorviSegmented(
                        listOf(com.paulneja.quorvi.core.settings.ClockAlign.START to stringResource(R.string.clock_align_start), com.paulneja.quorvi.core.settings.ClockAlign.CENTER to stringResource(R.string.clock_align_center), com.paulneja.quorvi.core.settings.ClockAlign.END to stringResource(R.string.clock_align_end)),
                        look.clockAlign, { a -> viewModel.setLook { it.copy(clockAlign = a) } }, Modifier.padding(vertical = 8.dp),
                    )
                    Label(stringResource(R.string.clock_hours))
                    QuorviSegmented(
                        listOf(com.paulneja.quorvi.core.settings.HourFormat.SYSTEM to stringResource(R.string.clock_hours_system), com.paulneja.quorvi.core.settings.HourFormat.H24 to "24 h", com.paulneja.quorvi.core.settings.HourFormat.H12 to "12 h"),
                        look.clockHours, { h -> viewModel.setLook { it.copy(clockHours = h) } }, Modifier.padding(vertical = 8.dp),
                    )
                    SwitchRow(stringResource(R.string.clock_seconds), look.clockSeconds) { v -> viewModel.setLook { it.copy(clockSeconds = v) } }
                    Label(stringResource(R.string.clock_date))
                    QuorviSegmented(
                        listOf(com.paulneja.quorvi.core.settings.ClockDate.FULL to stringResource(R.string.clock_date_full), com.paulneja.quorvi.core.settings.ClockDate.SHORT to stringResource(R.string.clock_date_short), com.paulneja.quorvi.core.settings.ClockDate.NONE to stringResource(R.string.clock_date_none)),
                        look.clockDate, { d -> viewModel.setLook { it.copy(clockDate = d) } }, Modifier.padding(vertical = 8.dp),
                    )
                    Label(stringResource(R.string.clock_color))
                    QuorviSegmented(
                        listOf(com.paulneja.quorvi.core.settings.ClockColor.WHITE to stringResource(R.string.clock_color_white), com.paulneja.quorvi.core.settings.ClockColor.ACCENT to stringResource(R.string.clock_color_accent), com.paulneja.quorvi.core.settings.ClockColor.CUSTOM to stringResource(R.string.clock_color_custom)),
                        look.clockColor, { c -> viewModel.setLook { it.copy(clockColor = c) } }, Modifier.padding(vertical = 8.dp),
                    )
                    if (look.clockColor == com.paulneja.quorvi.core.settings.ClockColor.CUSTOM) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                            items(CLOCK_COLORS) { value ->
                                val selected = value == look.clockCustomColor
                                Box(Modifier.size(32.dp).clip(CircleShape).background(Color(value)).border(if (selected) 3.dp else 0.dp, colors.text, CircleShape).clickable { viewModel.setLook { it.copy(clockCustomColor = value) } })
                            }
                        }
                    }
                    SwitchRow(stringResource(R.string.clock_shadow), look.clockShadow) { v -> viewModel.setLook { it.copy(clockShadow = v) } }
                    SwitchRow(stringResource(R.string.clock_weather), look.clockWeather) { v -> viewModel.setLook { it.copy(clockWeather = v) } }
                    SwitchRow(stringResource(R.string.clock_alarm), look.clockAlarm) { v -> viewModel.setLook { it.copy(clockAlarm = v) } }
                }
            }
            }

            if (HOME) {
            item { Section(stringResource(R.string.set_bar)) }
            item {
                Panel {
                    Text(stringResource(R.string.orientation), style = Quorvi.type.body, color = Quorvi.colors.text, modifier = Modifier.padding(top = 10.dp))
                    QuorviSegmented(
                        listOf(
                            com.paulneja.quorvi.core.settings.ScreenOrientation.AUTO to stringResource(R.string.orientation_auto),
                            com.paulneja.quorvi.core.settings.ScreenOrientation.PORTRAIT to stringResource(R.string.orientation_portrait),
                            com.paulneja.quorvi.core.settings.ScreenOrientation.LANDSCAPE to stringResource(R.string.orientation_landscape),
                        ),
                        look.orientation, { o -> viewModel.setLook { it.copy(orientation = o) } }, Modifier.padding(vertical = 8.dp),
                    )
                    SwitchRow(stringResource(R.string.bar_show), look.showBar) { v -> viewModel.setLook { it.copy(showBar = v) } }
                    if (look.showBar) SwitchRow(stringResource(R.string.bar_labels), look.barLabels) { v -> viewModel.setLook { it.copy(barLabels = v) } }
                    SwitchRow(stringResource(R.string.bar_pill), look.showPill) { v -> viewModel.setLook { it.copy(showPill = v) } }
                    SwitchRow(stringResource(R.string.bar_minimal), look.minimalChrome) { v -> viewModel.setLook { it.copy(minimalChrome = v) } }
                }
            }
            }

            if (HOME) {
            item { Section(stringResource(R.string.set_text_color)) }
            item {
                Panel {
                    Label(stringResource(R.string.studio_accent))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                        items(ACCENTS) { value ->
                            val selected = value == appearance.accent
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(value))
                                    .border(if (selected) 3.dp else 0.dp, colors.text, CircleShape)
                                    .clickable { viewModel.setAppearance { it.copy(accent = value) } },
                            )
                        }
                    }
                    SliderRow(stringResource(R.string.text_scale), look.textScale, 0.85f..1.25f, { "${(it * 100).roundToInt()} %" }) { v -> viewModel.setLook { it.copy(textScale = v) } }
                }
            }
            }

            if (HOME) {
            item { Section(stringResource(R.string.studio_grid)) }
            item {
                Panel {
                    Segmented(
                        options = listOf(GridMode.AUTO to stringResource(R.string.grid_auto), GridMode.MANUAL to stringResource(R.string.grid_manual)),
                        selected = layout.gridMode,
                    ) { mode ->
                        val auto = viewModel.autoSpec
                        viewModel.setGrid(mode, if (layout.gridMode == GridMode.AUTO) auto.columns else layout.columns, if (layout.gridMode == GridMode.AUTO) auto.rows else layout.rows)
                    }
                    if (layout.gridMode == GridMode.MANUAL) {
                        Stepper(stringResource(R.string.grid_columns), layout.columns, 3..8) { viewModel.setGrid(GridMode.MANUAL, it, layout.rows) }
                        Stepper(stringResource(R.string.grid_rows), layout.rows, 3..10) { viewModel.setGrid(GridMode.MANUAL, layout.columns, it) }
                    } else {
                        Hint(stringResource(R.string.grid_auto_hint, viewModel.autoSpec.columns, viewModel.autoSpec.rows))
                    }
                }
            }
            }

            if (HOME) {
            item { Section(stringResource(R.string.wallpaper_title)) }
            item {
                Panel {
                    ActionRow(stringResource(R.string.wallpaper_pick), stringResource(R.string.wallpaper_pick_hint)) {
                        photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    ActionRow(stringResource(R.string.wallpaper_current), if (wallpaper.systemIsLive) stringResource(R.string.wallpaper_current_live) else null) { viewModel.useSystemWallpaper() }
                }
            }
            }

            if (HOME) {
            item { Section(stringResource(R.string.studio_icon_pack)) }
            item {
                Panel {
                    ChoiceRow(stringResource(R.string.studio_icon_pack_system), appearance.iconPack == null) { viewModel.setAppearance { it.copy(iconPack = null) } }
                    packs.forEach { pack ->
                        ChoiceRow(pack.label, appearance.iconPack == pack.packageName) { viewModel.setAppearance { it.copy(iconPack = pack.packageName) } }
                    }
                    if (packs.isEmpty()) Hint(stringResource(R.string.studio_icon_pack_none))
                }
            }
            }

            if (APPS) {
            item { Section(stringResource(R.string.studio_hidden)) }
            item {
                Panel {
                    if (hidden.isEmpty()) Hint(stringResource(R.string.studio_hidden_none))
                    hidden.forEach { app ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                            com.paulneja.quorvi.ui.canvas.AppIcon(app, 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, style = Quorvi.type.body, color = colors.text, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.studio_unhide), style = Quorvi.type.action, color = colors.accent, modifier = Modifier.clip(Quorvi.shapes.control).clickable { viewModel.unhide(app) }.padding(8.dp))
                        }
                    }
                }
            }
            }

            if (APPS) {
            item { Section(stringResource(R.string.studio_unsuggested)) }
            item {
                Panel {
                    if (unsuggested.isEmpty()) Hint(stringResource(R.string.studio_unsuggested_none))
                    unsuggested.forEach { app ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                            com.paulneja.quorvi.ui.canvas.AppIcon(app, 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, style = Quorvi.type.body, color = colors.text, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.studio_resuggest), style = Quorvi.type.action, color = colors.accent, modifier = Modifier.clip(Quorvi.shapes.control).clickable { viewModel.resuggest(app) }.padding(8.dp))
                        }
                    }
                }
            }
            }

            if (BACKUP) {
            item { Section(stringResource(R.string.studio_scene)) }
            item {
                Panel {
                    ActionRow(stringResource(R.string.studio_export), stringResource(R.string.studio_export_hint)) { exporter.launch("quorvi-scene.json") }
                    ActionRow(stringResource(R.string.studio_import), stringResource(R.string.studio_import_hint)) { importer.launch(arrayOf("application/json", "text/plain")) }
                    ActionRow(stringResource(R.string.studio_reset), null) { viewModel.resetDefaults() }
                }
            }
            }

            if (BACKUP) {
            item { Section(stringResource(R.string.fb_title)) }
            item { FullBackupPanel() }
            }

            if (BACKUP) {
            item { Section(stringResource(R.string.studio_launcher)) }
            item {
                Panel {
                    val isDefault = DefaultHome.isHeld(context)
                    ActionRow(
                        stringResource(R.string.settings_default_home),
                        stringResource(if (isDefault) R.string.settings_default_home_on else R.string.settings_default_home_off),
                    ) { runCatching { context.startActivity(DefaultHome.settingsIntent()) } }
                    ActionRow(stringResource(R.string.settings_reset_layout), null, danger = true) { viewModel.clearLayout() }
                    if (BuildConfig.LABS) ActionRow(stringResource(R.string.settings_labs), null) { context.startActivity(Intent(context, LabActivity::class.java)) }
                    Hint(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
                }
            }
            }
        }
        if (choosing) {
            com.paulneja.quorvi.ui.onboarding.PresetChooser(preset, firstRun = false, onChoose = { chosen, _ -> viewModel.applyPreset(chosen); choosing = false }, onClose = { choosing = false })
        }
    }
}

@Composable
private fun presetName(preset: HomePreset?): String? = when (preset) {
    HomePreset.CLEAN -> stringResource(R.string.preset_clean)
    HomePreset.FULL -> stringResource(R.string.preset_full)
    HomePreset.BLANK -> stringResource(R.string.preset_blank)
    HomePreset.MINIMAL -> stringResource(R.string.preset_minimal)
    null -> null
}

@Composable
private fun LivePreview(apps: List<com.paulneja.quorvi.launcher.apps.AppEntry>, wallpaper: androidx.compose.ui.graphics.ImageBitmap?, iconScale: Float) {
    val layer = top.yukonga.miuix.kmp.blur.rememberLayerBackdrop()
    val highlight = com.paulneja.quorvi.ui.design.rememberGravityHighlight()
    Box(
        Modifier
            .background(Quorvi.colors.sunken)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(230.dp)
            .clip(Quorvi.shapes.sheet),
    ) {
        Box(Modifier.fillMaxSize().then(if (wallpaper != null) Modifier.layerBackdrop(layer) else Modifier).background(Brush.linearGradient(listOf(Color(0xFF16313A), Color(0xFF3A2556))))) {
            wallpaper?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        CompositionLocalProvider(
            com.paulneja.quorvi.ui.design.LocalGlassBackdrops provides com.paulneja.quorvi.ui.design.GlassBackdrops(chrome = if (wallpaper != null) layer else null),
            com.paulneja.quorvi.ui.design.LocalGlassHighlight provides if (Quorvi.look.tiltHighlight) highlight else null,
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.weight(1f).height(120.dp)) { com.paulneja.quorvi.ui.canvas.ClockBlock() }
                    Box(Modifier.width(150.dp).height(64.dp)) {
                        com.paulneja.quorvi.ui.design.BentoBlock(padding = 10.dp) { com.paulneja.quorvi.ui.design.BlockHeader(Glyphs.Agent, androidx.compose.ui.res.stringResource(com.paulneja.quorvi.R.string.panel_agent)) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().height(68.dp).liquidGlass(Quorvi.shapes.panel, GlassLevel.CHROME).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    apps.forEach { AppIconView(it, 42.dp * iconScale, false) }
                }
            }
        }
    }
}
