package com.paulneja.quorvi.ui.operations

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paulneja.quorvi.R
import com.paulneja.quorvi.ops.CiExecutionEntity
import com.paulneja.quorvi.ops.CoreClient
import com.paulneja.quorvi.ops.RunState
import com.paulneja.quorvi.ui.design.ActionRow
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.Hint
import com.paulneja.quorvi.ui.design.InputField
import com.paulneja.quorvi.ui.design.IconButton
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.QuorviDesign
import com.paulneja.quorvi.ui.design.Panel
import com.paulneja.quorvi.ui.design.Section
import com.paulneja.quorvi.ui.design.Segmented
import com.paulneja.quorvi.ui.theme.QuorviTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class OperationsActivity : ComponentActivity() {
    @Inject lateinit var core: CoreClient
    private val viewModel: OperationsViewModel by viewModels()
    private val connections: ConnectionsViewModel by viewModels()
    private val policy: PolicyViewModel by viewModels()
    private val memory: MemoryViewModel by viewModels()
    private val capsules: CapsuleViewModel by viewModels()
    private val capabilities: CapabilitiesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val start = intent.getStringExtra(EXTRA_EXECUTION)?.let { "ci:$it" } ?: intent.getStringExtra(EXTRA_RUN)?.let { "run:$it" }
            ?: intent.getStringExtra(EXTRA_HOST)?.let { "host:$it" } ?: intent.getStringExtra(EXTRA_HTTP)?.let { "http:$it" }
            ?: if (intent.getBooleanExtra(EXTRA_CAPABILITIES, false)) "capabilities" else null
        setContent {
            QuorviTheme {
                QuorviDesign {
                    LaunchedEffect(Unit) { viewModel.messages.collect { Toast.makeText(this@OperationsActivity, it, Toast.LENGTH_LONG).show() } }
                    LaunchedEffect(Unit) { memory.messages.collect { Toast.makeText(this@OperationsActivity, it, Toast.LENGTH_LONG).show() } }
                    LaunchedEffect(Unit) { connections.messages.collect { Toast.makeText(this@OperationsActivity, it, Toast.LENGTH_LONG).show() } }
                    Operations(viewModel, connections, policy, memory, capsules, capabilities, start, agentsFirst = intent.getBooleanExtra(EXTRA_AGENTS, false), onBack = ::finish)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        core.attach()
        core.setVisible(true)
        core.refresh(false)
    }

    override fun onStop() {
        core.setVisible(false)
        core.detach()
        super.onStop()
    }

    companion object {
        const val EXTRA_EXECUTION = "execution"
        const val EXTRA_RUN = "run"
        const val EXTRA_AGENTS = "agents"
        const val EXTRA_HOST = "host"
        const val EXTRA_HTTP = "http"
        const val EXTRA_CAPABILITIES = "capabilities"

        fun host(context: Context, id: String) = Intent(context, OperationsActivity::class.java).putExtra(EXTRA_HOST, id)
        fun http(context: Context, id: String) = Intent(context, OperationsActivity::class.java).putExtra(EXTRA_HTTP, id)
        fun capabilities(context: Context) = Intent(context, OperationsActivity::class.java).putExtra(EXTRA_CAPABILITIES, true)

        fun run(context: Context, id: String) = Intent(context, OperationsActivity::class.java).putExtra(EXTRA_RUN, id)

        fun agents(context: Context) = Intent(context, OperationsActivity::class.java).putExtra(EXTRA_AGENTS, true)

        fun ci(context: Context, id: String) = Intent(context, OperationsActivity::class.java).putExtra(EXTRA_EXECUTION, id)
    }
}

private enum class Tab { AGENTS, CI, HOSTS, HTTP }
private enum class CiFilter { ALL, FAILING, RUNNING }

@Composable
private fun Operations(viewModel: OperationsViewModel, connections: ConnectionsViewModel, policy: PolicyViewModel, memory: MemoryViewModel, capsules: CapsuleViewModel, capabilities: CapabilitiesViewModel, start: String?, agentsFirst: Boolean, onBack: () -> Unit) {
    var route by rememberSaveable { mutableStateOf(start ?: "list") }
    var tab by rememberSaveable { mutableStateOf(if (agentsFirst) Tab.AGENTS else Tab.CI) }
    val colors = Quorvi.colors
    BackHandler(enabled = route != "list" && start == null || route.startsWith("log:") || route.startsWith("term:")) {
        route = when {
            route.startsWith("log:") -> route.removePrefix("log:").substringBefore('|').let { "ci:$it" }.also { viewModel.closeLog() }
            route.startsWith("term:") -> "host:" + route.removePrefix("term:")
            else -> "list"
        }
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.sunken, colors.content))).windowInsetsPadding(WindowInsets.safeDrawing)) {
        when {
            route == "list" -> OperationsList(viewModel, connections, tab, { tab = it }, onBack, onOpenCi = { viewModel.openExecution(it); route = "ci:$it" }, onOpenRun = { route = "run:$it" }, onRoute = { route = it })
            route == "policy" -> PolicyScreen(policy, onBack = { route = "list" })
            route == "memory" -> MemoryScreen(memory, onBack = { route = "list" })
            route == "capsules" -> CapsulesTab(capsules, onEdit = { route = "capsule:$it" }, onRun = { route = "run:$it" })
            route == "capabilities" -> CapabilitiesScreen(capabilities, onBack = { route = "list" })
            route.startsWith("capsule:") -> CapsuleEditor(capsules, route.removePrefix("capsule:"), onBack = { route = "capsules" }, onRun = { route = "run:$it" })
            route == "hostnew" -> HostNew(connections, onBack = { route = "list" }, onCreated = { route = "host:$it" })
            route.startsWith("host:") -> HostDetail(connections, route.removePrefix("host:"), onBack = { if (start != null) onBack() else route = "list" }, onTerminal = { route = "term:" + route.removePrefix("host:") })
            route.startsWith("term:") -> HostTerminal(connections, route.removePrefix("term:"), onBack = { route = "host:" + route.removePrefix("term:") })
            route.startsWith("http:") -> HttpEdit(connections, route.removePrefix("http:"), onBack = { if (start != null) onBack() else route = "list" })
            route.startsWith("ci:") -> CiDetail(viewModel, route.removePrefix("ci:"), onBack = { if (start != null) onBack() else route = "list" }, onLog = { route = "log:${route.removePrefix("ci:")}|$it" }, onRun = { route = "run:$it" })
            route.startsWith("log:") -> LogViewer(viewModel, onBack = { viewModel.closeLog(); route = "ci:" + route.removePrefix("log:").substringBefore('|') })
            route.startsWith("run:") -> RunChat(viewModel, policy, route.removePrefix("run:"), onBack = { if (start != null) onBack() else route = "list" })
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(Glyphs.Close, onBack)
        Text(title, style = Quorvi.type.title, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
        action?.invoke()
    }
}

@Composable
private fun OperationsList(viewModel: OperationsViewModel, connections: ConnectionsViewModel, tab: Tab, onTab: (Tab) -> Unit, onBack: () -> Unit, onOpenCi: (String) -> Unit, onOpenRun: (String) -> Unit, onRoute: (String) -> Unit) {
    val ci by viewModel.ci.collectAsStateWithLifecycle()
    val runs by viewModel.runs.collectAsStateWithLifecycle()
    val watched by viewModel.watched.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(CiFilter.ALL) }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.ops_title), onBack) { IconButton(Glyphs.Redo, viewModel::refresh) }
        Box(Modifier.padding(horizontal = 16.dp)) {
            Segmented(listOf(Tab.AGENTS to stringResource(R.string.ops_agents), Tab.CI to stringResource(R.string.ops_ci), Tab.HOSTS to stringResource(R.string.ops_hosts), Tab.HTTP to "HTTP"), tab, onTab)
        }
        if (tab == Tab.HOSTS) {
            HostsTab(connections, onOpen = { onRoute("host:$it") }, onAdd = { onRoute("hostnew") })
        } else if (tab == Tab.HTTP) {
            HttpTab(connections, onEdit = { onRoute("http:$it") })
        } else if (tab == Tab.CI) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                Segmented(listOf(CiFilter.ALL to stringResource(R.string.ops_all), CiFilter.FAILING to stringResource(R.string.ops_failing), CiFilter.RUNNING to stringResource(R.string.ops_running)), filter) { filter = it }
            }
            val shown = ci.filter {
                when (filter) {
                    CiFilter.ALL -> true
                    CiFilter.FAILING -> it.conclusion == "failure" || it.conclusion == "timed_out"
                    CiFilter.RUNNING -> it.status != "completed"
                }
            }
            LazyColumn(contentPadding = PaddingValues(12.dp)) {
                if (!viewModel.signedIn || watched.isEmpty()) {
                    item {
                        Panel {
                            Hint(stringResource(if (!viewModel.signedIn) R.string.ops_ci_sign_in else R.string.ops_ci_no_repos))
                            ActionRow(stringResource(R.string.mod_github), null) { context.startActivity(com.paulneja.quorvi.ui.settings.SettingsActivity.intent(context, com.paulneja.quorvi.ui.settings.SettingsModule.GITHUB)) }
                        }
                    }
                } else if (shown.isEmpty()) {
                    item { Hint(stringResource(R.string.ops_ci_empty)) }
                }
                items(shown, key = { it.id }) { CiRow(it) { onOpenCi(it.id) } }
            }
        } else {
            val runtime by viewModel.runtimeState.collectAsStateWithLifecycle()
            val indicator by viewModel.indicator.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { viewModel.checkRuntime() }
            LazyColumn(contentPadding = PaddingValues(12.dp)) {
                val anyActive = runs.any { runCatching { RunState.valueOf(it.state) }.getOrNull()?.terminal == false }
                item {
                    Panel {
                        if (anyActive) ActionRow(stringResource(R.string.agents_stop_all), stringResource(R.string.ops_kill_hint), danger = true) { viewModel.killAll() }
                        ActionRow(stringResource(R.string.ops_indicator), indicatorLabel(indicator)) { viewModel.cycleIndicator() }

                        ActionRow(stringResource(R.string.mod_agents), stringResource(R.string.mod_agents_desc)) { context.startActivity(com.paulneja.quorvi.ui.settings.SettingsActivity.intent(context, com.paulneja.quorvi.ui.settings.SettingsModule.AGENTS)) }
                    }
                }
                if (runs.isEmpty()) item { Panel { Hint(stringResource(R.string.ops_runs_empty)) } }
                items(runs, key = { it.id }) { run ->
                    Row(
                        Modifier.fillMaxWidth().clip(Quorvi.shapes.control).clickable { onOpenRun(run.id) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(runStatus(run.state))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(run.goal, style = Quorvi.type.action, color = Quorvi.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${run.state.lowercase()} · ${relative(run.updatedAt)}", style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

enum class Status { QUEUED, RUNNING, SUCCESS, FAILURE, CANCELLED, NEUTRAL }

fun ciStatus(status: String?, conclusion: String?): Status = when {
    status == "queued" || status == "waiting" || status == "pending" || status == "requested" -> Status.QUEUED
    status != "completed" -> Status.RUNNING
    conclusion == "success" -> Status.SUCCESS
    conclusion == "failure" || conclusion == "timed_out" || conclusion == "startup_failure" -> Status.FAILURE
    conclusion == "cancelled" -> Status.CANCELLED
    else -> Status.NEUTRAL
}

private fun runStatus(state: String): Status = when (runCatching { RunState.valueOf(state) }.getOrNull()) {
    RunState.COMPLETED -> Status.SUCCESS
    RunState.FAILED -> Status.FAILURE
    RunState.CANCELLED, RunState.INTERRUPTED -> Status.CANCELLED
    RunState.QUEUED -> Status.QUEUED
    null -> Status.NEUTRAL
    else -> Status.RUNNING
}

@Composable
fun StatusDot(status: Status, size: androidx.compose.ui.unit.Dp = 12.dp) {
    val color = when (status) {
        Status.SUCCESS -> Color(0xFF4ADE80)
        Status.FAILURE -> Quorvi.colors.danger
        Status.RUNNING -> Quorvi.colors.accent
        Status.QUEUED -> Color(0xFFFFD166)
        Status.CANCELLED, Status.NEUTRAL -> Quorvi.colors.textSecondary
    }
    val pulse = if (status == Status.RUNNING || status == Status.QUEUED) {
        rememberInfiniteTransition(label = "pulse").animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a").value
    } else 1f
    Box(Modifier.size(size).alpha(pulse).clip(CircleShape).background(color))
}

@Composable
fun CiRow(e: CiExecutionEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(Quorvi.shapes.control).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(ciStatus(e.status, e.conclusion))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, style = Quorvi.type.action, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(e.repo.substringAfter('/'), "${e.workflow} #${e.number}", e.branch).joinToString(" · "),
                style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(relative(e.updatedAt ?: e.createdAt), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
    }
}

@Composable
private fun relative(at: Long?): String = at?.let { DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString() } ?: ""

private fun duration(from: Long?, to: Long?): String? {
    if (from == null) return null
    val end = to ?: System.currentTimeMillis()
    val s = ((end - from) / 1000).coerceAtLeast(0)
    return if (s >= 3600) "${s / 3600}h ${(s % 3600) / 60}m" else "${s / 60}m ${s % 60}s"
}

private fun iso(value: String?): Long? = value?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }

@Composable
private fun CiDetail(viewModel: OperationsViewModel, id: String, onBack: () -> Unit, onLog: (String) -> Unit, onRun: (String) -> Unit) {
    val execution by remember(id) { viewModel.execution(id) }.collectAsState(null)
    val jobs by remember(id) { viewModel.jobs(id) }.collectAsState(emptyList())
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<Boolean?>(null) }
    val done = stringResource(R.string.ops_rerun_done)
    val failed = stringResource(R.string.ops_rerun_failed)
    LaunchedEffect(id) { viewModel.openExecution(id) }
    val e = execution
    Column(Modifier.fillMaxSize()) {
        TopBar(e?.title ?: "", onBack)
        if (e == null) {
            Hint(stringResource(R.string.accounts_loading))
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            item {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        StatusDot(ciStatus(e.status, e.conclusion), 14.dp)
                        Spacer(Modifier.width(10.dp))
                        Text((e.conclusion ?: e.status).replace('_', ' '), style = Quorvi.type.action, color = Quorvi.colors.text)
                    }
                    Hint(listOfNotNull(e.repo, "${e.workflow} #${e.number}", e.branch, e.event, e.actor?.let { "@$it" }, duration(e.startedAt, if (e.status == "completed") e.updatedAt else null)).joinToString(" · "))
                    ActionRow(stringResource(R.string.ops_open_github), null) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(e.url))) } }
                    if (e.status == "completed" && (e.conclusion == "failure" || e.conclusion == "timed_out")) {
                        val goal = stringResource(R.string.ops_diagnose_goal, e.workflow, e.number, e.repo)
                        val unavailable = stringResource(R.string.command_agent_unavailable)
                        ActionRow(stringResource(R.string.ops_diagnose), stringResource(R.string.command_agent_ci)) {
                            val runId = viewModel.diagnose(e, goal)
                            if (runId != null) onRun(runId) else Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (e.status == "completed") {
                        if (e.conclusion == "failure") ActionRow(stringResource(R.string.ops_rerun_failed_jobs), null) { confirm = true }
                        ActionRow(stringResource(R.string.ops_rerun_all), null) { confirm = false }
                    }
                    confirm?.let { failedOnly ->
                        Hint(stringResource(R.string.ops_rerun_confirm, e.repo, e.number))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.pin_cancel), style = Quorvi.type.action, color = Quorvi.colors.textSecondary, modifier = Modifier.clip(Quorvi.shapes.control).clickable { confirm = null }.padding(12.dp))
                            Text(stringResource(R.string.ops_confirm), style = Quorvi.type.action, color = Quorvi.colors.accent, modifier = Modifier.clip(Quorvi.shapes.control).clickable { viewModel.rerun(e, failedOnly, done, failed); confirm = null }.padding(12.dp))
                        }
                    }
                }
            }
            item { Section(stringResource(R.string.ops_jobs)) }
            if (jobs.isEmpty()) item { Hint(stringResource(R.string.accounts_loading)) }
            items(jobs, key = { it.id }) { job ->
                val steps = viewModel.steps(job)
                Panel {
                    Row(
                        Modifier.fillMaxWidth().clip(Quorvi.shapes.control).clickable { viewModel.loadLog(e, job); onLog(job.id) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(ciStatus(job.status, job.conclusion))
                        Spacer(Modifier.width(10.dp))
                        Text(job.name, style = Quorvi.type.action, color = Quorvi.colors.text, modifier = Modifier.weight(1f))
                        duration(job.startedAt, job.completedAt)?.let { Text(it, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary) }
                    }
                    val (finished, total) = steps.count { it.status == "completed" } to steps.size
                    if (total > 0) Hint(stringResource(R.string.ops_steps_progress, finished, total))
                    steps.forEach { step ->
                        Row(Modifier.fillMaxWidth().padding(start = 22.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(ciStatus(step.status, step.conclusion), 8.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(step.name, style = Quorvi.type.caption, color = if (step.conclusion == "failure") Quorvi.colors.danger else Quorvi.colors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            duration(iso(step.startedAt), iso(step.completedAt))?.let { Text(it, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LogViewer(viewModel: OperationsViewModel, onBack: () -> Unit) {
    val state by viewModel.log.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        TopBar((state as? LogState.Ready)?.jobName ?: stringResource(R.string.ops_log), onBack)
        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(Quorvi.shapes.control).background(Quorvi.colors.field).padding(12.dp)) {
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = Quorvi.type.body.copy(color = Quorvi.colors.text), cursorBrush = SolidColor(Quorvi.colors.accent),
                decorationBox = { inner -> if (query.isEmpty()) Text(stringResource(R.string.ops_log_search), style = Quorvi.type.body, color = Quorvi.colors.textSecondary); inner() },
            )
        }
        when (val s = state) {
            LogState.Idle, LogState.Loading -> Hint(stringResource(R.string.accounts_loading))
            is LogState.Failed -> Hint(stringResource(R.string.accounts_failed, s.reason))
            is LogState.Ready -> LazyColumn(contentPadding = PaddingValues(12.dp)) {
                s.sections.forEach { section ->
                    val lines = if (query.isBlank()) section.lines else section.lines.filter { it.contains(query, true) }
                    if (query.isNotBlank() && lines.isEmpty()) return@forEach
                    item(key = "h" + section.title + section.hashCode()) {
                        Row(Modifier.padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(if (section.errors.isEmpty()) Status.SUCCESS else Status.FAILURE, 8.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(section.title, style = Quorvi.type.action, color = Quorvi.colors.text)
                        }
                    }
                    val shownLines = if (section.errors.isEmpty() && query.isBlank()) lines.takeLast(40) else lines
                    item(key = "b" + section.title + section.hashCode()) {
                        Box(Modifier.fillMaxWidth().clip(Quorvi.shapes.control).background(Color.Black.copy(alpha = 0.35f)).horizontalScroll(rememberScrollState()).padding(10.dp)) {
                            Text(
                                shownLines.joinToString("\n"),
                                style = Quorvi.type.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
                                color = if (section.errors.isEmpty()) Quorvi.colors.textSecondary else Quorvi.colors.text,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeRow(state: com.paulneja.quorvi.runtime.RuntimeAvailability?, onInstall: () -> Unit) {
    val context = LocalContext.current
    when (state) {
        null -> ActionRow(stringResource(R.string.ops_runtime), stringResource(R.string.accounts_loading)) {}
        com.paulneja.quorvi.runtime.RuntimeAvailability.READY -> ActionRow(stringResource(R.string.ops_runtime), stringResource(R.string.ops_runtime_ready)) {
            runCatching { context.startActivity(Intent(context, com.paulneja.quorvi.runtime.TerminalActivity::class.java)) }
        }
        com.paulneja.quorvi.runtime.RuntimeAvailability.INSTALLING -> ActionRow(stringResource(R.string.ops_runtime), stringResource(R.string.ops_runtime_installing)) {}
        else -> ActionRow(stringResource(R.string.ops_runtime), stringResource(R.string.ops_runtime_setup), onClick = onInstall)
    }
}

@Composable
private fun indicatorLabel(value: com.paulneja.quorvi.core.settings.AgentIndicator): String = stringResource(
    when (value) {
        com.paulneja.quorvi.core.settings.AgentIndicator.OFF -> R.string.indicator_off
        com.paulneja.quorvi.core.settings.AgentIndicator.SUBTLE -> R.string.indicator_subtle
        com.paulneja.quorvi.core.settings.AgentIndicator.COMPACT -> R.string.indicator_compact
        com.paulneja.quorvi.core.settings.AgentIndicator.OPERATIONS_ONLY -> R.string.indicator_operations
        com.paulneja.quorvi.core.settings.AgentIndicator.VERBOSE -> R.string.indicator_verbose
    },
)

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / 1048576.0)
}
