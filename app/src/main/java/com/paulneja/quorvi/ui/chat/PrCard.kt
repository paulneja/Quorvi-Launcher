package com.paulneja.quorvi.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.paulneja.quorvi.R
import com.paulneja.quorvi.agent.chat.ChatMessageEntity
import com.paulneja.quorvi.agent.ultra.PrChange
import com.paulneja.quorvi.agent.ultra.PrDiff
import com.paulneja.quorvi.agent.ultra.PrDraft
import com.paulneja.quorvi.agent.ultra.PrFile
import com.paulneja.quorvi.agent.ultra.PrStatus
import com.paulneja.quorvi.ui.design.GeistMono
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Glyph
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.liquidGlass

private const val EDIT_CAP = 60_000

@Composable
fun PrCard(
    m: ChatMessageEntity,
    animate: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onEdit: (title: String, body: String, commitMessage: String, excluded: List<String>, done: (String?) -> Unit) -> Unit,
    onEditFile: (path: String, content: String, done: (String?) -> Unit) -> Unit,
) {
    val draft = remember(m.text) { PrDraft.decode(m.text) } ?: return
    val context = LocalContext.current
    val diffs = remember(draft.diff) { PrDiff.byFile(draft.diff) }
    var open by rememberSaveable(m.id) { mutableStateOf<String?>(null) }
    var editing by rememberSaveable(m.id) { mutableStateOf(false) }
    var fileEditing by remember(m.id) { mutableStateOf<PrFile?>(null) }
    var notice by remember(m.id) { mutableStateOf<String?>(null) }
    val needOne = stringResource(R.string.pr_need_one_file)
    val border by animateColorAsState(
        when (draft.status) {
            PrStatus.SUBMITTED -> Quorvi.colors.success
            PrStatus.BLOCKED, PrStatus.FAILED -> Quorvi.colors.danger
            else -> Quorvi.colors.accent
        }.copy(alpha = 0.6f), tween(300), label = "border",
    )
    fun setExcluded(path: String, out: Boolean) {
        val next = if (out) draft.excluded + path else draft.excluded - path
        onEdit(draft.title, draft.body, draft.commitMessage, next) { err -> notice = if (err == "no-files") needOne else err }
    }
    Column(
        Modifier.fillMaxWidth().padding(start = 38.dp).entrance(animate, fromBelow = false)
            .clip(RoundedCornerShape(22.dp)).liquidGlass(RoundedCornerShape(22.dp), GlassLevel.PANEL).border(1.dp, border, RoundedCornerShape(22.dp)).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Quorvi.colors.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) { Glyph(Glyphs.PullRequest, 17.dp, Quorvi.colors.accent) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.pr_draft), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
                Text(draft.title, style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("${draft.repo} · ${draft.baseBranch}", style = Quorvi.type.caption, color = Quorvi.colors.textTertiary, modifier = Modifier.padding(top = 6.dp))
        Summary(draft.included)
        listOf(PrChange.ADDED to R.string.pr_group_added, PrChange.MODIFIED to R.string.pr_group_modified, PrChange.DELETED to R.string.pr_group_deleted).forEach { (kind, label) ->
            val group = draft.files.filter { it.kind == kind }
            if (group.isEmpty()) return@forEach
            Text(stringResource(label).uppercase() + " · ${group.size}", style = Quorvi.type.label.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.textTertiary, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
            group.forEach { f ->
                FileRow(
                    f, expanded = open == f.path, diff = diffs[f.path], excluded = f.path in draft.excluded, edited = f.path in draft.edited, editable = draft.editable,
                    onToggle = { open = if (open == f.path) null else f.path },
                    onExclude = { setExcluded(f.path, f.path !in draft.excluded) },
                    onEditFile = { fileEditing = f },
                )
            }
        }
        AnimatedVisibility(notice != null) { Text(notice.orEmpty(), style = Quorvi.type.caption, color = Quorvi.colors.danger, modifier = Modifier.padding(top = 8.dp)) }
        Text(stringResource(R.string.pr_commit), style = Quorvi.type.caption, color = Quorvi.colors.textTertiary, modifier = Modifier.padding(top = 12.dp))
        Text(draft.commitMessage, style = Quorvi.type.mono.copy(fontSize = 12.sp), color = Quorvi.colors.text, modifier = Modifier.padding(top = 2.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF07090D)).padding(10.dp))
        if (draft.body.isNotBlank()) MarkdownText(draft.body.take(1200), modifier = Modifier.padding(top = 8.dp), color = Quorvi.colors.textSecondary)
        Spacer(Modifier.height(12.dp))
        AnimatedContent(draft.status, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() }, label = "status") { status ->
            when (status) {
                PrStatus.PENDING, PrStatus.FAILED -> Column {
                    if (status == PrStatus.FAILED) Text(stringResource(R.string.pr_failed, draft.error.orEmpty()), style = Quorvi.type.caption, color = Quorvi.colors.danger, modifier = Modifier.padding(bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(stringResource(if (status == PrStatus.FAILED) R.string.chat_retry else R.string.pr_create), accent = true, onClick = onApprove)
                        Pill(stringResource(R.string.pr_edit)) { editing = true }
                        Pill(stringResource(R.string.pr_discard), onClick = onReject)
                    }
                }
                PrStatus.SUBMITTING -> ShimmerText(stringResource(R.string.pr_submitting))
                PrStatus.SUBMITTED -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(Glyphs.Check, 16.dp, Quorvi.colors.success)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pr_submitted, draft.number ?: 0), style = Quorvi.type.action, color = Quorvi.colors.success, modifier = Modifier.weight(1f))
                    draft.url?.let { url -> Pill(stringResource(R.string.pr_open)) { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) } } }
                }
                PrStatus.BLOCKED -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Quorvi.colors.danger.copy(alpha = 0.14f)).padding(12.dp)) {
                    Text(stringResource(R.string.pr_blocked_title), style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.danger)
                    Text(stringResource(R.string.pr_blocked_body), style = Quorvi.type.caption, color = Quorvi.colors.text, modifier = Modifier.padding(top = 4.dp))
                    Text(draft.error.orEmpty(), style = Quorvi.type.mono.copy(fontSize = 12.sp), color = Quorvi.colors.danger, modifier = Modifier.padding(top = 6.dp))
                }
                else -> Text(stringResource(R.string.pr_discarded), style = Quorvi.type.caption, color = Quorvi.colors.textTertiary)
            }
        }
    }
    if (editing) PrEditDialog(draft, onDismiss = { editing = false }) { t, b, c, done -> onEdit(t, b, c, draft.excluded, done) }
    fileEditing?.let { f -> FileEditDialog(f, onDismiss = { fileEditing = null }) { content, done -> onEditFile(f.path, content, done) } }
}

@Composable
private fun Summary(files: List<PrFile>) {
    val counts = listOf(
        PrChange.ADDED to R.string.pr_summary_added, PrChange.MODIFIED to R.string.pr_summary_modified, PrChange.DELETED to R.string.pr_summary_deleted,
    ).mapNotNull { (kind, res) -> files.count { it.kind == kind }.takeIf { it > 0 }?.let { stringResource(res, it) } }
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("+${files.sumOf { it.additions }}", style = Quorvi.type.mono.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = Quorvi.colors.success)
        Text(" −${files.sumOf { it.deletions }}", style = Quorvi.type.mono.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = Quorvi.colors.danger)
        Text("  ·  " + counts.joinToString(" · "), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FileRow(
    f: PrFile, expanded: Boolean, diff: String?, excluded: Boolean, edited: Boolean, editable: Boolean,
    onToggle: () -> Unit, onExclude: () -> Unit, onEditFile: () -> Unit,
) {
    val (glyph, tint) = when (f.kind) {
        PrChange.ADDED -> Glyphs.Plus to Quorvi.colors.success
        PrChange.DELETED -> Glyphs.Trash to Quorvi.colors.danger
        else -> Glyphs.Edit to Quorvi.colors.accent
    }
    val turn by animateFloatAsState(if (expanded) 180f else 0f, tween(160), label = "chevron")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onToggle).padding(vertical = 5.dp, horizontal = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(tint.copy(alpha = if (excluded) 0.06f else 0.16f)), contentAlignment = Alignment.Center) {
                Glyph(glyph, 12.dp, if (excluded) Quorvi.colors.textTertiary else tint)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                f.path, style = Quorvi.type.mono.copy(fontSize = 12.sp, textDecoration = if (excluded) TextDecoration.LineThrough else null),
                color = if (excluded) Quorvi.colors.textTertiary else Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (f.additions > 0) Text("+${f.additions}", style = Quorvi.type.mono.copy(fontSize = 12.sp), color = Quorvi.colors.success)
            if (f.deletions > 0) Text(" −${f.deletions}", style = Quorvi.type.mono.copy(fontSize = 12.sp), color = Quorvi.colors.danger)
            Spacer(Modifier.width(6.dp))
            Glyph(Glyphs.Down, 12.dp, Quorvi.colors.textTertiary, Modifier.rotate(turn))
        }
        if (excluded || edited) Row(Modifier.padding(start = 28.dp, top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (excluded) Text(stringResource(R.string.pr_excluded), style = Quorvi.type.label, color = Quorvi.colors.warning)
            if (edited) Text(stringResource(R.string.pr_edited), style = Quorvi.type.label, color = Quorvi.colors.accent)
        }
        AnimatedVisibility(expanded, enter = expandVertically(tween(160)) + fadeIn(tween(160)), exit = shrinkVertically(tween(140)) + fadeOut(tween(120))) {
            Column {
                if (diff != null) DiffView(diff) else Text(stringResource(R.string.pr_no_diff), style = Quorvi.type.caption, color = Quorvi.colors.textTertiary, modifier = Modifier.padding(top = 6.dp))
                if (editable) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val content = f.content
                    if (content != null) {
                        if (content.length <= EDIT_CAP) Pill(stringResource(R.string.pr_edit_file), onClick = onEditFile)
                        else Text(stringResource(R.string.pr_file_too_big, content.length / 1024), style = Quorvi.type.caption, color = Quorvi.colors.textTertiary)
                    }
                    Pill(stringResource(if (excluded) R.string.pr_include else R.string.pr_exclude), onClick = onExclude)
                }
            }
        }
    }
}

@Composable
private fun DiffView(diff: String) {
    Column(Modifier.padding(top = 6.dp).fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF07090D)).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(10.dp)) {
        diff.lines().take(600).forEach { line ->
            val color = when {
                line.startsWith("diff --git") || line.startsWith("index ") || line.startsWith("new file") || line.startsWith("deleted file") -> Quorvi.colors.textTertiary
                line.startsWith("+++") || line.startsWith("---") -> Quorvi.colors.textSecondary
                line.startsWith("+") -> Quorvi.colors.success
                line.startsWith("-") -> Quorvi.colors.danger
                line.startsWith("@@") -> Quorvi.colors.accent
                else -> Color(0xFFC9CED6)
            }
            Text(line, style = Quorvi.type.mono.copy(fontSize = 11.5.sp, fontFamily = GeistMono), color = color, softWrap = false)
        }
    }
}

@Composable
private fun PrEditDialog(draft: PrDraft, onDismiss: () -> Unit, onSave: (String, String, String, (String?) -> Unit) -> Unit) {
    var title by rememberSaveable { mutableStateOf(draft.title) }
    var body by rememberSaveable { mutableStateOf(draft.body) }
    var commit by rememberSaveable { mutableStateOf(draft.commitMessage) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp).imePadding().clip(RoundedCornerShape(24.dp)).background(Color(0xFF18181B))
                .border(1.dp, Color(0xFF34343B), RoundedCornerShape(24.dp)).verticalScroll(rememberScrollState()).padding(18.dp),
        ) {
            Text(stringResource(R.string.pr_edit_title), style = Quorvi.type.title.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text)
            Field(stringResource(R.string.pr_title_field), title, singleLine = true) { title = it }
            Field(stringResource(R.string.pr_body_field), body, minHeight = 120) { body = it }
            Field(stringResource(R.string.pr_commit_field), commit, minHeight = 72, mono = true) { commit = it }
            AnimatedVisibility(error != null) {
                Column(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Quorvi.colors.danger.copy(alpha = 0.14f)).padding(10.dp)) {
                    Text(stringResource(R.string.pr_blocked_title), style = Quorvi.type.caption.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.danger)
                    Text(error.orEmpty(), style = Quorvi.type.mono.copy(fontSize = 12.sp), color = Quorvi.colors.danger, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                Pill(stringResource(R.string.pr_cancel)) { if (!saving) onDismiss() }
                Pill(stringResource(R.string.pr_save), accent = true) {
                    if (saving || title.isBlank() || commit.isBlank()) return@Pill
                    saving = true
                    onSave(title, body, commit) { e -> saving = false; if (e == null) onDismiss() else error = e }
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, singleLine: Boolean = false, minHeight: Int = 0, mono: Boolean = false, onChange: (String) -> Unit) {
    Text(label, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
    val style: TextStyle = if (mono) Quorvi.type.mono.copy(fontSize = 13.sp, color = Quorvi.colors.text) else Quorvi.type.body.copy(color = Quorvi.colors.text)
    BasicTextField(
        value, onChange, singleLine = singleLine, textStyle = style, cursorBrush = SolidColor(Quorvi.colors.accent),
        modifier = Modifier.fillMaxWidth().heightIn(min = minHeight.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF25252A)).padding(12.dp),
    )
}

@Composable
private fun FileEditDialog(file: PrFile, onDismiss: () -> Unit, onSave: (String, (String?) -> Unit) -> Unit) {
    var text by rememberSaveable(file.path) { mutableStateOf(file.content.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF111113)).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable { if (!saving) onDismiss() }, contentAlignment = Alignment.Center) { Glyph(Glyphs.Close, 18.dp, Quorvi.colors.textSecondary) }
                Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                    Text(stringResource(R.string.pr_edit_file), style = Quorvi.type.label, color = Quorvi.colors.textTertiary)
                    Text(file.path, style = Quorvi.type.mono.copy(fontSize = 13.sp), color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Pill(stringResource(R.string.pr_save), accent = true) {
                    if (saving || text == file.content) return@Pill
                    saving = true; error = null
                    onSave(text) { e -> saving = false; if (e == null) onDismiss() else error = e }
                }
            }
            AnimatedVisibility(saving) { Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { ShimmerText(stringResource(R.string.pr_saving_file)) } }
            AnimatedVisibility(error != null) { Text(error.orEmpty(), style = Quorvi.type.caption, color = Quorvi.colors.danger, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            BasicTextField(
                text, { text = it }, enabled = !saving,
                textStyle = Quorvi.type.mono.copy(fontSize = 12.5.sp, fontFamily = GeistMono, color = Color(0xFFE6E8EC), lineHeight = 18.sp),
                cursorBrush = SolidColor(Quorvi.colors.accent),
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF18181B)).padding(12.dp),
            )
        }
    }
}
