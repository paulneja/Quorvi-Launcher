package com.paulneja.quorvi.ui.operations

import android.content.ClipData
import android.content.ClipboardManager
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paulneja.quorvi.R
import com.paulneja.quorvi.connections.ContainerEntity
import com.paulneja.quorvi.connections.HostEntity
import com.paulneja.quorvi.connections.HttpWidgetEntity
import com.paulneja.quorvi.connections.http.HttpDisplay
import com.paulneja.quorvi.connections.ssh.DockerAction
import com.paulneja.quorvi.connections.ssh.HostHealth
import com.paulneja.quorvi.connections.ssh.SshAuth
import com.paulneja.quorvi.ui.design.ActionRow
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.Hint
import com.paulneja.quorvi.ui.design.IconButton
import com.paulneja.quorvi.ui.design.InputField
import com.paulneja.quorvi.ui.design.Label
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.Panel
import com.paulneja.quorvi.ui.design.Section
import com.paulneja.quorvi.ui.design.Segmented
import com.paulneja.quorvi.ui.design.SwitchRow
import java.util.UUID

fun hostStatus(host: HostEntity, health: HostHealth?): Status = when {
    host.hostKeyFingerprint == null -> Status.NEUTRAL
    host.healthError != null -> Status.FAILURE
    health == null -> Status.QUEUED
    health.healthy -> Status.SUCCESS
    else -> Status.FAILURE
}

fun percent(ratio: Double?): String = ratio?.let { "${(it * 100).toInt()}%" } ?: "—"

@Composable
fun HostsTab(vm: ConnectionsViewModel, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val hosts by vm.hostList.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        item {
            Panel {
                if (hosts.isEmpty()) Hint(stringResource(R.string.hosts_empty))
                ActionRow(stringResource(R.string.hosts_add), stringResource(R.string.hosts_add_hint), onClick = onAdd)
            }
        }
        items(hosts, key = { it.id }) { host ->
            val health = vm.health(host)
            Row(Modifier.fillMaxWidth().clip(Quorvi.shapes.control).clickable { onOpen(host.id) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(hostStatus(host, health))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(host.name, style = Quorvi.type.action, color = Quorvi.colors.text)
                    val line = when {
                        host.hostKeyFingerprint == null -> stringResource(R.string.hosts_needs_key)
                        host.healthError != null -> host.healthError
                        health != null -> "load ${health.load1 ?: "—"} · mem ${percent(health.memUsedRatio)} · disk ${percent(health.diskUsedRatio)}"
                        else -> "${host.username}@${host.hostname}:${host.port}"
                    }
                    Text(line, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun HostNew(vm: ConnectionsViewModel, onBack: () -> Unit, onCreated: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var hostname by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("22") }
    var user by rememberSaveable { mutableStateOf("") }
    var auth by rememberSaveable { mutableStateOf(SshAuth.KEY) }
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        ScreenBar(stringResource(R.string.hosts_add), onBack)
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            item {
                Panel {
                    Hint(stringResource(R.string.hosts_intro))
                    InputField(name, { name = it }, stringResource(R.string.hosts_name))
                    InputField(hostname, { hostname = it.trim() }, stringResource(R.string.hosts_hostname))
                    Hint(stringResource(R.string.hosts_hostname_hint))
                    InputField(port, { port = it.filter(Char::isDigit).take(5) }, stringResource(R.string.hosts_port))
                    InputField(user, { user = it.trim() }, stringResource(R.string.hosts_user))
                    Hint(stringResource(R.string.hosts_user_hint))
                    Segmented(listOf(SshAuth.KEY to stringResource(R.string.hosts_auth_key), SshAuth.PASSWORD to stringResource(R.string.hosts_auth_password)), auth) { auth = it }
                    if (auth == SshAuth.PASSWORD) InputField(password, { password = it }, stringResource(R.string.hosts_password), secret = true)
                    else Hint(stringResource(R.string.hosts_key_hint))
                    val ready = hostname.isNotBlank() && user.isNotBlank() && (port.toIntOrNull() ?: 0) in 1..65535 && (auth == SshAuth.KEY || password.isNotEmpty())
                    if (ready) ActionRow(stringResource(R.string.hosts_save), null) { vm.create(name, hostname, port.toInt(), user, auth, password.takeIf { auth == SshAuth.PASSWORD }, onCreated) }
                }
            }
        }
    }
}

@Composable
fun HostDetail(vm: ConnectionsViewModel, id: String, onBack: () -> Unit, onTerminal: () -> Unit) {
    val host by remember(id) { vm.host(id) }.collectAsState(null)
    val containers by remember(id) { vm.containers(id) }.collectAsState(emptyList())
    val keyCheck by vm.keyCheck.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selected by remember { mutableStateOf<ContainerEntity?>(null) }
    var confirm by remember { mutableStateOf<DockerAction?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val h = host
    Column(Modifier.fillMaxSize()) {
        ScreenBar(h?.name ?: "", onBack) { if (h?.hostKeyFingerprint != null) IconButton(Glyphs.Redo, { vm.refresh(h) }, enabled = !busy) }
        if (h == null) return@Column
        val logText = logs
        if (logText != null && selected != null) {
            Column(Modifier.fillMaxSize()) {
                ActionRow(stringResource(R.string.hosts_back_to_host), selected?.name) { vm.closeLogs() }
                SelectionContainer {
                    LazyColumn(contentPadding = PaddingValues(12.dp)) {
                        item { Text(when (logText) { "" -> stringResource(R.string.accounts_loading); EMPTY_LOGS -> stringResource(R.string.hosts_no_logs); else -> logText }, style = Quorvi.type.caption.copy(fontFamily = FontFamily.Monospace), color = Quorvi.colors.text) }
                    }
                }
            }
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            item {
                Panel {
                    Hint("${h.username}@${h.hostname}:${h.port} · ${h.auth.lowercase()}")
                    if (h.hostKeyFingerprint == null) {
                        Label(stringResource(R.string.hosts_verify_title))
                        Hint(stringResource(R.string.hosts_verify_hint))
                        when (val k = keyCheck) {
                            KeyCheck.Idle -> ActionRow(stringResource(R.string.hosts_fetch_key), null) { vm.fetchKey(h) }
                            KeyCheck.Loading -> Hint(stringResource(R.string.accounts_loading))
                            is KeyCheck.Failed -> {
                                Text(k.reason, style = Quorvi.type.caption, color = Quorvi.colors.danger)
                                ActionRow(stringResource(R.string.hosts_fetch_key), null) { vm.fetchKey(h) }
                            }
                            is KeyCheck.Found -> {
                                Text("${k.key.type}\n${k.key.fingerprint}", style = Quorvi.type.body.copy(fontFamily = FontFamily.Monospace), color = Quorvi.colors.text, modifier = Modifier.padding(vertical = 8.dp))
                                ActionRow(stringResource(R.string.hosts_trust), stringResource(R.string.hosts_trust_hint)) { vm.trust(h, k.key) }
                            }
                        }
                    } else {
                        Hint(stringResource(R.string.hosts_pinned, h.hostKeyFingerprint))
                    }
                    if (h.auth == SshAuth.KEY.name) {
                        vm.publicKey(h)?.let { key ->
                            ActionRow(stringResource(R.string.hosts_copy_key), stringResource(R.string.hosts_copy_key_hint)) {
                                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("ssh key", key))
                            }
                            SelectionContainer { Text(key, style = Quorvi.type.caption.copy(fontFamily = FontFamily.Monospace), color = Quorvi.colors.textSecondary, modifier = Modifier.padding(bottom = 8.dp)) }
                        }
                    }
                }
            }
            if (h.hostKeyFingerprint != null) {
                item { Section(stringResource(R.string.hosts_health)) }
                item {
                    Panel {
                        val health = vm.health(h)
                        h.healthError?.let { Text(it, style = Quorvi.type.caption, color = Quorvi.colors.danger, modifier = Modifier.padding(vertical = 8.dp)) }
                        if (health == null) {
                            Hint(if (busy) stringResource(R.string.accounts_loading) else stringResource(R.string.hosts_no_health))
                        } else {
                            Hint(health.kernel)
                            MetricRow("Load", "${health.load1 ?: "—"} / ${health.load5 ?: "—"} / ${health.load15 ?: "—"}" + (health.cpus?.let { " · $it CPU" } ?: ""))
                            MetricRow("RAM", percent(health.memUsedRatio))
                            MetricRow("Disk /", percent(health.diskUsedRatio))
                            MetricRow("Uptime", health.uptimeSec?.let { DateUtils.formatElapsedTime(it) } ?: "—")
                            if (health.failedUnits.isNotEmpty()) Text("systemd: " + health.failedUnits.joinToString(), style = Quorvi.type.caption, color = Quorvi.colors.danger, modifier = Modifier.padding(vertical = 6.dp))
                            h.healthAt?.let { Hint(DateUtils.getRelativeTimeSpanString(it).toString()) }
                        }
                        ActionRow(stringResource(R.string.hosts_terminal), null, onClick = onTerminal)
                        SwitchRow(stringResource(R.string.hosts_agent_access), h.agentAccess) { vm.setAgentAccess(h, it) }
                    }
                }
                if (containers.isNotEmpty()) {
                    item { Section("Docker") }
                    items(containers, key = { it.id }) { c ->
                        Panel {
                            Row(Modifier.fillMaxWidth().clickable { selected = if (selected?.id == c.id) null else c; confirm = null }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(if (c.state == "running") Status.SUCCESS else if (c.state == "restarting") Status.RUNNING else Status.CANCELLED)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, style = Quorvi.type.action, color = Quorvi.colors.text)
                                    Text("${c.image} · ${c.status}", style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (selected?.id == c.id) {
                                val pending = confirm
                                if (pending == null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                        Chip(stringResource(R.string.hosts_logs)) { vm.loadLogs(h, c) }
                                        DockerAction.entries.forEach { a -> Chip(dockerLabel(a)) { confirm = a } }
                                    }
                                } else {
                                    Hint(stringResource(R.string.hosts_docker_confirm, pending.verb, c.name, h.name))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Chip(stringResource(R.string.pin_cancel)) { confirm = null }
                                        Chip(stringResource(R.string.ops_confirm), accent = true) { vm.docker(h, c, pending); confirm = null }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Panel {
                    if (!confirmDelete) ActionRow(stringResource(R.string.hosts_delete), null, danger = true) { confirmDelete = true }
                    else ActionRow(stringResource(R.string.hosts_delete_confirm, h.name), null, danger = true) { vm.delete(h, onBack) }
                }
            }
        }
    }
}

@Composable
fun HostTerminal(vm: ConnectionsViewModel, id: String, onBack: () -> Unit) {
    val host by remember(id) { vm.host(id) }.collectAsState(null)
    val text by vm.console.text.collectAsStateWithLifecycle()
    val connected by vm.console.connected.collectAsStateWithLifecycle()
    val error by vm.console.error.collectAsStateWithLifecycle()
    var line by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val h = host
    LaunchedEffect(h?.id) { if (h != null) vm.openConsole(h) }
    DisposableEffect(Unit) { onDispose { vm.closeConsole() } }
    LaunchedEffect(text.length) { list.scrollToItem(0) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime)) {
        ScreenBar(h?.name ?: "", onBack)
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).clip(Quorvi.shapes.control).background(Quorvi.colors.sunken)) {
            SelectionContainer {
                LazyColumn(state = list, reverseLayout = true, contentPadding = PaddingValues(10.dp)) {
                    item { Text(text, style = Quorvi.type.caption.copy(fontFamily = FontFamily.Monospace), color = Quorvi.colors.text) }
                }
            }
        }
        error?.let { Text(it, style = Quorvi.type.caption, color = Quorvi.colors.danger, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Ctrl-C") { vm.send("\u0003") }
            Chip("Ctrl-D") { vm.send("\u0004") }
            Chip("Tab") { vm.send("\t") }
            Chip("Esc") { vm.send("\u001B") }
            if (!connected && h != null) Chip(stringResource(R.string.hosts_reconnect), accent = true) { vm.openConsole(h) }
        }
        Row(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { InputField(line, { line = it }, if (connected) "$ " else stringResource(R.string.hosts_disconnected), onSubmit = { vm.send(line + "\n"); line = "" }) }
            Spacer(Modifier.width(8.dp))
            Chip(stringResource(R.string.hosts_send), accent = true) { vm.send(line + "\n"); line = "" }
        }
    }
}

@Composable
fun HttpTab(vm: ConnectionsViewModel, onEdit: (String) -> Unit) {
    val widgets by vm.httpList.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        item {
            Panel {
                if (widgets.isEmpty()) Hint(stringResource(R.string.http_empty))
                ActionRow(stringResource(R.string.http_add), stringResource(R.string.http_add_hint)) { onEdit("new") }
            }
        }
        items(widgets, key = { it.id }) { w ->
            Row(Modifier.fillMaxWidth().clip(Quorvi.shapes.control).clickable { onEdit(w.id) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(httpStatus(w))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(w.name, style = Quorvi.type.action, color = Quorvi.colors.text)
                    Text(w.error ?: listOfNotNull(w.value?.let { it + (w.unit?.let { u -> " $u" } ?: "") }, w.fetchedAt?.let { DateUtils.getRelativeTimeSpanString(it).toString() }).joinToString(" · "), style = Quorvi.type.caption, color = if (w.error != null) Quorvi.colors.danger else Quorvi.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

fun httpStatus(w: HttpWidgetEntity): Status = when {
    w.error != null -> Status.FAILURE
    w.value == null -> Status.QUEUED
    else -> when (com.paulneja.quorvi.connections.http.Condition.ok(w.value, w.okWhen)) {
        true -> Status.SUCCESS
        false -> Status.FAILURE
        null -> if (w.display == HttpDisplay.STATUS.name && w.value.toIntOrNull() in 200..299) Status.SUCCESS else if (w.display == HttpDisplay.STATUS.name) Status.FAILURE else Status.NEUTRAL
    }
}

@Composable
fun HttpEdit(vm: ConnectionsViewModel, id: String, onBack: () -> Unit) {
    var loaded by remember { mutableStateOf(id == "new") }
    var widget by remember { mutableStateOf(HttpWidgetEntity(id = UUID.randomUUID().toString(), name = "", url = "https://", createdAt = System.currentTimeMillis())) }
    var auth by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf("300") }
    var confirmDelete by remember { mutableStateOf(false) }
    val test by vm.test.collectAsStateWithLifecycle()
    LaunchedEffect(id) {
        vm.clearTest()
        if (id != "new") vm.widget(id)?.let { widget = it; refresh = it.refreshSec.toString() }
        loaded = true
    }
    if (!loaded) return
    val hasAuth = remember(widget.id) { vm.hasAuth(widget.id) }
    fun current() = widget.copy(refreshSec = refresh.toIntOrNull()?.coerceIn(15, 86_400) ?: 300)
    Column(Modifier.fillMaxSize()) {
        ScreenBar(widget.name.ifBlank { stringResource(R.string.http_add) }, onBack)
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            item {
                val target = com.paulneja.quorvi.connections.http.MonitorTarget.parse(widget.url) ?: com.paulneja.quorvi.connections.http.MonitorTarget(com.paulneja.quorvi.connections.http.MonitorKind.HTTPS, "", null)
                fun update(t: com.paulneja.quorvi.connections.http.MonitorTarget) { widget = widget.copy(url = t.url(), display = if (t.kind == com.paulneja.quorvi.connections.http.MonitorKind.TCP) HttpDisplay.STATUS.name else widget.display) }
                val tcp = target.kind == com.paulneja.quorvi.connections.http.MonitorKind.TCP
                Panel {
                    InputField(widget.name, { widget = widget.copy(name = it) }, stringResource(R.string.http_name))
                    Label(stringResource(R.string.monitor_type))
                    Segmented(
                        listOf(com.paulneja.quorvi.connections.http.MonitorKind.HTTPS to "HTTPS", com.paulneja.quorvi.connections.http.MonitorKind.HTTP to "HTTP", com.paulneja.quorvi.connections.http.MonitorKind.TCP to "TCP"),
                        target.kind,
                    ) { k -> update(target.copy(kind = k, port = null)) }
                    Hint(stringResource(when (target.kind) {
                        com.paulneja.quorvi.connections.http.MonitorKind.HTTPS -> R.string.monitor_https_hint
                        com.paulneja.quorvi.connections.http.MonitorKind.HTTP -> R.string.monitor_http_hint
                        com.paulneja.quorvi.connections.http.MonitorKind.TCP -> R.string.monitor_tcp_hint
                    }))
                    InputField(target.host, { h -> update(target.copy(host = h.trim().substringAfter("://").substringBefore('/'))) }, stringResource(R.string.monitor_host))
                    InputField(target.port?.toString().orEmpty(), { p -> update(target.copy(port = p.filter(Char::isDigit).take(5).toIntOrNull()?.takeIf { it in 1..65535 })) }, stringResource(R.string.monitor_port, target.kind.defaultPort))
                    if (!tcp) InputField(target.path, { p -> update(target.copy(path = p)) }, stringResource(R.string.monitor_path))
                    if (target.host.isNotBlank()) Text(widget.url, style = Quorvi.type.mono, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(vertical = 6.dp))
                    if (!tcp) {
                        var advanced by remember { mutableStateOf(widget.method != "GET" || widget.headersJson != "{}" || hasAuth) }
                        ActionRow(stringResource(R.string.monitor_advanced), stringResource(R.string.monitor_advanced_desc)) { advanced = !advanced }
                        androidx.compose.animation.AnimatedVisibility(advanced) {
                            Column {
                                Segmented(listOf("GET" to "GET", "POST" to "POST", "PUT" to "PUT"), widget.method) { widget = widget.copy(method = it) }
                                InputField(widget.headersJson, { widget = widget.copy(headersJson = it) }, stringResource(R.string.http_headers), singleLine = false)
                                InputField(auth, { auth = it }, if (hasAuth) stringResource(R.string.http_auth_saved) else stringResource(R.string.http_auth), secret = true)
                                if (widget.method != "GET") InputField(widget.body.orEmpty(), { widget = widget.copy(body = it.ifEmpty { null }) }, stringResource(R.string.http_body), singleLine = false)
                            }
                        }
                    }
                }
            }
            item { Section(stringResource(R.string.http_display)) }
            item {
                val tcp = com.paulneja.quorvi.connections.http.MonitorTarget.parse(widget.url)?.kind == com.paulneja.quorvi.connections.http.MonitorKind.TCP
                Panel {
                    if (!tcp) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HttpDisplay.entries.forEach { d -> Chip(stringResource(when (d) { HttpDisplay.TEXT -> R.string.http_display_text; HttpDisplay.COUNTER -> R.string.http_display_counter; HttpDisplay.STATUS -> R.string.http_display_status; HttpDisplay.SERIES -> R.string.http_display_series; HttpDisplay.LIST -> R.string.http_display_list; HttpDisplay.BUTTON -> R.string.http_display_button; HttpDisplay.TOGGLE -> R.string.http_display_toggle }), accent = widget.display == d.name) { widget = widget.copy(display = d.name) } }
                    }
                    InputField(widget.jsonPath.orEmpty(), { widget = widget.copy(jsonPath = it.ifBlank { null }) }, stringResource(R.string.http_jsonpath))
                    InputField(widget.unit.orEmpty(), { widget = widget.copy(unit = it.ifBlank { null }) }, stringResource(R.string.http_unit))
                    InputField(widget.okWhen.orEmpty(), { widget = widget.copy(okWhen = it.ifBlank { null }) }, stringResource(R.string.http_ok_when))
                    }
                    InputField(refresh, { refresh = it.filter(Char::isDigit).take(6) }, stringResource(R.string.http_refresh))
                    SwitchRow(stringResource(R.string.http_allow_lan), widget.allowLan) { widget = widget.copy(allowLan = it) }
                    Hint(stringResource(R.string.http_lan_hint))
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                Panel {
                    ActionRow(stringResource(R.string.http_test), null) { vm.testWidget(current()) }
                    test?.let { r ->
                        val tcpTest = com.paulneja.quorvi.connections.http.MonitorTarget.parse(widget.url)?.kind == com.paulneja.quorvi.connections.http.MonitorKind.TCP
                        val openLabel = stringResource(R.string.monitor_port_open)
                        r.onSuccess { Text((if (tcpTest) "$openLabel · ${it.value}" else "HTTP ${it.status} → ${it.value}") + (it.ok?.let { ok -> if (ok) " ✓" else " ✗" } ?: ""), style = Quorvi.type.caption.copy(fontFamily = FontFamily.Monospace), color = Quorvi.colors.text, modifier = Modifier.padding(vertical = 6.dp)) }
                            .onFailure { Text(it.message ?: "error", style = Quorvi.type.caption, color = Quorvi.colors.danger, modifier = Modifier.padding(vertical = 6.dp)) }
                    }
                    if (widget.name.isNotBlank() && com.paulneja.quorvi.connections.http.MonitorTarget.parse(widget.url)?.host?.isNotBlank() == true) ActionRow(stringResource(R.string.hosts_save), null) { vm.saveWidget(current(), auth.ifEmpty { null }, onBack) }
                    if (id != "new") {
                        if (!confirmDelete) ActionRow(stringResource(R.string.http_delete), null, danger = true) { confirmDelete = true }
                        else ActionRow(stringResource(R.string.hosts_delete_confirm, widget.name), null, danger = true) { vm.deleteWidget(widget.id, onBack) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = Quorvi.type.body, color = Quorvi.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = Quorvi.type.body.copy(fontFeatureSettings = "tnum"), color = Quorvi.colors.text)
    }
}

@Composable
fun Chip(label: String, accent: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        style = Quorvi.type.action,
        color = if (accent) Quorvi.colors.onAccent else Quorvi.colors.text,
        modifier = Modifier.clip(Quorvi.shapes.control).background(if (accent) Quorvi.colors.accent else Quorvi.colors.field).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
fun ScreenBar(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(Glyphs.Close, onBack)
        Text(title, style = Quorvi.type.title, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
        action?.invoke()
    }
}

@Composable
private fun dockerLabel(a: DockerAction): String = stringResource(when (a.name) {
    "START" -> R.string.hosts_docker_start
    "STOP" -> R.string.hosts_docker_stop
    else -> R.string.hosts_docker_restart
})
