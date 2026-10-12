package com.paulneja.quorvi.ui.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.paulneja.quorvi.R
import com.paulneja.quorvi.agent.engine.AiSwitch
import com.paulneja.quorvi.agent.jobs.BackgroundJobs
import com.paulneja.quorvi.core.settings.QuorviSettings
import com.paulneja.quorvi.ops.OpsDao
import com.paulneja.quorvi.ops.RunState
import com.paulneja.quorvi.ui.design.ActionRow
import com.paulneja.quorvi.ui.design.Hint
import com.paulneja.quorvi.ui.design.Panel
import com.paulneja.quorvi.ui.design.SwitchRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AiViewModel @Inject constructor(
    private val ai: AiSwitch,
    private val ops: OpsDao,
    private val jobs: BackgroundJobs,
    settings: QuorviSettings,
) : ViewModel() {
    val enabled = ai.enabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val voiceWanted = combine(ai.enabled, settings.voice) { on, v -> on && v.enabled }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    suspend fun busy(): Int {
        val runs = BUSY.sumOf { runCatching { ops.runsIn(it.name).size }.getOrDefault(0) }
        return runs + runCatching { jobs.aliveCount() }.getOrDefault(0)
    }

    fun set(on: Boolean) = viewModelScope.launch { ai.set(on) }

    private companion object {
        val BUSY = listOf(RunState.QUEUED, RunState.PLANNING, RunState.RUNNING, RunState.PAUSING, RunState.RETRYING, RunState.VERIFYING)
    }
}

fun isAssistant(context: Context): Boolean =
    runCatching { context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT) }.getOrDefault(false)

@Composable
fun AiSwitchPanel(vm: AiViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val on by vm.enabled.collectAsStateWithLifecycle()
    val voiceWanted by vm.voiceWanted.collectAsStateWithLifecycle()
    var pending by remember { mutableIntStateOf(0) }
    var reclaim by remember { mutableStateOf(false) }
    Panel {
        SwitchRow(stringResource(R.string.ai_switch), on) { next ->
            if (next) {
                pending = 0
                vm.set(true)
                reclaim = true
            } else scope.launch {
                val busy = vm.busy()
                if (busy > 0) pending = busy else vm.set(false)
            }
        }
        Hint(stringResource(if (on) R.string.ai_switch_on else R.string.ai_switch_off))
        if (pending > 0) {
            Hint(pluralStringResource(R.plurals.ai_off_confirm, pending, pending))
            ActionRow(stringResource(R.string.ai_off_stop), null, danger = true) {
                pending = 0
                vm.set(false)
            }
            ActionRow(stringResource(R.string.ai_off_keep), null) { pending = 0 }
        }
        if (on && reclaim && voiceWanted && !isAssistant(context)) {
            ActionRow(stringResource(R.string.ai_voice_reclaim), stringResource(R.string.voice_make_assistant_desc)) {
                runCatching { context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }
}
