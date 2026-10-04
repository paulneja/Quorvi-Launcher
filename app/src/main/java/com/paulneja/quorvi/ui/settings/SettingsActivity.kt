package com.paulneja.quorvi.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paulneja.quorvi.BuildConfig
import com.paulneja.quorvi.R
import com.paulneja.quorvi.connections.services.ServiceKind
import com.paulneja.quorvi.control.PrivBackend
import com.paulneja.quorvi.launcher.apps.AppCatalog
import com.paulneja.quorvi.launcher.apps.IconCache
import com.paulneja.quorvi.runtime.RuntimeAvailability
import com.paulneja.quorvi.ui.accounts.AccountsViewModel
import com.paulneja.quorvi.ui.accounts.CopilotPanel
import com.paulneja.quorvi.ui.accounts.GitHubAccountPanel
import com.paulneja.quorvi.ui.accounts.ProvidersPanel
import com.paulneja.quorvi.ui.accounts.ServicesPanel
import com.paulneja.quorvi.ui.accounts.ServicesViewModel
import com.paulneja.quorvi.ui.accounts.WatchedReposPanel
import com.paulneja.quorvi.ui.canvas.LocalIconCache
import com.paulneja.quorvi.ui.canvas.QuorviShell
import com.paulneja.quorvi.ui.chat.ChatViewModel
import com.paulneja.quorvi.ui.design.ActionRow
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.Hint
import com.paulneja.quorvi.ui.design.IconButton
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.QuorviDesign
import com.paulneja.quorvi.ui.design.Panel
import com.paulneja.quorvi.ui.design.Section
import com.paulneja.quorvi.ui.operations.CapabilitiesScreen
import com.paulneja.quorvi.ui.operations.CapabilitiesViewModel
import com.paulneja.quorvi.ui.operations.CapsuleEditor
import com.paulneja.quorvi.ui.operations.CapsuleViewModel
import com.paulneja.quorvi.ui.operations.CapsulesTab
import com.paulneja.quorvi.ui.operations.ConnectionsViewModel
import com.paulneja.quorvi.ui.operations.HostDetail
import com.paulneja.quorvi.ui.operations.HostNew
import com.paulneja.quorvi.ui.operations.HostTerminal
import com.paulneja.quorvi.ui.operations.HostsTab
import com.paulneja.quorvi.ui.operations.HttpEdit
import com.paulneja.quorvi.ui.operations.HttpTab
import com.paulneja.quorvi.ui.operations.OperationsActivity
import com.paulneja.quorvi.ui.operations.PolicyScreen
import com.paulneja.quorvi.ui.operations.PolicyViewModel
import com.paulneja.quorvi.ui.studio.StudioModule
import com.paulneja.quorvi.ui.studio.StudioViewModel
import com.paulneja.quorvi.ui.theme.QuorviTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {
    @Inject lateinit var icons: IconCache
    @Inject lateinit var catalog: AppCatalog
    private val studio: StudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val start = buildList {
            add(ROOT)
            SettingsModule.of(intent.getStringExtra(EXTRA_MODULE))?.let { m ->
                add(m.name)
                intent.getStringExtra(EXTRA_SUB)?.let { add(m.name + "/" + it) }
            }
        }
        setContent {
            val appearance by studio.appearance.collectAsStateWithLifecycle()
            val look by studio.look.collectAsStateWithLifecycle()
            QuorviTheme {
                QuorviDesign(profile = appearance.motion, accent = Color(appearance.accent), glass = appearance.glass, look = look) {
                    CompositionLocalProvider(LocalIconCache provides icons) {
                        Settings(studio, catalog, start, onExit = ::finish)
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_MODULE = "module"
        private const val EXTRA_SUB = "sub"
        const val ROOT = "root"

        fun intent(context: Context, module: SettingsModule? = null, sub: String? = null): Intent =
            Intent(context, SettingsActivity::class.java).apply {
                module?.let { putExtra(EXTRA_MODULE, it.name) }
                sub?.let { putExtra(EXTRA_SUB, it) }
            }

        fun open(context: Context, module: SettingsModule? = null, sub: String? = null) {
            context.startActivity(intent(context, module, sub).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

@Composable
private fun Settings(studio: StudioViewModel, catalog: AppCatalog, start: List<String>, onExit: () -> Unit) {
    var stack by rememberSaveable { mutableStateOf(start) }
    var forward by remember { mutableStateOf(true) }
    val push: (String) -> Unit = { r -> forward = true; stack = stack + r }
    val pop: () -> Unit = { if (stack.size <= 1) onExit() else { forward = false; stack = stack.dropLast(1) } }
    val replace: (String) -> Unit = { r -> forward = true; stack = stack.dropLast(1) + r }
    BackHandler { pop() }
    val colors = Quorvi.colors
    val motion = Quorvi.motion

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.sunken, colors.content)))) {
        AnimatedContent(
            targetState = stack.last(),
            transitionSpec = {
                if (!motion.enabled) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                else if (forward) {
                    (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(220, 60)) + scaleIn(tween(320, easing = FastOutSlowInEasing), initialScale = 0.98f)) togetherWith
                        (slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 6 } + fadeOut(tween(160)) + scaleOut(tween(320), targetScale = 0.97f))
                } else {
                    (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 6 } + fadeIn(tween(220, 60)) + scaleIn(tween(320), initialScale = 0.97f)) togetherWith
                        (slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(160)))
                }
            },
            label = "settings",
        ) { route ->
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Route(route, studio, catalog, push, pop, replace)
            }
        }
    }
}

@Composable
private fun Route(route: String, studio: StudioViewModel, catalog: AppCatalog, push: (String) -> Unit, pop: () -> Unit, replace: (String) -> Unit) {
    val context = LocalContext.current
    val module = SettingsModule.of(route.substringBefore('/'))
    val sub = route.substringAfter('/', "")
    when {
        route == SettingsActivity.ROOT -> Root(studio, onOpen = { push(it.name) }, onBack = pop)
        module == SettingsModule.HOME || module == SettingsModule.APPS || module == SettingsModule.BACKUP -> StudioModule(studio, catalog, module, onBack = pop)
        module == SettingsModule.SYSTEM -> CapabilitiesScreen(hiltViewModel<CapabilitiesViewModel>(), onBack = pop)
        module == SettingsModule.AI -> when (sub) {
            "orchestra" -> com.paulneja.quorvi.ui.chat.OrchestraScreen(hiltViewModel<ChatViewModel>(), onBack = pop, onModels = { push("AI/models") })
            "models" -> com.paulneja.quorvi.ui.chat.ModelsScreen(hiltViewModel<ChatViewModel>(), onBack = pop)
            "memory" -> com.paulneja.quorvi.ui.chat.MemoryScreen(hiltViewModel<ChatViewModel>(), onBack = pop)
            "voice" -> SubScreen(stringResource(R.string.voice_title), pop) { VoiceSettings(hiltViewModel<com.paulneja.quorvi.voice.VoiceSettingsViewModel>()) }
            else -> AiModule(onOpen = { push("AI/$it") }, onBack = pop)
        }
        module == SettingsModule.AGENTS -> {
            val capsules = hiltViewModel<CapsuleViewModel>()
            when {
                sub == "policy" -> PolicyScreen(hiltViewModel<PolicyViewModel>(), onBack = pop)
                sub == "capsules" -> SubScreen(stringResource(R.string.mod_capsules), pop) {
                    CapsulesTab(
                        capsules, onEdit = { push("AGENTS/capsule:$it") }, onRun = { context.startActivity(OperationsActivity.run(context, it)) },
                        onForm = { push("AGENTS/capsuleform:$it") },
                        onChat = { com.paulneja.quorvi.ui.chat.ChatActivity.open(context, chat = com.paulneja.quorvi.agent.capsules.CapsuleLaunch.chatId(it)) },
                    )
                }
                sub.startsWith("capsuleform:") -> com.paulneja.quorvi.ui.operations.CapsuleForm(
                    capsules, sub.removePrefix("capsuleform:"), onBack = pop,
                    onJson = { replace("AGENTS/capsule:" + sub.removePrefix("capsuleform:")) },
                    onRun = { context.startActivity(OperationsActivity.run(context, it)) },
                )
                sub.startsWith("capsule:") -> CapsuleEditor(capsules, sub.removePrefix("capsule:"), onBack = pop, onRun = { context.startActivity(OperationsActivity.run(context, it)) })
                else -> AgentsModule(capsules, onOpen = { push("AGENTS/$it") }, onBack = pop)
            }
        }
        module == SettingsModule.GITHUB -> when (sub) {
            "ultra" -> com.paulneja.quorvi.ui.chat.UltraScreen(hiltViewModel<ChatViewModel>(), onBack = pop)
            else -> GitHubModule(onOpen = { push("GITHUB/$it") }, onBack = pop)
        }
        module == SettingsModule.SERVERS -> {
            val connections = hiltViewModel<ConnectionsViewModel>()
            when {
                sub == "hosts" -> SubScreen(stringResource(R.string.mod_hosts), pop) { HostsTab(connections, onOpen = { push("SERVERS/host:$it") }, onAdd = { push("SERVERS/hostnew") }) }
                sub == "http" -> SubScreen(stringResource(R.string.mod_http), pop) { HttpTab(connections, onEdit = { push("SERVERS/http:$it") }) }
                sub == "hostnew" -> HostNew(connections, onBack = pop, onCreated = { replace("SERVERS/host:$it") })
                sub.startsWith("host:") -> HostDetail(connections, sub.removePrefix("host:"), onBack = pop, onTerminal = { push("SERVERS/term:" + sub.removePrefix("host:")) })
                sub.startsWith("term:") -> HostTerminal(connections, sub.removePrefix("term:"), onBack = pop)
                sub.startsWith("http:") -> HttpEdit(connections, sub.removePrefix("http:"), onBack = pop)
                else -> ServersModule(connections, onOpen = { push("SERVERS/$it") }, onBack = pop)
            }
        }
        module == SettingsModule.TERMINAL -> TerminalModule(onBack = pop)
        else -> Root(studio, onOpen = { push(it.name) }, onBack = pop)
    }
}

private enum class Group(val title: Int, val modules: List<SettingsModule>) {
    YOURS(R.string.mod_group_home, listOf(SettingsModule.HOME, SettingsModule.APPS)),
    MIND(R.string.mod_group_ai, listOf(SettingsModule.AI, SettingsModule.AGENTS)),
    LINKS(R.string.mod_group_links, listOf(SettingsModule.GITHUB, SettingsModule.SERVERS)),
    DEVICE(R.string.mod_group_device, listOf(SettingsModule.SYSTEM, SettingsModule.TERMINAL)),
    NEXO(R.string.mod_group_nexo, listOf(SettingsModule.BACKUP)),
}

@Composable
private fun Root(studio: StudioViewModel, onOpen: (SettingsModule) -> Unit, onBack: () -> Unit) {
    val summaries = rememberSummaries(studio)
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    val res = androidx.compose.ui.platform.LocalResources.current
    val matches = SettingsModule.entries.filter { m ->
        q.isEmpty() || res.getString(m.title).lowercase().contains(q) || res.getString(m.description).lowercase().contains(q) || m.keywords.any { it.contains(q) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(Glyphs.Close, onBack)
            }
            Text(stringResource(R.string.settings_title), style = Quorvi.type.title.copy(fontSize = 34.sp, lineHeight = 40.sp), color = Quorvi.colors.text, modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 14.dp))
            SearchField(query, { query = it })
        }
        if (q.isNotEmpty()) {
            item { Spacer(Modifier.height(10.dp)) }
            item {
                Panel {
                    if (matches.isEmpty()) Hint(stringResource(R.string.settings_search_none))
                    matches.forEach { m -> ModuleRow(m, summaries[m], onClick = { onOpen(m) }) }
                }
            }
        } else {
            Group.entries.forEach { g ->
                item { Section(stringResource(g.title)) }
                item { Panel { g.modules.forEach { m -> ModuleRow(m, summaries[m], onClick = { onOpen(m) }) } } }
            }
            item { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = Quorvi.type.caption, color = Quorvi.colors.textTertiary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(48.dp).clip(Quorvi.shapes.pill).background(Quorvi.colors.field).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(rememberVectorPainter(Glyphs.Search), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(Quorvi.colors.textSecondary))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value, onChange, singleLine = true,
            textStyle = Quorvi.type.body.copy(color = Quorvi.colors.text),
            cursorBrush = SolidColor(Quorvi.colors.accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(stringResource(R.string.settings_search), style = Quorvi.type.body, color = Quorvi.colors.textSecondary)
                inner()
            },
        )
    }
}

@Composable
private fun GlyphTile(glyph: ImageVector, size: Int = 38) {
    Box(Modifier.size(size.dp).clip(Quorvi.shapes.control).background(Quorvi.colors.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Image(rememberVectorPainter(glyph), null, Modifier.size((size * 0.52f).dp), colorFilter = ColorFilter.tint(Quorvi.colors.accent))
    }
}

@Composable
private fun ModuleRow(module: SettingsModule, summary: String?, onClick: () -> Unit) {
    NavRow(module.glyph, stringResource(module.title), summary ?: stringResource(module.description), onClick)
}

@Composable
fun NavRow(glyph: ImageVector?, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 62.dp).clip(Quorvi.shapes.control).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) { GlyphTile(glyph); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = Quorvi.type.action, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Image(rememberVectorPainter(Glyphs.Chevron), null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(Quorvi.colors.textTertiary))
    }
}

@Composable
private fun ModuleHeader(module: SettingsModule, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(Glyphs.Back, onBack)
    }
    Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        GlyphTile(module.glyph, 52)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(stringResource(module.title), style = Quorvi.type.title, color = Quorvi.colors.text)
            Text(stringResource(module.description), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
        }
    }
}

@Composable
private fun ModuleList(module: SettingsModule, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { ModuleHeader(module, onBack) }
        content()
    }
}

@Composable
private fun SubScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(Glyphs.Back, onBack)
            Text(title, style = Quorvi.type.title, color = Quorvi.colors.text, modifier = Modifier.padding(start = 8.dp))
        }
        content()
    }
}

@Composable
private fun AiModule(onOpen: (String) -> Unit, onBack: () -> Unit) {
    val accounts = hiltViewModel<AccountsViewModel>()
    val chat = hiltViewModel<ChatViewModel>()
    val orchestra by chat.orchestra.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ModuleList(SettingsModule.AI, onBack) {
        item { Section(stringResource(R.string.mod_ai_providers)) }
        item { ProvidersPanel(accounts) }
        item { Section(stringResource(R.string.mod_ai_how)) }
        item {
            Panel {
                NavRow(Glyphs.Model, stringResource(R.string.models_title), orchestra.coordinator?.let { b -> b.primary.model + if (b.fallbacks.isNotEmpty()) " +${b.fallbacks.size}" else "" } ?: stringResource(R.string.models_nav_auto)) { onOpen("models") }
                NavRow(Glyphs.Orchestra, stringResource(R.string.orc_title), stringResource(if (orchestra.enabled) R.string.mod_ai_orchestra_on else R.string.mod_ai_orchestra_off)) { onOpen("orchestra") }
                NavRow(Glyphs.Memory, stringResource(R.string.mem_title), stringResource(if (orchestra.memory) R.string.mod_ai_memory_on else R.string.mod_ai_memory_off)) { onOpen("memory") }
                val voiceOn by chat.voiceEnabled.collectAsStateWithLifecycle()
                NavRow(Glyphs.Mic, stringResource(R.string.voice_title), stringResource(if (voiceOn) R.string.voice_nav_desc else R.string.voice_nav_off)) { onOpen("voice") }
                NavRow(Glyphs.Compose, stringResource(R.string.mod_ai_open_chat), null) { com.paulneja.quorvi.ui.chat.ChatActivity.open(context) }
            }
        }
    }
}

@Composable
private fun AgentsModule(capsules: CapsuleViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val policy = hiltViewModel<PolicyViewModel>()
    val config by policy.config.collectAsStateWithLifecycle()
    val list by capsules.capsules.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ModuleList(SettingsModule.AGENTS, onBack) {
        item { Spacer(Modifier.height(12.dp)) }
        item {
            Panel {
                NavRow(Glyphs.Shield, stringResource(R.string.policy_rules), stringResource(R.string.mod_agents_mode, config.mode.name.lowercase().replaceFirstChar(Char::uppercase))) { onOpen("policy") }
                NavRow(Glyphs.Bolt, stringResource(R.string.mod_capsules), androidx.compose.ui.res.pluralStringResource(R.plurals.mod_capsules_count, list.size, list.size)) { onOpen("capsules") }
                val draft = stringResource(R.string.capsule_ai_draft)
                NavRow(Glyphs.Sparkle, stringResource(R.string.capsule_ai), stringResource(R.string.capsule_ai_desc)) { com.paulneja.quorvi.ui.chat.ChatActivity.open(context, draft = draft) }
                NavRow(Glyphs.Agent, stringResource(R.string.mod_agents_runs), stringResource(R.string.mod_agents_runs_desc)) { context.startActivity(OperationsActivity.agents(context)) }
            }
        }
        item {
            Panel {
                com.paulneja.quorvi.ui.design.SwitchRow(stringResource(R.string.agents_free_web), config.freeWeb) { on -> policy.set("freeWeb", on.toString()) }
                Hint(stringResource(R.string.agents_free_web_desc))
            }
        }
    }
}

@Composable
private fun GitHubModule(onOpen: (String) -> Unit, onBack: () -> Unit) {
    val accounts = hiltViewModel<AccountsViewModel>()
    val services = hiltViewModel<ServicesViewModel>()
    val login by accounts.login.collectAsStateWithLifecycle()
    ModuleList(SettingsModule.GITHUB, onBack) {
        item { Section(stringResource(R.string.mod_github_account)) }
        item { GitHubAccountPanel(accounts) }
        if (login != null) {
            item { Section(stringResource(R.string.accounts_watched)) }
            item { WatchedReposPanel(accounts) }
            item { Section("Copilot") }
            item { CopilotPanel(accounts) }
        }
        item { Section(stringResource(R.string.mod_ultra)) }
        item { Panel { NavRow(Glyphs.PullRequest, stringResource(R.string.mod_ultra_rules), stringResource(R.string.mod_ultra_rules_desc)) { onOpen("ultra") } } }
        item { Section("GitLab") }
        item { ServicesPanel(services, setOf(ServiceKind.GITLAB)) }
    }
}

@Composable
private fun ServersModule(connections: ConnectionsViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val services = hiltViewModel<ServicesViewModel>()
    val hosts by connections.hostList.collectAsStateWithLifecycle()
    val http by connections.httpList.collectAsStateWithLifecycle()
    ModuleList(SettingsModule.SERVERS, onBack) {
        item { Spacer(Modifier.height(12.dp)) }
        item {
            Panel {
                NavRow(Glyphs.Server, stringResource(R.string.mod_hosts), androidx.compose.ui.res.pluralStringResource(R.plurals.mod_hosts_count, hosts.size, hosts.size)) { onOpen("hosts") }
                NavRow(Glyphs.Globe, stringResource(R.string.mod_http), androidx.compose.ui.res.pluralStringResource(R.plurals.mod_http_count, http.size, http.size)) { onOpen("http") }
            }
        }
        item { Section(stringResource(R.string.mod_services)) }
        item { ServicesPanel(services, setOf(ServiceKind.HOME_ASSISTANT, ServiceKind.WORKER, ServiceKind.DISCORD)) }
    }
}

@Composable
private fun TerminalModule(onBack: () -> Unit) {
    val vm = hiltViewModel<SettingsViewModel>()
    val state by vm.runtime.collectAsStateWithLifecycle()
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshRuntime() }
    ModuleList(SettingsModule.TERMINAL, onBack) {
        item { Spacer(Modifier.height(12.dp)) }
        item {
            Panel {
                ActionRow(stringResource(R.string.mod_terminal_open), runtimeLabel(state)) { QuorviShell.open(context, null, null) }
                ActionRow(stringResource(R.string.mod_terminal_storage), stringResource(R.string.mod_terminal_storage_desc)) { QuorviShell.open(context, null, null, mountStorage = true) }
                if (state == RuntimeAvailability.FAILED || state == RuntimeAvailability.UNBOUND) ActionRow(stringResource(R.string.mod_terminal_prepare), stringResource(R.string.mod_terminal_prepare_desc)) { vm.prepareRuntime() }
                Hint(stringResource(R.string.mod_terminal_note))
            }
        }
    }
}

@Composable
private fun runtimeLabel(state: RuntimeAvailability?): String = stringResource(when (state) {
    RuntimeAvailability.READY -> R.string.ops_runtime_ready
    RuntimeAvailability.INSTALLING -> R.string.ops_runtime_installing
    RuntimeAvailability.FAILED -> R.string.shell_failed
    else -> R.string.ops_runtime_setup
})

@Composable
private fun rememberSummaries(studio: StudioViewModel): Map<SettingsModule, String?> {
    val accounts = hiltViewModel<AccountsViewModel>()
    val vm = hiltViewModel<SettingsViewModel>()
    val capabilities = hiltViewModel<CapabilitiesViewModel>()
    val login by accounts.login.collectAsStateWithLifecycle()
    val watched by accounts.watched.collectAsStateWithLifecycle()
    val providers by accounts.modelProviders.collectAsStateWithLifecycle()
    val hidden by studio.hiddenApps.collectAsStateWithLifecycle()
    val unsuggested by studio.unsuggestedApps.collectAsStateWithLifecycle()
    val look by studio.look.collectAsStateWithLifecycle()
    val runtime by vm.runtime.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val priv by capabilities.config.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshRuntime() }
    val context = androidx.compose.ui.platform.LocalResources.current
    return mapOf(
        SettingsModule.HOME to listOf(
            context.getString(if (look.glass.material) R.string.style_material else R.string.style_glass),
            context.getString(when (look.motion) {
                com.paulneja.quorvi.core.settings.MotionStyle.OFF -> R.string.motion_off
                com.paulneja.quorvi.core.settings.MotionStyle.SUBTLE -> R.string.motion_subtle
                com.paulneja.quorvi.core.settings.MotionStyle.FLUID -> R.string.motion_fluid
                com.paulneja.quorvi.core.settings.MotionStyle.DRAMATIC -> R.string.motion_dramatic
            }),
        ).joinToString(" · "),
        SettingsModule.APPS to context.getString(R.string.mod_apps_summary, hidden.size, unsuggested.size),
        SettingsModule.AI to if (providers.isEmpty()) context.getString(R.string.mod_ai_none) else context.getQuantityString(R.plurals.mod_ai_count, providers.size, providers.size),
        SettingsModule.AGENTS to null,
        SettingsModule.GITHUB to (login?.let { "@$it · " + context.getQuantityString(R.plurals.mod_repos_count, watched.size, watched.size) } ?: context.getString(R.string.mod_github_signed_out)),
        SettingsModule.SERVERS to context.getString(R.string.mod_servers_summary, counts.hosts, counts.services),
        SettingsModule.SYSTEM to if (priv.enabled.isEmpty()) context.getString(R.string.mod_system_none) else priv.enabled.joinToString(" · ") { when (it) { PrivBackend.ROOT -> "Root"; PrivBackend.SHIZUKU -> "Shizuku"; PrivBackend.ADB_TCP -> "ADB" } },
        SettingsModule.TERMINAL to runtime?.let { context.getString(when (it) { RuntimeAvailability.READY -> R.string.ops_runtime_ready; RuntimeAvailability.INSTALLING -> R.string.ops_runtime_installing; RuntimeAvailability.FAILED -> R.string.shell_failed; RuntimeAvailability.UNBOUND -> R.string.ops_runtime_setup }) },
        SettingsModule.BACKUP to null,
    )
}
