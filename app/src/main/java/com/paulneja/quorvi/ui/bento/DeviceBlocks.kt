package com.paulneja.quorvi.ui.bento

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paulneja.quorvi.R
import com.paulneja.quorvi.launcher.live.SystemInfo
import com.paulneja.quorvi.ui.design.BentoBlock
import com.paulneja.quorvi.ui.design.Glyph
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.QuorviSlider
import com.paulneja.quorvi.ui.panels.PanelsViewModel
import java.util.Locale

@Composable
fun DeviceBlock(panels: PanelsViewModel) {
    val battery by panels.battery.collectAsStateWithLifecycle()
    val system by panels.system.collectAsStateWithLifecycle()
    BentoBlock(padding = 14.dp) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val roomy = maxHeight > 120.dp
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                val percent = battery?.percent ?: 0
                val low = percent <= 20 && battery?.charging != true
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$percent", style = Quorvi.type.title.copy(fontSize = 34.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), color = if (low) Quorvi.colors.warning else Quorvi.colors.text)
                    Text("%", style = Quorvi.type.action, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(when { battery?.charging == true -> R.string.dev_charging; low -> R.string.dev_low; else -> R.string.dev_battery }),
                            style = Quorvi.type.caption.copy(fontWeight = FontWeight.Medium), color = if (battery?.charging == true) Quorvi.colors.success else Quorvi.colors.textSecondary,
                        )
                        battery?.temperatureC?.let { Text("${kotlin.math.round(it).toInt()} °C", style = Quorvi.type.caption, color = Quorvi.colors.textTertiary) }
                    }
                }
                BatteryBar(percent / 100f, charging = battery?.charging == true, low = low)
                if (roomy) system?.let { s ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        UsageLine(stringResource(R.string.dev_storage), stringResource(R.string.dev_free, bytes(s.storageTotal - s.storageUsed)), s.storageFraction)
                        UsageLine(stringResource(R.string.dev_memory), stringResource(R.string.dev_of, bytes(s.ramUsed), bytes(s.ramTotal)), s.ramFraction)
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryBar(fraction: Float, charging: Boolean, low: Boolean) {
    val fill by animateFloatAsState(fraction.coerceIn(0.02f, 1f), spring(dampingRatio = 0.8f, stiffness = 120f), label = "battery")
    val glow = rememberInfiniteTransition(label = "charge")
    val sweep by glow.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "sweep")
    val start = if (low) Quorvi.colors.warning else Quorvi.colors.success
    val end = if (low) Quorvi.colors.danger else Quorvi.colors.accent
    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(Color.White.copy(alpha = 0.08f), cornerRadius = radius)
        val w = size.width * fill
        drawRoundRect(Brush.horizontalGradient(listOf(start, end), 0f, size.width), size = Size(w, size.height), cornerRadius = radius)

        if (charging) {
            val x = size.width * sweep
            drawRoundRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent), x - size.width * 0.18f, x + size.width * 0.18f),
                size = Size(w, size.height), cornerRadius = radius,
            )
        }
    }
}

@Composable
private fun UsageLine(label: String, value: String, fraction: Float) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.weight(1f), maxLines = 1)
            Text(value, style = Quorvi.type.caption.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Medium), color = Quorvi.colors.text, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "usage")
        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(Color.White.copy(alpha = 0.08f), cornerRadius = radius)
            drawRoundRect(Color.White.copy(alpha = if (fraction > 0.9f) 0.9f else 0.55f), size = Size(size.width * f, size.height), cornerRadius = radius)
        }
    }
}

private fun bytes(value: Long): String {
    val gb = value / 1_073_741_824.0
    return if (gb >= 10) String.format(Locale.getDefault(), "%.0f GB", gb) else String.format(Locale.getDefault(), "%.1f GB", gb)
}

@Composable
fun ConnectionsBlock(panels: PanelsViewModel) {
    val net by panels.net.collectAsStateWithLifecycle()
    val device by panels.device.collectAsStateWithLifecycle()
    BentoBlock(padding = 12.dp) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val labels = maxHeight > 96.dp
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val data = net.mobileDataEnabled ?: net.cellularActive
                RoundSwitch(Glyphs.Wifi, stringResource(R.string.net_wifi), stateLabel(net.wifiConnected, net.wifiEnabled), net.wifiEnabled, labels) { panels.wifi(!net.wifiEnabled) }
                RoundSwitch(Glyphs.Cellular, stringResource(R.string.tg_data), stateLabel(data, data), data, labels) { panels.mobileData(!data) }
                RoundSwitch(Glyphs.Bluetooth, stringResource(R.string.tg_bt), stateLabel(false, net.bluetoothEnabled == true), net.bluetoothEnabled == true, labels) { panels.bluetooth(net.bluetoothEnabled != true) }
                if (device.hasTorch) RoundSwitch(Glyphs.Torch, stringResource(R.string.tg_torch), stateLabel(false, device.torch), device.torch, labels) { panels.torch() }
                RoundSwitch(Glyphs.Moon, stringResource(R.string.tg_dnd), stateLabel(false, device.dnd), device.dnd, labels) { panels.dnd() }
            }
        }
        Spacer(Modifier.height(8.dp))
        QuorviSlider(device.brightness, { if (device.canWriteSettings) panels.brightness(it) }, glyph = Glyphs.Brightness, height = 32.dp, onDone = { panels.brightness(it) })
    }
}

@Composable
private fun stateLabel(connected: Boolean, on: Boolean): String = stringResource(when { connected -> R.string.net_connected; on -> R.string.dev_on; else -> R.string.net_off })

@Composable
private fun RoundSwitch(glyph: ImageVector, label: String, state: String, on: Boolean, showLabels: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.45f, stiffness = 600f), label = "press")
    val bg by animateColorAsState(if (on) Quorvi.colors.accent else Color.White.copy(alpha = 0.08f), tween(260), label = "bg")
    val tint by animateColorAsState(if (on) Quorvi.colors.onAccent else Quorvi.colors.text, tween(260), label = "tint")
    val halo by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 200f), label = "halo")
    val accent = Quorvi.colors.accent
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)) {
            Canvas(Modifier.size(54.dp)) {
                if (halo > 0f) drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f * halo), Color.Transparent), center, size.minDimension / 2), size.minDimension / 2 * (0.8f + 0.2f * halo))
            }
            Box(
                Modifier.size(44.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(bg)
                    .clickable(interactionSource = source, indication = null, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) { Glyph(glyph, 20.dp, tint) }
        }
        if (showLabels) {
            Text(label, style = Quorvi.type.label.copy(fontWeight = FontWeight.Medium), color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(state, style = Quorvi.type.label.copy(fontSize = 10.sp), color = if (on) Quorvi.colors.accent else Quorvi.colors.textTertiary, maxLines = 1)
        }
    }
}
