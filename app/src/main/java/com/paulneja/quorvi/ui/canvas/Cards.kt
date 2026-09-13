package com.paulneja.quorvi.ui.canvas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paulneja.quorvi.R
import com.paulneja.quorvi.ops.CiExecutionEntity
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.liquidGlass
import com.paulneja.quorvi.ui.operations.StatusDot
import com.paulneja.quorvi.ui.operations.ciStatus

@Composable
fun CiCard(executions: List<CiExecutionEntity>, onOpen: (String?, Rect) -> Unit, modifier: Modifier = Modifier) {
    var bounds = Rect.Zero
    Column(
        modifier
            .fillMaxSize()
            .padding(6.dp)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .liquidGlass(Quorvi.shapes.panel, GlassLevel.PANEL)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth().clickable { onOpen(null, bounds) }, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.card_ci), style = Quorvi.type.action, color = Quorvi.colors.text, modifier = Modifier.weight(1f))
            val failing = executions.count { ciStatus(it.status, it.conclusion) == com.paulneja.quorvi.ui.operations.Status.FAILURE }
            if (failing > 0) Text(stringResource(R.string.card_ci_failing, failing), style = Quorvi.type.caption, color = Quorvi.colors.danger)
        }
        if (executions.isEmpty()) {
            Text(stringResource(R.string.card_ci_empty), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
        }
        executions.take(3).forEach { e ->
            Row(
                Modifier.fillMaxWidth().clip(Quorvi.shapes.control).clickable { onOpen(e.id, bounds) }.padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(ciStatus(e.status, e.conclusion), 9.dp)
                Spacer(Modifier.width(10.dp))
                Text(e.title, style = Quorvi.type.caption, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(e.repo.substringAfter('/'), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CardFrame(onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(6.dp)
            .liquidGlass(Quorvi.shapes.panel, GlassLevel.PANEL)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        content = content,
    )
}

@Composable
private fun CardTitle(title: String, status: com.paulneja.quorvi.ui.operations.Status?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (status != null) {
            StatusDot(status, 9.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(title, style = Quorvi.type.action, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
fun MissingCard() = CardFrame({}) { Text(stringResource(R.string.card_missing), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary) }

@Composable
fun HostCard(host: com.paulneja.quorvi.connections.HostEntity, health: com.paulneja.quorvi.connections.ssh.HostHealth?, onOpen: () -> Unit) = CardFrame(onOpen) {
    CardTitle(host.name, com.paulneja.quorvi.ui.operations.hostStatus(host, health))
    when {
        host.hostKeyFingerprint == null -> Text(stringResource(R.string.hosts_needs_key), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
        host.healthError != null -> Text(host.healthError, style = Quorvi.type.caption, color = Quorvi.colors.danger, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
        health == null -> Text(stringResource(R.string.hosts_no_health), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
        else -> {
            Meter("CPU", health.loadPerCpu?.coerceAtMost(1.0), "${health.load1 ?: "—"}")
            Meter("RAM", health.memUsedRatio, com.paulneja.quorvi.ui.operations.percent(health.memUsedRatio))
            Meter("Disk", health.diskUsedRatio, com.paulneja.quorvi.ui.operations.percent(health.diskUsedRatio))
        }
    }
}

@Composable
private fun Meter(label: String, ratio: Double?, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.width(40.dp))
        androidx.compose.foundation.layout.Box(Modifier.weight(1f).height(6.dp).clip(Quorvi.shapes.control).background(Quorvi.colors.overlayHover)) {
            val r = (ratio ?: 0.0).toFloat().coerceIn(0f, 1f)
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(r).height(6.dp).background(if (r > 0.9f) Quorvi.colors.danger else Quorvi.colors.accent))
        }
        Text(value, style = Quorvi.type.caption.copy(fontFeatureSettings = "tnum"), color = Quorvi.colors.text, modifier = Modifier.padding(start = 8.dp).width(44.dp))
    }
}

@Composable
fun DockerCard(host: com.paulneja.quorvi.connections.HostEntity, containers: List<com.paulneja.quorvi.connections.ContainerEntity>, onOpen: () -> Unit) = CardFrame(onOpen) {
    val running = containers.count { it.state == "running" }
    CardTitle("Docker · ${host.name}", if (containers.isEmpty()) null else if (running == containers.size) com.paulneja.quorvi.ui.operations.Status.SUCCESS else com.paulneja.quorvi.ui.operations.Status.FAILURE)
    if (containers.isEmpty()) Text(stringResource(R.string.card_docker_empty), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
    else Text(stringResource(R.string.card_docker_running, running, containers.size), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
    containers.sortedBy { it.state == "running" }.take(3).forEach { c ->
        Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(if (c.state == "running") com.paulneja.quorvi.ui.operations.Status.SUCCESS else com.paulneja.quorvi.ui.operations.Status.CANCELLED, 8.dp)
            Spacer(Modifier.width(8.dp))
            Text(c.name, style = Quorvi.type.caption, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(c.status.substringBefore(" ("), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1)
        }
    }
}

@Composable
fun HttpCard(widget: com.paulneja.quorvi.connections.HttpWidgetEntity, onOpen: () -> Unit, onTrigger: () -> Unit) = CardFrame(onOpen) {
    val display = runCatching { com.paulneja.quorvi.connections.http.HttpDisplay.valueOf(widget.display) }.getOrDefault(com.paulneja.quorvi.connections.http.HttpDisplay.TEXT)
    CardTitle(widget.name, com.paulneja.quorvi.ui.operations.httpStatus(widget))
    val stale = widget.fetchedAt != null && System.currentTimeMillis() - widget.fetchedAt > maxOf(widget.refreshSec * 3000L, 10 * 60_000L)
    when {
        widget.error != null -> Text(widget.error, style = Quorvi.type.caption, color = Quorvi.colors.danger, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
        widget.value == null -> Text(stringResource(R.string.accounts_loading), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
        display == com.paulneja.quorvi.connections.http.HttpDisplay.SERIES -> Sparkline(widget.history, Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp))
        display == com.paulneja.quorvi.connections.http.HttpDisplay.LIST -> widget.value.split(", ").take(4).forEach {
            Text(it, style = Quorvi.type.caption, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        display == com.paulneja.quorvi.connections.http.HttpDisplay.BUTTON || display == com.paulneja.quorvi.connections.http.HttpDisplay.TOGGLE -> Text(
            widget.value.take(40) + "  ·  " + widget.method,
            style = Quorvi.type.action,
            color = Quorvi.colors.onAccent,
            modifier = Modifier.padding(top = 8.dp).clip(Quorvi.shapes.control).background(Quorvi.colors.accent).clickable(onClick = onTrigger).padding(horizontal = 14.dp, vertical = 8.dp),
        )
        else -> Text(
            widget.value.take(60) + (widget.unit?.let { " $it" } ?: ""),
            style = if (display == com.paulneja.quorvi.connections.http.HttpDisplay.COUNTER) Quorvi.type.title.copy(fontFeatureSettings = "tnum") else Quorvi.type.body,
            color = Quorvi.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
        )
    }
    if (stale) Text(stringResource(R.string.card_stale), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
}

@Composable
private fun Sparkline(history: String, modifier: Modifier) {
    val values = history.split(',').mapNotNull { it.toDoubleOrNull() }
    val color = Quorvi.colors.accent
    androidx.compose.foundation.Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val span = (values.max() - min).takeIf { it > 0 } ?: 1.0
        val path = androidx.compose.ui.graphics.Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = size.height - (size.height * ((v - min) / span)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
    }
}

@Composable
fun HaCard(entities: List<com.paulneja.quorvi.connections.services.HaEntity>, onToggle: (com.paulneja.quorvi.connections.services.HaEntity) -> Unit, onOpen: () -> Unit) = CardFrame(onOpen) {
    CardTitle(stringResource(R.string.card_ha), null)
    if (entities.isEmpty()) Text(stringResource(R.string.card_ha_empty), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
    val now = System.currentTimeMillis()
    entities.take(4).forEach { e ->
        val switchable = com.paulneja.quorvi.connections.services.HaParsing.toggleService(e.entityId) != null
        val on = e.state == "on" || e.state == "open" || e.state == "playing" || e.state == "home"
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(e.name, style = Quorvi.type.caption, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            val stale = now - e.updatedAt > 10 * 60_000L
            val label = e.state + (e.unit?.let { " $it" } ?: "") + if (stale) " · " + stringResource(R.string.card_stale) else ""
            if (switchable) Text(
                label,
                style = Quorvi.type.caption,
                color = if (on) Quorvi.colors.onAccent else Quorvi.colors.text,
                modifier = Modifier.clip(Quorvi.shapes.control).background(if (on) Quorvi.colors.accent else Quorvi.colors.overlayHover).clickable { onToggle(e) }.padding(horizontal = 10.dp, vertical = 4.dp),
            ) else Text(label, style = Quorvi.type.caption.copy(fontFeatureSettings = "tnum"), color = Quorvi.colors.textSecondary)
        }
    }
}
