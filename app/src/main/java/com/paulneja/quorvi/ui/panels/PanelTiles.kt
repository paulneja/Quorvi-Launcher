package com.paulneja.quorvi.ui.panels

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paulneja.quorvi.R
import com.paulneja.quorvi.launcher.live.DataUsage
import com.paulneja.quorvi.ops.CiExecutionEntity
import com.paulneja.quorvi.ui.design.BentoBlock
import com.paulneja.quorvi.ui.design.BlockHeader
import com.paulneja.quorvi.ui.design.Dot
import com.paulneja.quorvi.ui.design.Glyph
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.ProgressLine
import com.paulneja.quorvi.ui.design.Ring
import com.paulneja.quorvi.ui.operations.Status
import com.paulneja.quorvi.ui.operations.ciStatus

object PanelKinds {
    const val AGENT = "panel:agent"
    const val GITHUB = "panel:github"
    const val NETWORK = "panel:network"
    const val SYSTEM = "panel:system"
    const val SHELL = "panel:shell"
    val supported = setOf(AGENT, GITHUB, NETWORK, SYSTEM, SHELL)
}

fun panelGlyph(kind: String): ImageVector = when (kind) {
    PanelKinds.AGENT -> Glyphs.Agent
    PanelKinds.GITHUB -> Glyphs.Git
    PanelKinds.NETWORK -> Glyphs.Network
    PanelKinds.SYSTEM -> Glyphs.Chip
    PanelKinds.SHELL -> Glyphs.Terminal
    else -> Glyphs.Widgets
}

@Composable
fun panelTitle(kind: String, short: Boolean = false): String = stringResource(
    when (kind) {
        PanelKinds.AGENT -> R.string.panel_agent
        PanelKinds.GITHUB -> R.string.panel_github
        PanelKinds.NETWORK -> if (short) R.string.panel_network_short else R.string.panel_network
        PanelKinds.SYSTEM -> R.string.panel_system
        PanelKinds.SHELL -> R.string.shell_title
        else -> R.string.card_missing
    },
)

@Composable
fun statusColor(status: Status): Color = when (status) {
    Status.SUCCESS -> Quorvi.colors.success
    Status.FAILURE -> Quorvi.colors.danger
    Status.RUNNING, Status.QUEUED -> Quorvi.colors.accent
    else -> Quorvi.colors.textTertiary
}

@Composable
fun PanelTile(kind: String, vm: PanelsViewModel, onOpen: () -> Unit, onLongClick: (() -> Unit)? = null) {
    BentoBlock(onClick = onOpen, onLongClick = onLongClick, padding = 12.dp) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val rich = maxHeight > 140.dp

            val compact = maxWidth < 120.dp
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                if (compact) com.paulneja.quorvi.ui.design.TileIcon(panelGlyph(kind))
                else BlockHeader(panelGlyph(kind), panelTitle(kind, short = true)) { Glyph(Glyphs.Chevron, 16.dp, Quorvi.colors.textTertiary) }
                when (kind) {
                    PanelKinds.AGENT -> AgentTileBody(vm, rich)
                    PanelKinds.GITHUB -> GitHubTileBody(vm, rich)
                    PanelKinds.NETWORK -> NetworkTileBody(vm, rich)
                    PanelKinds.SYSTEM -> SystemTileBody(vm, rich)
                    PanelKinds.SHELL -> ShellTileBody(vm)
                }
            }
        }
    }
}

@Composable
private fun ShellTileBody(vm: PanelsViewModel) {
    val state by vm.runtimeState.collectAsStateWithLifecycle()
    val last by vm.shellHistory.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshRuntime() }
    Column {
        last.lastOrNull()?.let { e -> Text("$ " + e.command, style = Quorvi.type.mono, color = Quorvi.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        StatusLine(
            stringResource(when (state) {
                com.paulneja.quorvi.runtime.RuntimeAvailability.READY -> R.string.shell_ready
                com.paulneja.quorvi.runtime.RuntimeAvailability.INSTALLING -> R.string.shell_installing
                com.paulneja.quorvi.runtime.RuntimeAvailability.FAILED -> R.string.shell_failed
                else -> R.string.shell_open
            }),
            dot = if (state == com.paulneja.quorvi.runtime.RuntimeAvailability.READY) Quorvi.colors.success else Quorvi.colors.textTertiary,
        )
    }
}

@Composable
private fun StatusLine(text: String, dot: Color? = null, color: Color = Quorvi.colors.text) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) {
            Dot(dot, 7.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = Quorvi.type.action, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AgentTileBody(vm: PanelsViewModel, rich: Boolean) {
    val runs by vm.runs.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val summary = PanelSummaries.agent(runs, providers.isNotEmpty())
    Column {
        if (rich) {
            runs.take(3).forEach { r ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(if (r.state.active) Quorvi.colors.accent else if (r.state == com.paulneja.quorvi.ops.RunState.COMPLETED) Quorvi.colors.success else Quorvi.colors.textTertiary, 6.dp, halo = false)
                    Spacer(Modifier.width(8.dp))
                    Text(r.goal, style = Quorvi.type.caption, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (r.total > 0) Text("${r.done}/${r.total}", style = Quorvi.type.mono, color = Quorvi.colors.textSecondary)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        when (summary) {
            is AgentSummary.Running -> {
                StatusLine(stringResource(R.string.tile_agent_running, summary.count, if (summary.total > 0) stringResource(R.string.tile_agent_progress, summary.done, summary.total) else "…"), Quorvi.colors.accent)
                Spacer(Modifier.height(7.dp))
                ProgressLine(if (summary.total > 0) summary.done.toFloat() / summary.total else 0.08f)
            }
            AgentSummary.Idle -> StatusLine(stringResource(R.string.tile_agent_idle), Quorvi.colors.success)
            AgentSummary.NoModel -> StatusLine(stringResource(R.string.tile_agent_nomodel), color = Quorvi.colors.accent)
        }
    }
}

@Composable
private fun GitHubTileBody(vm: PanelsViewModel, rich: Boolean) {
    val ci by vm.ci.collectAsStateWithLifecycle()
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    val prs by vm.prs.collectAsStateWithLifecycle()
    val watched by vm.watched.collectAsStateWithLifecycle()
    val summary = PanelSummaries.github(signedIn, ci, prs?.size, watched.size)
    val prSuffix = { n: Int? -> if (n != null && n > 0) " · " + n + " PR" else "" }
    Column {
        if (rich) {
            PanelSummaries.latestPerRepo(ci).take(3).forEach { e -> CiMiniRow(e) }
            Spacer(Modifier.height(6.dp))
        }
        when (summary) {
            GitHubSummary.SignedOut -> StatusLine(stringResource(R.string.tile_github_signin), color = Quorvi.colors.accent)
            GitHubSummary.Empty -> StatusLine(stringResource(R.string.tile_github_empty), color = Quorvi.colors.textSecondary)
            is GitHubSummary.NoCi -> StatusLine(androidx.compose.ui.res.pluralStringResource(R.plurals.tile_github_no_ci, summary.repos, summary.repos) + prSuffix(summary.prs), color = Quorvi.colors.textSecondary)
            is GitHubSummary.Ok -> StatusLine(stringResource(R.string.tile_github_ok) + prSuffix(summary.prs), Quorvi.colors.success)
            is GitHubSummary.Running -> StatusLine(stringResource(R.string.tile_github_running) + prSuffix(summary.prs), Quorvi.colors.accent)
            is GitHubSummary.Failing -> StatusLine(stringResource(R.string.tile_github_failing, summary.count) + prSuffix(summary.prs), Quorvi.colors.danger)
        }
        if (!rich && ci.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ci.take(6).reversed().forEach { e ->
                    Box(Modifier.size(14.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(statusColor(ciStatus(e.status, e.conclusion))))
                }
            }
        }
    }
}

@Composable
fun CiMiniRow(e: CiExecutionEntity) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(statusColor(ciStatus(e.status, e.conclusion)), 6.dp, halo = false)
        Spacer(Modifier.width(8.dp))
        Text("${e.repo.substringAfter('/')} · ${e.branch ?: e.workflow}", style = Quorvi.type.caption, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(relativeTime(e.createdAt ?: e.fetchedAt), style = Quorvi.type.mono, color = Quorvi.colors.textSecondary)
    }
}

@Composable
private fun NetworkTileBody(vm: PanelsViewModel, rich: Boolean) {
    val net by vm.net.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val line = when (val s = PanelSummaries.network(net)) {
        NetSummary.Wifi -> stringResource(R.string.tile_net_wifi) + " · " + stringResource(R.string.net_connected)
        is NetSummary.Cellular -> listOfNotNull(stringResource(R.string.tile_net_cell), s.operator).joinToString(" · ")
        NetSummary.Airplane -> stringResource(R.string.tile_net_airplane)
        NetSummary.Offline -> stringResource(R.string.tile_net_offline)
    }
    Column {
        StatusLine(line, if (net.wifiConnected || net.cellularActive) Quorvi.colors.success else Quorvi.colors.textTertiary)
        val month = usage as? DataUsage.Month
        if (month != null) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.tile_month), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.weight(1f))
                Text(Formatter.formatShortFileSize(context, month.mobileBytes), style = Quorvi.type.mono, color = Quorvi.colors.textSecondary)
            }
            if (rich) month.topMobile.take(2).forEach { a ->
                Spacer(Modifier.height(6.dp))
                Row { Text(a.label, style = Quorvi.type.caption, color = Quorvi.colors.text, modifier = Modifier.weight(1f), maxLines = 1); Text(Formatter.formatShortFileSize(context, a.bytes), style = Quorvi.type.mono, color = Quorvi.colors.textSecondary) }
            }
        }
    }
}

@Composable
private fun SystemTileBody(vm: PanelsViewModel, rich: Boolean) {
    val battery by vm.battery.collectAsStateWithLifecycle()
    val system by vm.system.collectAsStateWithLifecycle()
    val priv by vm.priv.collectAsStateWithLifecycle()
    val percent = battery?.percent
    val privLabel = priv?.usable?.let { it.name.lowercase().replaceFirstChar(Char::uppercase) }
    val batteryColor = when {
        percent == null -> Quorvi.colors.textTertiary
        percent <= 20 -> Quorvi.colors.warning
        else -> Quorvi.colors.success
    }
    if (rich) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            Ring((percent ?: 0) / 100f, batteryColor, size = 46.dp, stroke = 5.dp) { Text("${percent ?: "–"}", style = Quorvi.type.mono, color = Quorvi.colors.text) }
            Ring(system?.storageFraction ?: 0f, Quorvi.colors.warning, size = 46.dp, stroke = 5.dp) { Text("${((system?.storageFraction ?: 0f) * 100).toInt()}", style = Quorvi.type.mono, color = Quorvi.colors.text) }
            Ring(system?.ramFraction ?: 0f, Color(0xFFB58CFF), size = 46.dp, stroke = 5.dp) { Text("${((system?.ramFraction ?: 0f) * 100).toInt()}", style = Quorvi.type.mono, color = Quorvi.colors.text) }
        }
        Spacer(Modifier.height(4.dp))
        Text(privLabel ?: stringResource(R.string.tile_sys_priv_none), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1)
    } else {
        Column {
            val charging = if (battery?.charging == true) " · " + stringResource(R.string.tile_sys_charging) else ""
            StatusLine("${percent ?: "–"} %$charging" + (privLabel?.let { " · $it" } ?: ""))
            Spacer(Modifier.height(7.dp))
            ProgressLine((percent ?: 0) / 100f, batteryColor)
        }
    }
}

fun relativeTime(at: Long, now: Long = System.currentTimeMillis()): String {
    val m = ((now - at) / 60_000).coerceAtLeast(0)
    return when {
        m < 1 -> "<1m"
        m < 60 -> "${m}m"
        m < 60 * 24 -> "${m / 60}h"
        else -> "${m / (60 * 24)}d"
    }
}
