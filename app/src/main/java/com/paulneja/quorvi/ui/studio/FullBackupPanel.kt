package com.paulneja.quorvi.ui.studio

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.paulneja.quorvi.R
import com.paulneja.quorvi.core.backup.BackupException
import com.paulneja.quorvi.core.backup.BackupManifest
import com.paulneja.quorvi.core.backup.BackupPart
import com.paulneja.quorvi.core.backup.FullBackup
import com.paulneja.quorvi.ui.design.ActionRow
import com.paulneja.quorvi.ui.design.Hint
import com.paulneja.quorvi.ui.design.InputField
import com.paulneja.quorvi.ui.design.Panel
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.SwitchRow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class FullBackupViewModel @Inject constructor(@ApplicationContext private val context: Context, private val backup: FullBackup) : ViewModel() {
    sealed interface State {
        data object Idle : State
        data object Working : State
        data object Exported : State
        data class Staged(val manifest: BackupManifest) : State
        data class Failed(val reason: BackupException.Reason?, val detail: String?) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    fun export(uri: Uri, password: String, parts: Set<BackupPart>) = viewModelScope.launch {
        _state.value = State.Working
        _state.value = runCatching {
            val sealed = backup.export(password.toCharArray(), parts)
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "w")!!.use { it.write(sealed) } }
            State.Exported
        }.getOrElse { State.Failed((it as? BackupException)?.reason, it.message) }
    }

    fun import(uri: Uri, password: String) = viewModelScope.launch {
        _state.value = State.Working
        _state.value = runCatching {
            val data = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
            State.Staged(backup.stage(data, password.toCharArray()))
        }.getOrElse { State.Failed((it as? BackupException)?.reason, it.message) }
    }

    fun applyNow() = FullBackup.restart(context)

    fun cancel() {
        backup.discardPending()
        _state.value = State.Idle
    }
}

@Composable
private fun partLabel(p: BackupPart): Pair<String, String> = when (p) {
    BackupPart.HOME -> stringResource(R.string.fb_part_home) to stringResource(R.string.fb_part_home_desc)
    BackupPart.AI -> stringResource(R.string.fb_part_ai) to stringResource(R.string.fb_part_ai_desc)
    BackupPart.CHATS -> stringResource(R.string.fb_part_chats) to stringResource(R.string.fb_part_chats_desc)
    BackupPart.AGENTS -> stringResource(R.string.fb_part_agents) to stringResource(R.string.fb_part_agents_desc)
    BackupPart.CONNECTIONS -> stringResource(R.string.fb_part_connections) to stringResource(R.string.fb_part_connections_desc)
}

@Composable
fun FullBackupPanel() {
    val vm = hiltViewModel<FullBackupViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    var parts by rememberSaveable { mutableStateOf(BackupPart.entries.map { it.name }.toSet()) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var importPassword by remember { mutableStateOf("") }
    var pickedForImport by remember { mutableStateOf<Uri?>(null) }
    val today = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date()) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { vm.export(it, password, parts.map { n -> BackupPart.valueOf(n) }.toSet()); password = ""; confirm = "" }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pickedForImport = uri }
    val ready = password.length >= 8 && password == confirm && parts.isNotEmpty()
    Panel {
        Hint(stringResource(R.string.fb_intro))
        BackupPart.entries.forEach { p ->
            val (title, desc) = partLabel(p)
            Column {
                SwitchRow(title, p.name in parts) { on -> parts = if (on) parts + p.name else parts - p.name }
                Text(desc, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
        InputField(password, { password = it }, stringResource(R.string.fb_password), secret = true)
        InputField(confirm, { confirm = it }, stringResource(R.string.fb_password_again), secret = true)
        Hint(
            stringResource(
                when {
                    password.isNotEmpty() && password.length < 8 -> R.string.fb_password_short
                    confirm.isNotEmpty() && password != confirm -> R.string.fb_password_mismatch
                    else -> R.string.fb_password_warning
                },
            ),
        )
        ActionRow(stringResource(R.string.fb_export), if (ready) null else stringResource(R.string.fb_export_needs)) {
            if (ready) save.launch("quorvi-respaldo-$today.qbk")
        }
        ActionRow(stringResource(R.string.fb_import), stringResource(R.string.fb_import_desc)) { open.launch(arrayOf("*/*")) }
        pickedForImport?.let { uri ->
            InputField(importPassword, { importPassword = it }, stringResource(R.string.fb_import_password), secret = true)
            ActionRow(stringResource(R.string.fb_import_open), null) { vm.import(uri, importPassword); importPassword = ""; pickedForImport = null }
        }
        when (val s = state) {
            FullBackupViewModel.State.Working -> Hint(stringResource(R.string.fb_working))
            FullBackupViewModel.State.Exported -> Hint(stringResource(R.string.fb_exported))
            is FullBackupViewModel.State.Staged -> {
                val names = s.manifest.parts.mapNotNull { n -> BackupPart.entries.firstOrNull { it.name == n } }.map { partLabel(it).first }
                val date = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(s.manifest.exportedAt))
                Text(stringResource(R.string.fb_staged, date, s.manifest.app.replaceFirstChar { it.uppercase() }, names.joinToString()), style = Quorvi.type.body, color = Quorvi.colors.text, modifier = Modifier.padding(vertical = 8.dp))
                ActionRow(stringResource(R.string.fb_apply), stringResource(R.string.fb_apply_desc), danger = true) { vm.applyNow() }
                ActionRow(stringResource(R.string.fb_cancel), null) { vm.cancel() }
            }
            is FullBackupViewModel.State.Failed -> Text(
                when (s.reason) {
                    BackupException.Reason.WRONG_PASSWORD -> stringResource(R.string.fb_err_password)
                    BackupException.Reason.NOT_A_BACKUP -> stringResource(R.string.fb_err_not_backup)
                    BackupException.Reason.NEWER_VERSION -> stringResource(R.string.fb_err_newer)
                    BackupException.Reason.INCOMPATIBLE -> stringResource(R.string.fb_err_old)
                    null -> stringResource(R.string.fb_err_other, s.detail.orEmpty())
                },
                style = Quorvi.type.caption, color = Quorvi.colors.danger, modifier = Modifier.padding(vertical = 8.dp),
            )
            FullBackupViewModel.State.Idle -> Unit
        }
    }
}
