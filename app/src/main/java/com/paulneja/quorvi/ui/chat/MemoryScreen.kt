package com.paulneja.quorvi.ui.chat

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.paulneja.quorvi.R
import com.paulneja.quorvi.agent.memory.MemoryEntity
import com.paulneja.quorvi.ui.design.Glyph
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.Quorvi

@Composable
fun MemoryScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val all by vm.memories.collectAsState()
    val o by vm.orchestra.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val shown = remember(all, query) { all.filter { query.isBlank() || it.text.contains(query, ignoreCase = true) } }
    val pending = shown.filter { !it.reviewed }
    val kept = shown.filter { it.reviewed }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        ScreenBar(stringResource(R.string.mem_title), onBack)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Orb(44.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(stringResource(R.string.mem_intro), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.weight(1f))
                    }
                    ToggleLine(stringResource(R.string.mem_use), stringResource(R.string.mem_use_desc), o.memory) { v -> vm.updateOrchestra { it.copy(memory = v) } }
                    ToggleLine(stringResource(R.string.mem_auto), stringResource(R.string.mem_auto_desc), o.autoMemory, enabled = o.memory) { v -> vm.updateOrchestra { it.copy(autoMemory = v) } }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.07f)).padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(Glyphs.Search, 16.dp, Quorvi.colors.textSecondary)
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = Quorvi.type.body.copy(color = Quorvi.colors.text), cursorBrush = SolidColor(Quorvi.colors.accent), modifier = Modifier.weight(1f),
                        decorationBox = { inner -> Box { if (query.isEmpty()) Text(stringResource(R.string.mem_search), style = Quorvi.type.body, color = Quorvi.colors.textTertiary); inner() } })
                }
            }
            if (pending.isNotEmpty()) {
                item { Text(stringResource(R.string.mem_pending), style = Quorvi.type.section, color = Quorvi.colors.textTertiary, modifier = Modifier.padding(start = 6.dp, top = 8.dp)) }
                items(pending, key = { it.id }) { m -> MemoryRow(m, Modifier.animateItem(), onKeep = { vm.keep(m) }, onPin = null, onForget = { vm.forget(m.id) }) }
            }
            if (kept.isNotEmpty()) {
                item { Text(stringResource(R.string.mem_kept), style = Quorvi.type.section, color = Quorvi.colors.textTertiary, modifier = Modifier.padding(start = 6.dp, top = 8.dp)) }
                items(kept, key = { it.id }) { m -> MemoryRow(m, Modifier.animateItem(), onKeep = null, onPin = { vm.pin(m) }, onForget = { vm.forget(m.id) }) }
            }
            if (all.isEmpty()) item {
                Text(stringResource(R.string.mem_empty), style = Quorvi.type.body, color = Quorvi.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(28.dp))
            }
            if (all.isNotEmpty()) item {
                Row(Modifier.padding(top = 12.dp)) {
                    Pill(stringResource(if (confirm) R.string.mem_forget_all_confirm else R.string.mem_forget_all), danger = true) { if (confirm) { vm.forgetAll(); confirm = false } else confirm = true }
                }
            }
        }
    }
}

@Composable
private fun MemoryRow(m: MemoryEntity, modifier: Modifier, onKeep: (() -> Unit)?, onPin: (() -> Unit)?, onForget: () -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = if (m.reviewed) 0.05f else 0.08f)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(m.text, style = Quorvi.type.body, color = Quorvi.colors.text, modifier = Modifier.weight(1f))
            if (onPin != null) Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onPin), contentAlignment = Alignment.Center) {
                Glyph(Glyphs.Pin, 16.dp, if (m.pinned) Quorvi.colors.accent else Quorvi.colors.textTertiary)
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(kindLabel(m.kind), style = Quorvi.type.caption.copy(fontWeight = FontWeight.Medium), color = Quorvi.colors.accent, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Quorvi.colors.accent.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp))
            Text(m.source, style = Quorvi.type.caption, color = Quorvi.colors.textTertiary, modifier = Modifier.weight(1f), maxLines = 1)
            onKeep?.let { Pill(stringResource(R.string.mem_keep), accent = true, onClick = it) }
            Pill(stringResource(R.string.mem_forget), danger = true, onClick = onForget)
        }
    }
}

@Composable
private fun kindLabel(kind: String): String = stringResource(
    when (kind.uppercase()) {
        "PREFERENCE" -> R.string.mem_kind_preference
        "PROJECT" -> R.string.mem_kind_project
        "PROCEDURAL" -> R.string.mem_kind_procedure
        "EPISODIC" -> R.string.mem_kind_episode
        else -> R.string.mem_kind_fact
    },
)
