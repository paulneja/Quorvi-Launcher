package com.paulneja.quorvi.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paulneja.quorvi.R
import com.paulneja.quorvi.core.settings.HomePreset
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.liquidGlass

@Composable
fun PresetChooser(current: HomePreset?, firstRun: Boolean, onChoose: (HomePreset, Boolean?) -> Unit, onClose: () -> Unit) {
    var selected by remember { mutableStateOf(current ?: HomePreset.CLEAN) }
    var ai by remember { mutableStateOf(true) }
    if (!firstRun) BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFA070A0E), Color(0xFF070A0E))))
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 20.dp),
        ) {
            Text(stringResource(R.string.onb_title), style = Quorvi.type.headline, color = Quorvi.colors.text)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.onb_sub), style = Quorvi.type.body, color = Quorvi.colors.textSecondary)
            Spacer(Modifier.height(18.dp))
            val presets = HomePreset.entries
            presets.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { p -> PresetCard(p, p == selected, Modifier.weight(1f)) { selected = p } }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (firstRun) {
                com.paulneja.quorvi.ui.design.Panel {
                    com.paulneja.quorvi.ui.design.SwitchRow(stringResource(R.string.ai_onboarding), ai) { ai = it }
                    com.paulneja.quorvi.ui.design.Hint(stringResource(R.string.ai_onboarding_desc))
                }
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(Quorvi.shapes.pill).background(Quorvi.colors.accent).clickable { onChoose(selected, if (firstRun) ai else null) },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(if (firstRun) R.string.onb_start else R.string.onb_apply), style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.onAccent) }
            if (!firstRun) {
                Text(
                    stringResource(R.string.onb_backup_note),
                    style = Quorvi.type.caption,
                    color = Quorvi.colors.textTertiary,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PresetCard(preset: HomePreset, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (name, desc) = when (preset) {
        HomePreset.CLEAN -> stringResource(R.string.preset_clean) to stringResource(R.string.preset_clean_desc)
        HomePreset.FULL -> stringResource(R.string.preset_full) to stringResource(R.string.preset_full_desc)
        HomePreset.BLANK -> stringResource(R.string.preset_blank) to stringResource(R.string.preset_blank_desc)
        HomePreset.MINIMAL -> stringResource(R.string.preset_minimal) to stringResource(R.string.preset_minimal_desc)
    }
    Column(
        modifier
            .liquidGlass(Quorvi.shapes.panel, GlassLevel.PANEL)
            .then(if (selected) Modifier.border(2.dp, Quorvi.colors.accent, Quorvi.shapes.panel) else Modifier)
            .clip(Quorvi.shapes.panel)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.86f).clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF16313A), Color(0xFF0B1418)))).padding(10.dp),
        ) { Column(Modifier.fillMaxSize()) { PresetPreview(preset) } }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text, modifier = Modifier.weight(1f))
            if (preset == HomePreset.CLEAN) Text(stringResource(R.string.preset_suggested), style = Quorvi.type.label, color = Quorvi.colors.onAccent, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Quorvi.colors.accent).padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(desc, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
    }
}

@Composable
private fun ColumnScope.PresetPreview(preset: HomePreset) {
    val block = Color.White.copy(alpha = 0.16f)
    Text("20:10", style = Quorvi.type.clock.copy(fontSize = 30.sp, lineHeight = 30.sp), color = Color.White)
    Text("13°", style = Quorvi.type.caption.copy(fontSize = 9.sp), color = Color.White.copy(alpha = 0.8f))
    when (preset) {
        HomePreset.MINIMAL -> {
            Spacer(Modifier.height(12.dp))
            listOf(0.7f, 0.55f, 0.62f, 0.5f, 0.66f).forEach { w ->
                Box(Modifier.fillMaxWidth(w).height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.7f)))
                Spacer(Modifier.height(9.dp))
            }
            Spacer(Modifier.weight(1f))
        }
        HomePreset.BLANK -> {
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(46.dp).border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text("+", style = Quorvi.type.title, color = Color.White.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(8.dp))
            Bar(1)
        }
        HomePreset.CLEAN, HomePreset.FULL -> {
            Spacer(Modifier.weight(1f))
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(2) { Box(Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(6.dp)).background(block)) }
                }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(4.dp))
            Bar(if (preset == HomePreset.FULL) 3 else 2)
        }
    }
    if (preset != HomePreset.MINIMAL) {
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(6.dp)).background(block))
    }
}

@Composable
private fun Bar(tabs: Int) {
    Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
        repeat(tabs) { i -> Box(Modifier.size(width = 14.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = if (i == 0) 0.9f else 0.4f))) }
    }
    Spacer(Modifier.width(0.dp))
}
