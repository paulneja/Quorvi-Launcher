package com.paulneja.quorvi.ui.operations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.paulneja.quorvi.R
import com.paulneja.quorvi.agent.capsules.Capsule
import com.paulneja.quorvi.agent.capsules.CapsuleSchedule
import com.paulneja.quorvi.agent.capsules.CapsuleTrigger
import com.paulneja.quorvi.ui.design.ActionRow
import com.paulneja.quorvi.ui.design.Hint
import com.paulneja.quorvi.ui.design.InputField
import com.paulneja.quorvi.ui.design.Label
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.Panel
import com.paulneja.quorvi.ui.design.Section
import com.paulneja.quorvi.ui.design.Stepper

@Composable
fun CapsuleForm(vm: CapsuleViewModel, id: String, onBack: () -> Unit, onJson: () -> Unit, onRun: (String) -> Unit) {
    var loaded by rememberSaveable { mutableStateOf(id == "new") }
    var name by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(CapsuleTrigger.MANUAL) }
    var minutes by rememberSaveable { mutableStateOf(60) }
    var hour by rememberSaveable { mutableStateOf(9) }
    var minute by rememberSaveable { mutableStateOf(0) }
    var days by rememberSaveable { mutableStateOf(listOf(1, 2, 3, 4, 5, 6, 7)) }
    var ssid by rememberSaveable { mutableStateOf("") }
    var app by rememberSaveable { mutableStateOf("") }
    var repo by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        if (id == "new") return@LaunchedEffect
        vm.capsule(id)?.let { c ->
            name = c.name; prompt = c.prompt
            c.trigger.let { t -> kind = t.kind; minutes = t.minutes; hour = t.hour; minute = t.minute; days = t.days; ssid = t.ssid; app = t.app; repo = t.repo }
        }
        loaded = true
    }
    if (!loaded) return
    Column(Modifier.fillMaxSize()) {
        ScreenBar(stringResource(if (id == "new") R.string.capsule_form_new else R.string.capsule_form_edit), onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Section(stringResource(R.string.capsule_form_what)) }
            item {
                Panel {
                    InputField(name, { name = it }, stringResource(R.string.capsule_form_name))
                    InputField(prompt, { prompt = it }, stringResource(R.string.capsule_form_prompt), singleLine = false)
                    Hint(stringResource(R.string.capsule_form_prompt_hint))
                }
            }
            item { Section(stringResource(R.string.capsule_form_when)) }
            item {
                Panel {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        triggerKinds().forEach { (k, label) -> Chip(label, accent = kind == k) { kind = k } }
                    }
                    when (kind) {
                        CapsuleTrigger.INTERVAL -> {
                            Label(stringResource(R.string.capsule_form_every))
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(15, 30, 60, 180, 360, 720, 1440).forEach { m -> Chip(everyLabel(m), accent = minutes == m) { minutes = m } }
                            }
                            Hint(stringResource(R.string.capsule_form_every_hint, CapsuleSchedule.MIN_INTERVAL))
                        }
                        CapsuleTrigger.DAILY -> {
                            Stepper(stringResource(R.string.capsule_form_hour), hour, 0..23) { hour = it }
                            Stepper(stringResource(R.string.capsule_form_minute), minute, 0..59) { minute = it }
                            Label(stringResource(R.string.capsule_form_days))
                            Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(2, 3, 4, 5, 6, 7, 1).zip(stringResource(R.string.capsule_form_day_letters).split(",")).forEach { (d, letter) ->
                                    val on = d in days
                                    Text(
                                        letter, style = Quorvi.type.action, color = if (on) Quorvi.colors.onAccent else Quorvi.colors.textSecondary,
                                        modifier = Modifier.clip(Quorvi.shapes.control).background(if (on) Quorvi.colors.accent else Quorvi.colors.field).clickable { days = if (on) days - d else days + d }.padding(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                        CapsuleTrigger.WIFI -> { InputField(ssid, { ssid = it }, stringResource(R.string.capsule_form_ssid)); Hint(stringResource(R.string.capsule_form_ssid_hint)) }
                        CapsuleTrigger.NOTIFICATION -> { InputField(app, { app = it.trim() }, stringResource(R.string.capsule_form_app)); Hint(stringResource(R.string.capsule_form_app_hint)) }
                        CapsuleTrigger.CI_FAILED -> { InputField(repo, { repo = it.trim() }, stringResource(R.string.capsule_form_repo)); Hint(stringResource(R.string.capsule_form_repo_hint)) }
                        CapsuleTrigger.CHARGING -> Hint(stringResource(R.string.capsule_form_charging_hint))
                        CapsuleTrigger.BOOT -> Hint(stringResource(R.string.capsule_form_boot_hint))
                        else -> Hint(stringResource(R.string.capsule_form_manual_hint))
                    }
                }
            }
            item {
                Panel {
                    AnimatedVisibility(error != null) { Text(error.orEmpty(), style = Quorvi.type.caption, color = Quorvi.colors.danger, modifier = Modifier.padding(vertical = 6.dp)) }
                    if (name.isNotBlank() && prompt.isNotBlank()) ActionRow(stringResource(R.string.hosts_save), null) {
                        val trigger = CapsuleTrigger(kind, minutes, hour, minute, days, ssid.trim(), app, repo)
                        vm.saveCapsule(id.takeIf { it != "new" }, Capsule(name = name.trim(), prompt = prompt.trim(), trigger = trigger)) { err -> error = err; if (err == null) onBack() }
                    }
                    if (id != "new") {
                        ActionRow(stringResource(R.string.capsule_run), null) { vm.run(id)?.let(onRun) }
                        ActionRow(stringResource(R.string.capsule_delete), null, danger = true) { vm.delete(id, onBack) }
                    }
                    ActionRow(stringResource(R.string.capsule_form_json), stringResource(R.string.capsule_form_json_desc), onClick = onJson)
                }
            }
        }
    }
}

@Composable
fun triggerKinds(): List<Pair<String, String>> = listOf(
    CapsuleTrigger.MANUAL to stringResource(R.string.trigger_manual),
    CapsuleTrigger.INTERVAL to stringResource(R.string.trigger_interval),
    CapsuleTrigger.DAILY to stringResource(R.string.trigger_daily),
    CapsuleTrigger.CHARGING to stringResource(R.string.trigger_charging),
    CapsuleTrigger.WIFI to stringResource(R.string.trigger_wifi),
    CapsuleTrigger.NOTIFICATION to stringResource(R.string.trigger_notification),
    CapsuleTrigger.CI_FAILED to stringResource(R.string.trigger_ci),
    CapsuleTrigger.BOOT to stringResource(R.string.trigger_boot),
)

@Composable
private fun everyLabel(m: Int): String = if (m < 60) stringResource(R.string.trigger_every_min, m) else stringResource(R.string.trigger_every_hours, m / 60)

@Composable
fun triggerSummary(t: CapsuleTrigger): String = when (t.kind) {
    CapsuleTrigger.INTERVAL -> everyLabel(t.minutes.coerceAtLeast(CapsuleSchedule.MIN_INTERVAL))
    CapsuleTrigger.DAILY -> stringResource(R.string.trigger_daily_at, "%02d:%02d".format(t.hour, t.minute))
    CapsuleTrigger.WIFI -> if (t.ssid.isBlank()) stringResource(R.string.trigger_wifi) else stringResource(R.string.trigger_wifi_named, t.ssid)
    CapsuleTrigger.NOTIFICATION -> stringResource(R.string.trigger_notification_from, t.app)
    CapsuleTrigger.CI_FAILED -> stringResource(R.string.trigger_ci)
    CapsuleTrigger.CHARGING -> stringResource(R.string.trigger_charging)
    CapsuleTrigger.BOOT -> stringResource(R.string.trigger_boot)
    else -> stringResource(R.string.trigger_manual)
}
