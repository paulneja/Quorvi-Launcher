package com.paulneja.quorvi.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.paulneja.quorvi.R
import com.paulneja.quorvi.agent.engine.AgentLimits
import com.paulneja.quorvi.agent.engine.LimitsStore
import com.paulneja.quorvi.ui.design.ActionRow
import com.paulneja.quorvi.ui.design.Hint
import com.paulneja.quorvi.ui.design.Panel
import com.paulneja.quorvi.ui.design.SliderRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LimitsViewModel @Inject constructor(private val store: LimitsStore) : ViewModel() {
    val limits = store.limits.stateIn(viewModelScope, SharingStarted.Eagerly, AgentLimits())

    fun update(change: (AgentLimits) -> AgentLimits) = viewModelScope.launch { store.update(change) }
}

@Composable
fun LimitsSettings(vm: LimitsViewModel) {
    val l by vm.limits.collectAsStateWithLifecycle()
    val minutes = { v: Float -> "${v.roundToInt()} min" }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Spacer(Modifier.height(12.dp))
        Panel {
            SliderRow(stringResource(R.string.limits_command), l.commandMinutes.toFloat(), 1f..AgentLimits.MAX_COMMAND_MINUTES.toFloat(), minutes) { v -> vm.update { it.copy(commandMinutes = v.roundToInt()) } }
            Hint(stringResource(R.string.limits_command_desc))
            SliderRow(stringResource(R.string.limits_ssh), l.sshMinutes.toFloat(), 1f..AgentLimits.MAX_COMMAND_MINUTES.toFloat(), minutes) { v -> vm.update { it.copy(sshMinutes = v.roundToInt()) } }
            Hint(stringResource(R.string.limits_ssh_desc))
        }
        Spacer(Modifier.height(12.dp))
        Panel {
            SliderRow(stringResource(R.string.limits_model_idle), l.modelIdleSeconds.toFloat(), 30f..900f, { v -> "${v.roundToInt()} s" }) { v -> vm.update { it.copy(modelIdleSeconds = v.roundToInt()) } }
            Hint(stringResource(R.string.limits_model_idle_desc))
            SliderRow(stringResource(R.string.limits_model_total), l.modelTotalMinutes.toFloat(), 2f..120f, minutes) { v -> vm.update { it.copy(modelTotalMinutes = v.roundToInt()) } }
            Hint(stringResource(R.string.limits_model_total_desc))
        }
        Spacer(Modifier.height(12.dp))
        Panel {
            Hint(stringResource(R.string.limits_jobs_desc))
            ActionRow(stringResource(R.string.limits_reset), null) { vm.update { AgentLimits() } }
        }
    }
}
