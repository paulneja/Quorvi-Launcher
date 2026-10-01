package com.paulneja.quorvi.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.paulneja.quorvi.R
import com.paulneja.quorvi.connections.services.HaEntity
import com.paulneja.quorvi.connections.services.ServiceEntity
import com.paulneja.quorvi.connections.services.ServiceKind
import com.paulneja.quorvi.connections.services.ServicesRepo
import com.paulneja.quorvi.ops.CoreClient
import com.paulneja.quorvi.ui.design.ActionRow
import com.paulneja.quorvi.ui.design.Hint
import com.paulneja.quorvi.ui.design.InputField
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.Panel
import com.paulneja.quorvi.ui.design.Section
import com.paulneja.quorvi.ui.operations.Chip
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ServicesViewModel @Inject constructor(private val repo: ServicesRepo, private val core: CoreClient) : ViewModel() {
    val services = repo.all.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private fun run(onDone: () -> Unit = {}, block: suspend () -> Unit) = viewModelScope.launch {
        _busy.value = true
        _error.value = null
        runCatching { block() }.onSuccess { onDone(); core.refresh(true) }.onFailure { _error.value = it.message ?: it::class.simpleName }
        _busy.value = false
    }

    fun addHa(name: String, url: String, token: String, onDone: () -> Unit) = run(onDone) { repo.addHomeAssistant(name, url, token) }
    fun addGitLab(name: String, url: String, token: String, projects: String, onDone: () -> Unit) = run(onDone) { repo.addGitLab(name, url, token, projects.split(',', '\n', ' ')) }
    fun pairWorker(name: String, url: String, code: String, onDone: () -> Unit) = run(onDone) { repo.pairWorker(name, url, code, android.os.Build.MODEL) }
    fun addDiscord(name: String, webhook: String, onDone: () -> Unit) = run(onDone) { repo.addDiscord(name, webhook) }
    fun remove(service: ServiceEntity) = run { repo.remove(service) }
    fun entities(service: ServiceEntity) = repo.entities(service.id)
    fun favorite(entity: HaEntity) = viewModelScope.launch { repo.setFavorite(entity, !entity.favorite) }
}

@Composable
fun ServicesPanel(vm: ServicesViewModel, kinds: Set<ServiceKind> = ServiceKind.entries.toSet()) {
    val all by vm.services.collectAsStateWithLifecycle()
    val services = all.filter { s -> kinds.any { it.name == s.kind } }
    val error by vm.error.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf<ServiceKind?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    Panel {
        if (services.isEmpty()) Hint(stringResource(R.string.services_empty_kind))
        services.forEach { s ->
            Row(Modifier.fillMaxWidth().clickable { open = if (open == s.id) null else s.id }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${s.name} · ${kindLabel(s.kind)}", style = Quorvi.type.body, color = Quorvi.colors.text)
                    Text(listOfNotNull(s.baseUrl, s.status).joinToString(" · "), style = Quorvi.type.caption, color = if (s.status?.startsWith("error") == true || s.status?.startsWith("offline") == true) Quorvi.colors.danger else Quorvi.colors.textSecondary)
                }
            }
            if (open == s.id) {
                if (s.kind == ServiceKind.HOME_ASSISTANT.name) HaFavorites(vm, s)
                ActionRow(stringResource(R.string.menu_remove), null, danger = true) { vm.remove(s) }
            }
        }
        error?.let { Text(it, style = Quorvi.type.caption, color = Quorvi.colors.danger, modifier = Modifier.padding(vertical = 6.dp)) }
        when (adding) {
            null -> {
                if (ServiceKind.HOME_ASSISTANT in kinds) ActionRow(stringResource(R.string.services_add_ha), stringResource(R.string.services_add_ha_hint)) { adding = ServiceKind.HOME_ASSISTANT }
                if (ServiceKind.GITLAB in kinds) ActionRow(stringResource(R.string.services_add_gitlab), stringResource(R.string.services_add_gitlab_hint)) { adding = ServiceKind.GITLAB }
                if (ServiceKind.WORKER in kinds) ActionRow(stringResource(R.string.services_add_worker), stringResource(R.string.services_add_worker_hint)) { adding = ServiceKind.WORKER }
                if (ServiceKind.DISCORD in kinds) ActionRow(stringResource(R.string.services_add_discord), stringResource(R.string.services_add_discord_hint)) { adding = ServiceKind.DISCORD }
            }
            else -> AddService(adding!!, vm, busy, onDone = { adding = null })
        }
    }
}

@Composable
private fun AddService(kind: ServiceKind, vm: ServicesViewModel, busy: Boolean, onDone: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf(when (kind) { ServiceKind.GITLAB -> "https://gitlab.com"; ServiceKind.DISCORD -> "https://discord.com/api/webhooks/"; else -> "http://" }) }
    var secret by remember { mutableStateOf("") }
    var projects by rememberSaveable { mutableStateOf("") }
    InputField(name, { name = it }, stringResource(R.string.hosts_name))
    InputField(url, { url = it.trim() }, "URL")
    InputField(secret, { secret = it.trim() }, stringResource(when (kind) {
        ServiceKind.HOME_ASSISTANT -> R.string.services_ha_token
        ServiceKind.GITLAB -> R.string.services_gitlab_token
        ServiceKind.WORKER -> R.string.services_worker_code
        ServiceKind.DISCORD -> R.string.services_discord_confirm
    }), secret = kind != ServiceKind.WORKER && kind != ServiceKind.DISCORD)
    if (kind == ServiceKind.GITLAB) InputField(projects, { projects = it }, stringResource(R.string.services_gitlab_projects), singleLine = false)
    Row(Modifier.padding(vertical = 8.dp)) {
        Chip(stringResource(R.string.pin_cancel)) { onDone() }
        Text(" ")
        if (!busy && url.length > 8 && secret.isNotBlank()) Chip(stringResource(R.string.services_connect), accent = true) {
            when (kind) {
                ServiceKind.HOME_ASSISTANT -> vm.addHa(name, url, secret, onDone)
                ServiceKind.GITLAB -> vm.addGitLab(name, url, secret, projects, onDone)
                ServiceKind.WORKER -> vm.pairWorker(name, url, secret, onDone)
                ServiceKind.DISCORD -> vm.addDiscord(name, url, onDone)
            }
        }
    }
}

@Composable
private fun HaFavorites(vm: ServicesViewModel, service: ServiceEntity) {
    val entities by remember(service.id) { vm.entities(service) }.collectAsState(emptyList())
    Hint(stringResource(R.string.services_ha_favorites))
    entities.take(80).forEach { e ->
        Row(Modifier.fillMaxWidth().clickable { vm.favorite(e) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (e.favorite) "★" else "☆", style = Quorvi.type.body, color = if (e.favorite) Quorvi.colors.accent else Quorvi.colors.textSecondary, modifier = Modifier.padding(end = 10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name, style = Quorvi.type.body, color = Quorvi.colors.text)
                Text("${e.entityId} · ${e.state}${e.unit?.let { " $it" } ?: ""}", style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun kindLabel(kind: String) = when (kind) {
    ServiceKind.HOME_ASSISTANT.name -> "Home Assistant"
    ServiceKind.GITLAB.name -> "GitLab"
    ServiceKind.DISCORD.name -> "Discord"
    else -> stringResource(R.string.services_worker)
}
