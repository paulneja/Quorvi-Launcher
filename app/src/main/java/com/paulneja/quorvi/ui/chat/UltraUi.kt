package com.paulneja.quorvi.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paulneja.quorvi.R
import com.paulneja.quorvi.agent.chat.ChatMessageEntity
import com.paulneja.quorvi.agent.chat.ChatMode
import com.paulneja.quorvi.agent.models.ProviderKind
import com.paulneja.quorvi.agent.models.ReasoningEffort
import com.paulneja.quorvi.agent.models.ReasoningSupport
import com.paulneja.quorvi.agent.ultra.CommitLanguage
import com.paulneja.quorvi.agent.ultra.CommitStyle
import com.paulneja.quorvi.agent.ultra.PrDraft
import com.paulneja.quorvi.agent.ultra.PrStatus
import com.paulneja.quorvi.ui.design.GeistMono
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Glyph
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.QuorviSegmented
import com.paulneja.quorvi.ui.design.liquidGlass

@Composable
fun ModeSwitch(mode: String, onMode: (String) -> Unit) {
    val ultra = mode == ChatMode.ULTRA
    BoxWithConstraints(
        Modifier.width(260.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.07f)).padding(4.dp),
    ) {
        val half = maxWidth / 2
        val x by animateDpAsState(if (ultra) half else 0.dp, spring(dampingRatio = 0.62f, stiffness = 420f), label = "thumb")
        Box(
            Modifier.offset(x = x).width(half).fillMaxHeight().clip(RoundedCornerShape(18.dp))
                .background(if (ultra) Brush.linearGradient(listOf(Color(0xFF7B5CFF), Quorvi.colors.accent)) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.10f)))),
        )
        Row(Modifier.fillMaxSize()) {
            listOf(ChatMode.CHAT to R.string.mode_chat, ChatMode.ULTRA to R.string.mode_ultra).forEach { (m, label) ->
                Row(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).clickable { onMode(m) }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Glyph(if (m == ChatMode.ULTRA) Glyphs.Git else Glyphs.Message, 15.dp, if (m == mode) Color.White else Quorvi.colors.textSecondary)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(label), style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = if (m == mode) Color.White else Quorvi.colors.textSecondary)
                }
            }
        }
    }
}

@Composable
fun RepoChip(repo: String?, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).liquidGlass(RoundedCornerShape(16.dp), GlassLevel.CHROME).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(Glyphs.Git, 15.dp, Quorvi.colors.accent)
        Spacer(Modifier.width(8.dp))
        Text(repo ?: stringResource(R.string.ultra_pick_repo), style = Quorvi.type.action, color = if (repo == null) Quorvi.colors.accent else Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(6.dp))
        Glyph(Glyphs.Down, 13.dp, Quorvi.colors.textTertiary)
    }
}

@Composable
fun RepoPicker(vm: ChatViewModel, onDone: () -> Unit) {
    val repos by vm.repos.collectAsState()
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadRepos() }
    BackHandler(onBack = onDone)
    Box(Modifier.fillMaxSize().background(Quorvi.colors.scrim.copy(alpha = 0.6f)).clickable(onClick = onDone)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 560.dp).windowInsetsPadding(WindowInsets.safeDrawing)
                .liquidGlass(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), GlassLevel.OVERLAY).clickable(enabled = false) {}.padding(16.dp),
        ) {
            Text(stringResource(R.string.ultra_pick_repo), style = Quorvi.type.title.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text)
            Text(stringResource(R.string.ultra_pick_repo_desc), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.07f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph(Glyphs.Search, 16.dp, Quorvi.colors.textSecondary)
                Spacer(Modifier.width(8.dp))
                BasicTextField(query, { query = it }, singleLine = true, textStyle = Quorvi.type.body.copy(color = Quorvi.colors.text), cursorBrush = SolidColor(Quorvi.colors.accent), modifier = Modifier.weight(1f),
                    decorationBox = { inner -> Box { if (query.isEmpty()) Text(stringResource(R.string.ultra_search_repo), style = Quorvi.type.body, color = Quorvi.colors.textTertiary); inner() } })
            }
            Spacer(Modifier.height(8.dp))
            val result = repos
            when {
                result == null -> ShimmerText(stringResource(R.string.ultra_loading_repos))
                result.isFailure -> Text(stringResource(R.string.ultra_repos_error), style = Quorvi.type.caption, color = Quorvi.colors.danger, modifier = Modifier.padding(8.dp))
                else -> LazyColumn {
                    item {
                        RepoRow(stringResource(R.string.ultra_no_repo), null) { vm.pickRepo(null); onDone() }
                    }
                    val list = result.getOrNull().orEmpty().filter { query.isBlank() || it.fullName.contains(query, true) }
                    items(list, key = { it.fullName }) { r -> RepoRow(r.fullName, if (r.private) stringResource(R.string.ultra_private) else null) { vm.pickRepo(r.fullName); onDone() } }
                }
            }
        }
    }
}

@Composable
private fun RepoRow(name: String, tag: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(Glyphs.Git, 16.dp, Quorvi.colors.textSecondary)
        Spacer(Modifier.width(10.dp))
        Text(name, style = Quorvi.type.action, color = Quorvi.colors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        tag?.let { Text(it, style = Quorvi.type.caption, color = Quorvi.colors.textTertiary) }
    }
}

@Composable
fun ReasoningSheet(vm: ChatViewModel, onDone: () -> Unit) {
    val o by vm.orchestra.collectAsState()
    val providers by vm.providerList.collectAsState()
    val main = o.coordinator?.primary?.let { ref -> providers.firstOrNull { it.id == ref.providerId }?.let { it to ref.model } } ?: providers.firstOrNull()?.let { it to it.model }
    val support = main?.let { (p, model) -> ReasoningSupport.of(runCatching { ProviderKind.valueOf(p.kind) }.getOrDefault(ProviderKind.OPENAI_COMPATIBLE), model) } ?: ReasoningSupport.NONE
    val effort = o.coordinator?.effort ?: o.effort
    val thinking = o.coordinator?.thinking ?: o.thinking
    fun setEffort(e: ReasoningEffort?) = vm.updateOrchestra { cur -> cur.coordinator?.let { cur.copy(coordinator = it.copy(effort = e)) } ?: cur.copy(effort = e) }
    fun setThinking(v: Boolean) = vm.updateOrchestra { cur -> cur.coordinator?.let { cur.copy(coordinator = it.copy(thinking = v)) } ?: cur.copy(thinking = v) }
    BackHandler(onBack = onDone)
    Box(Modifier.fillMaxSize().background(Quorvi.colors.scrim.copy(alpha = 0.55f)).clickable(onClick = onDone)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing)
                .liquidGlass(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), GlassLevel.OVERLAY).clickable(enabled = false) {}.padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(Glyphs.Brain, 20.dp, Quorvi.colors.accent)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(stringResource(R.string.reasoning_title), style = Quorvi.type.title.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text)
                    Text(main?.let { "${it.first.name} · ${it.second}" } ?: stringResource(R.string.chat_no_model_title), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary)
                }
            }
            Text(stringResource(R.string.reasoning_effort), style = Quorvi.type.body, color = Quorvi.colors.text, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            Box(Modifier.graphicsLayer { alpha = if (support.effort) 1f else 0.4f }) {
                QuorviSegmented(
                    listOf<Pair<ReasoningEffort?, String>>(
                        null to stringResource(R.string.orc_effort_auto), ReasoningEffort.LOW to stringResource(R.string.orc_effort_low),
                        ReasoningEffort.MEDIUM to stringResource(R.string.orc_effort_medium), ReasoningEffort.HIGH to stringResource(R.string.orc_effort_high),
                        ReasoningEffort.MAX to stringResource(R.string.orc_effort_max),
                    ),
                    if (support.effort) effort else null, { if (support.effort) setEffort(it) },
                )
            }
            if (!support.effort) Text(stringResource(R.string.reasoning_effort_unsupported), style = Quorvi.type.caption, color = Quorvi.colors.textTertiary, modifier = Modifier.padding(top = 6.dp))
            ToggleLine(stringResource(R.string.reasoning_thinking), stringResource(if (support.thinking) R.string.reasoning_thinking_desc else R.string.reasoning_thinking_unsupported), thinking && support.thinking, enabled = support.thinking) { setThinking(it) }
            Spacer(Modifier.height(6.dp))
            Pill(stringResource(R.string.chat_close), accent = true, onClick = onDone)
        }
    }
}

@Composable
fun UltraScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val c by vm.ultra.collectAsState()
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        ScreenBar(stringResource(R.string.ultra_title), onBack)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Glyph(Glyphs.Git, 20.dp, Quorvi.colors.accent)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.ultra_what), style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text)
                    }
                    Text(stringResource(R.string.ultra_what_desc), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
                }
            }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Quorvi.colors.danger.copy(alpha = 0.10f)).border(1.dp, Quorvi.colors.danger.copy(alpha = 0.35f), RoundedCornerShape(22.dp)).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Glyph(Glyphs.Lock, 18.dp, Quorvi.colors.danger)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.ultra_rule_title), style = Quorvi.type.action.copy(fontWeight = FontWeight.SemiBold), color = Quorvi.colors.text)
                    }
                    Text(stringResource(R.string.ultra_rule_body), style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
                    var adding by remember { mutableStateOf("") }
                    if (c.allowedCoauthors.isNotEmpty()) {
                        Text(stringResource(R.string.ultra_allowed), style = Quorvi.type.caption, color = Quorvi.colors.textTertiary, modifier = Modifier.padding(top = 10.dp))
                        c.allowedCoauthors.forEach { who ->
                            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(who, style = Quorvi.type.mono.copy(fontSize = 12.sp), color = Quorvi.colors.text, modifier = Modifier.weight(1f))
                                Pill(stringResource(R.string.orc_remove), danger = true) { vm.updateUltra { it.copy(allowedCoauthors = it.allowedCoauthors - who) } }
                            }
                        }
                    }
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(adding, { adding = it }, singleLine = true, textStyle = Quorvi.type.mono.copy(color = Quorvi.colors.text, fontSize = 12.sp), cursorBrush = SolidColor(Quorvi.colors.accent),
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.06f)).padding(10.dp),
                            decorationBox = { inner -> Box { if (adding.isEmpty()) Text("Nombre <email>", style = Quorvi.type.mono.copy(fontSize = 12.sp), color = Quorvi.colors.textTertiary); inner() } })
                        Spacer(Modifier.width(8.dp))
                        Pill(stringResource(R.string.ultra_allow)) { if (adding.contains("<") && adding.contains("@")) { vm.updateUltra { it.copy(allowedCoauthors = (it.allowedCoauthors + adding.trim()).distinct()) }; adding = "" } }
                    }
                }
            }
            item {
                Card {
                    Text(stringResource(R.string.ultra_commit_style), style = Quorvi.type.body, color = Quorvi.colors.text)
                    Spacer(Modifier.height(8.dp))
                    QuorviSegmented(
                        listOf(CommitStyle.CONVENTIONAL to stringResource(R.string.ultra_style_conventional), CommitStyle.SIMPLE to stringResource(R.string.ultra_style_simple), CommitStyle.CUSTOM to stringResource(R.string.ultra_style_custom)),
                        c.commitStyle, { s -> vm.updateUltra { it.copy(commitStyle = s) } },
                    )
                    AnimatedVisibility(c.commitStyle == CommitStyle.CUSTOM) {
                        var custom by remember(c.customStyle) { mutableStateOf(c.customStyle) }
                        Column {
                            BasicTextField(custom, { custom = it }, textStyle = Quorvi.type.body.copy(color = Quorvi.colors.text), cursorBrush = SolidColor(Quorvi.colors.accent), minLines = 2,
                                modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).padding(12.dp),
                                decorationBox = { inner -> Box { if (custom.isEmpty()) Text(stringResource(R.string.ultra_style_custom_hint), style = Quorvi.type.body, color = Quorvi.colors.textTertiary); inner() } })
                            if (custom != c.customStyle) Row(Modifier.padding(top = 8.dp)) { Pill(stringResource(R.string.chat_save), accent = true) { vm.updateUltra { it.copy(customStyle = custom) } } }
                        }
                    }
                    Text(stringResource(R.string.ultra_commit_language), style = Quorvi.type.body, color = Quorvi.colors.text, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                    QuorviSegmented(listOf(CommitLanguage.ES to "Español", CommitLanguage.EN to "English"), c.commitLanguage, { l -> vm.updateUltra { it.copy(commitLanguage = l) } })
                    var prefix by remember(c.branchPrefix) { mutableStateOf(c.branchPrefix) }
                    Text(stringResource(R.string.ultra_branch_prefix), style = Quorvi.type.body, color = Quorvi.colors.text, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(prefix, { prefix = it }, singleLine = true, textStyle = Quorvi.type.mono.copy(color = Quorvi.colors.text), cursorBrush = SolidColor(Quorvi.colors.accent),
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.06f)).padding(10.dp))
                        if (prefix != c.branchPrefix) { Spacer(Modifier.width(8.dp)); Pill(stringResource(R.string.chat_save), accent = true) { vm.updateUltra { it.copy(branchPrefix = prefix) } } }
                    }
                    ToggleLine(stringResource(R.string.ultra_draft_prs), stringResource(R.string.ultra_draft_prs_desc), c.draftPullRequests) { v -> vm.updateUltra { it.copy(draftPullRequests = v) } }
                    ToggleLine(stringResource(R.string.ultra_commands), stringResource(R.string.ultra_commands_desc), c.allowCommands) { v -> vm.updateUltra { it.copy(allowCommands = v) } }
                }
            }
            item {
                Card {
                    Text(stringResource(R.string.ultra_instructions), style = Quorvi.type.body, color = Quorvi.colors.text)
                    var text by remember(c.instructions) { mutableStateOf(c.instructions) }
                    BasicTextField(text, { text = it }, textStyle = Quorvi.type.body.copy(color = Quorvi.colors.text), cursorBrush = SolidColor(Quorvi.colors.accent), minLines = 3,
                        modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).padding(12.dp),
                        decorationBox = { inner -> Box { if (text.isEmpty()) Text(stringResource(R.string.ultra_instructions_hint), style = Quorvi.type.body, color = Quorvi.colors.textTertiary); inner() } })
                    if (text != c.instructions) Row(Modifier.padding(top = 8.dp)) { Pill(stringResource(R.string.chat_save), accent = true) { vm.updateUltra { it.copy(instructions = text) } } }
                }
            }
        }
    }
}
