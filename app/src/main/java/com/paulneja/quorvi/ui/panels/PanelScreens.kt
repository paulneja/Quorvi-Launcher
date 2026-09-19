package com.paulneja.quorvi.ui.panels

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paulneja.quorvi.R
import com.paulneja.quorvi.agent.policy.AutonomyMode
import com.paulneja.quorvi.control.PrivBackend
import com.paulneja.quorvi.launcher.live.DataUsage
import com.paulneja.quorvi.ops.RunState
import com.paulneja.quorvi.ui.design.ChipButton
import com.paulneja.quorvi.ui.design.Dot
import com.paulneja.quorvi.ui.design.GlassGroup
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Glyph
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.GroupRow
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.QuorviSegmented
import com.paulneja.quorvi.ui.design.QuorviSlider
import com.paulneja.quorvi.ui.design.QuorviSwitch
import com.paulneja.quorvi.ui.design.ProgressLine
import com.paulneja.quorvi.ui.design.Ring
import com.paulneja.quorvi.ui.design.liquidGlass
import com.paulneja.quorvi.ui.operations.Status
import com.paulneja.quorvi.ui.operations.ciStatus

class PanelLinks(
    val openOperations: () -> Unit,
    val openAccounts: () -> Unit,
    val openCapabilities: () -> Unit,
    val openCi: (String) -> Unit,
    val openShell: (mountStorage: Boolean) -> Unit = {},
)

@Composable
fun PanelContent(kind: String, vm: PanelsViewModel, links: PanelLinks) {
    when (kind) {
        PanelKinds.AGENT -> AgentPanel(vm, links)
        PanelKinds.GITHUB -> GitHubPanel(vm, links)
        PanelKinds.NETWORK -> NetworkPanel(vm)
        PanelKinds.SYSTEM -> SystemPanel(vm, links)
        PanelKinds.SHELL -> ShellPanel(vm, links)
    }
}

@Composable
private fun ShellPanel(vm: PanelsViewModel, links: PanelLinks) {
    val state by vm.runtimeState.collectAsStateWithLifecycle()
    val history by vm.shellHistory.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshRuntime() }
    val ready = state == com.paulneja.quorvi.runtime.RuntimeAvailability.READY
    var command by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            Modifier.weight(1f).liquidGlass(Quorvi.shapes.panel, GlassLevel.PANEL).clip(Quorvi.shapes.panel).clickable { links.openShell(false) }.padding(14.dp),
        ) {
            Glyph(Glyphs.Terminal, 22.dp, Quorvi.colors.accent)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.shell_open_full), style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text)
            Text(stringResource(if (ready) R.string.shell_ready else R.string.shell_open), style = Quorvi.type.caption, color = if (ready) Quorvi.colors.success else Quorvi.colors.textSecondary)
        }
        Column(
            Modifier.weight(1f).liquidGlass(Quorvi.shapes.panel, GlassLevel.PANEL).clip(Quorvi.shapes.panel).clickable { links.openShell(true) }.padding(14.dp),
        ) {
            Glyph(Glyphs.Folder, 22.dp, Quorvi.colors.accent)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.shell_mount), style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text)
            Text(stringResource(R.string.shell_mount_desc), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 2)
        }
    }
    Column(Modifier.fillMaxWidth().liquidGlass(Quorvi.shapes.panel, GlassLevel.PANEL).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.shell_quick), style = Quorvi.type.section, color = Quorvi.colors.textTertiary, modifier = Modifier.weight(1f))
            if (history.isNotEmpty()) Text(stringResource(R.string.shell_clear), style = Quorvi.type.caption, color = Quorvi.colors.accent, modifier = Modifier.clip(CircleShape).clickable { vm.clearShell() }.padding(6.dp))
        }
        Column(
            Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(min = 120.dp, max = 340.dp).clip(Quorvi.shapes.control).background(Color(0xFF07090D))
                .verticalScroll(androidx.compose.foundation.rememberScrollState(), reverseScrolling = true).padding(12.dp),
        ) {
            if (history.isEmpty()) Text(stringResource(if (ready) R.string.shell_quick_hint else R.string.shell_not_ready), style = Quorvi.type.mono, color = Quorvi.colors.textTertiary)
            history.forEach { e ->
                Text("$ " + e.command, style = Quorvi.type.mono, color = Quorvi.colors.accent)
                if (e.running) Text("…", style = Quorvi.type.mono, color = Quorvi.colors.textSecondary)
                else if (e.output.isNotBlank()) Text(e.output, style = Quorvi.type.mono, color = if (e.exitCode == 0) Color(0xFFD7DCE3) else Quorvi.colors.danger)
                Spacer(Modifier.height(8.dp))
            }
        }
        Row(
            Modifier.padding(top = 10.dp).fillMaxWidth().height(46.dp).clip(Quorvi.shapes.pill).background(Color.White.copy(alpha = 0.07f)).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$", style = Quorvi.type.mono, color = Quorvi.colors.accent)
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = command, onValueChange = { command = it }, singleLine = true, enabled = ready,
                textStyle = Quorvi.type.mono.copy(color = Quorvi.colors.text), cursorBrush = SolidColor(Quorvi.colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { vm.runShell(command); command = "" }),
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(if (command.isBlank()) Color.White.copy(alpha = 0.08f) else Quorvi.colors.accent).clickable(enabled = command.isNotBlank()) { vm.runShell(command); command = "" },
                contentAlignment = Alignment.Center,
            ) { Glyph(Glyphs.Send, 17.dp, if (command.isBlank()) Quorvi.colors.textTertiary else Quorvi.colors.onAccent) }
        }
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ls -la", "pwd", "df -h", "uname -a", "pkg update -y", "ls ~/storage").forEach { c -> ChipButton(c, null, { if (ready) vm.runShell(c) }) }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier.liquidGlass(Quorvi.shapes.panel, GlassLevel.PANEL).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(value, style = Quorvi.type.metric, color = Quorvi.colors.text)
        Text(label, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun Chevron() = Glyph(Glyphs.Chevron, 15.dp, Quorvi.colors.textTertiary)

@Composable
private fun AgentPanel(vm: PanelsViewModel, links: PanelLinks) {
    val runs by vm.runs.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val policy by vm.policyConfig.collectAsStateWithLifecycle()
    val privileged by vm.privileged.collectAsStateWithLifecycle()
    val capsules by vm.capsules.collectAsStateWithLifecycle()
    val screen by vm.screenControl.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Row(
        Modifier.fillMaxWidth().clip(Quorvi.shapes.panel).liquidGlass(Quorvi.shapes.panel, GlassLevel.PANEL)
            .clickable { com.paulneja.quorvi.ui.chat.ChatActivity.open(context) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.paulneja.quorvi.ui.chat.NexoAvatar(working = runs.any { it.state.active }, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.agent_open_chat), style = Quorvi.type.title.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text)
            Text(
                if (providers.isEmpty()) stringResource(R.string.agent_no_model) else stringResource(R.string.agent_open_chat_desc),
                style = Quorvi.type.caption, color = if (providers.isEmpty()) Quorvi.colors.accent else Quorvi.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Chevron()
    }

    val active = runs.filter { !it.state.terminal }
    if (active.isNotEmpty()) {
        GlassGroup(title = stringResource(R.string.agent_now)) {
            active.forEach { r -> RunRow(r, onCancel = { vm.cancel(r.id) }, onOpen = if (r.chat) ({ com.paulneja.quorvi.ui.chat.ChatActivity.open(context, chat = r.origin.removePrefix("chat:")) }) else links.openOperations) }
            Row(
                Modifier.fillMaxWidth().clickable { vm.stopAll() }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(Glyphs.Stop, 18.dp, Quorvi.colors.danger)
                Spacer(Modifier.width(11.dp))
                Text(stringResource(R.string.agent_stop_all), style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.danger)
            }
        }
    }

    if (capsules.isNotEmpty()) {
        GlassGroup(title = stringResource(R.string.agent_capsules)) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                capsules.forEach { c -> ChipButton(c.name, Glyphs.Bolt, { vm.runCapsule(c.id) }) }
            }
        }
    }

    var open by remember { mutableStateOf(false) }
    val autonomy = stringResource(
        when (policy.mode) {
            AutonomyMode.MANUAL -> R.string.autonomy_manual
            AutonomyMode.AUTO -> R.string.autonomy_auto
            AutonomyMode.BYPASS -> R.string.autonomy_bypass
        },
    )
    GlassGroup {
        GroupRow(stringResource(R.string.agent_how), stringResource(R.string.agent_how_summary, autonomy, if (screen) stringResource(R.string.on) else stringResource(R.string.off)), Glyphs.Sliders, onClick = { open = !open }) {
            Glyph(if (open) Glyphs.Down else Glyphs.Chevron, 15.dp, Quorvi.colors.textTertiary)
        }
        androidx.compose.animation.AnimatedVisibility(open) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                Setting(stringResource(R.string.agent_model), stringResource(R.string.agent_model_desc)) {
                    Text(
                        stringResource(if (providers.isEmpty()) R.string.agent_add else R.string.models_choose_short),
                        style = Quorvi.type.action, color = Quorvi.colors.accent, maxLines = 1,
                        modifier = Modifier.clip(Quorvi.shapes.control).clickable {
                            if (providers.isEmpty()) links.openAccounts()
                            else context.startActivity(com.paulneja.quorvi.ui.settings.SettingsActivity.intent(context, com.paulneja.quorvi.ui.settings.SettingsModule.AI, "models").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }.padding(6.dp),
                    )
                }
                Text(stringResource(R.string.agent_autonomy), style = Quorvi.type.body, color = Quorvi.colors.text, modifier = Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(8.dp))
                QuorviSegmented(
                    listOf(AutonomyMode.MANUAL to stringResource(R.string.autonomy_manual), AutonomyMode.AUTO to stringResource(R.string.autonomy_auto), AutonomyMode.BYPASS to stringResource(R.string.autonomy_bypass)),
                    policy.mode, { vm.setMode(it) },
                )
                Text(
                    stringResource(when (policy.mode) { AutonomyMode.MANUAL -> R.string.autonomy_manual_desc; AutonomyMode.AUTO -> R.string.autonomy_auto_desc; AutonomyMode.BYPASS -> R.string.autonomy_bypass_desc }),
                    style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                )
                Setting(stringResource(R.string.agent_screen), stringResource(R.string.agent_screen_desc), onClick = { vm.open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { QuorviSwitch(screen, null) }
                val rootOn = privileged.enabled.isNotEmpty()
                Setting(stringResource(R.string.agent_root), stringResource(if (rootOn) R.string.agent_root_desc_on else R.string.agent_root_desc_off), onClick = links.openCapabilities) { QuorviSwitch(rootOn, null) }
                Setting(stringResource(R.string.agent_lan), stringResource(R.string.agent_lan_sub), onClick = { vm.setRelaxNetwork(!policy.relaxNetwork) }) { QuorviSwitch(policy.relaxNetwork, { vm.setRelaxNetwork(it) }) }
            }
        }
        GroupRow(stringResource(R.string.agent_history), stringResource(R.string.agent_history_desc), Glyphs.Timer, onClick = links.openOperations) { Chevron() }
    }
}

@Composable
private fun Setting(title: String, description: String, onClick: (() -> Unit)? = null, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(Quorvi.shapes.control).clickable(onClick = onClick) else Modifier).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = Quorvi.type.body, color = Quorvi.colors.text)
            Text(description, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        control()
    }
}

@Composable
private fun RunRow(r: RunView, onCancel: (() -> Unit)?, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val color = when {
                r.state.active -> Quorvi.colors.accent
                r.state == RunState.COMPLETED -> Quorvi.colors.success
                r.state == RunState.FAILED -> Quorvi.colors.danger
                else -> Quorvi.colors.textTertiary
            }
            Dot(color, 6.dp)
            Spacer(Modifier.width(8.dp))
            Text(r.goal, style = Quorvi.type.action, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(if (r.total > 0 && !r.state.terminal) "${r.done}/${r.total}" else relativeTime(r.updatedAt), style = Quorvi.type.mono, color = Quorvi.colors.textSecondary)
            if (onCancel != null) {
                Spacer(Modifier.width(8.dp))
                Glyph(Glyphs.Close, 16.dp, Quorvi.colors.textSecondary, Modifier.clip(CircleShape).clickable(onClick = onCancel))
            }
        }
        if (!r.state.terminal) {
            Spacer(Modifier.height(7.dp))
            ProgressLine(if (r.total > 0) r.done.toFloat() / r.total else 0.06f)
            (r.currentStep ?: r.state.name.lowercase()).let { Text(it, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(top = 5.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else if (r.error != null) {
            Text(r.error, style = Quorvi.type.caption, color = Quorvi.colors.danger, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun GitHubPanel(vm: PanelsViewModel, links: PanelLinks) {
    val ci by vm.ci.collectAsStateWithLifecycle()
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    val prs by vm.prs.collectAsStateWithLifecycle()
    val watched by vm.watched.collectAsStateWithLifecycle()
    if (!signedIn) {
        GlassGroup {
            Text(stringResource(R.string.gh_signin_hint), style = Quorvi.type.body, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(16.dp))
            GroupRow(stringResource(R.string.gh_signin), glyph = Glyphs.Git, onClick = links.openAccounts) { Chevron() }
        }
        return
    }
    val latest = PanelSummaries.latestPerRepo(ci)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val green = latest.count { ciStatus(it.status, it.conclusion) == Status.SUCCESS }
        Stat("$green/${latest.size}", stringResource(R.string.gh_ci_green), Modifier.weight(1f))
        Stat(prs?.size?.toString() ?: "–", stringResource(R.string.gh_prs), Modifier.weight(1f))
        Stat("${watched.size}", stringResource(R.string.gh_repos_count), Modifier.weight(1f))
    }
    GlassGroup(title = stringResource(R.string.gh_ci)) {
        if (latest.isEmpty() && watched.isEmpty()) Text(stringResource(R.string.gh_empty_ci), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))

        watched.filter { repo -> latest.none { it.repo == repo } }.forEach { repo ->
            GroupRow(repo.substringAfter('/'), stringResource(R.string.gh_no_runs), leading = { Dot(Quorvi.colors.textTertiary, 7.dp) })
        }
        latest.take(8).forEach { e ->
            val status = ciStatus(e.status, e.conclusion)
            GroupRow(
                e.repo.substringAfter('/'),
                listOfNotNull(e.workflow, e.branch, relativeTime(e.createdAt ?: e.fetchedAt)).joinToString(" · ") + if (e.provider != "github") " · ${e.provider}" else "",
                leading = { Dot(statusColor(status), 7.dp) },
                onClick = { links.openCi(e.id) },
            ) {
                if (status == Status.FAILURE && e.provider == "github") Glyph(Glyphs.Redo, 18.dp, Quorvi.colors.textSecondary, Modifier.clip(CircleShape).clickable { vm.rerun(e) }.padding(4.dp))
                else if (status == Status.RUNNING) Text("…", style = Quorvi.type.mono, color = Quorvi.colors.accent)
            }
        }
    }
    GlassGroup(title = stringResource(R.string.gh_review)) {
        val list = prs.orEmpty()
        if (list.isEmpty()) Text("–", style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        val context = LocalContext.current
        list.take(6).forEach { pr ->
            GroupRow(
                pr.title,
                "${pr.repositoryUrl.substringAfterLast('/')} #${pr.number}" + (pr.user?.login?.let { " · @$it" } ?: ""),
                leading = {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(Quorvi.colors.accent.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                        Text(pr.user?.login?.take(1)?.uppercase() ?: "?", style = Quorvi.type.label, color = Quorvi.colors.text)
                    }
                },
                onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pr.htmlUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
            ) { Chevron() }
        }
    }
    GlassGroup(title = stringResource(R.string.gh_repos)) {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            watched.forEach { ChipButton(it.substringAfter('/'), null, links.openAccounts) }
            ChipButton(stringResource(R.string.gh_manage), Glyphs.Plus, links.openAccounts)
        }
    }
}

@Composable
private fun BigToggle(title: String, subtitle: String, glyph: ImageVector, on: Boolean, modifier: Modifier, onToggle: (Boolean) -> Unit) {
    Column(
        modifier.height(118.dp).liquidGlass(Quorvi.shapes.panel, GlassLevel.PANEL).clip(Quorvi.shapes.panel).clickable { onToggle(!on) }.padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(Quorvi.shapes.control).background(if (on) Quorvi.colors.accent else Color.White.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                Glyph(glyph, 19.dp, if (on) Quorvi.colors.onAccent else Quorvi.colors.text)
            }
            Spacer(Modifier.weight(1f))
            QuorviSwitch(on, onToggle)
        }
        Column {
            Text(title, style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text, maxLines = 1)
            Text(subtitle, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NetworkPanel(vm: PanelsViewModel) {
    val net by vm.net.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val priv by vm.priv.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BigToggle(
            stringResource(R.string.net_wifi),
            stringResource(when { net.wifiConnected -> R.string.net_connected; net.wifiEnabled -> R.string.net_on_idle; else -> R.string.net_off }),
            Glyphs.Wifi, net.wifiEnabled, Modifier.weight(1f),
        ) { vm.wifi(it) }
        val data = net.mobileDataEnabled ?: net.cellularActive
        BigToggle(
            stringResource(R.string.net_mobile),
            listOfNotNull(net.operator, stringResource(if (data) R.string.net_connected else R.string.net_off)).joinToString(" · "),
            Glyphs.Cellular, data, Modifier.weight(1f),
        ) { vm.mobileData(it) }
    }

    if (priv.let { it != null && it.usable == null }) Text(
        stringResource(R.string.net_via_settings),
        style = Quorvi.type.caption, color = Quorvi.colors.textTertiary, modifier = Modifier.padding(horizontal = 8.dp),
    )
    GlassGroup(title = stringResource(R.string.net_usage)) {
        when (val u = usage) {
            DataUsage.NeedsAccess, null -> {
                Text(stringResource(R.string.net_usage_access), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                GroupRow(stringResource(R.string.net_grant), onClick = vm::usageAccess) { Chevron() }
            }
            is DataUsage.Month -> Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                val top = u.topMobile.firstOrNull()?.bytes ?: 1L
                Ring(1f, Quorvi.colors.accent, size = 84.dp, stroke = 8.dp) {
                    Text(Formatter.formatShortFileSize(context, u.mobileBytes), style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    u.topMobile.forEach { a ->
                        Column {
                            Row { Text(a.label, style = Quorvi.type.caption, color = Quorvi.colors.text, modifier = Modifier.weight(1f), maxLines = 1); Text(Formatter.formatShortFileSize(context, a.bytes), style = Quorvi.type.mono, color = Quorvi.colors.textSecondary) }
                            Spacer(Modifier.height(4.dp))
                            ProgressLine(a.bytes.toFloat() / top.coerceAtLeast(1))
                        }
                    }
                    Text(stringResource(R.string.net_usage_wifi, Formatter.formatShortFileSize(context, u.wifiBytes)), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
                }
            }
        }
    }
    GlassGroup {
        GroupRow(stringResource(R.string.net_bluetooth), null, Glyphs.Bluetooth) { QuorviSwitch(net.bluetoothEnabled == true, { vm.bluetooth(it) }) }
        GroupRow(stringResource(R.string.net_hotspot), null, Glyphs.Hotspot, onClick = vm::hotspot) { Chevron() }
        GroupRow(stringResource(R.string.net_vpn), stringResource(if (net.vpn) R.string.net_vpn_on else R.string.net_vpn_off), Glyphs.Vpn, onClick = vm::vpn) { Chevron() }
        GroupRow(
            stringResource(R.string.net_dns),
            when (net.privateDnsMode) { "hostname" -> net.privateDnsHost ?: "–"; "off" -> stringResource(R.string.net_dns_off); else -> stringResource(R.string.net_dns_auto) },
            Glyphs.Globe,
            onClick = { vm.privateDns(if (net.privateDnsMode == "hostname") null else "dns.adguard-dns.com") },
        ) { Chevron() }
        GroupRow(stringResource(R.string.net_adb), net.adbWifi?.let { null } ?: stringResource(R.string.net_adb_unknown), Glyphs.Terminal) {
            QuorviSwitch(net.adbWifi == true, { vm.adbWifi(it) })
        }
    }
}

@Composable
private fun SystemPanel(vm: PanelsViewModel, links: PanelLinks) {
    val battery by vm.battery.collectAsStateWithLifecycle()
    val system by vm.system.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val priv by vm.priv.collectAsStateWithLifecycle()
    val hooks by vm.hooksAtLevel.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().liquidGlass(Quorvi.shapes.panel, GlassLevel.PANEL).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        val percent = battery?.percent ?: 0
        RingStat(percent / 100f, if (percent <= 20) Quorvi.colors.warning else Quorvi.colors.success, "$percent %", stringResource(R.string.sys_battery) + if (battery?.charging == true) " ⚡" else "")
        RingStat(system?.storageFraction ?: 0f, Quorvi.colors.warning, "${((system?.storageFraction ?: 0f) * 100).toInt()} %", stringResource(R.string.sys_storage) + " · " + Formatter.formatShortFileSize(context, system?.storageTotal ?: 0))
        RingStat(system?.ramFraction ?: 0f, Color(0xFFB58CFF), "${((system?.ramFraction ?: 0f) * 100).toInt()} %", stringResource(R.string.sys_ram) + " · " + Formatter.formatShortFileSize(context, system?.ramTotal ?: 0))
    }
    Column(Modifier.fillMaxWidth().liquidGlass(Quorvi.shapes.panel, GlassLevel.PANEL).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        QuorviSlider(device.brightness, { if (device.canWriteSettings) vm.brightness(it) }, glyph = Glyphs.Brightness, onDone = { vm.brightness(it) })
        if (!device.canWriteSettings) Text(stringResource(R.string.sys_brightness_access), style = Quorvi.type.caption, color = Quorvi.colors.textTertiary, modifier = Modifier.padding(start = 4.dp))
        QuorviSlider(device.volume, { vm.volume(it) }, glyph = Glyphs.Volume)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (device.hasTorch) QuickTile(stringResource(R.string.sys_torch), Glyphs.Torch, device.torch, Modifier.weight(1f)) { vm.torch() }
            QuickTile(stringResource(R.string.sys_dnd), Glyphs.Moon, device.dnd, Modifier.weight(1f)) { vm.dnd() }
            QuickTile(stringResource(R.string.sys_rotate), Glyphs.Rotate, device.autoRotate, Modifier.weight(1f)) { vm.autoRotate(!device.autoRotate) }
            QuickTile(stringResource(R.string.sys_saver), Glyphs.Leaf, device.batterySaver, Modifier.weight(1f)) { vm.batterySaver(!device.batterySaver) }
        }
    }
    GlassGroup(title = stringResource(R.string.sys_privileges)) {
        val p = priv
        PrivBackend.entries.forEach { b ->
            val enabled = p != null && b in p.enabled
            val ok = p != null && b in p.available
            val degraded = p != null && b in p.degraded
            GroupRow(
                when (b) { PrivBackend.ROOT -> "Root"; PrivBackend.SHIZUKU -> "Shizuku"; PrivBackend.ADB_TCP -> "ADB TCP" },
                when {
                    degraded -> stringResource(R.string.sys_degraded)
                    enabled && ok -> stringResource(R.string.sys_active, if (b == PrivBackend.ROOT) p!!.rootLevel.name else "shell")
                    enabled -> stringResource(R.string.sys_enabled_missing)
                    else -> stringResource(R.string.sys_off)
                },
                leading = { Dot(when { degraded -> Quorvi.colors.warning; enabled && ok -> Quorvi.colors.success; enabled -> Quorvi.colors.danger; else -> Quorvi.colors.textTertiary }, 7.dp) },
                onClick = links.openCapabilities,
            ) { Chevron() }
        }
        GroupRow(stringResource(R.string.sys_hooks), stringResource(R.string.sys_hooks_count, hooks), Glyphs.Hook, onClick = links.openCapabilities) { Chevron() }
    }
}

@Composable
private fun RingStat(fraction: Float, color: Color, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Ring(fraction, color, size = 64.dp, stroke = 7.dp)
        Spacer(Modifier.height(6.dp))
        Text(value, style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), color = Quorvi.colors.text)
        Text(label, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun QuickTile(label: String, glyph: ImageVector, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 66.dp).clip(Quorvi.shapes.control).background(if (on) Quorvi.colors.accent else Color.White.copy(alpha = 0.07f)).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Glyph(glyph, 20.dp, if (on) Quorvi.colors.onAccent else Quorvi.colors.text)
        Spacer(Modifier.height(5.dp))
        Text(label, style = Quorvi.type.label, color = if (on) Quorvi.colors.onAccent else Quorvi.colors.text, maxLines = 1)
    }
}
