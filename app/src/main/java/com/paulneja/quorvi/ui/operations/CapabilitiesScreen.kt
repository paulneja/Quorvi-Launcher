package com.paulneja.quorvi.ui.operations

import com.paulneja.quorvi.control.message
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.paulneja.quorvi.R
import com.paulneja.quorvi.control.PowerHook
import com.paulneja.quorvi.control.PowerHooks
import com.paulneja.quorvi.control.PrivBackend
import com.paulneja.quorvi.control.PrivilegedShells
import com.paulneja.quorvi.control.RootLevel
import com.paulneja.quorvi.core.settings.QuorviSettings
import com.paulneja.quorvi.core.settings.PrivilegedConfig
import com.paulneja.quorvi.ui.design.ActionRow
import com.paulneja.quorvi.ui.design.ChoiceRow
import com.paulneja.quorvi.ui.design.Hint
import com.paulneja.quorvi.ui.design.InputField
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.Panel
import com.paulneja.quorvi.ui.design.Section
import com.paulneja.quorvi.ui.design.SwitchRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CapabilitiesViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val settings: QuorviSettings,
    private val shells: PrivilegedShells,
    private val status: com.paulneja.quorvi.launcher.live.PrivStatusSource,
    private val guard: com.paulneja.quorvi.control.BackgroundGuard,
) : ViewModel() {
    val protection = guard.protection.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun unrestricted() = guard.unrestricted
    fun exemptionIntent() = guard.exemptionIntent()
    fun setProtection(on: Boolean) = viewModelScope.launch {
        val message = runCatching { guard.setProtection(on) }.fold(
            { context.getString(if (on) R.string.bg_protection_on else R.string.bg_protection_off, it) },
            { it.message ?: "error" },
        )
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
    }
    val config = settings.privileged.stateIn(viewModelScope, SharingStarted.Eagerly, PrivilegedConfig())
    val degraded = settings.rootDegraded.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _available = kotlinx.coroutines.flow.MutableStateFlow<Set<PrivBackend>>(emptySet())
    val available = _available

    fun refresh() = viewModelScope.launch { _available.value = PrivBackend.entries.filter { shells.available(it) }.toSet() }
    fun setBackend(b: PrivBackend, on: Boolean) = viewModelScope.launch {
        if (on) {
            val result = shells.enable(b)
            android.widget.Toast.makeText(context, result.message(context, b), android.widget.Toast.LENGTH_LONG).show()
        } else {
            shells.disable(b)
        }
        status.refresh()
        refresh()
    }
    fun setLevel(l: RootLevel) = viewModelScope.launch { settings.setRootLevel(l) }
    fun setHooks(pre: String, post: String) = viewModelScope.launch { settings.setRootHooks(pre, post) }
    fun setEndpoint(e: String) = viewModelScope.launch { settings.setAdbEndpoint(e) }
    fun dismissDegraded() = viewModelScope.launch { shells.clearDegraded() }
    fun runHook(hook: PowerHook, args: Map<String, String>, onResult: (String) -> Unit) = viewModelScope.launch {
        val r = runCatching { shells.exec(PrivBackend.ROOT, hook.render(args), hook.level) }
        onResult(r.fold({ "exit ${it.exitCode}: ${(it.stdout + it.stderr).trim().take(300).ifBlank { "ok" }}" }, { it.message ?: "error" }))
    }
}

@Composable
fun CapabilitiesScreen(vm: CapabilitiesViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val config by vm.config.collectAsStateWithLifecycle()
    val degraded by vm.degraded.collectAsStateWithLifecycle()
    val available by vm.available.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    fun open(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    Column(Modifier.fillMaxSize()) {
        ScreenBar(stringResource(R.string.cap_title), onBack)
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            degraded?.let {
                item {
                    Panel {
                        Text(stringResource(R.string.cap_degraded, it), style = Quorvi.type.body, color = Quorvi.colors.danger)
                        Hint(stringResource(R.string.cap_degraded_hint))
                        ActionRow(stringResource(R.string.cap_degraded_ok), null) { vm.dismissDegraded() }
                    }
                }
            }
            item { Section(stringResource(R.string.bg_title)) }
            item {
                val protection by vm.protection.collectAsStateWithLifecycle()
                var unrestricted by remember { mutableStateOf(vm.unrestricted()) }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { unrestricted = vm.unrestricted() }
                Panel {
                    ActionRow(
                        stringResource(R.string.bg_battery),
                        stringResource(if (unrestricted) R.string.bg_battery_ok else R.string.bg_battery_restricted),
                    ) { if (!unrestricted) open(vm.exemptionIntent()) }
                    Hint(stringResource(R.string.bg_battery_hint))
                    com.paulneja.quorvi.ui.design.SwitchRow(stringResource(R.string.bg_protection), protection) { vm.setProtection(it) }
                    Hint(stringResource(R.string.bg_protection_hint))
                }
            }
            item { Section(stringResource(R.string.cap_privileged)) }
            item {
                Panel {
                    BackendRow("Root", PrivBackend.ROOT, config, available) { vm.setBackend(PrivBackend.ROOT, it) }
                    BackendRow("Shizuku", PrivBackend.SHIZUKU, config, available) { vm.setBackend(PrivBackend.SHIZUKU, it) }
                    BackendRow(stringResource(R.string.cap_adb), PrivBackend.ADB_TCP, config, available) { vm.setBackend(PrivBackend.ADB_TCP, it) }
                    if (PrivBackend.ADB_TCP in config.enabled) {
                        var ep by rememberSaveable { mutableStateOf(config.adbEndpoint) }
                        InputField(ep, { ep = it; vm.setEndpoint(it) }, stringResource(R.string.cap_adb_endpoint))
                        Hint(stringResource(R.string.cap_adb_hint))
                    }
                }
            }
            if (PrivBackend.ROOT in config.enabled) {
                item { Section(stringResource(R.string.cap_root_level)) }
                item {
                    Panel {
                        RootLevel.entries.filter { it != RootLevel.NONE }.forEach { level ->
                            ChoiceRow(levelLabel(level), config.rootLevel == level) { vm.setLevel(level) }
                        }
                        Hint(stringResource(R.string.cap_level_hint))
                    }
                }
                item { Section(stringResource(R.string.cap_hooks)) }
                item {
                    Panel {
                        var pre by rememberSaveable { mutableStateOf(config.preHook) }
                        var post by rememberSaveable { mutableStateOf(config.postHook) }
                        InputField(pre, { pre = it; vm.setHooks(it, post) }, stringResource(R.string.cap_pre_hook), singleLine = false)
                        InputField(post, { post = it; vm.setHooks(pre, it) }, stringResource(R.string.cap_post_hook), singleLine = false)
                        Hint(stringResource(R.string.cap_hooks_hint))
                    }
                }
                item { Section(stringResource(R.string.cap_power_hooks)) }
                items(PowerHooks.catalog.filter { config.rootLevel.allows(it.level) }, key = { it.id }) { hook -> PowerHookRow(vm, hook) }
                val locked = PowerHooks.catalog.filter { !config.rootLevel.allows(it.level) }
                if (locked.isNotEmpty()) item { Hint(stringResource(R.string.cap_locked, locked.joinToString { it.title }, locked.maxOf { it.level }.name.lowercase())) }
            }
        }
    }
}

@Composable
private fun BackendRow(label: String, backend: PrivBackend, config: PrivilegedConfig, available: Set<PrivBackend>, onToggle: (Boolean) -> Unit) {
    val on = backend in config.enabled
    SwitchRow(label + (if (on && backend !in available) "  ·  " + stringResource(R.string.cap_not_available) else ""), on, onToggle)
}

@Composable
private fun PowerHookRow(vm: CapabilitiesViewModel, hook: PowerHook) {
    var expanded by remember { mutableStateOf(false) }
    var args by remember { mutableStateOf(hook.params.associate { it.name to it.default }) }
    var result by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    Panel {
        Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 6.dp)) {
            Text("${hook.title}  ·  ${hook.level.name.lowercase()}", style = Quorvi.type.action, color = Quorvi.colors.text)
            Text(hook.description, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
        }
        if (expanded) {
            hook.params.forEach { p ->
                InputField(args[p.name].orEmpty(), { v -> args = args + (p.name to v) }, "${p.name} — ${p.hint}")
            }
            result?.let { Text(it, style = Quorvi.type.caption.copy(fontFamily = FontFamily.Monospace), color = Quorvi.colors.text, modifier = Modifier.padding(vertical = 6.dp)) }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!confirm) Chip(stringResource(R.string.cap_run_hook), accent = true) { confirm = true }
                else {
                    Chip(stringResource(R.string.pin_cancel)) { confirm = false }
                    Chip(stringResource(R.string.ops_confirm), accent = true) { confirm = false; vm.runHook(hook, args) { result = it } }
                }
                hook.revert?.let { Chip(stringResource(R.string.cap_revert)) { vm.runHook(hook.copy(template = it, params = emptyList()), emptyMap()) { r -> result = r } } }
            }
        }
    }
}

@Composable
private fun levelLabel(l: RootLevel): String = stringResource(
    when (l) {
        RootLevel.READ -> R.string.cap_level_read
        RootLevel.CONTROL -> R.string.cap_level_control
        RootLevel.EXEC -> R.string.cap_level_exec
        RootLevel.MODIFY -> R.string.cap_level_modify
        RootLevel.EXPERIMENTAL -> R.string.cap_level_experimental
        RootLevel.NONE -> R.string.cap_off
    },
)
