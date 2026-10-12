package com.paulneja.quorvi.ui.operations

import android.text.format.DateUtils
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.paulneja.quorvi.R
import com.paulneja.quorvi.agent.policy.ApprovalEntity
import com.paulneja.quorvi.agent.policy.ApprovalScope
import com.paulneja.quorvi.agent.policy.Approvals
import com.paulneja.quorvi.agent.policy.AutonomyMode
import com.paulneja.quorvi.agent.policy.Category
import com.paulneja.quorvi.agent.policy.PolicyConfig
import com.paulneja.quorvi.agent.policy.PolicyDao
import com.paulneja.quorvi.agent.policy.PolicySettingEntity
import com.paulneja.quorvi.agent.policy.Rule
import com.paulneja.quorvi.ops.CoreClient
import com.paulneja.quorvi.ui.design.ActionRow
import com.paulneja.quorvi.ui.design.Hint
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.Panel
import com.paulneja.quorvi.ui.design.Section
import com.paulneja.quorvi.ui.design.Segmented
import com.paulneja.quorvi.ui.design.Stepper
import com.paulneja.quorvi.ui.design.SwitchRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PolicyViewModel @Inject constructor(
    private val dao: PolicyDao,
    private val approvals: Approvals,
    private val core: CoreClient,
) : ViewModel() {
    val config = dao.observeSettings().map { list -> PolicyConfig.from(list.associate { it.key to it.value }) }.stateIn(viewModelScope, SharingStarted.Eagerly, PolicyConfig())
    val grants = dao.observeGrants(System.currentTimeMillis()).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val audit = dao.observeAudit().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun pending(runId: String): Flow<ApprovalEntity?> = dao.observePending(runId)

    fun set(key: String, value: String) = viewModelScope.launch { dao.putSetting(PolicySettingEntity(key, value)) }

    fun revoke(id: String) = viewModelScope.launch { dao.revoke(id) }

    fun approve(approval: ApprovalEntity, scope: ApprovalScope) = viewModelScope.launch {
        approvals.approve(approval.id, scope)
        core.resumeRun(approval.runId)
    }

    fun deny(approval: ApprovalEntity, comment: String? = null) = viewModelScope.launch {
        approvals.deny(approval.id)
        val note = comment?.trim()?.takeIf { it.isNotEmpty() }
        core.answerRun(
            approval.runId,
            if (note == null) "The user rejected this action: ${approval.summary}. Do not run it. Stop and ask them, with ask_user, why and whether they want you to change something."
            else "The user rejected this action: ${approval.summary}. Do not run it. Their comment: \"$note\". Follow it.",
        )
    }
}

@Composable
fun ApprovalCard(vm: PolicyViewModel, runId: String) {
    val pending by remember(runId) { vm.pending(runId) }.collectAsState(null)
    val a = pending ?: return
    var commenting by remember(a.id) { mutableStateOf(false) }
    var comment by remember(a.id) { mutableStateOf("") }
    Text(stringResource(R.string.policy_approval_title), style = Quorvi.type.action, color = Quorvi.colors.text, modifier = Modifier.padding(top = 10.dp))
    Text(a.summary, style = Quorvi.type.caption.copy(fontFamily = FontFamily.Monospace), color = Quorvi.colors.text, modifier = Modifier.padding(vertical = 6.dp))
    Hint(policyReason(a.reason))
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (a.action) {
            com.paulneja.quorvi.agent.policy.PolicyService.ROOT -> Chip(stringResource(R.string.policy_allow_root), accent = true) { vm.approve(a, ApprovalScope.ONCE) }
            com.paulneja.quorvi.agent.policy.PolicyService.UNUSUAL -> {
                Chip(stringResource(R.string.policy_allow_once), accent = true) { vm.approve(a, ApprovalScope.ONCE) }
                Chip(stringResource(R.string.policy_allow_session)) { vm.approve(a, ApprovalScope.HOUR) }
            }
            else -> {
                Chip(stringResource(R.string.policy_allow_once), accent = true) { vm.approve(a, ApprovalScope.ONCE) }
                Chip(stringResource(R.string.policy_allow_run)) { vm.approve(a, ApprovalScope.RUN) }
                Chip(stringResource(R.string.policy_allow_hour)) { vm.approve(a, ApprovalScope.HOUR) }
            }
        }
        Chip(stringResource(R.string.policy_deny)) { vm.deny(a) }
        Chip(stringResource(R.string.policy_deny_comment)) { commenting = !commenting }
    }
    androidx.compose.animation.AnimatedVisibility(commenting) {
        Column(Modifier.padding(bottom = 8.dp)) {
            com.paulneja.quorvi.ui.design.InputField(comment, { comment = it }, stringResource(R.string.policy_deny_comment_hint), singleLine = false)
            if (comment.isNotBlank()) Chip(stringResource(R.string.policy_deny_send), accent = true) { vm.deny(a, comment); commenting = false; comment = "" }
        }
    }
}

@Composable
fun PolicyScreen(vm: PolicyViewModel, onBack: () -> Unit) {
    val config by vm.config.collectAsStateWithLifecycle()
    val grants by vm.grants.collectAsStateWithLifecycle()
    val audit by vm.audit.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        ScreenBar(stringResource(R.string.policy_title), onBack)
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            item {
                Panel {
                    Segmented(AutonomyMode.entries.map { it to it.name.lowercase().replaceFirstChar(Char::uppercase) }, config.mode) { vm.set("mode", it.name) }
                    Hint(stringResource(when (config.mode) { AutonomyMode.MANUAL -> R.string.policy_manual_hint; AutonomyMode.AUTO -> R.string.policy_auto_hint; AutonomyMode.BYPASS -> R.string.policy_bypass_hint }))
                }
            }
            item { Section(stringResource(R.string.policy_rules)) }
            item {
                Panel {
                    Category.entries.filter { it != Category.WORKSPACE }.forEach { c ->
                        Text(categoryLabel(c), style = Quorvi.type.body, color = Quorvi.colors.text, modifier = Modifier.padding(top = 10.dp))
                        Segmented(listOf(Rule.ALLOW to stringResource(R.string.policy_rule_allow), Rule.ASK to stringResource(R.string.policy_rule_ask), Rule.DENY to stringResource(R.string.policy_rule_deny)), config.rule(c)) { vm.set("rule.${c.name}", it.name) }
                    }
                    Hint(stringResource(R.string.policy_hard_limits))
                }
            }
            item { Section(stringResource(R.string.policy_conditions)) }
            item {
                Panel {
                    Stepper(stringResource(R.string.policy_min_battery), config.minBattery, 0..50) { vm.set("minBattery", it.toString()) }
                    SwitchRow(stringResource(R.string.policy_unmetered), config.networkOnlyUnmetered) { vm.set("unmetered", it.toString()) }
                }
            }
            item { Section(stringResource(R.string.policy_power)) }
            item {
                Panel {
                    SwitchRow(stringResource(R.string.policy_relax_shell), config.relaxShell) { vm.set("relaxShell", it.toString()) }
                    SwitchRow(stringResource(R.string.policy_relax_network), config.relaxNetwork) { vm.set("relaxNetwork", it.toString()) }
                    Hint(stringResource(R.string.policy_power_hint))
                }
            }
            item { Section(stringResource(R.string.policy_grants)) }
            item {
                Panel {
                    if (grants.isEmpty()) Hint(stringResource(R.string.policy_grants_empty))
                    grants.forEach { g ->
                        ActionRow(
                            "${g.action} · ${if (g.subject == "agent") stringResource(R.string.policy_any_run) else stringResource(R.string.policy_one_run)}",
                            listOfNotNull(g.reason, g.expiresAt?.let { DateUtils.getRelativeTimeSpanString(it).toString() }, g.usesLeft?.let { "×$it" }).joinToString(" · ") + " · " + stringResource(R.string.policy_tap_revoke),
                        ) { vm.revoke(g.id) }
                    }
                }
            }
            item { Section(stringResource(R.string.policy_audit)) }
            items(audit, key = { it.id }) { e ->
                Text(
                    "${DateUtils.formatDateTime(null, e.at, DateUtils.FORMAT_SHOW_TIME)}  ${e.verdict}  ${e.action}  ${e.resource.take(80)}",
                    style = Quorvi.type.caption.copy(fontFamily = FontFamily.Monospace),
                    color = if (e.verdict == "ALLOW") Quorvi.colors.textSecondary else Quorvi.colors.text,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun categoryLabel(c: Category): String = stringResource(
    when (c) {
        Category.SHELL -> R.string.policy_cat_shell
        Category.SSH -> R.string.policy_cat_ssh
        Category.NETWORK -> R.string.policy_cat_network
        Category.EXPORT -> R.string.policy_cat_export
        Category.DEVICES -> R.string.policy_cat_devices
        Category.SCREEN -> R.string.policy_cat_screen
        Category.PRIVILEGED -> R.string.policy_cat_privileged
        Category.PUBLISH -> R.string.policy_cat_publish
        Category.UNUSUAL -> R.string.policy_cat_unusual
        Category.WORKSPACE -> R.string.policy_cat_shell
    },
)

@Composable
private fun policyReason(reason: String): String {
    if (reason == "This command reaches files outside the job's workspace.") return stringResource(R.string.policy_reason_outside)
    val danger = Regex("^This command (.+)\\.$").find(reason)?.groupValues?.get(1) ?: return reason
    val res = when (danger) {
        "deletes files recursively and without asking" -> R.string.policy_reason_0
        "writes straight to a disk device" -> R.string.policy_reason_1
        "runs a script downloaded from the internet" -> R.string.policy_reason_2
        "runs hidden (encoded) commands" -> R.string.policy_reason_3
        "can hang the system (fork bomb)" -> R.string.policy_reason_4
        "restarts or shuts the machine down" -> R.string.policy_reason_5
        "changes permissions of many files at once" -> R.string.policy_reason_6
        "clears the firewall" -> R.string.policy_reason_7
        "kills many processes" -> R.string.policy_reason_8
        "erases schedules or history" -> R.string.policy_reason_9
        "opens a remote shell" -> R.string.policy_reason_10
        "changes apps or system settings" -> R.string.policy_reason_11
        else -> return reason
    }
    return stringResource(R.string.policy_reason_command, stringResource(res))
}
