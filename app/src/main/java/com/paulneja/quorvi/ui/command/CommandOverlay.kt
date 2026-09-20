package com.paulneja.quorvi.ui.command

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.combinedClickable
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paulneja.quorvi.R
import com.paulneja.quorvi.core.command.CommandAction
import com.paulneja.quorvi.core.command.CommandGlyph
import com.paulneja.quorvi.core.command.CommandKind
import com.paulneja.quorvi.core.command.CommandResult
import com.paulneja.quorvi.core.command.QuorviAction
import com.paulneja.quorvi.launcher.apps.Shortcuts
import com.paulneja.quorvi.ui.canvas.AppIcon
import com.paulneja.quorvi.ui.canvas.CanvasViewModel
import com.paulneja.quorvi.ui.design.GlassLevel
import com.paulneja.quorvi.ui.design.Glyphs
import com.paulneja.quorvi.ui.design.Quorvi
import com.paulneja.quorvi.ui.design.liquidGlass

@Composable
fun CommandOverlay(
    viewModel: CanvasViewModel,
    shortcuts: Shortcuts,
    onQuorviAction: (QuorviAction) -> Unit,
    onClose: () -> Unit,
) {
    val query by viewModel.commandQuery.collectAsStateWithLifecycle()
    val results by viewModel.commandResults.collectAsStateWithLifecycle()
    val recent by viewModel.commandRecent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val motion = Quorvi.motion
    val appear = remember { Animatable(if (motion.enabled) 0f else 1f) }
    val copiedTemplate = stringResource(R.string.command_copied)
    val agentStarted = stringResource(R.string.command_agent_started)
    val agentUnavailable = stringResource(R.string.command_agent_unavailable)
    val grant = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.setCommandQuery(query) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    var armed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) { armed = null }
    LaunchedEffect(Unit) {
        viewModel.openCommand()
        runCatching { focus.requestFocus() }
        keyboard?.show()
        appear.animateTo(1f, motion.quick())
    }
    BackHandler(onBack = onClose)

    fun run(result: CommandResult) {
        viewModel.recordCommand(result)
        when (val action = result.action) {
            is CommandAction.LaunchApp -> { viewModel.launch(action.app); onClose() }
            is CommandAction.LaunchShortcut -> { shortcuts.start(action.shortcut); onClose() }
            is CommandAction.Start -> { runCatching { context.startActivity(action.intent) }; onClose() }
            is CommandAction.Copy -> {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Quorvi", action.text))
                Toast.makeText(context, copiedTemplate.replace("%1\$s", action.text), Toast.LENGTH_SHORT).show()
            }
            is CommandAction.Quorvi -> { onClose(); onQuorviAction(action.id) }
            is CommandAction.Grant -> grant.launch(action.permission)
            is CommandAction.Capsule -> {
                com.paulneja.quorvi.agent.capsules.CapsuleLaunch.start(context, action.id)
                Toast.makeText(context, result.title, Toast.LENGTH_SHORT).show()
                onClose()
            }
            is CommandAction.Agent -> {
                onClose()
                com.paulneja.quorvi.ui.chat.ChatActivity.open(context, prompt = action.goal)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value }
            .background(Color(0xFF0B0C10).copy(alpha = 0.62f))
            .pointerInput(Unit) { detectTapGestures { onClose() } },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.ime)
                .graphicsLayer { translationY = (1f - appear.value) * -24f },
        ) {
            Row(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(Quorvi.shapes.panel)
                    .background(Quorvi.colors.field)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(rememberVectorPainter(Glyphs.Search), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(Quorvi.colors.accent))
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = query,
                    onValueChange = viewModel::setCommandQuery,
                    singleLine = true,
                    textStyle = Quorvi.type.body.copy(color = Quorvi.colors.text),
                    cursorBrush = SolidColor(Quorvi.colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let(::run) }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text(stringResource(R.string.command_hint), style = Quorvi.type.body, color = Quorvi.colors.textSecondary)
                        inner()
                    },
                )
                if (query.isNotEmpty()) {
                    Image(
                        rememberVectorPainter(Glyphs.Close), null,
                        Modifier.size(32.dp).clip(Quorvi.shapes.control).clickable { viewModel.setCommandQuery("") }.padding(6.dp),
                        colorFilter = ColorFilter.tint(Quorvi.colors.textSecondary),
                    )
                }
            }
            if (query.isEmpty() && recent.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recent) { text ->
                        Text(
                            text, style = Quorvi.type.caption, color = Quorvi.colors.text, maxLines = 1,
                            modifier = Modifier.clip(Quorvi.shapes.control).background(Quorvi.colors.overlayHover).clickable { viewModel.setCommandQuery(text) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            LazyColumn(
                Modifier.weight(1f).pointerInput(Unit) { detectTapGestures { } },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (query.isEmpty() && results.isNotEmpty()) item { SectionLabel(stringResource(R.string.command_suggested)) }
                items(results, key = { it.key }) { result ->
                    val app = result.app.takeIf { query.isEmpty() && result.kind == CommandKind.APP }
                    ResultRow(
                        result,
                        onClick = { if (armed == result.key) armed = null else run(result) },
                        onLongClick = app?.let { { armed = if (armed == result.key) null else result.key } },
                        action = if (app != null && armed == result.key) ({ armed = null; viewModel.setSuggested(app, false) }) else null,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp))
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ResultRow(result: CommandResult, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, action: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val haptics = com.paulneja.quorvi.ui.design.rememberHaptics()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(Quorvi.shapes.control)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick?.let { { haptics.pickup(); it() } })
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            when {
                result.kind == CommandKind.APP && result.app != null -> AppIcon(result.app, 40.dp)
                result.image != null -> Image(result.image, null, Modifier.size(32.dp))
                else -> Box(Modifier.size(36.dp).clip(Quorvi.shapes.control).background(Quorvi.colors.overlayActive), contentAlignment = Alignment.Center) {
                    Image(rememberVectorPainter(glyphFor(result.glyph)), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(Quorvi.colors.text))
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(result.title, style = Quorvi.type.action, color = Quorvi.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            result.subtitle?.let { Text(it, style = Quorvi.type.caption, color = Quorvi.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = action != null,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.85f) + androidx.compose.animation.expandHorizontally(expandFrom = Alignment.End),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.85f) + androidx.compose.animation.shrinkHorizontally(shrinkTowards = Alignment.End),
        ) {
            Row(
                Modifier.padding(start = 8.dp).clip(Quorvi.shapes.pill).background(Quorvi.colors.overlayActive).clickable { action?.invoke() }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(rememberVectorPainter(Glyphs.Hide), null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(Quorvi.colors.text))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.command_unsuggest), style = Quorvi.type.caption, color = Quorvi.colors.text, maxLines = 1)
            }
        }
    }
}

private fun glyphFor(glyph: CommandGlyph): ImageVector = when (glyph) {
    CommandGlyph.SETTINGS -> Glyphs.Gear
    CommandGlyph.CONTACT -> Glyphs.Contact
    CommandGlyph.MEDIA -> Glyphs.Wallpaper
    CommandGlyph.CALC -> Glyphs.Calc
    CommandGlyph.WEB -> Glyphs.Search
    CommandGlyph.LINK -> Glyphs.Shortcut
    CommandGlyph.QUORVI -> Glyphs.Studio
    CommandGlyph.AGENT -> Glyphs.Agent
    CommandGlyph.LOCK -> Glyphs.Lock
    CommandGlyph.APP -> Glyphs.Widgets
}
