package com.paulneja.quorvi.ui.control

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.paulneja.quorvi.R
import com.paulneja.quorvi.control.ActiveNotification
import com.paulneja.quorvi.control.DeviceControls
import com.paulneja.quorvi.control.MediaInfo
import com.paulneja.quorvi.control.NotificationHub
import com.paulneja.quorvi.ops.CoreClient
import com.paulneja.quorvi.ops.OpsDao
import com.paulneja.quorvi.ops.RunState
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.operations.Chip
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ControlViewModel @Inject constructor(
    val controls: DeviceControls,
    private val hub: NotificationHub,
    private val core: CoreClient,
    ops: OpsDao,
    connections: com.paulneja.quorvi.connections.ConnectionsDao,
) : ViewModel() {
    val notifications = hub.active.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val listening = hub.connected
    val activeRuns = ops.observeRuns(30).map { runs -> runs.count { runCatching { !RunState.valueOf(it.state).terminal }.getOrDefault(false) } }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val connectorIssues = connections.observeHosts().map { hosts -> hosts.count { it.healthError != null } }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun dismiss(n: ActiveNotification) = hub.dismiss?.invoke(n.key)
    fun killAll() = core.killAll()
}

@Composable
fun ControlStudio(onClose: () -> Unit, onOpenOperations: () -> Unit, vm: ControlViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val c = vm.controls
    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val listening by vm.listening.collectAsStateWithLifecycle()
    val torch by c.torch.collectAsStateWithLifecycle()
    val runs by vm.activeRuns.collectAsStateWithLifecycle()
    val issues by vm.connectorIssues.collectAsStateWithLifecycle()
    var volume by remember { mutableStateOf(c.volume()) }
    var media by remember { mutableStateOf(c.media()) }
    var dnd by remember { mutableStateOf(c.dndOn()) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    LaunchedEffect(tick, notifications.size) {
        volume = c.volume(); media = c.media(); dnd = c.dndOn()
    }
    BackHandler(onBack = onClose)
    fun open(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    Box(
        Modifier.fillMaxSize().background(Color(0xFF0B0C10).copy(alpha = 0.72f)).pointerInput(Unit) { detectTapGestures { onClose() } },
    ) {
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).pointerInput(Unit) { detectTapGestures { } },
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.control_title), style = Quorvi.type.title, color = Quorvi.colors.text) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile(stringResource(R.string.control_internet), null, false, Modifier.weight(1f)) { open(DeviceControls.INTERNET_PANEL) }
                    Tile("Bluetooth", null, false, Modifier.weight(1f)) { open(DeviceControls.BLUETOOTH) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (c.hasTorch) Tile(stringResource(R.string.control_torch), if (torch) stringResource(R.string.control_on) else stringResource(R.string.control_off), torch, Modifier.weight(1f)) { c.toggleTorch() }
                    Tile(
                        stringResource(R.string.control_dnd),
                        if (!c.canSetDnd) stringResource(R.string.control_tap_allow) else if (dnd) stringResource(R.string.control_on) else stringResource(R.string.control_off),
                        dnd, Modifier.weight(1f),
                    ) { if (c.canSetDnd) { c.toggleDnd(); dnd = c.dndOn() } else open(DeviceControls.DND_ACCESS) }
                }
            }
            item {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.control_volume), style = Quorvi.type.body, color = Quorvi.colors.text, modifier = Modifier.weight(1f))
                        Chip("−") { c.adjustVolume(false); volume = c.volume() }
                        Text(" ${volume.first}/${volume.second} ", style = Quorvi.type.body.copy(fontFeatureSettings = "tnum"), color = Quorvi.colors.text)
                        Chip("+") { c.adjustVolume(true); volume = c.volume() }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (c.canWriteSettings) {
                        var level by remember { mutableFloatStateOf(c.brightness().toFloat()) }
                        Text(stringResource(R.string.control_brightness), style = Quorvi.type.body, color = Quorvi.colors.text)
                        Slider(level, { level = it; c.setBrightness(it.toInt()) }, valueRange = 1f..255f, colors = SliderDefaults.colors(thumbColor = Quorvi.colors.accent, activeTrackColor = Quorvi.colors.accent, inactiveTrackColor = Quorvi.colors.field))
                    } else {
                        Row(Modifier.fillMaxWidth().clickable { open(DeviceControls.writeSettings(context)) }.padding(vertical = 6.dp)) {
                            Text(stringResource(R.string.control_brightness_allow), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
                        }
                    }
                }
            }
            media?.let { m -> item { MediaPanel(m, c) { media = c.media() } } }
            item {
                Panel {
                    Row(Modifier.fillMaxWidth().clickable(onClick = onOpenOperations), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.control_agents), style = Quorvi.type.body, color = Quorvi.colors.text)
                            Text(
                                listOfNotNull(
                                    if (runs == 0) stringResource(R.string.control_agents_idle) else context.resources.getQuantityString(R.plurals.agents_working, runs, runs),
                                    if (issues > 0) stringResource(R.string.control_connector_issues, issues) else null,
                                ).joinToString(" · "),
                                style = Quorvi.type.caption, color = if (issues > 0) Quorvi.colors.danger else Quorvi.colors.textSecondary,
                            )
                        }
                        if (runs > 0) Chip(stringResource(R.string.agents_stop_all), accent = true) { vm.killAll() }
                    }
                }
            }
            item { Text(stringResource(R.string.control_notifications), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) }
            if (!listening) {
                item {
                    Panel {
                        Row(Modifier.fillMaxWidth().clickable { open(DeviceControls.NOTIFICATION_ACCESS) }.padding(vertical = 6.dp)) {
                            Text(stringResource(R.string.control_notifications_allow), style = Quorvi.type.body, color = Quorvi.colors.text)
                        }
                    }
                }
            } else if (notifications.isEmpty()) {
                item { Text(stringResource(R.string.control_notifications_empty), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(start = 4.dp)) }
            }
            val grouped = notifications.filter { !it.ongoing }.groupBy { it.packageName }
            items(grouped.entries.toList(), key = { it.key }) { (pkg, list) -> NotificationGroup(context, pkg, list, vm) }
        }
    }
}

@Composable
private fun NotificationGroup(context: Context, pkg: String, list: List<ActiveNotification>, vm: ControlViewModel) {
    val label = remember(pkg) { runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) }
    Panel {
        Text(if (list.size > 1) "$label · ${list.size}" else label, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
        list.take(3).forEach { n ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { context.packageManager.getLaunchIntentForPackage(pkg)?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } }) {
                    Text(n.title, style = Quorvi.type.action, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (n.text.isNotBlank()) Text(n.text, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text("×", style = Quorvi.type.title, color = Quorvi.colors.textSecondary, modifier = Modifier.clip(Quorvi.shapes.control).clickable { vm.dismiss(n) }.padding(horizontal = 10.dp))
            }
        }
    }
}

@Composable
private fun MediaPanel(m: MediaInfo, c: DeviceControls, refresh: () -> Unit) = Panel {
    Text(m.title, style = Quorvi.type.action, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    m.artist?.let { Text(it, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1) }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        com.paulneja.quorvi.ui.design.IconButton(com.paulneja.quorvi.ui.design.Glyphs.Previous, { c.mediaPrevious(); refresh() })
        com.paulneja.quorvi.ui.design.IconButton(if (m.playing) com.paulneja.quorvi.ui.design.Glyphs.Pause else com.paulneja.quorvi.ui.design.Glyphs.Play, { c.mediaPlayPause(); refresh() })
        com.paulneja.quorvi.ui.design.IconButton(com.paulneja.quorvi.ui.design.Glyphs.Next, { c.mediaNext(); refresh() })
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(Quorvi.shapes.panel).background(Quorvi.colors.panel).padding(16.dp)) { content() }
}

@Composable
private fun Tile(title: String, subtitle: String?, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(Quorvi.shapes.panel).background(if (active) Quorvi.colors.accent else Quorvi.colors.panel).clickable(onClick = onClick).padding(16.dp),
    ) {
        Text(title, style = Quorvi.type.action, color = if (active) Quorvi.colors.onAccent else Quorvi.colors.text)
        if (subtitle != null) Text(subtitle, style = Quorvi.type.caption, color = if (active) Quorvi.colors.onAccent else Quorvi.colors.textSecondary)
        else Spacer(Modifier.width(1.dp).height(16.dp))
    }
}
