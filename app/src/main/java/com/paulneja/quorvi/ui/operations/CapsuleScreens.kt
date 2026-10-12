package com.paulneja.quorvi.ui.operations

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.paulneja.quorvi.R
import com.paulneja.quorvi.agent.capsules.Capsule
import com.paulneja.quorvi.agent.capsules.CapsuleCompiler
import com.paulneja.quorvi.agent.capsules.CapsuleDao
import com.paulneja.quorvi.agent.capsules.CapsuleEntity
import com.paulneja.quorvi.agent.capsules.CompileResult
import com.paulneja.quorvi.agent.engine.ProviderModels
import com.paulneja.quorvi.agent.engine.RuntimeToolBox
import com.paulneja.quorvi.ops.CoreClient
import com.paulneja.quorvi.ui.design.ActionRow
import com.paulneja.quorvi.ui.design.Hint
import com.paulneja.quorvi.ui.design.InputField
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.Panel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val SAMPLE = """{
  "schema": 1,
  "name": "Prepare a report",
  "description": "Research a topic and leave a Markdown file in Downloads",
  "icon": "doc",
  "steps": [
    { "tool": "fs.write", "args": { "path": "report.md", "content": "# Title\n\n…" } },
    { "tool": "artifact.export", "args": { "path": "report.md", "name": "Report.md", "mime": "text/markdown" } }
  ]
}"""

@HiltViewModel
class CapsuleViewModel @Inject constructor(
    private val dao: CapsuleDao,
    private val toolbox: RuntimeToolBox,
    private val core: CoreClient,
    ai: com.paulneja.quorvi.agent.engine.AiSwitch,
) : ViewModel() {
    val capsules = dao.observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val aiEnabled = ai.enabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    private val json = Json { prettyPrint = true }

    suspend fun compiler() = CapsuleCompiler(toolbox.allTools().associateBy { it.name })

    suspend fun get(id: String): String = dao.get(id)?.json ?: SAMPLE

    suspend fun check(text: String): CompileResult = compiler().parse(text)

    fun save(id: String?, text: String, onDone: (String?) -> Unit) = viewModelScope.launch {
        when (val r = check(text)) {
            is CompileResult.Failed -> onDone(r.errors.joinToString("\n"))
            is CompileResult.Ok -> {
                dao.put(CapsuleEntity(id ?: UUID.randomUUID().toString(), r.capsule.name, r.capsule.icon, compiler().encode(r.capsule), createdAt = System.currentTimeMillis()))
                onDone(null)
            }
        }
    }

    fun run(id: String): String? = core.runCapsule(id)

    suspend fun capsule(id: String): com.paulneja.quorvi.agent.capsules.Capsule? = dao.get(id)?.let { e -> runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(com.paulneja.quorvi.agent.capsules.Capsule.serializer(), e.json) }.getOrNull() }

    fun saveCapsule(id: String?, capsule: com.paulneja.quorvi.agent.capsules.Capsule, onDone: (String?) -> Unit) = viewModelScope.launch {
        val compiler = compiler()
        when (val r = compiler.compile(capsule)) {
            is CompileResult.Failed -> onDone(r.errors.joinToString("\n"))
            is CompileResult.Ok -> {
                val existing = id?.let { dao.get(it) }
                dao.put(CapsuleEntity(id ?: UUID.randomUUID().toString(), r.capsule.name, r.capsule.icon, compiler.encode(r.capsule), createdAt = existing?.createdAt ?: System.currentTimeMillis(), lastRunAt = existing?.lastRunAt))
                onDone(null)
            }
        }
    }

    fun isAi(e: CapsuleEntity): Boolean = e.ai

    fun trigger(e: CapsuleEntity): com.paulneja.quorvi.agent.capsules.CapsuleTrigger =
        runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(com.paulneja.quorvi.agent.capsules.Capsule.serializer(), e.json).trigger }.getOrDefault(com.paulneja.quorvi.agent.capsules.CapsuleTrigger())
    fun delete(id: String, onDone: () -> Unit) = viewModelScope.launch { dao.delete(id); onDone() }
}

@Composable
fun CapsulesTab(vm: CapsuleViewModel, onEdit: (String) -> Unit, onRun: (String) -> Unit, onForm: ((String) -> Unit)? = null, onChat: ((String) -> Unit)? = null) {
    val all by vm.capsules.collectAsStateWithLifecycle()
    val aiOn by vm.aiEnabled.collectAsStateWithLifecycle()
    val capsules = if (aiOn) all else all.filterNot { vm.isAi(it) }
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        item {
            Panel {
                Hint(stringResource(if (aiOn) R.string.capsule_hint_ai else R.string.capsule_hint))
                ActionRow(stringResource(R.string.capsule_new), stringResource(if (aiOn) R.string.capsule_new_desc else R.string.capsule_new_fixed_desc)) { if (aiOn) onForm?.invoke("new") ?: onEdit("new") else onEdit("new") }
            }
        }
        items(capsules, key = { it.id }) { c ->
            val ai = vm.isAi(c)
            Panel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { if (ai && onForm != null) onForm(c.id) else onEdit(c.id) }.padding(vertical = 8.dp)) {
                        Text((if (ai) "✦ " else "⚡ ") + c.name, style = Quorvi.type.action, color = Quorvi.colors.text)
                        Text(
                            triggerSummary(vm.trigger(c)) + " · " + (c.lastRunAt?.let { stringResource(R.string.capsule_last_run, android.text.format.DateUtils.getRelativeTimeSpanString(it).toString()) } ?: stringResource(R.string.capsule_never_run)),
                            style = Quorvi.type.caption, color = Quorvi.colors.textSecondary,
                        )
                    }
                    if (ai && onChat != null) Chip(stringResource(R.string.capsule_open_chat)) { onChat(c.id) }
                    Chip(stringResource(R.string.capsule_run), accent = true) { vm.run(c.id)?.let(onRun) }
                }
            }
        }
    }
}

@Composable
fun CapsuleEditor(vm: CapsuleViewModel, id: String, onBack: () -> Unit, onRun: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val validLabel = stringResource(R.string.capsule_valid)
    val savedLabel = stringResource(R.string.capsule_saved)
    androidx.compose.runtime.LaunchedEffect(id) { if (!loaded) { text = vm.get(id); loaded = true } }
    val share = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let { scope.launch { runCatching { context.contentResolver.openInputStream(it)!!.bufferedReader().readText() }.onSuccess { t -> text = t } } }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenBar(stringResource(R.string.capsule_title), onBack) {
            Chip(stringResource(R.string.capsule_import)) { share.launch("application/json") }
        }
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            item {
                Panel {
                    InputField(text, { text = it; feedback = null }, "JSON", singleLine = false)
                    feedback?.let { Text(it, style = Quorvi.type.caption.copy(fontFamily = FontFamily.Monospace), color = if (ok) Quorvi.colors.accent else Quorvi.colors.danger, modifier = Modifier.padding(vertical = 8.dp)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(stringResource(R.string.capsule_check)) { scope.launch { feedback = when (val r = vm.check(text)) { is CompileResult.Ok -> { ok = true; validLabel + (if (r.warnings.isEmpty()) "" else "\n" + r.warnings.joinToString("\n")) }; is CompileResult.Failed -> { ok = false; r.errors.joinToString("\n") } } } }
                        Chip(stringResource(R.string.capsule_export)) {
                            runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_TEXT, text), null)) }
                        }
                        Chip(stringResource(R.string.hosts_save), accent = true) { vm.save(id.takeIf { it != "new" }, text) { err -> feedback = err ?: savedLabel; ok = err == null; if (err == null) onBack() } }
                    }
                }
            }
            if (id != "new") item {
                Panel {
                    ActionRow(stringResource(R.string.capsule_run), null) { vm.run(id)?.let(onRun) }
                    ActionRow(stringResource(R.string.capsule_delete), null, danger = true) { vm.delete(id, onBack) }
                }
            }
        }
    }
}
