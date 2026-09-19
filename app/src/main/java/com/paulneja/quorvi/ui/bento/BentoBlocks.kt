package com.paulneja.quorvi.ui.bento

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paulneja.quorvi.R
import com.paulneja.quorvi.connections.HostEntity
import com.paulneja.quorvi.connections.HttpWidgetEntity
import com.paulneja.quorvi.connections.services.HaEntity
import com.paulneja.quorvi.connections.ssh.HostHealth
import com.paulneja.quorvi.launcher.apps.AppEntry
import com.paulneja.quorvi.launcher.live.Gated
import com.paulneja.quorvi.launcher.live.MediaState
import com.paulneja.quorvi.launcher.live.Sky
import com.paulneja.quorvi.launcher.live.WeatherNow
import com.paulneja.quorvi.launcher.live.WeatherState
import com.paulneja.quorvi.ui.canvas.AppIcon
import com.paulneja.quorvi.ui.design.BentoBlock
import com.paulneja.quorvi.ui.design.BlockHeader
import com.paulneja.quorvi.ui.design.BlockState
import com.paulneja.quorvi.ui.design.ChipButton
import com.paulneja.quorvi.ui.design.Dot
import com.paulneja.quorvi.ui.design.Glyph
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.QuorviSlider
import com.paulneja.quorvi.ui.design.ProgressLine
import com.paulneja.quorvi.ui.design.Ring
import com.paulneja.quorvi.ui.operations.hostStatus
import com.paulneja.quorvi.ui.panels.CiMiniRow
import com.paulneja.quorvi.ui.panels.PanelSummaries
import com.paulneja.quorvi.ui.panels.PanelsViewModel
import com.paulneja.quorvi.ui.panels.relativeTime
import com.paulneja.quorvi.ui.panels.statusColor
import java.util.Date
import kotlinx.coroutines.delay

fun Sky.glyph(): ImageVector = when (this) {
    Sky.CLEAR -> Glyphs.Sun
    Sky.PARTLY -> Glyphs.CloudSun
    Sky.CLOUDY -> Glyphs.Cloud
    Sky.FOG -> Glyphs.Fog
    Sky.DRIZZLE, Sky.RAIN -> Glyphs.Rain
    Sky.SNOW -> Glyphs.Snow
    Sky.STORM -> Glyphs.Storm
}

@Composable
fun Sky.label(): String = stringResource(
    when (this) {
        Sky.CLEAR -> R.string.sky_clear
        Sky.PARTLY -> R.string.sky_partly
        Sky.CLOUDY -> R.string.sky_cloudy
        Sky.FOG -> R.string.sky_fog
        Sky.DRIZZLE -> R.string.sky_drizzle
        Sky.RAIN -> R.string.sky_rain
        Sky.SNOW -> R.string.sky_snow
        Sky.STORM -> R.string.sky_storm
    },
)

private val notificationAccess get() = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

@Composable
private fun rememberMinute(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000 + 50)
        }
    }
    return now
}

@Composable
private fun permissionLauncher(onResult: () -> Unit) = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult() }

@Composable
private fun Mini(text: String, glyph: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(modifier.clip(Quorvi.shapes.control).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (glyph != null) {
            Glyph(glyph, 14.dp, Quorvi.colors.text.copy(alpha = 0.8f))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = Quorvi.type.caption.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"), color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TodayBlock(vm: BlocksViewModel) {
    val context = LocalContext.current
    val now = rememberMinute()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val agenda by vm.agenda.collectAsStateWithLifecycle()
    val is24 = DateFormat.is24HourFormat(context)
    val locale = LocalConfiguration.current.locales[0]
    val time = remember(now, is24) { DateFormat.format(if (is24) "HH:mm" else "h:mm", now).toString() }
    val date = remember(now, locale) { DateFormat.format(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), now).toString().replaceFirstChar { it.titlecase(locale) } }
    val alarm = remember(now) {
        context.getSystemService(android.app.AlarmManager::class.java)?.nextAlarmClock?.triggerTime?.let { DateFormat.format(if (is24) "HH:mm" else "h:mm a", Date(it)).toString() }
    }
    val calendarPermission = permissionLauncher { vm.permissionsChanged() }
    val locationPermission = permissionLauncher { vm.permissionsChanged() }
    var picking by remember { mutableStateOf(false) }
    BentoBlock(padding = 16.dp) {
        if (picking) {
            CityPicker(vm, onDone = { picking = false })
            return@BentoBlock
        }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.weight(1f)) {
                Text(time, style = Quorvi.type.clock.copy(fontSize = 68.sp, lineHeight = 66.sp), color = Quorvi.colors.text)
                Spacer(Modifier.height(6.dp))
                Text(date, style = Quorvi.type.body, color = Quorvi.colors.textSecondary, maxLines = 1)
            }
            WeatherCorner(weather, onLocate = { locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, onPick = { picking = true }, onRetry = vm::retryWeather)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (alarm != null) Mini(alarm, Glyphs.Alarm)
            when (val a = agenda) {
                is Gated.Ready -> a.value.firstOrNull()?.let { e ->
                    val at = if (e.allDay) stringResource(R.string.blk_allday) else DateFormat.format(if (is24) "HH:mm" else "h:mm a", e.begin).toString()
                    Mini("${e.title} · $at", Glyphs.Calendar, Modifier.weight(1f, fill = false))
                } ?: Mini(stringResource(R.string.blk_agenda_empty), Glyphs.Calendar)
                Gated.NeedsPermission -> Mini(stringResource(R.string.blk_agenda_access), Glyphs.Calendar, Modifier.clickable { calendarPermission.launch(Manifest.permission.READ_CALENDAR) })
                null -> Unit
            }
            (weather as? WeatherState.Ready)?.weather?.rainChance?.let { Mini("$it %", Glyphs.Drop) }
        }
    }
}

@Composable
private fun WeatherCorner(state: WeatherState, onLocate: () -> Unit, onPick: () -> Unit, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.End) {
        when (state) {
            WeatherState.Loading -> Unit
            WeatherState.NeedsLocation -> {
                Text(stringResource(R.string.blk_weather_need), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.blk_weather_locate), style = Quorvi.type.action, color = Quorvi.colors.accent, modifier = Modifier.clickable(onClick = onLocate))
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.blk_weather_city), style = Quorvi.type.action, color = Quorvi.colors.accent, modifier = Modifier.clickable(onClick = onPick))
            }
            is WeatherState.Ready -> WeatherNowView(state.weather, onPick)
            is WeatherState.Failed -> {
                state.last?.let { WeatherNowView(it, onPick) }
                Text(stringResource(R.string.blk_weather_failed), style = Quorvi.type.caption, color = Quorvi.colors.danger, modifier = Modifier.clickable(onClick = onRetry))
            }
        }
    }
}

@Composable
private fun WeatherNowView(w: WeatherNow, onPick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onPick)) {
        Glyph(w.sky.glyph(), 28.dp, Color(0xFFFFD27A))
        Spacer(Modifier.width(8.dp))
        Text("${w.tempC.toInt()}°", style = Quorvi.type.clock.copy(fontSize = 34.sp, lineHeight = 36.sp), color = Quorvi.colors.text)
    }
    Spacer(Modifier.height(4.dp))
    Text(w.sky.label(), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
    val range = listOfNotNull(w.maxC?.let { "${it.toInt()}°" }, w.minC?.let { "${it.toInt()}°" }).joinToString(" / ")
    Text(listOfNotNull(w.place, range.ifBlank { null }).joinToString(" · "), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1)
    if (w.stale(System.currentTimeMillis())) Text(stringResource(R.string.blk_ago, relativeTime(w.fetchedAt)), style = Quorvi.type.caption, color = Quorvi.colors.warning)
}

@Composable
private fun ColumnScope.CityPicker(vm: BlocksViewModel, onDone: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val places by vm.places.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth().clip(Quorvi.shapes.control).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(Glyphs.Search, 16.dp, Quorvi.colors.textSecondary)
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = { query = it; vm.searchPlaces(it) },
            singleLine = true,
            textStyle = Quorvi.type.body.copy(color = Quorvi.colors.text),
            cursorBrush = SolidColor(Quorvi.colors.accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner -> if (query.isEmpty()) Text(stringResource(R.string.blk_weather_search), style = Quorvi.type.body, color = Quorvi.colors.textSecondary); inner() },
        )
        Glyph(Glyphs.Close, 16.dp, Quorvi.colors.textSecondary, Modifier.clickable(onClick = onDone))
    }
    places.take(3).forEach { p ->
        Text(p.name, style = Quorvi.type.body, color = Quorvi.colors.text, maxLines = 1, modifier = Modifier.fillMaxWidth().clickable { vm.choosePlace(p); onDone() }.padding(vertical = 7.dp, horizontal = 4.dp))
    }
    Spacer(Modifier.weight(1f))
    Text(stringResource(R.string.weather_attribution), style = Quorvi.type.caption, color = Quorvi.colors.textTertiary)
}

@Composable
fun ClockWeather(vm: BlocksViewModel) {
    val weather by vm.weather.collectAsStateWithLifecycle()
    val w = (weather as? WeatherState.Ready)?.weather ?: (weather as? WeatherState.Failed)?.last ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Glyph(w.sky.glyph(), 17.dp, Color(0xFFFFD27A))
        Spacer(Modifier.width(5.dp))
        Text("${w.tempC.toInt()}°", style = Quorvi.type.action.copy(fontSize = 15.sp, fontFeatureSettings = "tnum"), color = Color.White)
    }
}

@Composable
fun MediaBlock(vm: BlocksViewModel) {
    val state by vm.mediaState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    when (val s = state) {
        is MediaState.Active -> MediaWidget(s.now, vm::playPause, vm::next, vm::previous, vm::seekTo, vm::openMedia)
        MediaState.NeedsAccess -> BentoBlock(state = BlockState.NeedsPermission(stringResource(R.string.blk_media_access), stringResource(R.string.blk_allow)) { context.startActivity(notificationAccess) }, title = stringResource(R.string.blk_media), glyph = Glyphs.Play) {}
        MediaState.Idle -> BentoBlock(onClick = { vm.openMedia() }) {
            BlockHeader(Glyphs.Play, stringResource(R.string.blk_media))
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.blk_media_idle), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
        }
    }
}

private fun mmss(ms: Long): String = "%d:%02d".format(ms / 60_000, (ms / 1000) % 60)

@Composable
fun NotificationsBlock(vm: BlocksViewModel) {
    val connected by vm.listenerConnected.collectAsStateWithLifecycle()
    val groups by vm.notifications.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val resumed by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val granted = remember(connected, resumed) { com.paulneja.quorvi.control.NotificationHub.granted(context) }
    val state = when {
        connected -> BlockState.Ready
        granted -> BlockState.Loading
        else -> BlockState.NeedsPermission(stringResource(R.string.blk_notif_access), stringResource(R.string.blk_allow)) { context.startActivity(notificationAccess) }
    }
    BentoBlock(state = state, title = stringResource(R.string.blk_notifs), glyph = Glyphs.Bell) {
        BlockHeader(Glyphs.Bell, stringResource(R.string.blk_notifs)) {
            val total = groups.sumOf { it.count }
            if (total > 0) Text("$total", style = Quorvi.type.label.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.onAccent, modifier = Modifier.clip(CircleShape).background(Quorvi.colors.accent).padding(horizontal = 7.dp, vertical = 1.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (groups.isEmpty()) Text(stringResource(R.string.blk_notif_empty), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val rows = (maxHeight / 44.dp).toInt().coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.take(rows).forEach { g ->
                    Row(Modifier.fillMaxWidth().clip(Quorvi.shapes.control).clickable { vm.openPackage(g.packageName) }, verticalAlignment = Alignment.Top) {
                        if (g.app != null) AppIcon(g.app, 24.dp) else Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.14f)))
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.latest.title + if (g.count > 1) " · ${g.count}" else "", style = Quorvi.type.caption.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(g.latest.text, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppsBlock(apps: List<AppEntry>, iconSize: Dp, onLaunch: (AppEntry, androidx.compose.ui.geometry.Rect?) -> Unit, badges: Set<String>) {
    BentoBlock(padding = 10.dp) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = 4
            val rows = (maxHeight / (iconSize + 28.dp)).toInt().coerceIn(1, 3)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                apps.take(columns * rows).chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { app ->
                            var bounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                            Column(Modifier.weight(1f).onGloballyPositioned { bounds = it.boundsInRoot() }.clip(Quorvi.shapes.control).clickable { onLaunch(app, bounds) }.padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box {
                                    AppIcon(app, iconSize)
                                    if (app.key.packageName in badges) Box(Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape).background(Quorvi.colors.danger))
                                }
                                Spacer(Modifier.height(5.dp))
                                Text(app.label, style = Quorvi.type.label, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
fun PhotosBlock(vm: BlocksViewModel) {
    val photos by vm.photos.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher = permissionLauncher { vm.permissionsChanged() }
    val permission = Manifest.permission.READ_EXTERNAL_STORAGE
    val state = when (val p = photos) {
        Gated.NeedsPermission -> BlockState.NeedsPermission(stringResource(R.string.blk_photos_access), stringResource(R.string.blk_allow)) { launcher.launch(permission) }
        is Gated.Ready -> if (p.value.isEmpty()) BlockState.Empty(stringResource(R.string.blk_photos_empty)) else BlockState.Ready
        null -> BlockState.Loading
    }
    BentoBlock(state = state, padding = 8.dp, title = stringResource(R.string.blk_photos), glyph = Glyphs.Photo) {
        val list = (photos as? Gated.Ready)?.value.orEmpty()
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            list.take(3).forEach { photo ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.08f))
                        .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, photo.uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)) } },
                ) { photo.thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
            }
        }
    }
}

@Composable
private fun QuickTile(label: String, glyph: ImageVector, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().clip(Quorvi.shapes.control).background(if (on) Quorvi.colors.accent else Color.White.copy(alpha = 0.08f)).clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Glyph(glyph, 20.dp, if (on) Quorvi.colors.onAccent else Quorvi.colors.text)
        Spacer(Modifier.height(4.dp))
        Text(label, style = Quorvi.type.label, color = if (on) Quorvi.colors.onAccent else Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TogglesBlock(panels: PanelsViewModel) = ConnectionsBlock(panels)

@Composable
fun SystemBlock(panels: PanelsViewModel) = DeviceBlock(panels)

@Composable
fun TimerBlock(vm: BlocksViewModel, id: Long, config: String?) {
    val endsAt = config?.toLongOrNull()?.takeIf { it > System.currentTimeMillis() }
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) { while (endsAt != null && tick < endsAt) { delay(1000); tick = System.currentTimeMillis() } }
    BentoBlock(onClick = vm::openTimers) {
        BlockHeader(Glyphs.Timer, stringResource(R.string.blk_timer))
        Spacer(Modifier.weight(1f))
        if (endsAt != null && tick < endsAt) {
            Text(mmss(endsAt - tick), style = Quorvi.type.metric.copy(fontSize = 30.sp), color = Quorvi.colors.text)
            Text(stringResource(R.string.blk_timer_running, DateFormat.format("HH:mm", endsAt)), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5, 10, 25).forEach { m -> ChipButton("$m′", null, { vm.startTimer(id, m) }) }
            }
        }
    }
}

@Composable
fun CapsulesBlock(panels: PanelsViewModel, onCreate: () -> Unit) = CapsuleLauncher(panels, onCreate)

@Composable
fun ContactsBlock(vm: BlocksViewModel) {
    val starred by vm.starred.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher = permissionLauncher { vm.permissionsChanged() }
    val state = when (val s = starred) {
        Gated.NeedsPermission -> BlockState.NeedsPermission(stringResource(R.string.blk_contacts_access), stringResource(R.string.blk_allow)) { launcher.launch(Manifest.permission.READ_CONTACTS) }
        is Gated.Ready -> if (s.value.isEmpty()) BlockState.Empty(stringResource(R.string.blk_contacts_empty)) else BlockState.Ready
        null -> BlockState.Loading
    }
    BentoBlock(state = state, padding = 10.dp, title = stringResource(R.string.blk_contacts), glyph = Glyphs.Contact) {
        val list = (starred as? Gated.Ready)?.value.orEmpty()
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            list.take(4).forEach { c ->
                Box(
                    Modifier.fillMaxHeight().aspectRatio(1f).clip(CircleShape).background(Quorvi.colors.accent.copy(alpha = 0.25f))
                        .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, c.uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                    contentAlignment = Alignment.Center,
                ) {
                    if (c.photo != null) Image(c.photo, c.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Text(c.name.take(1).uppercase(), style = Quorvi.type.action, color = Quorvi.colors.text)
                }
            }
        }
    }
}

@Composable
fun NoteBlock(vm: BlocksViewModel, id: Long, config: String?) {
    var text by remember(id) { mutableStateOf(config.orEmpty()) }
    LaunchedEffect(text) {
        if (text == config.orEmpty()) return@LaunchedEffect
        delay(600)
        vm.saveConfig(id, text.ifBlank { null })
    }
    BentoBlock(padding = 12.dp) {
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(500) },
            textStyle = Quorvi.type.body.copy(color = Quorvi.colors.text),
            cursorBrush = SolidColor(Quorvi.colors.accent),
            modifier = Modifier.fillMaxSize(),
            decorationBox = { inner ->
                if (text.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(Glyphs.Note, 16.dp, Quorvi.colors.textSecondary)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.blk_note_hint), style = Quorvi.type.body, color = Quorvi.colors.textSecondary)
                }
                inner()
            },
        )
    }
}

@Composable
fun AgentsBlock(panels: PanelsViewModel, onOpen: () -> Unit) {
    val runs by panels.runs.collectAsStateWithLifecycle()
    BentoBlock(onClick = onOpen) {
        BlockHeader(Glyphs.Agent, stringResource(R.string.blk_agents)) { Glyph(Glyphs.Chevron, 16.dp, Quorvi.colors.textTertiary) }
        Spacer(Modifier.height(8.dp))
        if (runs.isEmpty()) Text(stringResource(R.string.agent_none), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val rows = (maxHeight / 34.dp).toInt().coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                runs.take(rows).forEach { r ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Dot(if (r.state.active) Quorvi.colors.accent else if (r.state == com.paulneja.quorvi.ops.RunState.COMPLETED) Quorvi.colors.success else Quorvi.colors.textTertiary, 6.dp, halo = false)
                            Spacer(Modifier.width(8.dp))
                            Text(r.goal, style = Quorvi.type.caption, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(if (r.total > 0 && !r.state.terminal) "${r.done}/${r.total}" else relativeTime(r.updatedAt), style = Quorvi.type.mono, color = Quorvi.colors.textSecondary)
                        }
                        if (!r.state.terminal && r.total > 0) ProgressLine(r.done.toFloat() / r.total, modifier = Modifier.padding(top = 4.dp, start = 14.dp), height = 3.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun CiBlock(panels: PanelsViewModel, onOpen: () -> Unit) {
    val ci by panels.ci.collectAsStateWithLifecycle()
    val signedIn by panels.signedIn.collectAsStateWithLifecycle()
    val state = if (!signedIn) BlockState.Empty(stringResource(R.string.gh_signin_hint), stringResource(R.string.gh_signin), onOpen) else BlockState.Ready
    BentoBlock(state = state, onClick = onOpen, title = stringResource(R.string.blk_ci), glyph = Glyphs.Git) {
        BlockHeader(Glyphs.Git, stringResource(R.string.blk_ci)) { Glyph(Glyphs.Chevron, 16.dp, Quorvi.colors.textTertiary) }
        Spacer(Modifier.height(6.dp))
        val latest = PanelSummaries.latestPerRepo(ci)
        if (latest.isEmpty()) Text(stringResource(R.string.gh_empty_ci), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
        latest.take(4).forEach { CiMiniRow(it) }
    }
}

@Composable
fun HaBlock(favorites: List<HaEntity>, hasHa: Boolean, onToggle: (HaEntity) -> Unit, onSetup: () -> Unit) {
    val state = if (!hasHa) BlockState.Empty(stringResource(R.string.blk_ha_empty), stringResource(R.string.blk_setup), onSetup) else BlockState.Ready
    BentoBlock(state = state, title = stringResource(R.string.blk_ha), glyph = Glyphs.House) {
        BlockHeader(Glyphs.House, stringResource(R.string.blk_ha))
        Spacer(Modifier.height(6.dp))
        favorites.take(4).forEach { e ->
            val on = e.state == "on"
            Row(Modifier.fillMaxWidth().clip(Quorvi.shapes.control).clickable { onToggle(e) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Dot(if (on) Quorvi.colors.warning else Quorvi.colors.textTertiary, 6.dp, halo = false)
                Spacer(Modifier.width(8.dp))
                Text(e.name, style = Quorvi.type.caption, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(e.state + (e.unit?.let { " $it" } ?: ""), style = Quorvi.type.mono, color = Quorvi.colors.textSecondary)
            }
        }
    }
}

@Composable
fun HostsBlock(hosts: List<HostEntity>, health: (HostEntity) -> HostHealth?, onOpen: (String) -> Unit, onSetup: () -> Unit) {
    val state = if (hosts.isEmpty()) BlockState.Empty(stringResource(R.string.blk_hosts_empty), stringResource(R.string.blk_setup), onSetup) else BlockState.Ready
    BentoBlock(state = state, padding = 12.dp, title = stringResource(R.string.blk_hosts), glyph = Glyphs.Server) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            hosts.take(3).forEach { h ->
                val hh = health(h)
                Row(Modifier.weight(1f).fillMaxHeight().clip(Quorvi.shapes.control).background(Color.White.copy(alpha = 0.06f)).clickable { onOpen(h.id) }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(statusColor(hostStatus(h, hh)), 6.dp, halo = false)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(h.name, style = Quorvi.type.caption.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(hh?.load1?.let { "load %.2f".format(it) } ?: h.hostname, style = Quorvi.type.mono.copy(fontSize = 10.sp), color = Quorvi.colors.textSecondary, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun HttpBlock(widgets: List<HttpWidgetEntity>, onOpen: (String) -> Unit, onSetup: () -> Unit) {
    val state = if (widgets.isEmpty()) BlockState.Empty(stringResource(R.string.blk_http_empty), stringResource(R.string.blk_setup), onSetup) else BlockState.Ready
    BentoBlock(state = state, padding = 12.dp, title = stringResource(R.string.blk_http), glyph = Glyphs.Globe) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            widgets.take(3).forEach { w ->
                Column(Modifier.weight(1f).fillMaxHeight().clip(Quorvi.shapes.control).background(Color.White.copy(alpha = 0.06f)).clickable { onOpen(w.id) }.padding(horizontal = 10.dp), verticalArrangement = Arrangement.Center) {
                    Text(w.name, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(w.error?.let { stringResource(R.string.blk_http_error) } ?: ((w.value ?: "–") + (w.unit?.let { " $it" } ?: "")), style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), color = if (w.error != null) Quorvi.colors.danger else Quorvi.colors.text, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun FavListBlock(apps: List<AppEntry>, onLaunch: (AppEntry, androidx.compose.ui.geometry.Rect?) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 18.dp, top = 8.dp)) {
        val rows = (maxHeight / 56.dp).toInt().coerceIn(1, 9)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            apps.take(rows).forEach { app ->
                var bounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                Text(
                    app.label,
                    style = Quorvi.type.headline.copy(fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Light),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.onGloballyPositioned { bounds = it.boundsInRoot() }.clip(Quorvi.shapes.control).clickable { onLaunch(app, bounds) }.padding(vertical = 4.dp, horizontal = 2.dp),
                )
            }
        }
    }
}
