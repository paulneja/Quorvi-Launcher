package com.paulneja.quorvi.ui.operations

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paulneja.quorvi.R
import com.paulneja.quorvi.ops.RunState
import com.paulneja.quorvi.ops.StepEntity
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Glyph
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.ProgressLine
import com.paulneja.quorvi.ui.design.liquidGlass

@Composable
fun runStateLabel(state: RunState?): String = stringResource(
    when (state) {
        RunState.QUEUED -> R.string.rs_queued
        RunState.PLANNING -> R.string.rs_planning
        RunState.RUNNING, RunState.RETRYING -> R.string.rs_running
        RunState.VERIFYING -> R.string.rs_verifying
        RunState.WAITING_USER -> R.string.rs_waiting_user
        RunState.WAITING_CAPABILITY -> R.string.rs_waiting_capability
        RunState.PAUSING, RunState.PAUSED -> R.string.rs_paused
        RunState.CANCELLING, RunState.CANCELLED -> R.string.rs_cancelled
        RunState.COMPLETED -> R.string.rs_completed
        RunState.FAILED -> R.string.rs_failed
        RunState.INTERRUPTED -> R.string.rs_interrupted
        null -> R.string.rs_queued
    },
)

@Composable
fun RunChat(viewModel: OperationsViewModel, policy: PolicyViewModel, id: String, onBack: () -> Unit) {
    val run by remember(id) { viewModel.run(id) }.collectAsState(null)
    val steps by remember(id) { viewModel.runSteps(id) }.collectAsState(emptyList())
    val events by remember(id) { viewModel.events(id) }.collectAsState(emptyList())
    val artifacts by remember(id) { viewModel.artifacts(id) }.collectAsState(emptyList())
    val context = LocalContext.current
    val list = rememberLazyListState()
    var showLog by rememberSaveable { mutableStateOf(false) }
    val r = run ?: return
    val state = runCatching { RunState.valueOf(r.state) }.getOrNull()
    var question by remember(r.id, r.updatedAt) { mutableStateOf<String?>(null) }
    LaunchedEffect(r.id, r.updatedAt) { if (state == RunState.WAITING_USER) question = viewModel.question(r.id) }
    LaunchedEffect(steps.size, artifacts.size, question) { if (steps.isNotEmpty()) list.animateScrollToItem(list.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1) }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Glyph(Glyphs.Back, 20.dp) }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(Quorvi.colors.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Glyph(Glyphs.Agent, 18.dp, Quorvi.colors.accent) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.chat_agent), style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text)
                Text(runStateLabel(state) + (r.model?.let { " · $it" } ?: ""), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (state != null && !state.terminal) {
                ChatAction(if (state == RunState.PAUSED || state == RunState.INTERRUPTED || state == RunState.WAITING_CAPABILITY) stringResource(R.string.ops_resume) else stringResource(R.string.ops_pause)) {
                    if (state == RunState.PAUSED || state == RunState.INTERRUPTED || state == RunState.WAITING_CAPABILITY) viewModel.resumeRun(r.id) else viewModel.pauseRun(r.id)
                }
                Spacer(Modifier.width(6.dp))
                ChatAction(stringResource(R.string.chat_stop), danger = true) { viewModel.cancelRun(r.id) }
            }
        }
        val done = steps.count { it.state == "SUCCEEDED" || it.state == "SKIPPED" }
        if (state != null && !state.terminal && steps.isNotEmpty()) ProgressLine(done.toFloat() / steps.size, modifier = Modifier.padding(horizontal = 16.dp), height = 3.dp)

        LazyColumn(state = list, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { MineBubble(r.goal, DateUtils.formatDateTime(context, r.createdAt, DateUtils.FORMAT_SHOW_TIME)) }
            if (steps.isEmpty() && state != null && !state.terminal) item { AgentBubble { Text(stringResource(R.string.chat_thinking), style = Quorvi.type.body, color = Quorvi.colors.textSecondary) } }
            items(steps, key = { it.id }) { step -> StepBubble(step) }
            if (artifacts.isNotEmpty()) item {
                AgentBubble {
                    Text(stringResource(R.string.chat_files), style = Quorvi.type.body, color = Quorvi.colors.text)
                    artifacts.forEach { a ->
                        Row(
                            Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f))
                                .clickable {
                                    a.exportedUri?.let { uri -> runCatching { context.startActivity(Intent(context, com.paulneja.quorvi.runtime.OpenActivity::class.java).setDataAndType(Uri.parse(uri), a.mime)) } }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Glyph(Glyphs.Note, 20.dp, Quorvi.colors.accent)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.name, style = Quorvi.type.action, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOf(a.mime, android.text.format.Formatter.formatShortFileSize(context, a.size)).joinToString(" · ") + if (a.verified) " · ✓" else "", style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (state == RunState.WAITING_USER) item {
                AgentBubble {
                    ApprovalCard(policy, r.id)
                    question?.let { Text(it, style = Quorvi.type.body, color = Quorvi.colors.text) }
                }
            }
            when (state) {
                RunState.COMPLETED -> item { AgentBubble { Text(stringResource(R.string.chat_done), style = Quorvi.type.body, color = Quorvi.colors.success) } }
                RunState.FAILED -> item { AgentBubble { Text(stringResource(R.string.chat_failed) + (r.error?.let { "\n$it" } ?: ""), style = Quorvi.type.body, color = Quorvi.colors.danger) } }
                else -> Unit
            }
            if (events.isNotEmpty()) item {
                Text(
                    stringResource(if (showLog) R.string.chat_hide_log else R.string.chat_show_log),
                    style = Quorvi.type.caption, color = Quorvi.colors.accent,
                    modifier = Modifier.padding(start = 6.dp, top = 6.dp).clickable { showLog = !showLog }.padding(4.dp),
                )
            }
            if (showLog) items(events, key = { "${it.runId}-${it.sequence}" }) { ev ->
                Text(
                    DateUtils.formatDateTime(context, ev.timestamp, DateUtils.FORMAT_SHOW_TIME) + "  " + ev.actor + " · " + ev.message,
                    style = Quorvi.type.mono, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }

        if (state == RunState.WAITING_USER) {
            var answer by rememberSaveable(r.id) { mutableStateOf("") }
            Row(
                Modifier.padding(12.dp).fillMaxWidth().liquidGlass(Quorvi.shapes.pill, GlassLevel.CHROME).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = answer, onValueChange = { answer = it },
                    textStyle = Quorvi.type.body.copy(color = Quorvi.colors.text), cursorBrush = SolidColor(Quorvi.colors.accent),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner -> if (answer.isEmpty()) Text(stringResource(R.string.ops_answer_hint), style = Quorvi.type.body, color = Quorvi.colors.textSecondary); inner() },
                )
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(if (answer.isBlank()) Color.White.copy(alpha = 0.1f) else Quorvi.colors.accent)
                        .clickable(enabled = answer.isNotBlank()) { viewModel.answerRun(r.id, answer.trim()); answer = "" },
                    contentAlignment = Alignment.Center,
                ) { Glyph(Glyphs.Send, 18.dp, if (answer.isBlank()) Quorvi.colors.textSecondary else Quorvi.colors.onAccent) }
            }
        }
    }
}

@Composable
private fun ChatAction(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        label, style = Quorvi.type.caption.copy(fontWeight = FontWeight.SemiBold), color = if (danger) Quorvi.colors.danger else Quorvi.colors.text,
        modifier = Modifier.clip(Quorvi.shapes.pill).background(if (danger) Quorvi.colors.danger.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.08f)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun MineBubble(text: String, time: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Text(
            text, style = Quorvi.type.body, color = Quorvi.colors.onAccent,
            modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)).background(Quorvi.colors.accent).padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Text(time, style = Quorvi.type.caption, color = Quorvi.colors.textTertiary, modifier = Modifier.padding(top = 3.dp, end = 4.dp))
    }
}

@Composable
private fun AgentBubble(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Quorvi.colors.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Glyph(Glyphs.Agent, 15.dp, Quorvi.colors.accent) }
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp)).liquidGlass(RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp), GlassLevel.PANEL).padding(horizontal = 14.dp, vertical = 10.dp).animateContentSize(),
        ) { content() }
    }
}

@Composable
private fun StepBubble(step: StepEntity) {
    var open by remember(step.id) { mutableStateOf(false) }
    AgentBubble {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (glyph, tint) = when (step.state) {
                "SUCCEEDED", "SKIPPED" -> Glyphs.Check to Quorvi.colors.success
                "FAILED", "CANCELLED" -> Glyphs.Close to Quorvi.colors.danger
                "RUNNING" -> Glyphs.Bolt to Quorvi.colors.accent
                else -> Glyphs.Timer to Quorvi.colors.textTertiary
            }
            Glyph(glyph, 16.dp, tint)
            Spacer(Modifier.width(8.dp))
            Text(step.title, style = Quorvi.type.body, color = Quorvi.colors.text, modifier = Modifier.weight(1f, fill = false))
        }
        step.toolName?.let {
            Text(it, style = Quorvi.type.mono, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        step.error?.let { Text(it, style = Quorvi.type.caption, color = Quorvi.colors.danger, modifier = Modifier.padding(top = 6.dp)) }
        val output = step.output?.takeIf { it.isNotBlank() }
        if (output != null) {
            Text(
                stringResource(if (open) R.string.chat_hide_output else R.string.chat_show_output),
                style = Quorvi.type.caption, color = Quorvi.colors.accent,
                modifier = Modifier.padding(top = 6.dp).clickable { open = !open }.padding(vertical = 2.dp),
            )
            if (open) Text(
                output.take(4000), style = Quorvi.type.mono, color = Quorvi.colors.text,
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF080A0E)).padding(10.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}
