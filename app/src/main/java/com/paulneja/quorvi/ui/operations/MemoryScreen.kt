package com.paulneja.quorvi.ui.operations

import android.content.Intent
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.paulneja.quorvi.R
import com.paulneja.quorvi.agent.memory.MemoryEntity
import com.paulneja.quorvi.agent.memory.MemoryKind
import com.paulneja.quorvi.agent.memory.MemoryStore
import com.paulneja.quorvi.agent.memory.Verification
import com.paulneja.quorvi.ui.design.Hint
import com.paulneja.quorvi.ui.design.InputField
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.Panel
import com.paulneja.quorvi.ui.design.Section
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@HiltViewModel
class MemoryViewModel @Inject constructor(private val store: MemoryStore) : ViewModel() {
    val memories = store.all.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun add(text: String, kind: MemoryKind) = viewModelScope.launch {
        runCatching { store.remember(text, kind, "user", Verification.STATED, reviewed = true) }.onFailure { _messages.emit(it.message ?: "error") }
    }

    fun approve(m: MemoryEntity) = viewModelScope.launch { store.update(m.copy(reviewed = true)) }
    fun pin(m: MemoryEntity) = viewModelScope.launch { store.update(m.copy(pinned = !m.pinned)) }
    fun forget(m: MemoryEntity) = viewModelScope.launch { store.forget(m.id) }

    suspend fun exportJson(): String = Json { prettyPrint = true }.encodeToString(
        kotlinx.serialization.json.JsonArray.serializer(),
        buildJsonArray {
            store.export().forEach { m ->
                add(buildJsonObject {
                    put("text", m.text); put("kind", m.kind); put("source", m.source); put("verification", m.verification)
                    put("confidence", m.confidence); put("reviewed", m.reviewed); put("pinned", m.pinned); put("createdAt", m.createdAt)
                })
            }
        },
    )
}

@Composable
fun MemoryScreen(vm: MemoryViewModel, onBack: () -> Unit) {
    val memories by vm.memories.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(MemoryKind.PREFERENCE) }
    var open by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        ScreenBar(stringResource(R.string.memory_title), onBack) {
            Chip(stringResource(R.string.memory_export)) {
                scope.launch {
                    val json = vm.exportJson()
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_TEXT, json), null))
                }
            }
        }
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            item {
                Panel {
                    Hint(stringResource(R.string.memory_hint))
                    InputField(text, { text = it }, stringResource(R.string.memory_add_hint), singleLine = false)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MemoryKind.entries.forEach { k -> Chip(k.name.lowercase(), accent = kind == k) { kind = k } }
                    }
                    if (text.isNotBlank()) Chip(stringResource(R.string.memory_save), accent = true) { vm.add(text, kind); text = "" }
                }
            }
            val pending = memories.filter { !it.reviewed }
            if (pending.isNotEmpty()) item { Section(stringResource(R.string.memory_pending)) }
            items(pending, key = { "p" + it.id }) { m -> MemoryRow(m, open == m.id, { open = if (open == m.id) null else m.id }, vm) }
            item { Section(stringResource(R.string.memory_saved, memories.count { it.reviewed })) }
            items(memories.filter { it.reviewed }, key = { it.id }) { m -> MemoryRow(m, open == m.id, { open = if (open == m.id) null else m.id }, vm) }
        }
    }
}

@Composable
private fun MemoryRow(m: MemoryEntity, expanded: Boolean, onToggle: () -> Unit, vm: MemoryViewModel) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(m.text, style = Quorvi.type.body, color = Quorvi.colors.text)
        Text((if (m.pinned) "pinned · " else "") + "${m.kind.lowercase()} · ${m.verification.lowercase()} · ${m.source}", style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
        if (expanded) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!m.reviewed) Chip(stringResource(R.string.memory_approve), accent = true) { vm.approve(m) }
            Chip(stringResource(if (m.pinned) R.string.memory_unpin else R.string.memory_pin)) { vm.pin(m) }
            Chip(stringResource(R.string.memory_forget)) { vm.forget(m) }
        }
    }
}
